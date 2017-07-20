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

package org.openml.weka.algorithm;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.DataSetDescription;
import org.openml.apiconnector.xml.Task;
import org.openml.apiconnector.xml.Task.Input.Data_set;

import weka.core.Instances;

public class InstancesHelper {

	public static void setTargetAttribute(Instances instances, String classAttribute) throws Exception {
		for (int i = 0; i < instances.numAttributes(); ++i) {
			if (instances.attribute(i).name().equals(classAttribute)) {
				instances.setClassIndex(i);
				return;
			}
		}
		throw new Exception("classAttribute " + classAttribute + " non-existant on dataset. ");
	}

	public static int getAttributeIndex(Instances instances, String attribute) throws Exception {
		for (int i = 0; i < instances.numAttributes(); ++i) {
			if (instances.attribute(i).name().equals(attribute)) {
				return i;
			}
		}
		throw new Exception("Attribute " + attribute + " non-existant on dataset. ");
	}

	public static List<String> getAttributes(Instances dataset) {
		List<String> attributesAvailable = new ArrayList<String>();
		for (int j = 0; j < dataset.numAttributes(); ++j) {
			attributesAvailable.add(dataset.attribute(j).name());
		}

		return attributesAvailable;
	}
	
	public static Instances getDatasetFromTask(OpenmlConnector apiconnector, Task task) throws Exception {
		Data_set ds = TaskInformation.getSourceData(task);

		DataSetDescription dsd = ds.getDataSetDescription(apiconnector);
		Instances instances = new Instances(new FileReader(dsd.getDataset(apiconnector.getApiKey())));

		InstancesHelper.setTargetAttribute(instances, ds.getTarget_feature());

		// remove attributes that may not be used.
		if (dsd.getIgnore_attribute() != null) {
			for (String ignoreAttr : dsd.getIgnore_attribute()) {
				String attName = ignoreAttr;
				Integer attIdx = instances.attribute(ignoreAttr).index();
				Conversion.log("OK", "Remove Attribte", "Removing attribute " + attName + " (1-based index: " + attIdx + ")");
				instances.deleteAttributeAt(attIdx);
			}
		}

		if (dsd.getRow_id_attribute() != null) {
			String attName = dsd.getRow_id_attribute();
			Integer attIdx = instances.attribute(dsd.getRow_id_attribute()).index();
			Conversion.log("OK", "Remove Attribte", "Removing attribute " + attName + " (1-based index: " + attIdx + ")");
			instances.deleteAttributeAt(attIdx);
		}
		
		return instances;
	}

}
