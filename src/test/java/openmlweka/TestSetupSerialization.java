package openmlweka;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;

import org.junit.Ignore;
import org.junit.Test;
import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.SetupExists;
import org.openml.apiconnector.xml.SetupParameters;
import org.openml.apiconnector.xml.Run.Parameter_setting;
import org.openml.apiconnector.xstream.XstreamXmlMapping;
import org.openml.weka.algorithm.WekaAlgorithm;
import org.openml.weka.algorithm.WekaConfig;
import org.openml.weka.experiment.RunOpenmlJob;

import com.thoughtworks.xstream.XStream;

import weka.classifiers.Classifier;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.Kernel;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.Bagging;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.meta.LogitBoost;
import weka.classifiers.meta.MultiSearch;
import weka.classifiers.meta.multisearch.RandomSearch;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.core.OptionHandler;
import weka.core.setupgenerator.AbstractParameter;
import weka.core.setupgenerator.ListParameter;
import weka.core.setupgenerator.MathParameter;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Normalize;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;

public class TestSetupSerialization {

	private static final String configString = "server=https://test.openml.org/; avoid_duplicate_runs=false; skip_jvm_benchmark=true; api_key=8baa83ecddfe44b561fd3d92442e3319";
	private static final WekaConfig config = new WekaConfig(configString);
	public final OpenmlConnector connector = new OpenmlConnector(config.getServer(), config.getApiKey());
	
	public final String[] TAGS = {"OpenmlWeka", "weka"};
	public final XStream xstream = XstreamXmlMapping.getInstance();
	
	private OptionHandler deserializeSetup(OptionHandler classifier) throws Exception {
		Flow flowOrig = WekaAlgorithm.serializeClassifier(classifier, null);
		int runId = RunOpenmlJob.executeTask(connector, config, 115, (Classifier) classifier);
		Run run = connector.runGet(runId);
		Flow flow = connector.flowGet(run.getFlow_id());
		SetupParameters setup = connector.setupParameters(run.getSetup_id());
		
		OptionHandler retrieved = WekaAlgorithm.deserializeSetup(setup, flow, false);
		
		// check if flows are equal
		Flow flowRetrieved = WekaAlgorithm.serializeClassifier(classifier, null);
		assertEquals(xstream.toXML(flowOrig), xstream.toXML(flowRetrieved));
		
		// check if options are the same
		assertArrayEquals(classifier.getOptions(), retrieved.getOptions());
		
		ArrayList<Parameter_setting> parameterSettingsList = WekaAlgorithm.getParameterSetting(retrieved.getOptions(), flow);
		Parameter_setting[] parameterSettingsArray = parameterSettingsList.toArray(new Parameter_setting[parameterSettingsList.size()]);
		Run setupObject = new Run(null, null, flow.getId(), null, parameterSettingsArray, null);
		File setutupObjectDescription = Conversion.stringToTempFile(xstream.toXML(setupObject), "setup", "xml");
		
		SetupExists se = connector.setupExists(setutupObjectDescription);
		assertTrue(se.exists());
		assertEquals(run.getSetup_id(), se.getId());
		
		return retrieved;
	}
	
	@Test
	public void testJ48() throws Exception {
		J48 classifier = new J48();
		
		classifier.setConfidenceFactor(0.03F);
		classifier.setMinNumObj(10);
		
		deserializeSetup(classifier);
	}
	

	@Test
	public void testMultiFilterClassifier() throws Exception {
		FilteredClassifier classifier = new FilteredClassifier();

		Filter[] filter = new Filter[3];
		filter[0] = new ReplaceMissingValues();
		filter[1] = new RemoveUseless();
		filter[2] = new Normalize();
		
		MultiFilter multifilter = new MultiFilter();
		multifilter.setFilters(filter);
		
		classifier.setFilter(multifilter);
		
		deserializeSetup(classifier);
	}
	
	private void addLevel(OptionHandler baseClassifier, int currentLevel, int maxLevel) throws Exception {
		if (currentLevel > maxLevel) {
			return;
		}
		Bagging metaclassifier = new Bagging();
		metaclassifier.setClassifier((Classifier) baseClassifier);
		metaclassifier.setBagSizePercent((currentLevel + 1) * 7);
		metaclassifier.setNumIterations(currentLevel + 2);
		deserializeSetup(metaclassifier);
		
		addLevel(metaclassifier, currentLevel + 1, maxLevel);
	}
	
	@Test
	public void testMultiLevelBaggingTree() throws Exception {
		J48 classifier = new J48();
		
		classifier.setConfidenceFactor(0.03F);
		classifier.setMinNumObj(10);
		
		addLevel(classifier, 0, 2);
	}
	
	@Test
	public void testMultiLevelSVM() throws Exception {
		PolyKernel poly = new PolyKernel();
		poly.setExponent(3);
		RBFKernel rbf = new RBFKernel();
		rbf.setGamma(0.123);
		Kernel[] kernels = {poly, rbf};
		SMO classifier = new SMO();
		classifier.setC(0.123);
		for (Kernel kernel : kernels) {
			classifier.setKernel(kernel);
			
			addLevel(classifier, 0, 2);
		}
	}
	
	@Test
	public void testMultiLevelBaggingSVM() throws Exception {
		RBFKernel rbf = new RBFKernel();
		SMO classifier = new SMO();
		rbf.setGamma(0.32);
		classifier.setKernel(rbf);
		classifier.setC(0.21);
		
		addLevel(classifier, 0, 2);
	}
	
	private void testRandomSearchSetup(Classifier baseclassifier, AbstractParameter[] searchParameters) throws Exception {
		Filter[] filter = new Filter[3];
		filter[0] = new ReplaceMissingValues();
		filter[1] = new RemoveUseless();
		filter[2] = new Normalize();
		
		MultiFilter multifilter = new MultiFilter();
		multifilter.setFilters(filter);
		
		FilteredClassifier classifier = new FilteredClassifier();
		classifier.setFilter(multifilter);
		classifier.setClassifier(baseclassifier);
		
		RandomSearch randomSearchAlgorithm = new RandomSearch();
		randomSearchAlgorithm.setNumIterations(10);
		randomSearchAlgorithm.setSearchSpaceNumFolds(3);
		
		MultiSearch search = new MultiSearch();
		String[] evaluation = {"-E", "ACC"};
		search.setOptions(evaluation);
		search.setClassifier(classifier);
		search.setAlgorithm(randomSearchAlgorithm);
		search.setSearchParameters(searchParameters);
		
		deserializeSetup(search);
	}
	
	@Test
	public void testRandomSearchDecisionTree() throws Exception {
		J48 baseclassifier = new J48();
		
		MathParameter numFeatures = new MathParameter();
		numFeatures.setProperty("classifier.minNumObj");
		numFeatures.setBase(1);
		numFeatures.setExpression("I");
		numFeatures.setMin(1);
		numFeatures.setMax(20);
		numFeatures.setStep(1);

		MathParameter maxDepth = new MathParameter();
		maxDepth.setProperty("classifier.confidenceFactor");
		maxDepth.setBase(10);
		maxDepth.setExpression("pow(BASE,I)");
		maxDepth.setMin(-4);
		maxDepth.setMax(-1);
		maxDepth.setStep(1);
		
		AbstractParameter[] searchParameters = {numFeatures, maxDepth};
		
		testRandomSearchSetup(baseclassifier, searchParameters);
	}
	
	@Test
	public void testRandomSearchRandomForest() throws Exception {
		RandomForest baseclassifier = new RandomForest();
		
		MathParameter numFeatures = new MathParameter();
		numFeatures.setProperty("classifier.numFeatures");
		numFeatures.setBase(1);
		numFeatures.setExpression("I");
		numFeatures.setMin(0.1);
		numFeatures.setMax(0.9);
		numFeatures.setStep(0.1);
		
		AbstractParameter[] searchParameters = {numFeatures};
		
		testRandomSearchSetup(baseclassifier, searchParameters);
	}
	
	@Test
	public void testRandomSearchSVM() throws Exception {
		SMO baseclassifier = new SMO();
		baseclassifier.setKernel(new RBFKernel());
		
		MathParameter gamma = new MathParameter();
		gamma.setProperty("classifier.kernel.gamma");
		gamma.setBase(2);
		gamma.setExpression("pow(BASE,I)");
		gamma.setMin(-12);
		gamma.setMax(12);
		gamma.setStep(1);
		
		MathParameter complexity = new MathParameter();
		complexity.setProperty("classifier.c");
		complexity.setBase(2);
		complexity.setExpression("pow(BASE,I)");
		complexity.setMin(-12);
		complexity.setMax(12);
		complexity.setStep(1);
		
		AbstractParameter[] searchParameters = {gamma, complexity};
		
		testRandomSearchSetup(baseclassifier, searchParameters);
	}
	
	@Test
	public void testRandomSearchLogisticBoosting() throws Exception {
		LogitBoost baseclassifier = new LogitBoost();
		baseclassifier.setClassifier(new REPTree());
		
		MathParameter numIterations = new MathParameter();
		numIterations.setProperty("classifier.numIterations");
		numIterations.setBase(1);
		numIterations.setExpression("I");
		numIterations.setMin(5);
		numIterations.setMax(10);
		numIterations.setStep(1);
		

		MathParameter treeDepth = new MathParameter();
		treeDepth.setProperty("classifier.classifier.maxDepth");
		treeDepth.setBase(1);
		treeDepth.setExpression("I");
		treeDepth.setMin(1);
		treeDepth.setMax(5);
		treeDepth.setStep(1);

		MathParameter shrinkage = new MathParameter();
		shrinkage.setProperty("classifier.shrinkage");
		shrinkage.setBase(10);
		shrinkage.setExpression("pow(BASE,I)");
		shrinkage.setMin(-4);
		shrinkage.setMax(-1);
		shrinkage.setStep(1);
		
		AbstractParameter[] searchParameters = {numIterations, treeDepth, shrinkage};
		testRandomSearchSetup(baseclassifier, searchParameters);
	}
	
	@Test
	public void testRandomSearchKNN() throws Exception {
		IBk baseclassifier = new IBk();
		
		MathParameter numNeighbours = new MathParameter();
		numNeighbours.setProperty("classifier.KNN");
		numNeighbours.setBase(1);
		numNeighbours.setExpression("I");
		numNeighbours.setMin(1);
		numNeighbours.setMax(50);
		numNeighbours.setStep(1);
		
		AbstractParameter[] searchParameters = {numNeighbours};
		
		testRandomSearchSetup(baseclassifier, searchParameters);
	}
	
	@Test
	public void testRandomSearchLogistic() throws Exception {
		Logistic baseclassifier = new Logistic();

		MathParameter ridge = new MathParameter();
		ridge.setProperty("classifier.ridge");
		ridge.setBase(2);
		ridge.setExpression("pow(BASE,I)");
		ridge.setMin(-12);
		ridge.setMax(12);
		ridge.setStep(1);
		
		AbstractParameter[] searchParameters = {ridge};
		
		testRandomSearchSetup(baseclassifier, searchParameters);
	}
	
	@Test
	@Ignore
	public void testRandomSearchNeuralNetwork() throws Exception {
		MultilayerPerceptron baseclassifier = new MultilayerPerceptron();

		MathParameter hiddenlayers = new MathParameter();
		hiddenlayers.setProperty("classifier.hiddenLayers");
		hiddenlayers.setBase(1);
		hiddenlayers.setExpression("I");
		hiddenlayers.setMin(32);
		hiddenlayers.setMax(64);
		hiddenlayers.setStep(1);

		MathParameter learningRate = new MathParameter();
		learningRate.setProperty("classifier.learningRate");
		learningRate.setBase(10);
		learningRate.setExpression("pow(BASE,I)");
		learningRate.setMin(-5);
		learningRate.setMax(0);
		learningRate.setStep(1);

		ListParameter decay = new ListParameter();
		decay.setProperty("classifier.decay");
		decay.setList("false true");
		// TODO: check if this works with flags

		MathParameter epochs = new MathParameter();
		epochs.setProperty("classifier.trainingTime");
		epochs.setBase(1);
		epochs.setExpression("I");
		epochs.setMin(2);
		epochs.setMax(50);
		epochs.setStep(1);

		MathParameter momentum = new MathParameter();
		momentum.setProperty("classifier.momentum");
		momentum.setBase(1);
		momentum.setExpression("I");
		momentum.setMin(0.1);
		momentum.setMax(0.9);
		momentum.setStep(0.1);
		
		AbstractParameter[] searchParameters = {hiddenlayers, learningRate, decay, epochs, momentum};
		
		testRandomSearchSetup(baseclassifier, searchParameters);
	}
	
	// TODO: test this in various other orders (Filter / ), email Mark / Eibe / Peter
}
