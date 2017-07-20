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

package org.openml.weka.gui;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import weka.core.Memory;
import weka.experiment.Experiment;
import weka.gui.GUIChooser.GUIChooserMenuPlugin;
import weka.gui.experiment.RunPanel;
import weka.gui.experiment.SimpleSetupPanel;

public class OpenmlExperimenter extends JPanel implements GUIChooserMenuPlugin {

	/** for serialization */
	private static final long serialVersionUID = -5751617505738193788L;

	/** The panel for configuring the experiment */
	protected SimpleSetupPanel m_SetupModePanel;

	/** The panel for running the experiment */
	protected RunPanel m_RunPanel;

	/** The tabbed pane that controls which sub-pane we are working with */
	protected JTabbedPane m_TabbedPane = new JTabbedPane();

	/**
	 * Creates the experiment environment gui with no initial experiment
	 */
	public OpenmlExperimenter(boolean classFirst) {

		m_SetupModePanel = new OpenmlSimpleSetupPanel();
		m_RunPanel = new RunPanel(m_SetupModePanel.getExperiment());

		m_TabbedPane.addTab("Setup", null, m_SetupModePanel, "Set up the experiment");
		m_TabbedPane.addTab("Run", null, m_RunPanel, "Run the experiment");
		
		m_TabbedPane.setSelectedIndex(0);
		m_SetupModePanel.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				Experiment exp = m_SetupModePanel.getExperiment();
				exp.classFirst(true);
				m_RunPanel.setExperiment(exp);
				// m_ResultsPanel.setExperiment(exp);
				m_TabbedPane.setEnabledAt(1, true);
			}
		});
		setLayout(new BorderLayout());
		add(m_TabbedPane, BorderLayout.CENTER);
	}

	public OpenmlExperimenter() {
		this(true);
	}

	@Override
	public String getApplicationName() {
		return "OpenML Experimenter";
	}

	@Override
	public JMenuBar getMenuBar() {
		return null;
	}

	@Override
	public String getMenuEntryText() {
		return "OpenML Experimenter";
	}

	@Override
	public Menu getMenuToDisplayIn() {
		return GUIChooserMenuPlugin.Menu.TOOLS;
	}

	/** for monitoring the Memory consumption */
	protected static Memory m_Memory = new Memory(true);

}