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
import org.openml.apiconnector.xml.Flow.Parameter;
import org.openml.apiconnector.xml.FlowExists;
import org.openml.apiconnector.xml.Run.Parameter_setting;
import org.openml.apiconnector.xml.SetupExists;
import org.openml.apiconnector.xml.SetupParameters;
import org.openml.apiconnector.xml.UploadFlow;
import org.openml.apiconnector.xstream.XstreamXmlMapping;

import weka.classifiers.Classifier;
import weka.classifiers.functions.supportVector.Kernel;
import weka.classifiers.meta.multisearch.AbstractSearch;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.RevisionHandler;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;
import weka.core.Version;
import weka.core.setupgenerator.AbstractParameter;
import weka.experiment.SplitEvaluator;
import weka.filters.Filter;

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
	
	public static int countFlowComponents(Flow flow) {
		int count = 1;
		if (flow.getComponent() != null) {
			for (Flow.Component sub : flow.getComponent()) {
				count += countFlowComponents(sub.getImplementation());
			}
		}
		return count;
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
	
	public static String parameterValuesToJson(String[] defaultValues) {
		JSONArray values = new JSONArray();
		for (int i = 0; i < defaultValues.length; ++i) {
			values.put(defaultValues[i]);
		}
		return values.toString();
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
		String xml = XstreamXmlMapping.getInstance().toXML(implementation);
		//System.err.println(xml);
		String flowName = implementation.getName();
		if (flowName.length() > 64) {
			flowName = flowName.substring(0, 64);
		}
		File implementationFile = Conversion.stringToTempFile(xml, flowName, "xml");
		UploadFlow ui = apiconnector.flowUpload(implementationFile, null, null);
		return ui.getId();
	}

	public static Flow serializeClassifier(OptionHandler classifierOrig, String[] tags) throws Exception {
		String classifier_name = classifierOrig.getClass().getName();
		Object classifierNew = Class.forName(classifier_name).newInstance();
		String[] defaultOptions = ((OptionHandler) classifierNew).getClass().newInstance().getOptions();
		String[] currentOptions = classifierOrig.getOptions();
		
		Map<String, Parameter> flowParameters = new LinkedHashMap<String, Flow.Parameter>();
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
				
				Parameter current = new Parameter(parameter.name(), type.getName(), parameterValuesToJson(defaultValues), parameter.description());
				flowParameters.put(parameter.name(), current);
				continue;
			}
			
			if (currentValues.length == 0) {
				throw new Exception("Inferred complex parameter type but no values set. ");
			}
			
			if (isAssignableFrom(AbstractParameter.class, currentValues)) {
				WekaParameterType type = WekaParameterType.ARRAY;
				Parameter current = new Parameter(parameter.name(), type.getName(), null, parameter.description());
				flowParameters.put(parameter.name(), current);
			} else if (isAssignableFrom(Classifier.class, currentValues) && currentValues.length == 1 && currentValues[0].indexOf(" ") == -1) {
				// Meta algorithms and stuff. All parameters follow from the hyphen in currentOptions
				String[] subclassifierOptions = Utils.partitionOptions(currentOptions);
				Object parameterObject = Utils.forName(Classifier.class, currentValues[0], Arrays.copyOf(subclassifierOptions, subclassifierOptions.length));
				Flow subimplementation = serializeClassifier((OptionHandler) parameterObject, tags);
				WekaParameterType type = WekaParameterType.CLASSIFIER;
				String[] currentParamDefaultValue = {currentValues[0] + " " + Utils.joinOptions(subclassifierOptions)};
				Parameter current = new Parameter(parameter.name(), type.getName(), parameterValuesToJson(currentParamDefaultValue), parameter.description());
				flowParameters.put(parameter.name(), current);
				flowComponents.put(parameter.name(), subimplementation);
			} else if (isAssignableFrom(Kernel.class, currentValues) || 
					   isAssignableFrom(Filter.class, currentValues) || 
					   isAssignableFrom(Classifier.class, currentValues) ||
					   isAssignableFrom(AbstractSearch.class, currentValues)) { // TODO: not correct way of discriminating
				// Kernels etc. All parameters of the kernel are on the same currentOptions entry
				
				for (int i = 0; i < currentValues.length; ++i) {
					String currentValue = currentValues[i];
					String[] currentValueSplitted = Utils.splitOptions(currentValue);
					Class<?> parameterClass = Class.forName(currentValueSplitted[0]);
					String[] options = Arrays.copyOfRange(currentValueSplitted, 1, currentValueSplitted.length);
					Object parameterObject = Utils.forName(parameterClass, 
														   currentValueSplitted[0], 
						                                   options);
					
					Flow subimplementation = serializeClassifier((OptionHandler) parameterObject, tags);
					flowComponents.put(parameter.name() + "_" + i, subimplementation);
				}
				
				Parameter current = new Parameter(parameter.name(), WekaParameterType.OPTIONHANDLER.getName(), parameterValuesToJson(currentValues), parameter.description());
				flowParameters.put(parameter.name(), current);
			} else {
				throw new Exception("Classifier contains an unsupported parameter type: " + parameter.name());
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
		
		return i;
	}
	
	public static OptionHandler deserializeClassifier(Flow f) throws Exception {
		String baseclass = f.getName().split("\\(")[0];
		String[] primaryOptions = new String[0];
		String[] secondaryOptions = new String[0];
		
		for (Parameter p : f.getParameter()) {
			JSONArray defaultValues = new JSONArray(p.getDefault_value()); 
			WekaParameterType type = WekaParameterType.fromString(p.getData_type());
			switch(type) {
				case CLASSIFIER:
					String[] currentValueSplitted = Utils.splitOptions(defaultValues.getString(0));
					String[] exceptFirst = Arrays.copyOfRange(currentValueSplitted, 1, currentValueSplitted.length);
					primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
					primaryOptions = ArrayUtils.add(primaryOptions, currentValueSplitted[0]);
					secondaryOptions = ArrayUtils.add(secondaryOptions, "--");
					secondaryOptions = ArrayUtils.addAll(secondaryOptions, exceptFirst);
					break;
				case OPTIONHANDLER:
					for (int i = 0; i < defaultValues.length(); ++i) {
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getName());
						primaryOptions = ArrayUtils.add(primaryOptions, defaultValues.getString(i));
					}
					break;
				default:
					break;
			}
		}
		
		String[] allOptions = ArrayUtils.addAll(primaryOptions, secondaryOptions);
		// System.out.println(baseclass + " " + Arrays.toString(allOptions));
		OptionHandler result = (OptionHandler) Utils.forName(OptionHandler.class, baseclass, allOptions);
		return result;
	}
	
	
	public static OptionHandler deserializeSetup(SetupParameters setup, Flow f) throws Exception {
		String baseclass = f.getName().split("\\(")[0];
		String[] allOptions = setupToOptionArray(setup, f, 0);
		OptionHandler result = (OptionHandler) Utils.forName(OptionHandler.class, baseclass, allOptions);
		return result;
	}

	private static String[] setupToOptionArray(SetupParameters setup, Flow f, int level) throws Exception {
		if (f.getId() == null) {
			throw new Exception("Can only deserialize setups based on flows with ids. Flow: " + f.getName());
		}
		String[] primaryOptions = new String[0];
		String[] secondaryOptions = new String[0];
		
		for (SetupParameters.Parameter p : setup.getParameters()) {
			// System.out.println(p.getParameter_name() + " " + p.getFlow_id() + " - " + f.getId());
			if (! p.getFlow_id().equals(f.getId())) {
				continue;
			}
			
			WekaParameterType type = WekaParameterType.fromString(p.getData_type());
			JSONArray currentValues = new JSONArray(p.getValue());
			if (currentValues.length() > 1 && type != WekaParameterType.OPTIONHANDLER && type != WekaParameterType.ARRAY) {
				throw new Exception("Found multiple values in not-allowed parameter: " + p.getFull_name());
			}
			
			switch(type) {
				case CLASSIFIER: {
					// System.out.println(p.getParameter_name() + " - classifier");
					String[] currentValueSplitted = Utils.splitOptions(currentValues.getString(0));
					primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getParameter_name());
					primaryOptions = ArrayUtils.add(primaryOptions, currentValueSplitted[0]);
					secondaryOptions = ArrayUtils.add(secondaryOptions, "--");
					secondaryOptions = ArrayUtils.addAll(secondaryOptions, setupToOptionArray(setup, f.getSubImplementation(p.getParameter_name()), level + 1));
					break;
				} case OPTIONHANDLER: {
					// System.out.println(p.getParameter_name() + " - kernel");
					JSONArray componentValues = new JSONArray(p.getValue());
					for (int i = 0; i < componentValues.length(); ++i) {
						String[] currentValueSplitted = Utils.splitOptions(currentValues.getString(i));
						String[] subOptions = setupToOptionArray(setup, f.getSubImplementation(p.getParameter_name() + "_" + i), level + 1);
						String subWithOptions = currentValueSplitted[0] + " " + StringUtils.join(subOptions, " ");
						if (level > 0) {
							subWithOptions = "\"" + Utils.backQuoteChars(subWithOptions) + "\""; // TODO: fix me!
						}
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getParameter_name());
						primaryOptions = ArrayUtils.add(primaryOptions, subWithOptions);
					}
					break;
				} case OPTION:
					// System.out.println(p.getParameter_name() + " - option");
					primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getParameter_name());
					primaryOptions = ArrayUtils.add(primaryOptions, currentValues.getString(0));
					break;
				case FLAG:
					// System.out.println(p.getParameter_name() + " - flag");
					if (currentValues.getString(0).equals("true")) {
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getParameter_name());
					}
					break;
				case ARRAY:
					// System.out.println(p.getParameter_name() + " - array");
					// break;
					JSONArray allParams = new JSONArray(p.getValue());
					for (int i = 0; i < allParams.length(); i++) {
						primaryOptions = ArrayUtils.add(primaryOptions, "-" + p.getParameter_name());
						primaryOptions = ArrayUtils.add(primaryOptions, allParams.getString(i));
					}
					break;
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
					case CLASSIFIER:
						String[] baselearnervalue = {Utils.getOption(p.getName(), parameters)};
						String[] baselearnersettings = Utils.partitionOptions(parameters);
						settings.addAll(getParameterSetting(baselearnersettings, implementation.getSubImplementation(p.getName())));
						settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(baselearnervalue)));
						break;
					case OPTIONHANDLER:
						String paramValue = Utils.getOption(p.getName(), parameters);
						String[] baseValues = new String[0];
						for (int i = 0; !paramValue.equals(""); ++i) {
							String[] componentSettings = Utils.splitOptions(paramValue);
							ArrayList<Parameter_setting> paramSettings = getParameterSetting(Arrays.copyOfRange(componentSettings, 1, componentSettings.length), 
																 				 			 implementation.getSubImplementation(p.getName() + "_" + i));
							settings.addAll(paramSettings);
							baseValues = ArrayUtils.add(baseValues, paramValue);
							paramValue = Utils.getOption(p.getName(), parameters);
						}
						settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(baseValues)));
						paramValue = Utils.getOption(p.getName(), parameters);
						break;
					case OPTION:
						String optionvalue = Utils.getOption(p.getName(), parameters);
						if(optionvalue != "") {
							String[] currentValue = {optionvalue};
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(currentValue)));
						}
						break;
					case FLAG: 
						boolean flagvalue = Utils.getFlag(p.getName(), parameters);
						String[] currentValue = {flagvalue ? "true" : "false"};
						settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(currentValue)));
						break;
					case ARRAY:
						String[] values = new String[0];
						String currentvalue = Utils.getOption(p.getName(), parameters);
						while (!currentvalue.equals("")) {
							values = ArrayUtils.add(values, currentvalue);
							currentvalue = Utils.getOption(p.getName(), parameters);
						}
						
						if(values.length > 0) {
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), parameterValuesToJson(values)));
						}
						break;
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
