package openmlweka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.xml.DataSetDescription;
import org.openml.apiconnector.xml.EstimationProcedure;
import org.openml.apiconnector.xml.Task;
import org.openml.weka.algorithm.DataSplits;

import weka.core.Instances;

public class TestDataSplits extends BaseTestFramework {
	
	private static void doCheckTrainOnTest(DataSplits ds, Instances dataset) {
		assertEquals(ds.REPEATS, 1);
		assertEquals(ds.FOLDS, 1);
		
		Instances train = ds.getTrainingSet(0, 0);
		Instances test = ds.getTestSet(0, 0);
		
		assertEquals(dataset.toString(), test.toString());
		assertEquals(train.toString(), test.toString());
	}
	
	private static void doCheckLeaveOneOut(DataSplits ds, Instances dataset) {
		assertEquals(ds.REPEATS, 1);
		assertEquals(ds.FOLDS, dataset.numInstances());
		
		Set<Integer> testIndices = new TreeSet<Integer>();
		Instances rebuild = new Instances(dataset, 0);
		for (int i = 0; i < ds.FOLDS; ++i) {
			testIndices.addAll(ds.getTestSetRowIds(0,  i, 0));
			Instances testSet = ds.getTestSet(0, i);
			assertEquals(testSet.numInstances(), 1);
			rebuild.add(testSet.instance(0));
		}
		assertEquals(dataset.toString(), rebuild.toString());
	}
	
	private static void doCheck(int taskId) throws Exception {
		Task task = client_read_test.taskGet(taskId);
		EstimationProcedure ep = client_read_test.estimationProcedureGet(TaskInformation.getEstimationProcedure(task).getId());
		DataSetDescription dsd = client_read_test.dataGet(TaskInformation.getSourceData(task).getData_set_id());
		Instances dataset = client_read_test.getDataset(dsd);
		Instances datasplits = client_read_test.getSplitsFromTask(task);
		DataSplits ds = new DataSplits(dsd.getId(), ep, dataset, datasplits);
		
		assertTrue(ds.REPEATS * ds.FOLDS > 0);
		
		switch(ep.getType()) {
			case TESTONTRAININGDATA:
				doCheckTrainOnTest(ds, dataset);
				break;
			case LEAVEONEOUT:
				doCheckLeaveOneOut(ds, dataset);
				break;
			default:
				throw new Exception("test not defined for estimation procedure type: " + ep.getType());
		}
	}
	
	@Test
	public void testDataSplitsTrainOnTest() throws Exception {
		int taskId = 1108; // test on train / irish
		doCheck(taskId);
	}

	@Test
	public void testDataSplitsLeaveOneOut() throws Exception {
		int taskId = 238; // leave one out / irish
		doCheck(taskId);
	}
}
