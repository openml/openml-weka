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

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import weka.classifiers.Classifier;
import weka.classifiers.meta.MultiSearch;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Utils;

public class OptimizationTrace {

	private static final String SETUP_STRING_ATT = "setup_string";
	private static final String PARAMETER_PREFIX = "parameter_";
	
	private static Instances getHeader(int taskId,List<Entry<String,Object>> parameters) {
		ArrayList<Attribute> attInfo = new ArrayList<Attribute>();
		List<String> stringValues = null;
		List<String> trueFalse = new ArrayList<String>();
		trueFalse.add("false");
		trueFalse.add("true");
		
		attInfo.add(new Attribute("repeat"));
		attInfo.add(new Attribute("fold"));
		attInfo.add(new Attribute("iteration"));
		attInfo.add(new Attribute(SETUP_STRING_ATT,stringValues));
		attInfo.add(new Attribute("evaluation"));
		attInfo.add(new Attribute("selected",trueFalse));
		for (Entry<String,Object> parameter : parameters) {
			attInfo.add(new Attribute(PARAMETER_PREFIX + parameter.getKey(),stringValues));
		}
		
		Instances dataset = new Instances("openml_task_" + taskId + "_optimization_trace", attInfo, 0);
		return dataset;
	}
	
	public static Instances addTraceToDataset(Instances dataset, List<Quadlet<String,Double,List<Entry<String,Object>>,Boolean>> trace, int taskId, int repeat, int fold) {
		if (dataset == null) {
			dataset = getHeader(taskId, trace.get(0).getParameters());
		}
		
		for (int i = 0; i < trace.size(); ++i) {
			double[] values = new double[dataset.numAttributes()];
			values[0] = repeat;
			values[1] = fold;
			values[2] = i;
			values[4] = trace.get(i).getEvaluation();
			values[5] = trace.get(i).isSelected() ? 1.0 : 0.0;
			DenseInstance instance = new DenseInstance(1.0, values);
			instance.setDataset(dataset);
			instance.setValue(3, trace.get(i).getClassifier());
			for (Entry<String,Object> parameter : trace.get(i).getParameters()) {
				// TODO: think about casting
				String value = parameter.getValue().toString(); 
				instance.setValue(dataset.attribute(PARAMETER_PREFIX + parameter.getKey()), value);
			}
			dataset.add(instance);
		}
		
		return dataset;
	}
	
	public static List<Quadlet<String,Double,List<Entry<String,Object>>,Boolean>> extractTrace(Classifier classifier) throws Exception {
		try {
			Classifier classifierReference = classifier;
			if (!(classifierReference instanceof MultiSearch)) {
				throw new NoClassDefFoundError("Classifier not instance of 'weka.classifiers.meta.MultiSearch'");
				// Is OK, will be catched by outer function
			}
			
			MultiSearch multiSearch = (MultiSearch) classifierReference;
			List<Quadlet<String,Double,List<Entry<String,Object>>,Boolean>> result = new ArrayList<OptimizationTrace.Quadlet<String,Double,List<Entry<String,Object>>,Boolean>>();
			if (multiSearch.getTraceSize() == 0) {
				throw new Exception("No Trace iterations performed with MultiSearch");
			}
			
			String selectedSetupString = Utils.toCommandLine(multiSearch.getBestClassifier());
			for (int i = 0; i < multiSearch.getTraceSize(); ++i) {
				String classifName = multiSearch.getTraceClassifierAsCli(i);
				double classifEval = multiSearch.getTraceValue(i);
				List<Entry<String,Object>> parameterSettings = multiSearch.getTraceParameterSettings(i);
				result.add(new Quadlet<String, Double,List<Entry<String,Object>>, Boolean>(classifName, classifEval, parameterSettings, classifName.equals(selectedSetupString)));
			}
			
			return result;
		} catch(NoClassDefFoundError e) {
			throw new NoClassDefFoundError("Could not find MultiSearch package. Ignoring trace options. ");
		}
	}
	
	public static class Quadlet<T, U, V, W> {
	   private T a;
	   private U b;
	   private V c;
	   private W d;

	   Quadlet(T a, U b, V c, W d)
	   {
	    this.a = a;
	    this.b = b;
	    this.c = c;
	    this.d = d;
	   }

	   T getClassifier(){ return a;}
	   U getEvaluation(){ return b;}
	   V getParameters(){ return c;}
	   W isSelected(){ return d;}
	}
}
