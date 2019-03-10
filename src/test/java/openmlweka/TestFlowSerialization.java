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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.math3.util.Pair;
import org.junit.Test;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Parameter;
import org.openml.weka.algorithm.WekaAlgorithm;

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
import weka.classifiers.meta.FilteredClassifier;
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
import weka.core.neighboursearch.CoverTree;
import weka.core.neighboursearch.KDTree;
import weka.core.neighboursearch.LinearNNSearch;
import weka.core.neighboursearch.NearestNeighbourSearch;
import weka.core.tokenizers.WordTokenizer;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Normalize;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;
import weka.filters.unsupervised.attribute.StringToWordVector;

public class TestFlowSerialization extends BaseTestFramework {
	
	private static final String[] TAGS = {"OpenmlWeka", "weka"};
	
	private static Map<String, Parameter> getParametersAsMap(Flow flow) {
		Parameter[] parameters = flow.getParameter();
		Map<String, Parameter> paramMap = new TreeMap<String, Parameter>();
		for (Parameter p : parameters) {
			paramMap.put(p.getName(), p);
		}
		return paramMap;
	}
	
	private static Pair<Flow, OptionHandler> serializeUploadDownloadDeserialize(OptionHandler classifier) throws IOException, Exception {
		Flow flowToUpload = WekaAlgorithm.serializeClassifier(classifier, TAGS);
		String uuid = UUID.randomUUID().toString();
		
		flowToUpload.setName(flowToUpload.getName() + "_" + uuid);
		String uploadName = flowToUpload.getName();
		if (uploadName.length() > 64) {
			uploadName = uploadName.substring(0, 64);
		}
		int flowId = client_write_test.flowUpload(flowToUpload);
		Flow downloaded = client_write_test.flowGet(flowId);
		
		downloaded.setName(downloaded.getName().substring(0, downloaded.getName().indexOf("_" + uuid)));
		flowToUpload.setName(flowToUpload.getName().substring(0, flowToUpload.getName().indexOf("_" + uuid)));
		
		// check parameter names equals
		assert(getParametersAsMap(downloaded).keySet().equals(getParametersAsMap(flowToUpload).keySet()));
		
		// check deserialized flow
		String uploadedAsString = xstream.toXML(flowToUpload);
		OptionHandler retrievedFlow = WekaAlgorithm.deserializeClassifier(downloaded);
		Flow reconstructed = WekaAlgorithm.serializeClassifier(retrievedFlow, TAGS);
		String reconstructedAsString = xstream.toXML(reconstructed);
		assertEquals(uploadedAsString, reconstructedAsString);
		
		assertArrayEquals(classifier.getOptions(), retrievedFlow.getOptions());
		return new Pair<Flow, OptionHandler>(downloaded, retrievedFlow);
	}
	
	@Test
	public void testSimpleFlow() throws Exception {
		OptionHandler[] classifiers = { 
			new ZeroR(), new OneR(), new JRip(), new J48(), new REPTree(), 
			new HoeffdingTree(), new LMT(), new NaiveBayes(), new IBk(), 
			new SMO(), new Logistic(), new MultilayerPerceptron(), 
			new RandomForest(), new Bagging(), new AdaBoostM1(), 
			new FilteredClassifier() 
		};
		                            
		for (OptionHandler classifier : classifiers){
			serializeUploadDownloadDeserialize(classifier);
		}
	}
	
	@Test
	public void testFlowWithKernel() throws Exception {
		Kernel[] kernels = {new PolyKernel(), new RBFKernel(), new StringKernel()};
		Classifier calibrator = new Logistic();
		
		SMO svm = new SMO();
		for (Kernel k : kernels) {
			svm.setKernel(k);
			svm.setCalibrator(calibrator);
			Flow flow = WekaAlgorithm.serializeClassifier(svm, null);
			
			// check the name of the kernel
			String calibratorName = calibrator.getClass().getName();
			String kernelName = k.getClass().getName();
			String expectedName = svm.getClass().getName() + "(" + kernelName + "," + calibratorName + ")";
			assertEquals(expectedName, flow.getName());
			
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
				Flow flowOrig = WekaAlgorithm.serializeClassifier(metaclassif, null);
				
				// check the name of the subclassifier
				String baseName = baseClassifier.getClass().getName();
				String expectedName = metaclassif.getClass().getName() + "(" + baseName + ")";
				
				assertEquals(expectedName, flowOrig.getName());
				
				String[] expectedDefaultValueArray = {baseClassifier.getClass().getName()};
				String expectedDefaultValue = WekaAlgorithm.parameterValuesToJson(expectedDefaultValueArray, null);
				
				// parameter default value
				assertEquals(expectedDefaultValue, getParametersAsMap(flowOrig).get("W").getDefault_value());
				
				// check if can re-instantiate
				serializeUploadDownloadDeserialize(metaclassif);
			}
		}
	}
	
	private void addLevelToFlow(Classifier baseClassifier, Flow baselevelFlow, int levelsToGo, int currentFlowCount) throws Exception {
		IteratedSingleClassifierEnhancer[] metaclassifiers = {new AdaBoostM1(), new Bagging()}; // must have W option for classifier 
		if (levelsToGo <= 0) {
			return;
		}
		
		currentFlowCount += 1;
		for (IteratedSingleClassifierEnhancer metaclassif : metaclassifiers) {
			metaclassif.setClassifier(baseClassifier);
			Flow flow = WekaAlgorithm.serializeClassifier(metaclassif, null);
			
			// check if name is ok
			String expectedName =  metaclassif.getClass().getName() + "(" + baselevelFlow.getName() + ")";
			assertEquals(expectedName, flow.getName());
			
			// check if default value is OK
			String[] expectedDefaultValueArray = {baseClassifier.getClass().getName()};
			String expectedDefaultValue = WekaAlgorithm.parameterValuesToJson(expectedDefaultValueArray, null);
			
			assertEquals(expectedDefaultValue, getParametersAsMap(flow).get("W").getDefault_value());
			
			// check if can re-instantiate
			Pair<Flow, OptionHandler> result = serializeUploadDownloadDeserialize(metaclassif);
			
			
			// check if number of components is OK
			assertEquals(currentFlowCount, result.getFirst().countComponents());
			
			// check options are equal
			assert(Arrays.equals(metaclassif.getOptions(), result.getSecond().getOptions()));
			
			// continue to the next level 
			addLevelToFlow(metaclassif, flow, levelsToGo - 1, currentFlowCount);
		}
	}
	
	@Test
	public void testMultiLevelFlow() throws Exception {
		Classifier[] baseclassifiers = {new REPTree(), new J48(), new RandomTree()}; // base classifier mist have options
		
		for (Classifier classifier : baseclassifiers) {
			Flow baseflow = WekaAlgorithm.serializeClassifier((OptionHandler) classifier, null);
			addLevelToFlow(classifier, baseflow, 3, 1);
		}
	}
	
	@Test
	public void testMultiLevelFlowWithFilter() throws Exception {
		Filter[] filters = {
			new ReplaceMissingValues(), 
			new RemoveUseless(), 
			new Normalize(),
		};
		
		FilteredClassifier classifier = new FilteredClassifier();
		for (Filter filter : filters) {
			classifier.setFilter(filter);
			Flow baseflow = WekaAlgorithm.serializeClassifier(classifier, null);
			// this one starts at level 1, as the "base classifier" already has a "--" notation
			addLevelToFlow(classifier, baseflow, 2, 3);
		}
		
		// now also test a multi filter
		MultiFilter multi = new MultiFilter();
		multi.setFilters(filters);
		classifier.setFilter(multi);
		Flow baseflow = WekaAlgorithm.serializeClassifier(classifier, null);
		addLevelToFlow(classifier, baseflow, 2, 6);
	}

	@Test
	public void testMultiLevelFlowWithKernel() throws Exception {
		Kernel[] kernels = {new PolyKernel(), new RBFKernel(), new StringKernel()}; // base classifier mist have options
		
		// this unit test should crash in its current state!
		for (Kernel kernel : kernels) {
			SMO classifier = new SMO();
			classifier.setKernel(kernel);
			Flow baseflow = WekaAlgorithm.serializeClassifier(classifier, null);
			addLevelToFlow(classifier, baseflow, 2, 3);
		}
	}
	
	@Test
	public void testKnn() throws Exception {
		IBk knn = new IBk();
		
		NearestNeighbourSearch[] nns = {new LinearNNSearch(), new KDTree(), new CoverTree()};
		
		for (NearestNeighbourSearch search : nns) {
			knn.setNearestNeighbourSearchAlgorithm(search);
			serializeUploadDownloadDeserialize(knn);
		}
	}
	
	@Test
	public void testCommonFilters() throws Exception {
		Filter[] filters = { 
			new Normalize(),
			new RemoveUseless(),
			new ReplaceMissingValues(),
			new StringToWordVector(),
		};

		FilteredClassifier classifier = new FilteredClassifier();
		for (Filter filter : filters) {
			classifier.setFilter(filter);
			serializeUploadDownloadDeserialize(classifier);
		}
	}
	
	@Test
	public void testParameterWithRegexValue() throws Exception {
		FilteredClassifier fc = new FilteredClassifier();
		StringToWordVector stw = new StringToWordVector();
		WordTokenizer wt = new WordTokenizer();
		// ensure that there are some \r\t\n in there, as 
		// well as a space (complex cases for weka)
		wt.setDelimiters(" \\r\\n\\t.,;:'\\\"()?!");
		stw.setTokenizer(wt);
		fc.setFilter(stw);
		
		Flow flow = WekaAlgorithm.serializeClassifier(fc, TAGS);
		String uuid = UUID.randomUUID().toString();
		flow.setName(flow.getName() + "_" + uuid);
		int flowId = client_write_test.flowUpload(flow);
		Flow downloaded = client_write_test.flowGet(flowId);
		
		OptionHandler fc2 = WekaAlgorithm.deserializeClassifier(downloaded);
		assertArrayEquals(fc.getOptions(), fc2.getOptions());
	}
}
