Package for uploading Weka experiments to OpenML.

Code example, download 100 (pre-selected) datasets and execute a REPTree on these.

```java
public static void runTasksAndUpload() throws Exception {
  // Fill in the API key (obtainable from your OpenML profile)
  String apikey = "d488d8afd93b32331cf6ea9d7003d4c3";
  
  // The WekaConfig module gives us the possibilities to enable or disable various Weka Specific options
  WekaConfig config = new WekaConfig();
  
  // Instantiate the OpenmlConnector object 
  // requires artifact org.openml.apiconnector (version 1.0.14) from Maven central
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
