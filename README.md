Package for uploading Weka experiments to OpenML.

Code example, download 100 (pre-selected) datasets and execute a REPTree on these.

```
public static void runTasksAndUpload() throws Exception {
  OpenmlConnector openml = new OpenmlConnector("<FILL_IN_OPENML_API_KEY>");
  Study s = openml.studyGet("OpenML100", "tasks"); 
  Classifier tree = new REPTree(); 
  for (Integer taskId : s.getTasks()) { 
    Task t = openml.taskGet(taskId); 
    Instances d = InstancesHelper.getDatasetFromTask(openml, t);
    int runId = RunOpenmlJob.executeTask(openml, new WekaConfig(), taskId, tree);
    Run run = openml.runGet(runId);
  }
} 
```
