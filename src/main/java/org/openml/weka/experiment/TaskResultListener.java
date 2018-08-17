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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.algorithms.SciMark;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.models.MetricScore;
import org.openml.apiconnector.settings.Constants;
import org.openml.apiconnector.xml.EvaluationScore;
import org.openml.apiconnector.xml.Flow;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.Run.Parameter_setting;
import org.openml.apiconnector.xml.Task;
import org.openml.apiconnector.xml.Task.Output.Predictions.Feature;
import org.openml.apiconnector.xml.UploadRun;
import org.openml.apiconnector.xstream.XstreamXmlMapping;
import org.openml.weka.algorithm.OptimizationTrace;
import org.openml.weka.algorithm.WekaAlgorithm;
import org.openml.weka.algorithm.OptimizationTrace.Quadlet;
import org.openml.weka.algorithm.WekaConfig;

import com.thoughtworks.xstream.XStream;

import weka.classifiers.Classifier;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.Prediction;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.RevisionHandler;
import weka.core.Utils;
import weka.core.Version;
import weka.experiment.InstancesResultListener;

public class TaskResultListener extends InstancesResultListener {

	private static final long serialVersionUID = 7230120341L;

	private static final String[] DEFAULT_TAGS = { "weka", "weka_" + Version.VERSION };

	/** List of OpenML tasks currently being solved. Folds/repeats are gathered */
	private final Map<String, OpenmlExecutedTask> currentlyCollecting;

	/**
	 * List of OpenML tasks that reported back with errors. Will be send to
	 * server with error message
	 */
	private final List<String> tasksWithErrors;

	private final OpenmlConnector apiconnector;
	
	private final String[] all_tags;
	
	private final List<Integer> runIds;

	boolean skipJvmBenchmark = false;
	
	public TaskResultListener(OpenmlConnector apiconnector, WekaConfig config) {
		super();

		this.apiconnector = apiconnector;
		currentlyCollecting = new HashMap<String, OpenmlExecutedTask>();
		tasksWithErrors = new ArrayList<String>();
		all_tags = ArrayUtils.addAll(DEFAULT_TAGS, config.getTags());
		skipJvmBenchmark = config.getSkipJvmBenchmark();
		runIds = new ArrayList<Integer>();
	}

	public void acceptFullModel(Task t, Instances sourceData, Classifier classifier, String options, Map<String, Object> splitEvaluatorResults,
			OpenmlSplitEvaluator tse) throws Exception {
		String revision = (classifier instanceof RevisionHandler) ? ((RevisionHandler) classifier).getRevision() : "undefined";
		String implementationId = classifier.getClass().getName() + "(" + revision + ")";

		String key = t.getTask_id() + "_" + implementationId + "_" + options;
		if (currentlyCollecting.containsKey(key) == false) {
			currentlyCollecting.put(key, new OpenmlExecutedTask(t, classifier, sourceData, null, options, apiconnector, true, all_tags));
		}
		OpenmlExecutedTask oet = currentlyCollecting.get(key);
		oet.modelFullDataset(splitEvaluatorResults, tse);

		if (oet.complete()) {
			int runId = sendTask(oet);
			currentlyCollecting.remove(key);
			runIds.add(runId);
		}
	}

	public void acceptResultsForSending(Task t, Instances sourceData, Integer repeat, Integer fold, Integer sample, Classifier classifier, String options,
			List<Integer> rowids, ArrayList<Prediction> predictions, Map<String, MetricScore> userMeasures,
			List<Quadlet<String, Double, List<Entry<String, Object>>, Boolean>> optimizationTrace, boolean wantFullModel) throws Exception {
		// TODO: do something better than undefined
		String revision = (classifier instanceof RevisionHandler) ? ((RevisionHandler) classifier).getRevision() : "undefined";
		String implementationId = classifier.getClass().getName() + "(" + revision + ")";
		String key = t.getTask_id() + "_" + implementationId + "_" + options;
		if (currentlyCollecting.containsKey(key) == false) {
			currentlyCollecting.put(key, new OpenmlExecutedTask(t, classifier, sourceData, null, options, apiconnector, wantFullModel, all_tags));
		}
		OpenmlExecutedTask oet = currentlyCollecting.get(key);
		oet.addBatchOfPredictions(fold, repeat, sample, rowids, predictions, optimizationTrace);
		oet.addUserDefinedMeasures(fold, repeat, sample, userMeasures);

		if (oet.complete()) {
			int runId = sendTask(oet);
			currentlyCollecting.remove(key);
			runIds.add(runId);
		}
	}

	public void acceptErrorResult(Task t, Instances sourceData, Classifier classifier, String error_message, String options) throws Exception {
		// TODO: do something better than undefined
		String revision = (classifier instanceof RevisionHandler) ? ((RevisionHandler) classifier).getRevision() : "undefined";
		String implementationId = classifier.getClass().getName() + "(" + revision + ")";
		String key = t.getTask_id() + "_" + implementationId + "_" + options;

		if (tasksWithErrors.contains(key) == false) {
			tasksWithErrors.add(key);
			int runId = sendTaskWithError(new OpenmlExecutedTask(t, classifier, sourceData, error_message, options, apiconnector, false, all_tags));
			runIds.add(runId);
		}
	}

	private int sendTask(OpenmlExecutedTask oet) throws Exception {
		Conversion.log("INFO", "Upload Run", "Starting send run process... ");
		XStream xstream = XstreamXmlMapping.getInstance();
		File tmpPredictionsFile;
		File tmpDescriptionFile;

		// also add information about CPU performance and OS to run:
		SciMark benchmarker = SciMark.getInstance();
		oet.getRun().addOutputEvaluation(new EvaluationScore("os_information", null, null, "['" + StringUtils.join(benchmarker.getOsInfo(), "', '") + "']"));
		if (skipJvmBenchmark == false) {
			oet.getRun().addOutputEvaluation(new EvaluationScore("scimark_benchmark", benchmarker.getResult(), null, "[" + StringUtils.join(benchmarker.getStringArray(), ", ") + "]"));
		}
		tmpPredictionsFile = Conversion.stringToTempFile(oet.getPredictions().toString(), "weka_generated_predictions", Constants.DATASET_FORMAT);
		tmpDescriptionFile = Conversion.stringToTempFile(xstream.toXML(oet.getRun()), "weka_generated_run", "xml");
		Map<String, File> output_files = new HashMap<String, File>();

		output_files.put("predictions", tmpPredictionsFile);
		if (oet.serializedClassifier != null) {
			output_files.put("model_serialized", oet.serializedClassifier);
		}
		if (oet.humanReadableClassifier != null) {
			output_files.put("model_readable", oet.humanReadableClassifier);
		}
		if (oet.optimizationTrace != null) {
			output_files.put("trace", Conversion.stringToTempFile(oet.optimizationTrace.toString(), "optimization_trace", "arff"));
		}

		UploadRun ur = apiconnector.runUpload(tmpDescriptionFile, output_files);
		return ur.getRun_id();
	}

	private int sendTaskWithError(OpenmlExecutedTask oet) throws Exception {
		Conversion.log("WARNING", "Upload Run", "Starting to upload run... (including error results) ");
		XStream xstream = XstreamXmlMapping.getInstance();
		File tmpDescriptionFile;

		tmpDescriptionFile = Conversion.stringToTempFile(xstream.toXML(oet.getRun()), "weka_generated_run", Constants.DATASET_FORMAT);
		
		UploadRun ur = apiconnector.runUpload(tmpDescriptionFile, new HashMap<String, File>());
		return ur.getRun_id();
	}
	
	public List<Integer> getRunIds() {
		return runIds;
	}

	private class OpenmlExecutedTask {
		private final boolean isRegression;
		private int task_id;
		private Task task;
		private Instances predictions;
		private Instances inputData;
		private Instances optimizationTrace;
		private int nrOfResultBatches;
		private final int nrOfExpectedResultBatches;
		private List<String> classnames;
		private Run run;
		private int implementation_id;
		private boolean waitForFullModel;
		private boolean hasFullModel;

		private int repeats;
		private int samples;

		private File serializedClassifier = null;
		private File humanReadableClassifier = null;

		public OpenmlExecutedTask(Task t, Classifier classifier, Instances sourceData, String error_message, String options, OpenmlConnector apiconnector, boolean waitForFullModel, String[] tags) throws Exception {
			this.task = t;
			this.waitForFullModel = waitForFullModel;
			this.hasFullModel = false;
			
			// TODO: we need more information!
			isRegression = t.getTask_type_id().equals(2);
			inputData = sourceData;
			optimizationTrace = null;

			if (!isRegression) {
				Attribute classAttribute = sourceData.attribute(TaskInformation.getSourceData(t).getTarget_feature());
				classnames = new ArrayList<String>();
				for (int i = 0; i < classAttribute.numValues(); ++i) {
					classnames.add(classAttribute.value(i));
				}
			}
			task_id = this.task.getTask_id();

			repeats = 1;
			int folds = 1;
			samples = 1;
			
			try { repeats = TaskInformation.getNumberOfRepeats(t); } catch (Exception e) {}
			try { folds = TaskInformation.getNumberOfFolds(t); } catch (Exception e) {}
			try { samples = TaskInformation.getNumberOfSamples(t); } catch (Exception e) {}
			
			nrOfExpectedResultBatches = repeats * folds * samples;
			nrOfResultBatches = 0;
			ArrayList<Attribute> attInfo = new ArrayList<Attribute>();
			for (Feature f : TaskInformation.getPredictions(t).getFeatures()) {
				if (f.getName().equals("confidence.classname")) {
					for (String s : classnames) {
						attInfo.add(new Attribute("confidence." + s));
					}
				} else if (f.getName().equals("prediction")) {
					if (isRegression) {
						attInfo.add(new Attribute("prediction"));
					} else {
						attInfo.add(new Attribute(f.getName(), classnames));
					}
				} else {
					attInfo.add(new Attribute(f.getName()));
				}
			}

			attInfo.add(inputData.classAttribute().copy("correct"));

			predictions = new Instances("openml_task_" + t.getTask_id() + "_predictions", attInfo, 0);

			Flow find = WekaAlgorithm.serializeClassifier((OptionHandler) classifier, tags);

			implementation_id = WekaAlgorithm.getImplementationId(find, classifier, apiconnector);
			Flow implementation = apiconnector.flowGet(implementation_id);
			
			String[] params = Utils.splitOptions(options);
			List<Parameter_setting> list = WekaAlgorithm.getParameterSetting(params, implementation);
			
			String setup_string = classifier.getClass().getName() + " " + options;
			
			run = new Run(t.getTask_id(), error_message, implementation.getId(), setup_string, list.toArray(new Parameter_setting[list.size()]), tags);
		}

		public void addBatchOfPredictions(Integer fold, Integer repeat, Integer sample, List<Integer> rowids, ArrayList<Prediction> batchPredictions,
				List<Quadlet<String, Double, List<Entry<String, Object>>, Boolean>> optimizationTraceFold) {
			nrOfResultBatches += 1;
			for (int i = 0; i < rowids.size(); ++i) {
				Prediction current = batchPredictions.get(i);
				double[] values = new double[predictions.numAttributes()];
				values[predictions.attribute("row_id").index()] = rowids.get(i);
				values[predictions.attribute("fold").index()] = fold;
				values[predictions.attribute("repeat").index()] = repeat;
				values[predictions.attribute("prediction").index()] = current.predicted();
				if (predictions.attribute("sample") != null) {
					values[predictions.attribute("sample").index()] = sample;
				}
				values[predictions.attribute("correct").index()] = inputData.instance(rowids.get(i)).classValue();

				if (current instanceof NominalPrediction) {
					double[] confidences = ((NominalPrediction) current).distribution();
					for (int j = 0; j < confidences.length; ++j) {
						values[predictions.attribute("confidence." + classnames.get(j)).index()] = confidences[j];
					}
				}

				predictions.add(new DenseInstance(1.0D, values));
			}

			// add trace
			if (optimizationTraceFold != null) {
				this.optimizationTrace = OptimizationTrace.addTraceToDataset(this.optimizationTrace, optimizationTraceFold, task_id, repeat, fold);
			}
		}

		public void addUserDefinedMeasures(Integer fold, Integer repeat, Integer sample, Map<String, MetricScore> userMeasures) throws Exception {
			// attach fold/sample specific user measures to run
			for (String m : userMeasures.keySet()) {
				MetricScore score = userMeasures.get(m);

				getRun().addOutputEvaluation(new EvaluationScore(m, score.getScore(), null, repeat, fold, sample, null));
			}
		}

		public void modelFullDataset(Map<String, Object> splitEvaluatorResults, OpenmlSplitEvaluator tse) {
			// build model for entire data set. This can take some time
			Classifier classifierModel = tse.getClassifier();
			hasFullModel = true;
			String keyTraining = "UserCPU_Time_millis_training";
			String keyTesting = "UserCPU_Time_millis_testing";

			if (splitEvaluatorResults.containsKey(keyTraining) && splitEvaluatorResults.containsKey(keyTesting)) {
				Double totalTimeTraining = (Double) splitEvaluatorResults.get(keyTraining);
				Double totalTimeTesting = (Double) splitEvaluatorResults.get(keyTesting);
				Double totalTime = totalTimeTesting + totalTimeTraining;
				
				getRun().addOutputEvaluation(new EvaluationScore(keyTesting.toLowerCase(), totalTimeTesting, null, null));
				getRun().addOutputEvaluation(new EvaluationScore(keyTraining.toLowerCase(), totalTimeTraining, null, null));
				getRun().addOutputEvaluation(new EvaluationScore("usercpu_time_millis", totalTime, null, null));
			}

			try {
				humanReadableClassifier = Conversion.stringToTempFile(classifierModel.toString(), "WekaModel_" + classifierModel.getClass().getName(), "model");
			} catch (IOException ioe) {
				Conversion.log("Warning", "Model", "Problem extracting human readible model. ");
			}

			try {
				serializedClassifier = WekaAlgorithm.classifierSerializedToFile(classifierModel, task_id);
			} catch (IOException ioe) {
				Conversion.log("Warning", "Model", "Problem extracting serializable model. ");
			}
		}

		public Run getRun() {
			return run;
		}

		public Instances getPredictions() {
			return predictions;
		}

		public boolean complete() {
			boolean allFolds = nrOfResultBatches == nrOfExpectedResultBatches;
			
			if (waitForFullModel) {
				return allFolds && hasFullModel;
			} else {
				return allFolds;
			}
		}
	}
}
