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

package org.openml.weka.experiment;

import java.io.File;
import java.util.HashMap;
import java.util.Random;
import org.apache.commons.lang3.ArrayUtils;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.settings.Constants;
import org.openml.apiconnector.xml.Tasks;
import org.openml.apiconnector.xml.Tasks.Task;
import org.openml.weka.algorithm.WekaConfig;

/*
 * RandomBot is a class which submits a run to OpenMl.
 * The run consists of applying a random flow on a random task.
 */

public class RandomBot 
{
	private static final String TAG = "RandomBot";
	
	/*
	 * First argument - Task type
	 * Second argument - Task tag
	 * Third argument - Classifier category
	 */
	public static void main(String[] args) 
	{
		RandomBot bot = new RandomBot();
		int taskType;
		try
		{
			taskType = Integer.parseInt(args[0]);
		}
		catch(NumberFormatException e)
		{
			showErrorMessage(TAG + ":" + e.toString());
			return;
		}
		String taskTag = args[1];
		String classifierCategory = args[2];
		// get a random task id given the task filters
		int taskId = bot.getRandomTaskId(taskType, taskTag);
		// start a run on the task  if we have a correct task id
		if(taskId == -1)
		{
			return;
		}
		bot.startTask(taskId, classifierCategory);
	}
	
	/**
	 * Get the list of all tasks with a certain tag and type from the openml server. 
	 * Return a random task id from the list.
	 * 
	 * @param type- task type
	 * @param tag - task tag
	 * @return the id of a random task from the list.
	 */
	public int getRandomTaskId(int type, String tag)
	{
		WekaConfig config = null;
		config = getConfigurationFile();
		// without a configuration file the connection cannot be made
		if(config == null)
		{
			return -1;
		}
		OpenmlConnector connector = new OpenmlConnector(config.getServer(), config.getApiKey());
		int taskId;
		try
		{
			Tasks tasks = connector.taskList(type, tag);
			Task[] arrayTasks = tasks.getTask();
			int taskArraySize = tasks.getTask().length;
			int randomId = new Random().nextInt(taskArraySize);
			taskId = arrayTasks[randomId].getTask_id();
		}
		catch(Exception e)
		{
			showErrorMessage(TAG + ":" + e.toString());
			return -1;
		}
		return taskId;
	}
	
	/**
	 * Start a run on a task with a given classifier category
	 * 
	 * @param id - task id
	 * @param classifier - weka classifier category 
	 */
	public void startTask(int id, String classifier)
	{
		WekaConfig config = null;
		config = getConfigurationFile();
		// without a configuration file the connection cannot be made
		if(config == null)
		{
			showErrorMessage(TAG + ":" + "No Config file reference in startTask");
			return;
		}
		HashMap<String,String[]> algorithms = initializeAlgorithms();
		if(algorithms.get(classifier) == null)
		{
			showErrorMessage(TAG + ":" + "Please give a correct category for the classifiers");
			return;
		}
		// get the number of classifiers under the category
		int nrClassifiers = algorithms.get(classifier).length;
		// generate a random id for the classifier category
		int randomPosition = new Random().nextInt(nrClassifiers);
		String[] arguments = {"-task_id", "" + id, "-config", "server=" + config.getServer() + "; avoid_duplicate_runs=" + config.getAvoidDuplicateRuns() + "; skip_jvm_benchmark=" + config.getSkipJvmBenchmark()+ "; api_key=" + config.getApiKey(), "-C"};
		try
		{
			RunOpenmlJob.main(ArrayUtils.add(arguments, algorithms.get(classifier)[randomPosition]));
		}
		catch(Exception e)
		{
			showErrorMessage(TAG + ":" + e.toString());
		}
	}
	
	// initialize all of the weka algorithms under their categories. Return a hashmap which contains them.
	private HashMap<String,String[]> initializeAlgorithms()
	{
		HashMap<String,String[]> algorithms = new HashMap<String, String[]>();
		algorithms.put("bayes", new String[]{"weka.classifiers.bayes.BayesNet", "weka.classifiers.bayes.NaiveBayes", "weka.classifiers.bayes.NaiveBayesMultinomial", "weka.classifiers.bayes.NaiveBayesMultinomialText", "weka.classifiers.bayes.NaiveBayesMultinomialUpdateable", "weka.classifiers.bayes.NaiveBayesUpdateable"});
		algorithms.put("functions", new String[]{"weka.classifiers.functions.GaussianProcesses", "weka.classifiers.functions.LinearRegression", "weka.classifiers.functions.Logistic", "weka.classifiers.functions.MultilayerPerceptron", "weka.classifiers.functions.SGD", "weka.classifiers.functions.SGDText", "weka.classifiers.functions.SimpleLinearRegression", "weka.classifiers.functions.SimpleLogistic", "weka.classifiers.functions.SMO", "weka.classifiers.functions.SMOreg", "weka.classifiers.functions.VotedPerceptron"});
		algorithms.put("lazy", new String[]{"weka.classifiers.lazy.IBk", "weka.classifiers.lazy.KStar", "weka.classifiers.lazy.LWL"});
		algorithms.put("meta", new String[]{"weka.classifiers.meta.AdaBoostM1", "weka.classifiers.meta.AdditiveRegression", "weka.classifiers.meta.AttributeSelectedClassifier", "weka.classifiers.meta.Bagging", "weka.classifiers.meta.ClassificationViaRegression", "weka.classifiers.meta.CostSensitiveClassifier", "weka.classifiers.meta.CVParameterSelection", "weka.classifiers.meta.FilteredClassifier", "weka.classifiers.meta.IterativeClassifierOptimizer", "weka.classifiers.meta.LogitBoost", "weka.classifiers.meta.MultiClassClassifier", "weka.classifiers.meta.MultiClassClassifierUpdateable", "weka.classifiers.meta.MultiScheme", "weka.classifiers.meta.RandomCommittee", "weka.classifiers.meta.RandomizableFilteredClassifier", "weka.classifiers.meta.RandomSubSpace", "weka.classifiers.meta.Stacking", "weka.classifiers.meta.RegressionByDiscretization", "weka.classifiers.meta.WeightedInstancesHandlerWrapper", "weka.classifiers.meta.Vote"});
		algorithms.put("misc", new String[]{"weka.classifiers.misc.InputMappedClassifier", "weka.classifiers.misc.SerializedClassifier"});
		algorithms.put("rules", new String[]{"weka.classifiers.rules.DecisionTable", "weka.classifiers.rules.DecisionTableHashKey", "weka.classifiers.rules.JRip", "weka.classifiers.rules.M5Rules", "weka.classifiers.rules.OneR", "weka.classifiers.rules.PART", "weka.classifiers.rules.Rule", "weka.classifiers.rules.RuleStats", "weka.classifiers.rules.ZeroR"});
		algorithms.put("trees", new String[]{"weka.classifiers.trees.DecisionStump", "weka.classifiers.trees.HoeffdingTree", "weka.classifiers.trees.J48", "weka.classifiers.trees.LMT", "weka.classifiers.trees.M5P", "weka.classifiers.trees.RandomForest", "weka.classifiers.trees.RandomTree", "weka.classifiers.trees.REPTree"});
		return algorithms;
	}
	
	private static void showErrorMessage(String errorMessage)
	{
		System.out.println(errorMessage);
	}
	private WekaConfig getConfigurationFile()
	{
		WekaConfig config = null;
		// Check if have a config file
		File configFile = new File(Constants.OPENML_DIRECTORY + "/openml.conf");
		if(configFile.exists() && configFile.isFile())
		{
			config = new WekaConfig();
		}
		return config;
	}
}