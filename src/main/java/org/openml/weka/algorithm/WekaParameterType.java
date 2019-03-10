package org.openml.weka.algorithm;


public enum WekaParameterType {
	// flag: boolean parameter, 0 arguments
	// classifier: has a weka classifier as subclass. supports options. often used for ensembles
	// optionhandler: has any other type of option handler as subclass. supports options. Often used for k-nn or svn (kernel)
	// parameterfree_class: one level higher than optionhandler. Typically indicates that the class does not handle
	// option: vanilla option, for string, float or int values
	// array: array of vanilla values
	FLAG("flag"), CLASSIFIER("classifier"), OPTIONHANDLER("optionhandler"), PARAMETERFREE_CLASS("parameterfree_class"), OPTION("option"), ARRAY("array");

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
