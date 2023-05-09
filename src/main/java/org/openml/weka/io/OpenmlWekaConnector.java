package org.openml.weka.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.algorithms.TaskInformation;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.DataSetDescription;
import org.openml.apiconnector.xml.Task;

import weka.core.Instances;

public class OpenmlWekaConnector extends OpenmlConnector {
	
	private static final long serialVersionUID = -2963321362833051576L;
	

	public OpenmlWekaConnector() {
		super();
	}
	
	public OpenmlWekaConnector(String url, String api_key) {
		super(url, api_key);
	}
	
	/**
	 * Open a http connection to a openML dataset file and return a file reader.
	 * The resulting file reader can be wrapped by a Weka ArffReader. Alternatively, it can be wrapped by a Weka
	 * Instances object, in which case the complete file will be read into memory.
	 * 
	 * @param dsd - the data set description object, as downloaded from openml
	 * @return the dataset file parsed as arff
	 * @throws Exception - Can be various things, but most notably a 
	 * 					   parsing exception when the file id is not
	 *                     valid arff
	 */
	public Reader getDataset(DataSetDescription dsd) throws Exception {
		URL url = super.getOpenmlFileUrl(dsd.getFile_id(), dsd.getName() + ".arff");
		return urlToStreamReader(url);
	}
	
	/**
	 * Open a http connection to a openML splits file and return a file reader.
	 * The resulting file reader can be wrapped by a Weka ArffReader. Alternatively, it can be wrapped by a Weka
	 * Instances object, in which case the complete file will be read into memory.
	 * 
	 * @param task - the downloaded task object, as downloaded from openml
	 * @return the splits file parsed as arff
	 * @throws Exception - Can be various things, but most notably a 
	 * 					   parsing exception when the file id is not
	 *                     valid arff
	 */
	public Reader getSplitsFromTask(Task task) throws Exception {
		URL url = TaskInformation.getEstimationProcedure(task).getData_splits_url();
		return urlToStreamReader(url);
	}
	
	/**
	 * Open a http connection to a openML file and return a file reader.
	 * The resulting file reader can be wrapped by a Weka ArffReader. Alternatively, it can be wrapped by a Weka
	 * Instances object, in which case the complete file will be read into memory.
	 * 
	 * @param fileId - the openml file id
	 * @return a file reader
	 * @throws Exception - Can be various things, but most notably a 
	 * 					   parsing exception when the file id is not
	 *                     valid arff
	 */
	public Reader getArffFromUrl(int fileId) throws Exception {
		URL url = super.getOpenmlFileUrl(fileId, fileId + ".arff");
		return urlToStreamReader(url);
	}

	private static Reader urlToStreamReader(URL url) throws IOException {
		HttpURLConnection urlConnection = (HttpURLConnection) (url.openConnection());
		urlConnection.setInstanceFollowRedirects(true);
		urlConnection.setConnectTimeout(1000);
		urlConnection.setReadTimeout(30000);
		urlConnection.connect();
		int responseCode = urlConnection.getResponseCode();
		Conversion.log("OK", "URL", "HTTP request status code [" + responseCode + "] URL [" + url + "]");
		return new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
	}
}
