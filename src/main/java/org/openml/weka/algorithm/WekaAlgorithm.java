/*
BSD 3-Clause License

Copyright (c) 2017, Jan N. van Rijn <j.n.van.rijn@liacs.leidenuniv.nl>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.*/

package org.openml.weka.algorithm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.FlowExists;
import org.openml.apiconnector.xml.Parameter;
import org.openml.apiconnector.xml.Run.Parameter_setting;
import org.openml.apiconnector.xml.SetupExists;
import org.openml.apiconnector.xml.SetupParameters;
import org.openml.apiconnector.xstream.XstreamXmlMapping;

import weka.classifiers.Classifier;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.RevisionHandler;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;
import weka.core.Version;
import weka.core.setupgenerator.AbstractParameter;
import weka.core.stemmers.Stemmer;
import weka.experiment.SplitEvaluator;

public class WekaAlgorithm {
	
	public static String getVersion(String algorithm) {
		String version = "undefined";
		try {
			RevisionHandler classifier = (RevisionHandler) Class.forName(algorithm).newInstance();
			if( StringUtils.isAlphanumeric( classifier.getRevision() )) {
				version = classifier.getRevision();
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch(Exception e) {
			e.printStackTrace();
		}
		return version;
	}

	private static boolean isSimpleParameter(String[] defaultValues) throws Exception {
		int count = 0;
		for  (String s : defaultValues) {
			if (s.startsWith("weka.")) {
				count += 1;
			}
		}
		if (count > 0 && count < defaultValues.length) {
			throw new Exception("Can not determine whether parameter is of simple type. ");
		}
		return count == 0;
	}
	
	private static boolean isAssignableFrom(Class<?> parent, String[] values) throws Exception {
		int count = 0; 
		for  (String currentValue : values) {
			String[] currentValueSplitted = Utils.splitOptions(currentValue);
			if (currentValueSplitted.length == 0) {
				continue;
			}
			Class<?> child = Class.forName(currentValueSplitted[0]);
			
			if (parent.isAssignableFrom(child)) {
				count += 1;
			}
		}
		
		if (count > 0 && count < values.length) {
			throw new Exception("Can not determine type of complex type. ");
		}
		return count == values.length;
	}
	
	public static String[] getMultipleParamValues(String[] parameters, String name) throws Exception {
		String paramValue = Utils.getOption(name, parameters);
		List<String> paramValues = new ArrayList<String>(); 
		while (!paramValue.equals("")) {
			paramValues.add(paramValue);
			paramValue = Utils.getOption(name, parameters);
		}
		return paramValues.toArray(new String[paramValues.size()]);
	}
	
	public static String parameterValuesToJson(String[] defaultValues, Integer maxLength) throws Exception {
		JSONArray values = new JSONArray();
		for (int i = 0; i < defaultValues.length; ++i) {
			values.put(defaultValues[i]);
		}
		String result = values.toString();
		if (maxLength != null) {
			if (result.length() > maxLength) {
				throw new Exception("Parameter value longer than allowed " + maxLength + " characters. ");
			}
		}
		return result;
	}
	
	public static boolean containsEscapableCharacter(String value) {
		// Function copied from weka.core.Utils:joinOptions()
		// important to determine whether the Options of an OptionHandling Component should go between quotes or behind dashes
		boolean foundEscapableCharacter = false;
		for (int n = 0; n < value.length(); n++) {
			if (Character.isWhitespace(value.charAt(n)) || value.charAt(n) == '"' || value.charAt(n) == '\'') {
				foundEscapableCharacter = true;
				break;
			}
        }
		return foundEscapableCharacter;
	}
	
	public static Integer getSetupId(String classifierName, String option_str, OpenmlConnector apiconnector) throws Exception {
		// first find flow. if the flow doesn't exist, neither does the setup.
		Classifier classifier = (Classifier) Class.forName(classifierName).newInstance();
		Flow find = WekaAlgorithm.serializeClassifier((OptionHandler) classifier, null);
		int flow_id = -1;
		try {
			FlowExists result = apiconnector.flowExists(find.getName(), find.getExternal_version());
			if(result.exists()) { 
				flow_id = result.getId(); 
			} else {
				return null;
			}
		} catch( Exception e ) {
			return null;
		}
		Flow implementation = apiconnector.flowGet(flow_id);
		
		String[] params = Utils.splitOptions(option_str);
		List<Parameter_setting> list = WekaAlgorithm.getParameterSetting(params, implementation);
		
		// now create the setup object
		Run run = new Run(null, null, implementation.getId(), null, list.toArray(new Parameter_setting[list.size()]), null);
		File setup = Conversion.stringToTempFile(XstreamXmlMapping.getInstance().toXML(run), "setup", "xml");
		SetupExists se = apiconnector.setupExists(setup);
		
		if (se.exists()) {
			return se.getId();
		} else {
			return null;
		}
	}
	
	public static int getImplementationId(Flow implementation, Classifier classifier, OpenmlConnector apiconnector) throws Exception {
		try {
			// First ask OpenML whether this implementation already exists
			FlowExists result = apiconnector.flowExists(implementation.getName(), implementation.getExternal_version());
			if(result.exists()) return result.getId();
		} catch( Exception e ) { /* Suppress Exception since it is totally OK. */ }
		// It does not exist. Create it. 
		int flowId = apiconnector.flowUpload(implementation);
		return flowId;
	}

	public static Flow serializeClassifier(OptionHandler classifierOrig, String[] tags) throws Exception {
		String classifier_name = classifierOrig.getClass().getName();
		Object classifierNew = Class.forName(classifier_name).newInstance();
		String[] defaultOptions = ((OptionHandler) classifierNew).getClass().newInstance().getOptions();
		String[] currentOptions = classifierOrig.getOptions();
		
		Map<String, Parameter> flowParameters = new LinkedHashMap<String, Parameter>();
		Map<String, Flow> flowComponents = new LinkedHashMap<String, Flow>();
		
		Enumeration<Option> parameters = ((OptionHandler) classifierNew).listOptions();
		//System.out.println(Arrays.toString(defaultOptions));
		while(parameters.hasMoreElements()) {
			Option parameter = parameters.nextElement();
			// JvR 30/05/2018: It is my conjecture that all hyperparameters after this one are specific to 
			// kernel/base-classifier and should not be used in the main classifier. 
			if (parameter.name().equals("") && parameter.synopsis().startsWith("\nOptions specific to")) {
				// TODO: do something better with lookup in childeren's parameter maps.
				break;
			}

			if (flowParameters.containsKey(parameter.name())) {
				throw new Exception("Duplicate parameter: " + parameter.name());
			}
			
			if(parameter.name().trim().equals("")) {
				throw new Exception("Empty parameter name: " + parameter.name());
			}
			String[] defaultValues = new String[0];
			String[] currentValues = new String[0];
			if(parameter.numArguments() == 0) {
				defaultValues = ArrayUtils.add(defaultValues, Utils.getFlag(parameter.name(), defaultOptions) == true ? "true" : "false");
				currentValues = ArrayUtils.add(currentValues, Utils.getFlag(parameter.name(), currentOptions) == true ? "true" : "false");
			} else {
				String currentDefaultValue = Utils.getOption(parameter.name(), defaultOptions);
				// each option can occur multiple times in the option string
				while(!currentDefaultValue.equals("")) {
					defaultValues = ArrayUtils.add(defaultValues, currentDefaultValue);
					currentDefaultValue = Utils.getOption(parameter.name(), defaultOptions);
				}
				String currentCurrentValue = Utils.getOption(parameter.name(), currentOptions);
				while(!currentCurrentValue.equals("")) {
					currentValues = ArrayUtils.add(currentValues, currentCurrentValue);
					currentCurrentValue = Utils.getOption(parameter.name(), currentOptions);
				}
			}
			// System.out.println("- " + classifierOrig.getClass().getName() + "_" + parameter.name() + " (" + parameter.numArguments() + " args), default: " + parameterValuesToJson(defaultValues) + "; current: " +  parameterValuesToJson(currentValues));
			
			if (defaultValues.length == 0 || isSimpleParameter(defaultValues)) {
				// Parameter with vanilla option (recognized by integer, float or empty value)
				// also string values that do not start with "weka." are considered "normal parameters" (if a string value starts with "weka." it is probably a class)
				WekaParameterType type;
				if (parameter.numArguments() == 0) {
					type = WekaParameterType.FLAG;
				} else {
					type = WekaParameterType.OPTION;
				}
				
				Parameter current = new Parameter(parameter.name(), type.getName(), parameterValuesToJson(defaultValues, Constants.MAX_LENGTH_PARAM_VALUE), parameter.description());
				flowParameters.put(parameter.name(), current);
				continue;
			}
			
			if (currentValues.length == 0) {
				throw new Exception("Inferred complex parameter type but no values set. ");
			}
			
			if (isAssignableFrom(AbstractParameter.class, currentValues)) {
				WekaParameterType type = WekaParameterType.ARRAY;
				Parameter current = new Parameter(parameter.name(), type.getName(), parameterValuesToJson(new String[0], Constants.MAX_LENGTH_PARAM_VALUE), parameter.description());
				flowParameters.put(parameter.name(), current);
			} else if (isAssignableFrom(Classifier.class, currentValues) && currentValues.length == 1 && containsEscapableCharacter(currentValues[0]) == false) {
				// Meta algorithms and stuff. All parameters follow from the hyphen in currentOptions
				String[] subclassifierOptions = Utils.partitionOptions(currentOptions);
				Object parameterObject = Utils.forName(Classifier.class, currentValues[0], Arrays.copyOf(subclassifierOptions, subclassifierOptions.length));
				Flow subimplementation = serializeClassifier((OptionHandler) parameterObject, tags);
				WekaParameterType type = WekaParameterType.CLASSIFIER;
				
				String[] currentParamDefaultValue = {currentValues[0]};
				Parameter current = new Parameter(parameter.name(), type.getName(), parameterValuesToJson(currentParamDefaultValue, Constants.MAX_LENGTH_PARAM_VALUE), parameter.description());
				flowParameters.put(parameter.name(), current);
				flowComponents.put(parameter.name() + "_0", subimplementation);
			} else if (isAssignableFrom(OptionHandler.class, currentValues)) {
				// Kernels etc. All parameters of the optionahandler are on the same currentOptions entry
				
				String[] selectedDefaultValues = new String[0];
				for (int i = 0; i < currentValues.length; ++i) {
					String[] currentValueSplitted = Utils.splitOptions(currentValues[i]);
					selectedDefaultValues = ArrayUtils.add(selectedDefaultValues, currentValueSplitted[0]);
					Class<?> parameterClass = Class.forName(currentValueSplitted[0]);
					// TODO: determine if we really need the sub classifier options here
					String[] options = Arrays.copyOfRange(currentValueSplitted, 1, currentValueSplitted.length);
					Object parameterObject = Utils.forName(parameterClass, 
														   currentValueSplitted[0], 
						                                   options);
					
					Flow subimplementation = serializeClassifier((OptionHandler) parameterObject, tags);
					flowComponents.put(parameter.name() + "_" + i, subimplementation);
				}
				
				Parameter current = new Parameter(parameter.name(), WekaParameterType.OPTIONHANDLER.getName(), parameterValuesToJson(selectedDefaultValues, Constants.MAX_LENGTH_PARAM_VALUE), parameter.description());
				flowParameters.put(parameter.name(), current);
			} else if (isAssignableFrom(Stemmer.class, currentValues)) {
				WekaParameterType type = WekaParameterType.PARAMETERFREE_CLASS;
				Parameter current = new Parameter(parameter.name(), type.getName(), parameterValuesToJson(defaultValues, Constants.MAX_LENGTH_PARAM_VALUE), parameter.description());
				flowParameters.put(parameter.name(), current);
			} else {
				System.err.print(parameter.name() + ", " + parameter.numArguments());
				throw new Exception("Classifier contains an unsupported parameter type. Name: " + parameter.name());
			}
		}
		
		String version = getVersion(classifier_name);
		String description = "Weka implementation. ";
		String language = "English";
		String dependencies = "Weka_" + Version.VERSION;
		
		if(classifierNew instanceof TechnicalInformationHandler) {
			description = ((TechnicalInformationHandler) classifierNew).getTechnicalInformation().toString();
		}
		
		String flowName = classifierNew.getClass().getName();
		if (flowComponents.size() > 0) {
			flowName += "(";
			for (Flow component : flowComponents.values()) {
				flowName += component.getName() + ",";
			}
			flowName = flowName.substring(0, flowName.length()-1) + ")";
		}
		
		Flow i = new Flow(flowName, classifierNew.getClass().getName(), dependencies + "_" + version, description, language, dependencies);
		if (tags != null) {
			for(String tag : tags) {
				i.addTag(tag);
			}
		}
		for (String key : flowComponents.keySet()) {
			i.addComponent(key, flowComponents.get(key));
		}
		for (Parameter p : flowParameters.values()) {
			i.addParameter(p);
		}
		
		int paramCount = i.countParameters();
		if (paramCount > Constants.MAX_ALLOWED_PARAMETERS) {
			throw new Exception("Flow got " + paramCount + " parameters, only " + Constants.MAX_ALLOWED_PARAMETERS + " allowed: " + i.getName());
		}
		if (i.getName().length() > Constants.MAX_LENGTH_FLOW_NAME) {
			throw new Exception("Flow name is " + i.getName().length() + " characters, only " + Constants.MAX_LENGTH_FLOW_NAME + " characters allowed: " + i.getName());
		}
		
		return i;
	}
	
	public static OptionHandler deserializeClassifier(Flow f) throws Exception {
		SetupParameters sp = new SetupParameters(f.getId(), f.getParametersRecursive());
		return deserializeSetup(sp, f, true);
	}
	
	public static OptionHandler deserializeSetup(SetupParameters setup, Flow f, boolean defaultParameters) throws Exception {
		String baseclass = f.getName().split("\\(")[0];
		String[] allOptions = setupToOptionArray(setup, f, 0, defaultParameters);
		System.out.println(Arrays.toString(allOptions));
		OptionHandler result = (OptionHandler) Utils.forName(OptionHandler.class, baseclass, allOptions);
		return result;
	}

	private static String[] setupToOptionArray(SetupParameters setup, Flow f, int level, boolean useDefaultValues) throws Exception {
		if (f.getId() == null) {
			throw new Exception("Can only deserialize setups based on flows with ids. Flow: " + f.getName());
		}
		String[] primaryOptions = new String[0];
		String[] secondaryOptions = new String[0];
		for (Parameter p : setup.getParameters()) {
			// System.out.println(f.getName() + "_" + p.getName() + " " + p.getFlow_id() + " - " + f.getId());
			if (! p.getFlow_id().equals(f.getId())) {
				continue;
			}
			
			WekaParameterType type = WekaParameterType.fromString(p.getData_type());
			JSONArray currentValues = new JSONArray(p.getDefault_value());
			if (!useDefaultValues) {
				currentValues = new JSONArray(p.getValue());
			}
			
			if (currentValues.length() > 1 && type != WekaParameterType.OPTIONHANDLER && type != WekaParameterType.ARRAY) {
				throw new Exception("Found multiple values in not-allowed parameter: " + f.getName() + "_" + p.getName());
			}
			
			switch(type) {
				case CLASSIFIER: {
						String[] currentValueSplitted = Utils.splitOptions(currentValues.getString(0));
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
						primaryOptions = ArrayUtils.add(primaryOptions, currentValueSplitted[0]);
						secondaryOptions = ArrayUtils.add(secondaryOptions, "--");
						secondaryOptions = ArrayUtils.addAll(secondaryOptions, setupToOptionArray(setup, f.getSubImplementation(p.getName() + "_0"), level, useDefaultValues));
						break;
				} case OPTIONHANDLER: {
					for (int i = 0; i < currentValues.length(); ++i) {
						String[] currentValueSplitted = Utils.splitOptions(currentValues.getString(i));
						String[] subOptions = setupToOptionArray(setup, f.getSubImplementation(p.getName() + "_" + i), level + 1, useDefaultValues);
						String subWithOptions = currentValueSplitted[0] + " " + StringUtils.join(subOptions, " ");
						if (level > 0) {
							subWithOptions = "\"" + Utils.backQuoteChars(subWithOptions) + "\""; // TODO: fix me!
						}
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
						primaryOptions = ArrayUtils.add(primaryOptions, subWithOptions);
					}
					break;
				} case OPTION: {
					if (currentValues.length() > 0) {
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
						primaryOptions = ArrayUtils.add(primaryOptions, Utils.backQuoteChars(currentValues.getString(0)));
					}
					break;
				} case PARAMETERFREE_CLASS: {
					// should be the same as option. 
					if (currentValues.length() > 0) {
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
						primaryOptions = ArrayUtils.add(primaryOptions, currentValues.getString(0));
					}
					break;
				} case FLAG: {
					if (currentValues.getString(0).equals("true")) {
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
					}
					break;
				} case ARRAY: {
					for (int i = 0; i < currentValues.length(); i++) {
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
						primaryOptions = ArrayUtils.add(primaryOptions, currentValues.getString(i));
					}
					break;
				}
			}
		}
		
		String[] allOptions = ArrayUtils.addAll(primaryOptions, secondaryOptions);
		return allOptions;
	}
	
	public static ArrayList<Parameter_setting> getParameterSetting(String[] parameters, Flow implementation) throws Exception {
		ArrayList<Parameter_setting> settings = new ArrayList<Parameter_setting>();
		if (implementation.getParameter() != null) {
			for(Parameter p : implementation.getParameter()) {
				WekaParameterType type = WekaParameterType.fromString(p.getData_type());
				
				switch(type) {
					case CLASSIFIER: {
						// there can be multiple param values
						String[] baselearnervalue = {Utils.getOption(p.getName(), parameters)};
						String[] baselearnersettings = Utils.partitionOptions(parameters);
						settings.addAll(getParameterSetting(baselearnersettings, implementation.getSubImplementation(p.getName() + "_0")));
						settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(baselearnervalue, Constants.MAX_LENGTH_PARAM_VALUE)));
						break;
					} case OPTIONHANDLER: {
						String[] paramValues = getMultipleParamValues(parameters, p.getName());
						String[] baseValues = new String[0];
						for (int i = 0; i < paramValues.length; ++i) {
							String[] componentSettings = Utils.splitOptions(paramValues[i]);
							ArrayList<Parameter_setting> paramSettings = getParameterSetting(Arrays.copyOfRange(componentSettings, 1, componentSettings.length), 
																 				 			 implementation.getSubImplementation(p.getName() + "_" + i));
							settings.addAll(paramSettings);
							baseValues = ArrayUtils.add(baseValues, componentSettings[0]);
						}
						settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(baseValues, Constants.MAX_LENGTH_PARAM_VALUE)));
						break;
					} case OPTION: {
						String optionvalue = Utils.getOption(p.getName(), parameters);
						if(optionvalue != "") {
							String[] currentValue = {optionvalue};
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(currentValue, Constants.MAX_LENGTH_PARAM_VALUE)));
						}
						break;
					} case PARAMETERFREE_CLASS: {
						String optionvalue = Utils.getOption(p.getName(), parameters);
						if(optionvalue != "") {
							String[] currentValue = {optionvalue};
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(currentValue, Constants.MAX_LENGTH_PARAM_VALUE)));
						}
						break;
					} case FLAG: {
						boolean flagvalue = Utils.getFlag(p.getName(), parameters);
						String[] currentValue = {flagvalue ? "true" : "false"};
						settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(currentValue, Constants.MAX_LENGTH_PARAM_VALUE)));
						break;
					} case ARRAY: {
						String[] paramValues = getMultipleParamValues(parameters, p.getName());
						if(paramValues.length > 0) {
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(paramValues, Constants.MAX_LENGTH_PARAM_VALUE)));
						}
						break;
					}
				}
			}
		}
		return settings;
	}
	
	public static File classifierSerializedToFile(Classifier cls, Integer task_id) throws IOException {
		File file = File.createTempFile("WekaSerialized_" + cls.getClass().getName(), ".model");
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
		oos.writeObject(cls);
		oos.flush();
		oos.close();
		file.deleteOnExit();
		return file;
	}
	
	public static Map<String, Object> splitEvaluatorToMap(SplitEvaluator se, Object[] results) {
		Map<String, Object> splitEvaluatorResults = new HashMap<String, Object>();
		String[] seResultNames = se.getResultNames();
		
		for(int i = 0; i < seResultNames.length; ++i) {
			splitEvaluatorResults.put(seResultNames[i], results[i]);
		}
		
		return splitEvaluatorResults;
	}
}
