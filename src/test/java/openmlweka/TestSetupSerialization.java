package openmlweka;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.SetupParameters;
import org.openml.apiconnector.xstream.XstreamXmlMapping;
import org.openml.weka.algorithm.WekaAlgorithm;
import org.openml.weka.algorithm.WekaConfig;
import org.openml.weka.experiment.RunOpenmlJob;

import com.thoughtworks.xstream.XStream;

import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.classifiers.meta.Bagging;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.meta.MultiSearch;
import weka.classifiers.meta.multisearch.RandomSearch;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.OptionHandler;
import weka.core.SelectedTag;
import weka.core.Tag;
import weka.core.setupgenerator.AbstractParameter;
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
		
		OptionHandler retrieved = WekaAlgorithm.deserializeSetup(setup, flow);
		
		// check if flows are equal
		Flow flowRetrieved = WekaAlgorithm.serializeClassifier(classifier, null);
		assert(xstream.toXML(flowOrig).equals(xstream.toXML(flowRetrieved)));
		
		// check if options are the same
		assertArrayEquals(classifier.getOptions(), retrieved.getOptions());
		
		return retrieved;
	}
	
	@Test
	public void testJ48() throws Exception {
		J48 classifier = new J48();
		
		classifier.setConfidenceFactor(0.03F);
		classifier.setMinNumObj(10);
		
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
	public void testMultiLevelBaggingSVM() throws Exception {
		RBFKernel rbf = new RBFKernel();
		SMO classifier = new SMO();
		rbf.setGamma(0.32);
		classifier.setKernel(rbf);
		classifier.setC(0.21);
		
		addLevel(classifier, 0, 2);
	}
	
	@Test
	public void testRandomSearchRandomForest() throws Exception {
		RandomForest baseclassifier = new RandomForest();
		baseclassifier.setNumIterations(16);
		
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
		
		MathParameter numFeatures = new MathParameter();
		numFeatures.setProperty("classifier.numFeatures");
		numFeatures.setBase(1);
		numFeatures.setExpression("I");
		numFeatures.setMin(0.1);
		numFeatures.setMax(0.9);
		numFeatures.setStep(0.1);

		MathParameter maxDepth = new MathParameter();
		maxDepth.setProperty("classifier.maxDepth");
		maxDepth.setBase(1);
		maxDepth.setExpression("I");
		maxDepth.setMin(0);
		maxDepth.setMax(10);
		maxDepth.setStep(1);
		
		AbstractParameter[] searchParameters = {numFeatures, maxDepth};
		
		MultiSearch search = new MultiSearch();
		String[] evaluation = {"-E", "ACC"};
		search.setOptions(evaluation);
		search.setClassifier(baseclassifier);
		search.setAlgorithm(randomSearchAlgorithm);
		search.setSearchParameters(searchParameters);
		
		deserializeSetup(search);
	}
}
