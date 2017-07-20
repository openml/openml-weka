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

import org.openml.apiconnector.settings.Config;

public class WekaConfig extends Config {
	
	private static final long serialVersionUID = 5388614163709096253L;

	public WekaConfig() {
		super();
	}
	
	public WekaConfig(String config) {
		super(config);
	}
	
	/**
	 * @return Whether to build a model over full dataset in runs (takes time)
	 */
	public boolean getModelFullDataset() {
		if (get("model_full_dataset") == null) {
			return true; // default value
		}
		if (get("model_full_dataset").equals("false")) {
			return false;
		}
		return true;
	}
	
	/**
	 * @return Whether to benchmark Jvm before uploading results
	 */
	public boolean getSkipJvmBenchmark() {
		if (get("skip_jvm_benchmark") == null) {
			return false; // default value
		}
		if (get("skip_jvm_benchmark").equals("true")) {
			return true;
		}
		return false;
	}
	

	/**
	 * @return Whether to avoid duplicate runs
	 */
	public boolean getAvoidDuplicateRuns() {
		if (get("avoid_duplicate_runs") == null) {
			return true; // default value
		}
		if (get("avoid_duplicate_runs").equals("false")) {
			return false;
		}
		return true;
	}
	
	public String getJobRequestTaskTag() {
		return get("job_request_task_tag");
	}
	
	public String getJobRequestSetupTag() {
		return get("job_request_setup_tag");
	}
	
	public Integer getJobRequestSetupId() {
		if (get("job_request_setup_id") != null) {
			return Integer.parseInt(get("job_request_setup_id"));
		}
		return null;
	}
}
