package openmlweka;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.xml.DataFeature.Feature;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.Task;
import org.openml.apiconnector.xml.Task.Input.Data_set;
import org.openml.weka.algorithm.WekaConfig;
import org.openml.weka.experiment.RunOpenmlJob;

import weka.classifiers.bayes.NaiveBayes;
import weka.core.Instances;

public class TestVariousEstimationProcedures extends BaseTestFramework {
	private static final String[] CONSTANT_ATTRIBUTES = {"correct", "fold", "prediction", "repeat", "row_id"};
	private static final String configString = "avoid_duplicate_runs=false; skip_jvm_benchmark=true;";
	private static final WekaConfig config = new WekaConfig(configString);
	
	private static void runAndCheck(int taskId) throws Exception {
		Task task = client_write_test.taskGet(taskId);
		Data_set dataInfo = TaskInformation.getSourceData(task);
		Feature targetFatures = client_write_test.dataFeatures(dataInfo.getData_set_id()).getFeatureMap().get(dataInfo.getTarget_feature());
		int runId = RunOpenmlJob.executeTask(client_write_test, config, taskId, new NaiveBayes()).getLeft();
		Run r = client_write_test.runGet(runId);
		assertNull(r.getError_message());
		assertTrue(r.getFlow_name().contains("NaiveBayes"));
		assertTrue(r.getOutputFileAsMap().size() == 2);
		
		int fileId = r.getOutputFileAsMap().get("predictions").getFileId();
		Instances predictions = client_read_test.getArffFromUrl(fileId);
		
		Set<String> expectedAttributes = new TreeSet<String>(Arrays.asList(CONSTANT_ATTRIBUTES));
		if (task.getTask_type_id() == 3) {
			expectedAttributes.add("sample");
		}
		if (task.getTask_type_id() == 1 || task.getTask_type_id() == 3) {
			for (String value : targetFatures.getNominalValues()) {
				expectedAttributes.add("confidence." + value);
			}
		}
		
		Set<String> predictionsAttributes = new TreeSet<String>(); 
		for (int i = 0; i < predictions.numAttributes(); ++i) {
			predictionsAttributes.add(predictions.attribute(i).name());
		}
		assertEquals(expectedAttributes, predictionsAttributes);
	}
	
	@Test
	public void test10foldCV() throws Exception {
		// irish dataset
		int taskId = 235;
		runAndCheck(taskId);
	}
	
	@Test
	public void test5times2foldCV() throws Exception {
		// irish dataset
		int taskId = 236;
		runAndCheck(taskId);
	}
	
	@Test
	public void test10times10foldCV() throws Exception {
		// irish dataset
		int taskId = 237;
		runAndCheck(taskId);
	}
	
	@Test
	public void testLeaveOneOut() throws Exception {
		// irish dataset
		int taskId = 238;
		runAndCheck(taskId);
	}
	
	@Test
	public void test33Holdout() throws Exception {
		// irish dataset
		int taskId = 239;
		runAndCheck(taskId);
	}
	
	@Test
	public void test10Holdout() throws Exception {
		// irish dataset
		int taskId = 240;
		runAndCheck(taskId);
	}
	
	@Test
	public void test10foldLearningCurve() throws Exception {
		// irish dataset
		int taskId = 841;
		runAndCheck(taskId);
	}
	
	@Test
	public void test10times10foldLearningCurve() throws Exception {
		// irish dataset
		int taskId = 842;
		runAndCheck(taskId);
	}
	
	@Test
	public void testTestOnTrainSet() throws Exception {
		// irish dataset
		int taskId = 1108;
		runAndCheck(taskId);
	}
	
	@Test
	public void testHoldoutOrdered() throws Exception {
		// irish dataset
		int taskId = 1245;
		runAndCheck(taskId);
	}
}
