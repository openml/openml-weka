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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.settings.Constants;
import org.openml.weka.algorithm.WekaConfig;
import org.openml.weka.experiment.OpenmlClassificationSplitEvaluator;
import org.openml.weka.experiment.TaskBasedExperiment;
import org.openml.weka.experiment.TaskResultListener;
import org.openml.weka.experiment.TaskResultProducer;

import weka.classifiers.Classifier;
import weka.experiment.Experiment;
import weka.experiment.PropertyNode;
import weka.experiment.SplitEvaluator;
import weka.gui.experiment.AlgorithmListPanel;
import weka.gui.experiment.SetupModePanel;
import weka.gui.experiment.SimpleSetupPanel;

/**
 * This panel controls the configuration of an experiment.
 * <p>
 * If <a href="http://koala.ilog.fr/XML/serialization/" target="_blank">KOML</a>
 * is in the classpath the experiments can also be serialized to XML instead of
 * a binary format.
 * 
 * @author Richard kirkby (rkirkby@cs.waikato.ac.nz)
 * @author FracPete (fracpete at waikato dot ac dot nz)
 * @version $Revision: 8034 $
 */
public class OpenmlSimpleSetupPanel extends SimpleSetupPanel { 
	// inherits from SimpleSetupPanel rather than AbstractSetupPanel 
	// for compatibility with Weka 3.7.12
	private static final long serialVersionUID = -4411056918296619225L;

	/** The experiment being configured */
	protected TaskBasedExperiment m_Exp;
	
	/** The panel for configuring selected OpenML tasks */
	protected TaskListPanel m_TaskListPanel;

	/** The panel for configuring selected algorithms */
	protected AlgorithmListPanel m_AlgorithmListPanel = new AlgorithmListPanel();
	
	/** Button for browsing destination files */
	protected JButton m_BrowseDestinationButton = new JButton("Change");
	
	/** A button for bringing up the notes */
	protected JButton m_NotesButton =  new JButton("Notes");
	
	/** Frame for the notes */
	protected JFrame m_NotesFrame = new JFrame("Notes");
	
	/** Area for user notes Default of 10 rows */
	protected JTextArea m_NotesText = new JTextArea(null, 10, 0);
	
	/**
	 * Manages sending notifications to people when we change the experiment,
	 * at this stage, only the resultlistener so the resultpanel can update.
	 **/
	protected final PropertyChangeSupport m_Support;
	
	protected OpenmlConnector apiconnector;
	
	protected WekaConfig openmlconfig;
	
	
	/** Label for destination field */
	protected JLabel m_ResultsDestinationPathLabel = new JLabel("OpenML.org");

	/** Input field for result destination path */ 
	protected JTextField m_ResultsDestinationPathTField = new JTextField();
	
	
	/**
	 * Creates the setup panel with the supplied initial experiment.
	 * 
	 * @param exp
	 *            a value of type 'Experiment'
	 */
	public OpenmlSimpleSetupPanel(Experiment exp) {

		this();
		setExperiment(exp);
	}

	/**
	 * Creates the setup panel with no initial experiment.
	 */
	public OpenmlSimpleSetupPanel() {
		m_Support = new PropertyChangeSupport(this);
		m_ResultsDestinationPathTField.setEnabled(false);
		
		try {
			openmlconfig = new WekaConfig();
			
			String apiKey = openmlconfig.getApiKey();
			
			if( openmlconfig.getServer() != null ) {
				apiconnector = new OpenmlConnector(openmlconfig.getServer(), apiKey);
				m_ResultsDestinationPathTField.setText(apiKeyToStarred(apiKey));
			} else { 
				apiconnector = new OpenmlConnector(apiKey);
			}
		} catch( RuntimeException e ) {
			apiconnector = new OpenmlConnector();
		}
		
		m_TaskListPanel = new TaskListPanel( apiconnector );
		
		m_NotesFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				m_NotesButton.setEnabled(true);
			}
		});
		m_NotesFrame.getContentPane().add(new JScrollPane(m_NotesText));
		m_NotesFrame.setSize(600, 400);
		
		m_NotesButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				  m_NotesButton.setEnabled(false);
				  m_NotesFrame.setVisible(true);
			}
		});
		m_NotesButton.setEnabled(false);
		
		m_NotesText.setEditable(true);
		//m_NotesText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		m_NotesText.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				m_Exp.setNotes(m_NotesText.getText());
			}
		});
		m_NotesText.addFocusListener(new FocusAdapter() {
			public void focusLost(FocusEvent e) {
				m_Exp.setNotes(m_NotesText.getText());
			}
		});
		
		m_BrowseDestinationButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//using this button for both browsing file & setting username/password
				chooseOpenmlAuthenticate();
			}
		});
		

	    JPanel destInner = new JPanel();
	    destInner.setLayout(new BorderLayout(5, 5));
	    destInner.add(m_ResultsDestinationPathLabel, BorderLayout.WEST);
	    destInner.add(m_ResultsDestinationPathTField, BorderLayout.CENTER);
	    destInner.add(m_BrowseDestinationButton, BorderLayout.EAST);
		
		JPanel dest = new JPanel();
		dest.setLayout(new BorderLayout());
		dest.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder("Results Destination"),
			BorderFactory.createEmptyBorder(0, 5, 5, 5)
		));
		dest.add(destInner, BorderLayout.NORTH);
		
		JPanel notes = new JPanel();
	    notes.setLayout(new BorderLayout());
	    notes.add(m_NotesButton, BorderLayout.CENTER);
		
	    JPanel tasks = new JPanel();
	    tasks.setLayout(new BorderLayout());
	    tasks.add(m_TaskListPanel, BorderLayout.CENTER);

	    JPanel algorithms = new JPanel();
	    algorithms.setLayout(new BorderLayout());
	    algorithms.add(m_AlgorithmListPanel, BorderLayout.CENTER);

	    JPanel schemes = new JPanel();
	    schemes.setLayout(new GridLayout(1,0));
	    schemes.add(tasks);
	    schemes.add(algorithms);

	    setLayout(new BorderLayout());
	    add(dest, BorderLayout.NORTH);
	    add(schemes, BorderLayout.CENTER);
	    add(notes, BorderLayout.SOUTH);
	    
	    setExperiment(new Experiment());
	}

	/**
	 * Sets the experiment to configure.
	 * 
	 * @param exp_old - a value of type 'Experiment'
	 * @return true if experiment could be configured, false otherwise
	 */
	public boolean setExperiment(Experiment exp_old) {

		TaskBasedExperiment exp = new TaskBasedExperiment(exp_old, apiconnector, openmlconfig);
		
		TaskResultListener trl = new TaskResultListener(apiconnector, openmlconfig);
		try {
			File f = File.createTempFile("WekaOpenMLResults", Constants.DATASET_FORMAT);
			f.deleteOnExit();
			trl.setOutputFile(f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		exp.setResultListener(trl);
		
		TaskResultProducer trp = new TaskResultProducer(apiconnector, openmlconfig);
		
		SplitEvaluator se = null;
	    Classifier sec = null;
	    
	    se = new OpenmlClassificationSplitEvaluator();
    	sec = ((OpenmlClassificationSplitEvaluator)se).getClassifier();
	    
		
		PropertyNode[] propertyPath = new PropertyNode[2];
		try {
			propertyPath[0] = new PropertyNode(se, new PropertyDescriptor(
					"splitEvaluator", TaskResultProducer.class),
					TaskResultProducer.class);
			propertyPath[1] = new PropertyNode(sec, new PropertyDescriptor(
					"classifier", se.getClass()), se.getClass());
		} catch (IntrospectionException e) {
			e.printStackTrace();
		}

		exp.setResultProducer(trp);
		exp.setPropertyPath(propertyPath);
		
		// TODO: implement
		m_TaskListPanel.setExperiment(exp);
	    m_AlgorithmListPanel.setExperiment(exp);
	    
	    m_Exp = exp;
	    
		m_Support.firePropertyChange("", null, null);
		
		return true;
	}

	/**
	 * Adds a PropertyChangeListener who will be notified of value changes.
	 * 
	 * @param l
	 *            a value of type 'PropertyChangeListener'
	 */
	@Override
	public void addPropertyChangeListener(PropertyChangeListener l) {
		if (m_Support == null) {
			// TODO: SHOULD never happen. Happens nonetheless... 
			return;
		}
		
		m_Support.addPropertyChangeListener(l);
	}

	/**
	 * Removes a PropertyChangeListener.
	 * 
	 * @param l
	 *            a value of type 'PropertyChangeListener'
	 */
	@Override
	public void removePropertyChangeListener(PropertyChangeListener l) {
		m_Support.removePropertyChangeListener(l);
	}
	
	/**
	 * Gets the currently configured experiment.
	 * 
	 * @return the currently configured experiment.
	 */
	@Override
	public Experiment getExperiment() {

		return m_Exp;
	}

	@Override
	public String getName() {
		return "OpenML Experimenter";
	}

	@Override
	public void setModePanel(SetupModePanel modePanel) {
		// TODO Auto-generated method stub
		
	}

	private void chooseOpenmlAuthenticate() {
		AuthenticationDialog ad = new AuthenticationDialog(null);
		ad.setVisible(true);

		// if (dbaseURL == null) {
		if (ad.getReturnValue() == JOptionPane.CLOSED_OPTION) {
			return;
		}
		
		apiconnector.setApiKey(ad.getApiKey());
		m_ResultsDestinationPathTField.setText(apiKeyToStarred(ad.getApiKey()));
	}
	
	private static String apiKeyToStarred(String apiKey) {
		return "********************" + apiKey.substring(apiKey.length() - 4);
	}
}
