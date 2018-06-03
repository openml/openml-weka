package org.openml.weka.algorithm;


public enum WekaParameterType {
	FLAG("flag"), KERNEL("kernel"), CLASSIFIER("classifier"), OPTION("option"), ARRAY("array");

	private String text;

	WekaParameterType(String text) {
		this.text = text;
	}

	/**
	 * @return The name of this parameter type;
	 */
	public String getName() {
		return this.text;
	}

	/**
	 * Converts a textual description of a parameter into a ParameterType
	 * 
	 * @param text (String)
	 * @return the ParameterType
	 */
	public static WekaParameterType fromString(String text) {
		if (text != null) {
			for (WekaParameterType b : WekaParameterType.values()) {
				if (text.equalsIgnoreCase(b.text)) {
					return b;
				}
			}
		}
		return null;
	}
}
