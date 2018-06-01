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

import org.apache.commons.lang3.StringUtils;
import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.Flow.Parameter;
import org.openml.apiconnector.xml.FlowExists;
import org.openml.apiconnector.xml.Run.Parameter_setting;
import org.openml.apiconnector.xml.SetupExists;
import org.openml.apiconnector.xml.UploadFlow;
import org.openml.apiconnector.xstream.XstreamXmlMapping;

import weka.classifiers.Classifier;
import weka.classifiers.functions.supportVector.Kernel;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.RevisionHandler;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;
import weka.core.Version;
import weka.core.setupgenerator.AbstractParameter;
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

	public static boolean isFloat(String s) {
	    try { 
	        Float.parseFloat(s); 
	    } catch(NumberFormatException e) { 
	        return false; 
	    } catch(NullPointerException e) {
	        return false;
	    }
	    // only got here if we didn't return false
	    return true;
	}

	public static boolean isChar(String s) {
	    return s.length() == 1;
	}

	public static boolean isBoolean(String s) {
	    String lower = s.toLowerCase().trim();
	    return lower == "true" || lower == "false";
	}
	
	public static boolean isAbstractParameter(Class parameterClass) {
		try {
			if (AbstractParameter.class.isAssignableFrom(parameterClass)) {
				return true;
			}
			return false;
		} catch(NoClassDefFoundError e) {
			// If the module is not loaded, it can not be part of it
			return false;
		}
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
		File implementationFile = Conversion.stringToTempFile(xml, implementation.getName(), "xml");
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
				// TODO: do something better with lookup in childrens dicts.
				break;
			}
			
			if(parameter.name().trim().equals("")) {
				throw new Exception("Empty parameter name: " + parameter.name());
			}
			String defaultValue = "";
			String currentValue = "";
			
			if(parameter.numArguments() == 0) {
				//System.out.println(parameter.name() + " = FLAG + value = " + Utils.getFlag(parameter.name(), currentOptions));
				defaultValue = Utils.getFlag(parameter.name(), defaultOptions) == true ? "true" : "";
				currentValue = Utils.getFlag(parameter.name(), currentOptions) == true ? "true" : "";
			} else {
				defaultValue = Utils.getOption(parameter.name(), defaultOptions);
				currentValue = Utils.getOption(parameter.name(), currentOptions);
			}
			//System.out.println("- " + classifierOrig.getClass().getName() + "_" + parameter.name() + ", " + defaultValue);
			
			if (flowParameters.containsKey(parameter.name())) {
				// some weka classifiers have duplicate options!
				//Parameter other = flowParameters.get(parameter.name());
				//if (parameter.description().equals(other.getDescription()) && other.getDefault_value().equals(defaultValue)) {
				//	// same name and same default value, probably same parameter
				//	continue;
				//}
				throw new Exception("Duplicate parameter: " + parameter.name());
			}
				
			if (defaultValue.length() == 0 || isFloat(defaultValue) || isChar(defaultValue) || isBoolean(defaultValue)) {
				// Parameter with vanilla option (recognized by integer, float or empty value)
				WekaParameterType type;
				if (parameter.numArguments() == 0) {
					type = WekaParameterType.FLAG;
				} else {
					type = WekaParameterType.OPTION;
				}
				
				Parameter current = new Parameter(parameter.name(), type.getName(), defaultValue, parameter.description());
				flowParameters.put(parameter.name(), current);
				continue;
			}
			
			String[] currentValueSplitted = Utils.splitOptions(currentValue);
			Class parameterClass = Class.forName(currentValueSplitted[0]);
			
			Flow subimplementation;
			
			if (isAbstractParameter(parameterClass)) {
				WekaParameterType type = WekaParameterType.ARRAY;
				Parameter current = new Parameter(parameter.name(), type.getName(), null, parameter.description());
				flowParameters.put(parameter.name(), current);
			} else if (Classifier.class.isAssignableFrom(parameterClass) && currentValueSplitted.length == 1) {
				// Meta algorithms and stuff. All parameters follow from the hyphen in currentOptions
				String[] subclassifierOptions = Utils.partitionOptions(currentOptions);
				Object parameterObject = Utils.forName(Classifier.class, currentValue, Arrays.copyOf(subclassifierOptions, subclassifierOptions.length));
				subimplementation = serializeClassifier((OptionHandler) parameterObject, tags);
				WekaParameterType type = WekaParameterType.CLASSIFIER;
				String currentParamDefaultValue = currentValue + " " + Utils.joinOptions(subclassifierOptions);
				Parameter current = new Parameter(parameter.name(), type.getName(), currentParamDefaultValue, parameter.description());
				flowParameters.put(parameter.name(), current);
				flowComponents.put(parameter.name(), subimplementation);
			} else if (Classifier.class.isAssignableFrom(parameterClass)) { // TODO: not correct way of distinguishing
				// Classifier that is used for doing something with the original algorithm. 
				Object parameterObject = Utils.forName(parameterClass, 
						currentValueSplitted[0], 
						Arrays.copyOfRange(currentValueSplitted, 1, currentValueSplitted.length));
				
				subimplementation = serializeClassifier((OptionHandler) parameterObject, tags);
				WekaParameterType type = WekaParameterType.OPTIONHANDLER;
				
				Parameter current = new Parameter(parameter.name(), type.getName(), currentValue, parameter.description());
				flowParameters.put(parameter.name(), current);
				flowComponents.put(parameter.name(), subimplementation);
			} else if(Kernel.class.isAssignableFrom(parameterClass)) {
				// Kernels etc. All parameters of the kernel are on the same currentOptions entry
				Object parameterObject = Utils.forName(parameterClass, 
						currentValueSplitted[0], 
						Arrays.copyOfRange(currentValueSplitted, 1, currentValueSplitted.length));
				
				subimplementation = serializeClassifier((Kernel) parameterObject, tags);
				WekaParameterType type = WekaParameterType.OPTIONHANDLER;
				
				Parameter current = new Parameter(parameter.name(), type.getName(), currentValue, parameter.description());
				flowParameters.put(parameter.name(), current);
				flowComponents.put(parameter.name(), subimplementation);
			} else {
				throw new Exception("Can not determine type of parameter: " + parameter.name());
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
			i.addComponent(key, flowComponents.get(key), false);
		}
		for (Parameter p : flowParameters.values()) {
			i.addParameter(p);
		}
		
		return i;
	}
	
	public static ArrayList<Parameter_setting> getParameterSetting(String[] parameters, Flow implementation) {
		ArrayList<Parameter_setting> settings = new ArrayList<Parameter_setting>();
		if (implementation.getParameter() != null) {
			for(Parameter p : implementation.getParameter()) {
				try {
					WekaParameterType type = WekaParameterType.fromString(p.getData_type());
					switch(type) {
					case OPTIONHANDLER:
						String kernelvalue = Utils.getOption(p.getName(), parameters);
						try {
							String kernelname = kernelvalue.substring(0, kernelvalue.indexOf(' '));
							String[] kernelsettings = Utils.splitOptions(kernelvalue.substring(kernelvalue.indexOf(' ')+1));
							ArrayList<Parameter_setting> kernelresult = getParameterSetting(kernelsettings, implementation.getSubImplementation(p.getName()));
							settings.addAll(kernelresult);
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), kernelname));
						} catch(ClassNotFoundException e) {}
						break;
					case CLASSIFIER:
						String baselearnervalue = Utils.getOption(p.getName(), parameters);
						try {
							String[] baselearnersettings = Utils.partitionOptions(parameters);
							settings.addAll(getParameterSetting(baselearnersettings, implementation.getSubImplementation(p.getName())));
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), baselearnervalue));
						} catch(ClassNotFoundException e) {}
						break;
					case OPTION:
						String optionvalue = Utils.getOption(p.getName(), parameters);
						if(optionvalue != "") {
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), optionvalue));
						}
						break;
					case FLAG:
						boolean flagvalue = Utils.getFlag(p.getName(), parameters);
						if(flagvalue) {
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), "true"));
						}
						break;
					case ARRAY:
						List<String> values = new ArrayList<String>();
						String currentvalue = Utils.getOption(p.getName(), parameters);
						while (!currentvalue.equals("")) {
							values.add(currentvalue);
							currentvalue = Utils.getOption(p.getName(), parameters);
						}
						
						if(values.size() > 0) {
							settings.add(new Parameter_setting(implementation.getId(), p.getName(), values.toString()));
						}
						break;
					}
				} catch(Exception e) {/*Parameter not found. */}
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
