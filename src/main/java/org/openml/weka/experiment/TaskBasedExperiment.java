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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;

import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.Run;
import org.openml.apiconnector.xml.RunList;
import org.openml.apiconnector.xml.Task;
import org.openml.weka.algorithm.WekaAlgorithm;
import org.openml.weka.algorithm.WekaConfig;

import weka.experiment.Experiment;

public class TaskBasedExperiment extends Experiment {

	private static final long serialVersionUID = 1L;

	/** An array of the Tasks to be executed */
	protected DefaultListModel<Task> m_Tasks = new DefaultListModel<Task>();

	/** The task currently being used */
	protected Task m_CurrentTask;

	protected final OpenmlConnector apiconnector;
	
	protected final WekaConfig openmlconfig;

	public TaskBasedExperiment(Experiment exp, OpenmlConnector apiconnector, WekaConfig config) {
		this.m_ResultListener = exp.getResultListener();
		this.m_ResultProducer = exp.getResultProducer();
		this.m_RunLower = exp.getRunLower();
		this.m_RunUpper = exp.getRunUpper();
		this.m_Datasets = exp.getDatasets();
		this.m_UsePropertyIterator = true;
		this.m_PropertyArray = exp.getPropertyArray();
		this.m_Notes = exp.getNotes();
		// this.m_AdditionalMeasures =
		// this.m_ClassFirst = exp.classFirst(flag)
		this.m_AdvanceDataSetFirst = exp.getAdvanceDataSetFirst();

		this.apiconnector = apiconnector;
		this.openmlconfig = config;
	}

	public DefaultListModel<Task> getTasks() {
		return m_Tasks;
	}

	public void setTasks(DefaultListModel<Task> tasks) {
		m_Tasks = tasks;
	}

	// TODO: dummy function for compatibility with Weka's RunPanel
	public DefaultListModel<File> getDatasets() {
		DefaultListModel<File> datasets = new DefaultListModel<File>();
		for (int i = 0; i < m_Tasks.size(); ++i) {
			datasets.add(i, new File("Task_" + m_Tasks.get(i).getTask_id() + ".arff"));
		}
		return datasets;
	}

	@Override
	public void initialize() throws Exception {
		m_RunNumber = getRunLower();
		m_DatasetNumber = 0;
		m_PropertyNumber = 0;
		m_CurrentProperty = -1;
		m_CurrentInstances = null;
		m_CurrentTask = null;
		m_Finished = false;

		if (m_UsePropertyIterator && (m_PropertyArray == null)) {
			throw new Exception("Null array for property iterator");
		}
		if (getRunLower() > getRunUpper()) {
			throw new Exception("Lower run number is greater than upper run number");
		}
		if (getTasks().size() == 0) {
			throw new Exception("No tasks have been specified");
		}
		if (m_ResultProducer == null) {
			throw new Exception("No ResultProducer set");
		}
		if (m_ResultListener == null) {
			throw new Exception("No ResultListener set");
		}

		m_ResultProducer.setResultListener(m_ResultListener);
		m_ResultProducer.setAdditionalMeasures(m_AdditionalMeasures);
		m_ResultProducer.preProcess();

		// constrain the additional measures to be only those allowable
		// by the ResultListener
		String[] columnConstraints = m_ResultListener.determineColumnConstraints(m_ResultProducer);

		if (columnConstraints != null) {
			m_ResultProducer.setAdditionalMeasures(columnConstraints);
		}
	}

	@Override
	public void nextIteration() throws Exception {

		if (m_CurrentTask == null) {
			m_CurrentTask = (Task) getTasks().elementAt(m_DatasetNumber);

			((TaskResultProducer) m_ResultProducer).setTask(m_CurrentTask);
			this.setRunUpper(TaskInformation.getNumberOfRepeats(m_CurrentTask));

			// set classifier. Important, since by alternating between
			// regression and
			// classification tasks we possibly have resetted the splitevaluator

			System.err.println(((TaskResultProducer) m_ResultProducer).getSplitEvaluator().getClass().toString());

			if (m_UsePropertyIterator) {
				setProperty(0, m_ResultProducer);
				m_CurrentProperty = m_PropertyNumber;
			}

		}
		
		if (openmlconfig.getAvoidDuplicateRuns()) {
			String classifierName = (String) ((TaskResultProducer) m_ResultProducer).getSplitEvaluatorKey(0);
			String classifierOptions = (String) ((TaskResultProducer) m_ResultProducer).getSplitEvaluatorKey(1);
			
			Integer setupId = WekaAlgorithm.getSetupId(classifierName, classifierOptions, apiconnector);

			if (setupId != null) {
				List<Integer> taskIds = new ArrayList<Integer>();
				taskIds.add(m_CurrentTask.getTask_id());
				List<Integer> setupIds = new ArrayList<Integer>();
				setupIds.add(setupId);

				try {
					Map<String, List<Integer>> filters = new HashMap<String, List<Integer>>();
					filters.put("task", taskIds);
					filters.put("setup", setupIds);
					
					RunList rl = apiconnector.runList(filters, null, null);

					if (rl.getRuns().length > 0) {
						List<Integer> runIds = new ArrayList<Integer>();
						for (Run r : rl.getRuns()) {
							runIds.add(r.getRun_id());
						}

						Conversion.log("INFO", "Skip", "Skipping run "+classifierName+" (setup #"+setupId+") repeat "+m_RunNumber+", already available. Run ids: " + runIds);
						advanceCounters();
						return;
					}
				} catch (Exception e) {}
			}
		}
		
		
		m_ResultProducer.doRun(m_RunNumber);
		advanceCounters();
	}

	@Override
	public void advanceCounters() {
		m_RunNumber++;
		if (m_RunNumber > getRunUpper()) {
			m_RunNumber = getRunLower();
			m_DatasetNumber++;
			m_CurrentInstances = null;
			m_CurrentTask = null;
			if (m_DatasetNumber >= getTasks().size()) {
				m_DatasetNumber = 0;
				if (m_UsePropertyIterator) {
					m_PropertyNumber++;
					if (m_PropertyNumber >= Array.getLength(m_PropertyArray)) {
						m_Finished = true;
					}
				} else {
					m_Finished = true;
				}
			}
		}
	}
}
