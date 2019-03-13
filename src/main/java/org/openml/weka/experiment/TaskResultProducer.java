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

import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.models.MetricScore;
import org.openml.apiconnector.xml.EstimationProcedure;
import org.openml.apiconnector.xml.EstimationProcedureType;
import org.openml.apiconnector.xml.Task;
import org.openml.apiconnector.xml.Task.Input.Data_set;
import org.openml.weka.algorithm.DataSplits;
import org.openml.weka.algorithm.InstancesHelper;
import org.openml.weka.algorithm.OptimizationTrace;
import org.openml.weka.algorithm.WekaAlgorithm;
import org.openml.weka.algorithm.OptimizationTrace.Quadlet;
import org.openml.weka.algorithm.WekaConfig;

import weka.core.AttributeStats;
import weka.core.Instances;
import weka.core.UnsupportedAttributeTypeException;
import weka.experiment.CrossValidationResultProducer;
import weka.experiment.OutputZipper;

public class TaskResultProducer extends CrossValidationResultProducer {

	private static final long serialVersionUID = 1L;
	
	public static final UserMeasures[] USER_MEASURES = {
		new UserMeasures("predictive_accuracy", "Percent_correct", .01),
		new UserMeasures("kappa", "Kappa_statistic"),
		new UserMeasures("root_mean_squared_error", "Root_mean_squared_error"),
		new UserMeasures("root_relative_squared_error", "Root_relative_squared_error", .01),
		new UserMeasures("usercpu_time_millis_training", "UserCPU_Time_millis_training"),
		new UserMeasures("usercpu_time_millis_testing", "UserCPU_Time_millis_testing"),
		new UserMeasures("usercpu_time_millis", "UserCPU_Time_millis"), 
		new UserMeasures("wall_clock_time_millis_training", "Elapsed_Time_training",  1000),
		new UserMeasures("wall_clock_time_millis_testing", "Elapsed_Time_testing",  1000),
		new UserMeasures("wall_clock_time_millis", "Elapsed_Time", 1000), 
	};

	public static final String TASK_FIELD_NAME = "OpenML_Task_id";
	public static final String SAMPLE_FIELD_NAME = "Sample";
	
	/** The task to be run */
	protected Task m_Task;
	protected boolean regressionTask;
	protected boolean missingLabels;
	
	/** Object representing the datasplits **/
	protected DataSplits m_DataSplits;

	/** Number of samples, if applicable **/
	protected int m_NumSamples = 1; // default to 1

	/** Current task information string **/
	protected String currentTaskRepresentation = "";

	protected OpenmlConnector apiconnector;
	protected WekaConfig openmlconfig;

	public TaskResultProducer(OpenmlConnector apiconnector, WekaConfig openmlconfig) {
		super();
		this.m_SplitEvaluator = new OpenmlClassificationSplitEvaluator();
		this.apiconnector = apiconnector;
		this.openmlconfig = openmlconfig;
	}

	public void setTask(Task t) throws Exception {
		m_Task = t;

		regressionTask = t.getTask_type_id() == 2;

		if (regressionTask) {
			throw new Exception("OpenML Plugin Exception: Regression tasks currently not supported. Aborting.");
		}

		/*
		 * if( regressionTask && !( m_SplitEvaluator instanceof
		 * OpenmlRegressionSplitEvaluator ) ) { m_SplitEvaluator = new
		 * OpenmlRegressionSplitEvaluator(); } else if( !( m_SplitEvaluator
		 * instanceof OpenmlClassificationSplitEvaluator ) ) { m_SplitEvaluator
		 * = new OpenmlClassificationSplitEvaluator(); }
		 */
		
		m_Instances = InstancesHelper.getDatasetFromTask(apiconnector, m_Task);
		Data_set ds = TaskInformation.getSourceData(m_Task);
		int targetAttributeIndex = InstancesHelper.getAttributeIndex(m_Instances, ds.getTarget_feature());
		AttributeStats targetStats = m_Instances.attributeStats(targetAttributeIndex);
		Instances splits = new Instances(new FileReader(apiconnector.taskSplitsGet(m_Task)));
		int epId = TaskInformation.getEstimationProcedure(m_Task).getId();
		EstimationProcedure ep = apiconnector.estimationProcedureGet(epId);

		missingLabels = targetStats.missingCount > 0;
		m_DataSplits = new DataSplits(m_Task, ep, m_Instances, splits);
		m_NumFolds = m_DataSplits.FOLDS;
		m_NumSamples = m_DataSplits.SAMPLES;

		currentTaskRepresentation = "Task " + m_Task.getTask_id() + " (" + TaskInformation.getSourceData(m_Task).getDataSetDescription(apiconnector).getName() + ")";
	}
	
	public Object getSplitEvaluatorKey(int index) {
		return m_SplitEvaluator.getKey()[index];
	}

	@Override
	public void setInstances(Instances m_Instances) {
		throw new RuntimeException("TaskResultProducer Exception: function setInstances may not be invoked. Use setTask instead. ");
	}

	@Override
	public void doRun(int run) throws Exception {
		if (m_DataSplits == null) {
			// interestingly, weka catches all errors thrown in the setTask()
			// function. In the case that the constructor of m_DataSplits throws 
			// an error, it will get ignored. This if statement is a failsafe
			// for this. 
			throw new Exception("DataSplits not properly initialized.");
		}
		OpenmlSplitEvaluator tse = ((OpenmlSplitEvaluator) m_SplitEvaluator);
		String currentRunRepresentation = currentTaskRepresentation + " with " + (String) tse.getKey()[0] + " - Repeat " + (run - 1);
		Conversion.log("OK", "Attribtes", "Attributes available: " + InstancesHelper.getAttributes(m_Instances));
		Conversion.log("OK", "Class", "Class attribute: " + m_Instances.classAttribute());
		
		if (getRawOutput()) {
			if (m_ZipDest == null) {
				m_ZipDest = new OutputZipper(m_OutputFile);
			}
		}

		if (m_Instances == null) {
			throw new Exception("No Instances set");
		}

		if (m_Task == null) {
			throw new Exception("No task set");
		}

		int repeat = run - 1; // 0/1 based
		for (int fold = 0; fold < m_NumFolds; fold++) {
			for (int sample = 0; sample < m_NumSamples; ++sample) {
				// Add in some fields to the key like run and fold number, data
				// set, name
				String currentFoldRepresentation = "fold " + fold + ", sample " + sample;
				Conversion.log("INFO", "Perform Run", "Started on performing " + currentRunRepresentation + ", " + currentFoldRepresentation);

				Map<String, MetricScore> userMeasures = new HashMap<String, MetricScore>();
				
				Instances train = m_DataSplits.getTrainingSet(repeat, fold, sample);
				Instances test = m_DataSplits.getTestSet(repeat, fold, sample);

				try {
					Object[] seResults = tse.getResult(train, test);
					Object[] results = new Object[seResults.length + 1];
					results[0] = getTimestamp();
					System.arraycopy(seResults, 0, results, 1, seResults.length);

					Map<String, Object> splitEvaluatorResults = WekaAlgorithm.splitEvaluatorToMap(tse, seResults);
					List<Quadlet<String, Double, List<Entry<String, Object>>, Boolean>> trace = null;
					try {
						trace = OptimizationTrace.extractTrace(tse.getTrainedClassifier());

						Conversion.log("OK", "Trace", "Found MultiSearch or FilteredClassifier(MultiSearch). Extracting trace. ");
					} catch (NoClassDefFoundError e) {
						// This is totally OK, no need to catch this
						
					}

					// adding an combination measures: UserCPU_Time_millis
					// (total training time + test time)
					
					if (m_DataSplits.getEstimationProcedure().getType() != EstimationProcedureType.LEAVEONEOUT) {
						if (splitEvaluatorResults.containsKey("UserCPU_Time_millis_training") && splitEvaluatorResults.containsKey("UserCPU_Time_millis_testing")) {
							double traintime = (Double) splitEvaluatorResults.get("UserCPU_Time_millis_training");
							double testtime = (Double) splitEvaluatorResults.get("UserCPU_Time_millis_testing");
							splitEvaluatorResults.put("UserCPU_Time_millis", traintime + testtime);
						}
						if (splitEvaluatorResults.containsKey("Elapsed_Time_training") && splitEvaluatorResults.containsKey("Elapsed_Time_testing")) {
							double traintime = (Double) splitEvaluatorResults.get("Elapsed_Time_training");
							double testtime = (Double) splitEvaluatorResults.get("Elapsed_Time_testing");
							splitEvaluatorResults.put("Elapsed_Time", traintime + testtime);
						}
						
						// can't add user measures if: there is no labeled test set / LOO 
						// TODO: LOO can actually have run time predictions
						if (missingLabels == false) {
							for (UserMeasures um : USER_MEASURES) {
								if (splitEvaluatorResults.containsKey(um.wekaFunctionName)) {
									userMeasures.put(um.openmlFunctionName, new MetricScore(((Double) splitEvaluatorResults.get(um.wekaFunctionName)) * um.factor, test.size()));
								} else {
									Conversion.log("WARNING", "MEASURE", "Missing measure " + um.wekaFunctionName);
								}
							}
						}
					} else {
						splitEvaluatorResults = null;
					}
					
					if (m_ResultListener instanceof TaskResultListener) {
						((TaskResultListener) m_ResultListener).acceptResultsForSending(m_Task, m_Instances, m_DataSplits.getNrOfRuns(), repeat, fold, m_DataSplits.HAS_SAMPLES ? sample : null,
								tse.getTrainedClassifier(), (String) tse.getKey()[1], m_DataSplits.getTestSetRowIds(repeat, fold, sample), tse.recentPredictions(), userMeasures,
								trace);
					}
				} catch (UnsupportedAttributeTypeException ex) {
					// Save the train and test data sets for debugging purposes?
					Conversion.log("ERROR", "Perform Run", "Unable to finish " + currentRunRepresentation + ", " + currentFoldRepresentation + " with "
							+ tse.getTrainedClassifier().getClass().getName() + ": " + ex.getMessage());
					if (m_ResultListener instanceof TaskResultListener) {
						((TaskResultListener) m_ResultListener).acceptErrorResult(m_Task, m_Instances, m_DataSplits.getNrOfRuns(), tse.getTrainedClassifier(), ex.getMessage(), (String) tse.getKey()[1]);
					}
				}
			}
		}
	}

	public static class UserMeasures {
		private final String openmlFunctionName;
		private final String wekaFunctionName;
		private final double factor;

		private UserMeasures(String openmlFunctionName, String wekaFunctionName, double factor) {
			this.openmlFunctionName = openmlFunctionName;
			this.wekaFunctionName = wekaFunctionName;
			this.factor = factor;
		}

		private UserMeasures(String openmlFunctionName, String wekaFunctionName) {
			this(openmlFunctionName, wekaFunctionName, 1.0D);
		}
	}
}
