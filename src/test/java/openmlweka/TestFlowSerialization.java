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

package openmlweka;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Flow.Parameter;
import org.openml.apiconnector.xml.UploadFlow;
import org.openml.apiconnector.xstream.XstreamXmlMapping;
import org.openml.weka.algorithm.WekaAlgorithm;

import com.thoughtworks.xstream.XStream;

import weka.classifiers.Classifier;
import weka.classifiers.IteratedSingleClassifierEnhancer;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.Kernel;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.classifiers.functions.supportVector.StringKernel;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.Bagging;
import weka.classifiers.rules.JRip;
import weka.classifiers.rules.OneR;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.HoeffdingTree;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.LMT;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.trees.RandomTree;
import weka.core.OptionHandler;

public class TestFlowSerialization {
	
	public final String[] TAGS = {"OpenmlWeka", "weka"};
	public final boolean USE_SENTINEL = true;
	public final XStream xstream = XstreamXmlMapping.getInstance();
	public final OpenmlConnector connector = new OpenmlConnector(
			"https://test.openml.org/", "8baa83ecddfe44b561fd3d92442e3319");
	
	private static Map<String, Parameter> getParametersAsMap(Flow flow) {
		Parameter[] parameters = flow.getParameter();
		Map<String, Parameter> paramMap = new TreeMap<String, Parameter>();
		for (Parameter p : parameters) {
			paramMap.put(p.getName(), p);
		}
		return paramMap;
	}
	
	@Test
	public void testSimpleFlow() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Classifier[] classifiers = {new ZeroR(), new OneR(), new JRip(), 
									new J48(), new REPTree(), new HoeffdingTree(), new LMT(),
		                            new NaiveBayes(), new IBk(), new SMO(),
		                            new Logistic(), new MultilayerPerceptron(),
		                            new RandomForest(), new Bagging(), new AdaBoostM1()};
		                            
		for (Classifier classif : classifiers){
			System.out.println(classif.getClass().getName());
			Flow uploaded = WekaAlgorithm.serializeClassifier((OptionHandler) classif, TAGS);

			if (USE_SENTINEL) {
				uploaded.setName(uuid + "_" + uploaded.getName());
			}
			String resultAsString = xstream.toXML(uploaded);
			UploadFlow uf = connector.flowUpload(Conversion.stringToTempFile(resultAsString, uploaded.getName(), "xml"), null, null);
			Flow downloaded = connector.flowGet(uf.getId());
			
			// check parameter values
			assert(getParametersAsMap(downloaded).keySet().equals(getParametersAsMap(uploaded).keySet()));
		}
	}
	
	@Test
	public void testFlowWithKernel() throws Exception {
		Kernel[] kernels = {new PolyKernel(), new RBFKernel(), new StringKernel()};
		
		SMO svm = new SMO();
		for (Kernel k : kernels) {
			svm.setKernel(k);
			Flow flow = WekaAlgorithm.serializeClassifier(svm, null);
			
			// check the name of the kernel
			String kernelName = k.getClass().getName();
			String expectedName = svm.getClass().getName() + "(" + kernelName + ")";
			assert(flow.getName().equals(expectedName));
			// this unit test only works because there is a bug in the current SVM version. 
			
			// parameter default value
			assert(getParametersAsMap(flow).get("K").getDefault_value().contains(kernelName));
		}
	}
	
	@Test
	public void testFlowWithSubclassifier() throws Exception {
		Classifier[] baseclassifiers = {new REPTree(), new J48(), new NaiveBayes()};
		IteratedSingleClassifierEnhancer[] metaclassifiers = {new AdaBoostM1(), new Bagging()};
		
		for (IteratedSingleClassifierEnhancer metaclassif : metaclassifiers) {
			for (Classifier baseClassifier : baseclassifiers) {
				metaclassif.setClassifier(baseClassifier);
				Flow flow = WekaAlgorithm.serializeClassifier(metaclassif, null);
				
				// check the name of the kernel
				String baseName = baseClassifier.getClass().getName();
				String expectedName = metaclassif.getClass().getName() + "(" + baseName + ")";
				
				assert(flow.getName().equals(expectedName));
				
				String[] baseClassifierOptions = ((OptionHandler) baseClassifier).getOptions(); 
				String expectedDefaultValue = baseClassifier.getClass().getName() + " " + StringUtils.join(baseClassifierOptions, " ");
				
				// parameter default value
				assert(getParametersAsMap(flow).get("W").getDefault_value().equals(expectedDefaultValue));
			}
		}
	}
	
	private void addLevelToFlow(Classifier baseClassifier, Flow baselevelFlow, int currentLevel, int maxLevel) throws Exception {
		IteratedSingleClassifierEnhancer[] metaclassifiers = {new AdaBoostM1(), new Bagging()}; // must have W option for classifier 
		
		if (currentLevel > maxLevel) {
			return;
		}
		
		for (IteratedSingleClassifierEnhancer metaclassif : metaclassifiers) {
			metaclassif.setClassifier(baseClassifier);
			Flow flow = WekaAlgorithm.serializeClassifier(metaclassif, null);
			String expectedName =  metaclassif.getClass().getName() + "(" + baselevelFlow.getName() + ")";

			assert(flow.getName().equals(expectedName));
			String[] baseClassifierOptions = ((OptionHandler) baseClassifier).getOptions(); 
			String expectedDefaultValue = baseClassifier.getClass().getName() + " " + StringUtils.join(baseClassifierOptions, " ");
			
			assert(getParametersAsMap(flow).get("W").getDefault_value().equals(expectedDefaultValue));
			assert(StringUtils.countMatches(expectedDefaultValue, "--") == currentLevel);
			addLevelToFlow(metaclassif, flow, currentLevel + 1, maxLevel);
		}
	}
	
	@Test
	public void testFlowMultiLevelFlow() throws Exception {
		Classifier[] baseclassifiers = {new REPTree(), new J48(), new RandomTree()}; // base classifier mist have options
		
		for (Classifier classifier : baseclassifiers) {
			Flow baseflow = WekaAlgorithm.serializeClassifier((OptionHandler) classifier, null);
			addLevelToFlow(classifier, baseflow, 0, 5);
		}
	}
}
