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

import static org.junit.Assert.*;

import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.junit.Test;
import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.SetupParameters;
import org.openml.apiconnector.xml.Study;
import org.openml.apiconnector.xml.Parameter;
import org.openml.weka.algorithm.WekaConfig;
import org.openml.weka.experiment.RunOpenmlJob;
import org.openml.weka.experiment.TaskResultProducer;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.meta.Bagging;
import weka.classifiers.trees.J48;
import weka.core.Utils;

public class TestRunJob extends BaseTestFramework {
	
	private static final String configString = "avoid_duplicate_runs=false; skip_jvm_benchmark=true;";
	private static final String configStringWithServer = configString + ";server=" + url_test + ";api_key=" + client_write_test.getApiKey();
	private static final WekaConfig config = new WekaConfig(configString);
	
	@Test
	public void testApiRunUploadFromCliString() throws Exception {
		// Note that this test function requires a config string with server, as it constructs the OpenMLConnector object itself. 
		String[] algorithms = {"weka.classifiers.trees.REPTree", "weka.classifiers.meta.Bagging -P 50 -S 4385 -num-slots 4 -I 10 -W weka.classifiers.trees.J48 -- -R -N 3", "weka.classifiers.meta.FilteredClassifier -F \"weka.filters.supervised.attribute.Discretize -R first-last -precision 6\" -W weka.classifiers.trees.RandomForest -- -I 100 -K 0 -S 1 -num-slots 1"};
		String[] args = {"-task_id", "1", "-config", configStringWithServer, "-C"};
		
		for (String algorithm : algorithms) {
			RunOpenmlJob.main(ArrayUtils.add(args, algorithm));
		}
	}
	
	@Test
	public void testApiRunUploadNB() throws Exception {
		int runIdA = RunOpenmlJob.executeTask(client_write_test, config, 115, new NaiveBayes()).getLeft();
		assertTrue(client_write_test.runGet(runIdA).getFlow_name().contains("NaiveBayes"));
	}
	
	@Test
	public void testApiRunUploadJ48() throws Exception {
		float confidenceFactor = 0.001F;
		int minNumObj = 5;
		boolean binarySplits = true;
		
		J48 tree = new J48();
		tree.setConfidenceFactor(confidenceFactor);
		tree.setMinNumObj(minNumObj);
		tree.setBinarySplits(binarySplits);
		int runId = RunOpenmlJob.executeTask(client_write_test, config, 115, tree).getLeft();
		Run run = client_write_test.runGet(runId);
		int setupId = run.getSetup_id();
		Flow flow = client_write_test.flowGet(run.getFlow_id());
		SetupParameters sp = client_write_test.setupParameters(setupId);
		Map<String, Parameter> parameters = sp.getParametersAsMap();
		String fullName = flow.getName() + "(" + flow.getVersion() + ")";
		assertEquals(new JSONArray(parameters.get(fullName + "_M").getValue()).getString(0), "" + minNumObj);
		assertEquals(new JSONArray(parameters.get(fullName + "_C").getValue()).getString(0), "" + confidenceFactor);
		assertEquals(new JSONArray(parameters.get(fullName + "_B").getValue()).getString(0), "" + binarySplits);
		
		// try deleting the run afterwards
		client_write_test.runDelete(run.getRun_id());
		Conversion.log("OK", "run delete", "deleted run " + runId);
	}
	
	@Test
	public void testApiRunUploadBaggingJ48() throws Exception {
		float confidenceFactor = 0.001F;
		int minNumObj = 5;
		boolean binarySplits = true;
		
		J48 tree = new J48();
		tree.setConfidenceFactor(confidenceFactor);
		tree.setMinNumObj(minNumObj);
		tree.setBinarySplits(binarySplits);
		Bagging metaClassifier = new Bagging();
		metaClassifier.setClassifier(tree);
		
		Pair<Integer, Run> result = RunOpenmlJob.executeTask(client_write_test, config, 115, metaClassifier);
		Run run = client_write_test.runGet(result.getLeft());
		int setupId = run.getSetup_id();
		client_write_test.flowGet(run.getFlow_id());
		client_write_test.setupParameters(setupId);

		int expectedEvaluations = TaskResultProducer.USER_MEASURES.length * 10 + 1; // times 10 because 10f CV, plus 1 for OS info
		assertEquals(expectedEvaluations, result.getRight().getOutputEvaluation().length);
		
		// try deleting the flow afterwards, with force delete (can take a while)
		int id = client_admin_test.runDelete(run.getRun_id()).getId();
		Conversion.log("OK", "flow force delete", "deleted run " + id);
	}
	
	@Test
	public void testForName() throws Exception {
		String[] options = {"-P", "100", "-S", "1", "-I", "10", "-W", "weka.classifiers.trees.REPTree", "--", "-M", "2", "-V", "0.001", "-N", "3", "-S", "1", "-L", "-1", "-I", "0.0"};
		String className = "weka.classifiers.meta.AdaBoostM1";
		Class classType = Classifier.class;
		Object c = Utils.forName(classType, className, options);
		
		// System.out.println("old: " + (Arrays.toString(((OptionHandler)c).getOptions())));
		// System.out.println("new: " + (Arrays.toString(((OptionHandler)c).getOptions())));
		// System.out.println(((AdaBoostM1) c).getClassifier().getClass().getName());
	}
}
