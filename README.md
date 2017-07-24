# OpenML Weka Connector
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)
[![Build Status](https://travis-ci.org/openml/openml-weka.svg?branch=master)](https://travis-ci.org/openml/openml-weka)
[![Coverage Status](https://coveralls.io/repos/github/openml/openml-weka/badge.svg?branch=master)](https://coveralls.io/github/openml/openml-weka?branch=master)

Package for uploading Weka experiments to OpenML. Works in combination with the [OpenML Apiconnector](https://github.com/openml/java/tree/master/apiconnector) (available on [Maven Central](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22apiconnector%22); version >= 1.0.14) and [Weka](http://www.cs.waikato.ac.nz/ml/weka/) (available on [Maven Central](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22weka-dev%22); version >= 3.9.0)

# Downloading datasets from OpenML
The following code example downloads a specific set of OpenML datasets and loads them into the Weka data format ([weka.core.Instances](http://weka.sourceforge.net/doc.dev/weka/core/Instance.html)), that can be used trivially for off line development and experimenting.
```java
public static void downloadData() throws Exception {
  // Fill in the API key (obtainable from your OpenML profile)
  String apikey = "<FILL_IN_OPENML_API_KEY>";
  
  // Instantiate the OpenmlConnector object 
  // requires artifact org.openml.apiconnector (version 1.0.14) from Maven central
  OpenmlConnector openml = new OpenmlConnector(apikey);
  
  // Download the OpenML object containing the `OpenML100' benchmark set
  Study s = openml.studyGet("OpenML100", "data");
  
  // Loop over all the datasets
  for (Integer dataId : s.getDataset()) {
    // DataSetDescription is an OpenML object containing meta-information about the dataset
    DataSetDescription dsd = openml.dataGet(dataId);
    
    // datasetFile downloads the raw dataset file from openml
    File datasetFile = dsd.getDataset(apikey);
    
    // Converts this file into the Weka format
    Instances dataset = new Instances(new FileReader(datasetFile));
    System.out.println("Downloaded " + dsd.getName());
    System.out.println("numObservations = " + dataset.numInstances() + "; numFeatures = " + dataset.numAttributes());
  }
}
```

# Uploading Weka experiments
The following code example downloads a specific set of OpenML tasks (dubbed: [the OpenML100](https://www.openml.org/s/14/)) and executes a NaiveBayes classifier on it. 
```java
public static void runTasksAndUpload() throws Exception {
  // Fill in the API key (obtainable from your OpenML profile)
  String apikey = "<FILL_IN_APIKEY>";
  
  // The WekaConfig module gives us the possibilities to enable or disable various Weka Specific options
  WekaConfig config = new WekaConfig();
  
  // Instantiate the OpenmlConnector object 
  // requires artifact org.openml.apiconnector (version >= 1.0.14) from Maven central
  OpenmlConnector openml = new OpenmlConnector(apikey);
  
  // Download the OpenML object containing the `OpenML100' benchmark set
  Study s = openml.studyGet("OpenML100", "tasks");
  
  // Loop over all the tasks
  for (Integer taskId : s.getTasks()) {
    // create a Weka classifier to run on the task
    Classifier tree = new NaiveBayes();
    
    // execute the task (can take a while, depending on the classifier / dataset combination)
    int runId = RunOpenmlJob.executeTask(openml, config, taskId, tree);
    
    // After several minutes, the evaluation measures will be available on the server
    System.out.println("Available on " + openml.getApiUrl() + "run/" + runId);
    
    // Download the run from the server:
    Run run = openml.runGet(runId);
  }
}
```

# Obtaining experimental results from OpenML
OpenML contains a large number of experiments, conveniently available for everyone. In order to obtain and analyse these results, the [OpenML Apiconnector](https://github.com/openml/java/tree/master/apiconnector) could be of use. Please follow the demonstration depicted on the respective Github page. 

# How to cite
If you found this package useful, please cite: J. N. van Rijn, Massively Collaborative Machine Learning, Leiden University, 2016. If you used OpenML in a scientific publication, please check out the [OpenML citation policy](https://www.openml.org/cite). 

