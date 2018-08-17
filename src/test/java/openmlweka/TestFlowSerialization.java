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

import org.junit.Ignore;
import org.junit.Test;
import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Parameter;
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
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Normalize;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;

public class TestFlowSerialization {
	
	public final String[] TAGS = {"OpenmlWeka", "weka"};
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
	
	private Flow uploadFlowWithSentinelThenDownload(Flow uploaded) throws IOException, Exception {
		String uuid = UUID.randomUUID().toString();
		
		uploaded.setName(uploaded.getName() + "_" + uuid);
		String uploadName = uploaded.getName();
		if (uploadName.length() > 64) {
			uploadName = uploadName.substring(0, 64);
		}
		UploadFlow uf = connector.flowUpload(Conversion.stringToTempFile(xstream.toXML(uploaded), uploadName, "xml"), null, null);
		Flow downloaded = connector.flowGet(uf.getId());
		
		downloaded.setName(downloaded.getName().substring(0, downloaded.getName().indexOf("_" + uuid)));
		uploaded.setName(uploaded.getName().substring(0, uploaded.getName().indexOf("_" + uuid)));
		
		return downloaded;
	}
	
	@Test
	public void testSimpleFlow() throws Exception {
		OptionHandler[] classifiers = {new ZeroR(), new OneR(), new JRip(), 
									   new J48(), new REPTree(), new HoeffdingTree(), new LMT(),
		                               new NaiveBayes(), new IBk(), new SMO(),
		                               new Logistic(), new MultilayerPerceptron(),
		                               new RandomForest(), new Bagging(), new AdaBoostM1(), 
		                               new FilteredClassifier()};
		                            
		for (OptionHandler classif : classifiers){
			Flow uploaded = WekaAlgorithm.serializeClassifier(classif, TAGS);

			Flow downloaded = uploadFlowWithSentinelThenDownload(uploaded);
			
			// check parameter names equals
			assert(getParametersAsMap(downloaded).keySet().equals(getParametersAsMap(uploaded).keySet()));
			
			// check deserialized flow
			String uploadedAsString = xstream.toXML(uploaded);
			OptionHandler retrievedFlow = WekaAlgorithm.deserializeClassifier(downloaded);
			Flow reconstructed = WekaAlgorithm.serializeClassifier(retrievedFlow, TAGS);
			String reconstructedAsString = xstream.toXML(reconstructed);
			assertEquals(uploadedAsString, reconstructedAsString);
			
			// check options are equal
			assertArrayEquals(classif.getOptions(), retrievedFlow.getOptions());
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
				Flow uploadedFlow = uploadFlowWithSentinelThenDownload(flowOrig);
				assert(uploadedFlow.getParameter().length > 0);
				OptionHandler deserialized = WekaAlgorithm.deserializeClassifier(uploadedFlow);
				Flow deserializedFlow = WekaAlgorithm.serializeClassifier(deserialized, null);
				assertEquals(xstream.toXML(flowOrig), xstream.toXML(deserializedFlow));
				
				// check options are equal
				assert(Arrays.equals(metaclassif.getOptions(), deserialized.getOptions()));
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
			Flow uploadedFlow = uploadFlowWithSentinelThenDownload(flow);
			assert(uploadedFlow.getParameter().length > 0);
			OptionHandler deserialized = WekaAlgorithm.deserializeClassifier(uploadedFlow);
			Flow deserializedFlow = WekaAlgorithm.serializeClassifier(deserialized, null);
			assertEquals(xstream.toXML(flow), xstream.toXML(deserializedFlow));
			
			// check if number of components is OK
			assertEquals(currentFlowCount, deserializedFlow.countComponents());
			
			// check options are equal
			assert(Arrays.equals(metaclassif.getOptions(), deserialized.getOptions()));
			
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
		Filter[] filters = {new ReplaceMissingValues(), new RemoveUseless(), new Normalize()};
		
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
	@Ignore
	public void testKnn() throws Exception {
		IBk knn = new IBk();
		
		NearestNeighbourSearch[] nns = {new LinearNNSearch(), new KDTree(), new CoverTree()};
		
		for (NearestNeighbourSearch search : nns) {
			knn.setNearestNeighbourSearchAlgorithm(search);
			Flow flow = WekaAlgorithm.serializeClassifier(knn, null);
			
			// check if can re-instantiate
			Flow uploadedFlow = uploadFlowWithSentinelThenDownload(flow);
			OptionHandler deserialized = WekaAlgorithm.deserializeClassifier(uploadedFlow);
			Flow deserializedFlow = WekaAlgorithm.serializeClassifier(deserialized, null);
			assertEquals(xstream.toXML(deserializedFlow), xstream.toXML(flow));
			
			// check options are equal
			assertArrayEquals(knn.getOptions(), deserialized.getOptions());
		}
	}
}
