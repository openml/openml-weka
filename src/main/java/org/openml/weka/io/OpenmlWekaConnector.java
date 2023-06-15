package org.openml.weka.io;

import java.io.IOException;
import java.io.InputStreamReader;
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
	 * Downloads a dataset from OpenML and parses it as Instances object
	 * 
	 * @param dsd - the data set description object, as downloaded from openml
	 * @return the dataset file parsed as arff
	 * @throws Exception - Can be various things, but most notably a 
	 * 					   parsing exception when the file id is not
	 *                     valid arff
	 */
	public Instances getDataset(DataSetDescription dsd) throws Exception {
		URL url = super.getOpenmlFileUrl(dsd.getFile_id(), dsd.getName() + ".arff");
		return new Instances(urlToStreamReader(url));
	}
	
	/**
	 * Downloads a data splits file from OpenML and parses it as Instances object
	 * 
	 * @param task - the downloaded task object, as downloaded from openml
	 * @return the splits file parsed as arff
	 * @throws Exception - Can be various things, but most notably a 
	 * 					   parsing exception when the file id is not
	 *                     valid arff
	 */
	public Instances getSplitsFromTask(Task task) throws Exception {
		URL url = TaskInformation.getEstimationProcedure(task).getData_splits_url();
		return new Instances(urlToStreamReader(url));
	}
	
	/**
	 * Downloads a file from OpenML and parses it as Instances object
	 * 
	 * @param fileId - the openml file id
	 * @return the file parsed as arff
	 * @throws Exception - Can be various things, but most notably a 
	 * 					   parsing exception when the file id is not
	 *                     valid arff
	 */
	public Instances getArffFromUrl(int fileId) throws Exception {
		URL url = super.getOpenmlFileUrl(fileId, fileId + ".arff");
		return new Instances(urlToStreamReader(url));
	}

	private static InputStreamReader urlToStreamReader(URL url) throws IOException {
		// JvR: please note that not all redirects are being respected. 
		// Forwards that change protocol (such as http - https) are not respected (safety, documented)
		// please ensure to only use https calls
		HttpURLConnection urlConnection = (HttpURLConnection) (url.openConnection());
		urlConnection.setInstanceFollowRedirects(true);
		urlConnection.setConnectTimeout(1000);
		urlConnection.setReadTimeout(30000);
		urlConnection.connect();
		int responseCode = urlConnection.getResponseCode();
		Conversion.log("OK", "URL", "HTTP request status code [" + responseCode + "] URL [" + url + "]");
		return new InputStreamReader(urlConnection.getInputStream());
	}
}
