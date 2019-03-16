package openmlweka;

import static org.junit.Assert.assertEquals;

import java.io.FileReader;

import org.junit.Test;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.xml.DataSetDescription;
import org.openml.apiconnector.xml.EstimationProcedure;
import org.openml.apiconnector.xml.Task;
import org.openml.weka.algorithm.DataSplits;

import weka.core.Instances;

public class TestDataSplits extends BaseTestFramework {

	@Test
	public void testDataSplitsTrainOnTest() throws Exception {
		Task t = client_read_test.taskGet(1108); // train on test, irish dataset
		
		EstimationProcedure ep = client_read_test.estimationProcedureGet(TaskInformation.getEstimationProcedure(t).getId());
		DataSetDescription dsd = client_read_test.dataGet(TaskInformation.getSourceData(t).getData_set_id());
		Instances dataset = new Instances(new FileReader(client_read_test.datasetGet(dsd)));
		Instances datasplits = new Instances(new FileReader(client_read_test.taskSplitsGet(t)));
		DataSplits ds = new DataSplits(12167, ep, dataset, datasplits);
		
		Instances train = ds.getTrainingSet(0, 0);
		Instances test = ds.getTestSet(0, 0);
		
		assertEquals(ds.REPEATS, 1);
		assertEquals(ds.FOLDS, 1);
		assertEquals(ds.SAMPLES, 1);
		assertEquals(dataset.toString(), test.toString());
		assertEquals(train.toString(), test.toString());
	}
}
