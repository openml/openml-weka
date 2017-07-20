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

package weka.filters.unsupervised.attribute;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.Utils;
import weka.filters.SimpleBatchFilter;

public class RemoveUnusedClassValues extends SimpleBatchFilter implements OptionHandler {

	private static final long serialVersionUID = 5724291284990109383L;
	
	private int oldClassIndex;
	private int threshold = 1;

	public String globalInfo() {
		return "A simple batch filter that replaces a nominal class with a nominal class that only contains the used values.";
	}

	public Capabilities getCapabilities() {
		Capabilities result = super.getCapabilities();
		result.enableAllAttributes();
		result.enable(Capability.NOMINAL_CLASS); 
		return result;
	}

	protected Instances determineOutputFormat(Instances inputFormat) {
		inputFormat = getInputFormat(); // FIX, we didn't want just the header!
		
		Instances result = new Instances(inputFormat, 0);
		
		int[] usedClassValues = inputFormat.attributeStats( inputFormat.classIndex() ).nominalCounts;
		oldClassIndex = inputFormat.classIndex();
		
		List<String> newClassValues = new ArrayList<String>();
		for( int i = 0; i < usedClassValues.length; ++i ) {
			if( usedClassValues[i] > threshold ) newClassValues.add( inputFormat.classAttribute().value( i ) );
		}
		result.setClassIndex( -1 );
		result.deleteAttributeAt( oldClassIndex );
		Attribute newClassAttribute = new Attribute( "class", newClassValues );
		
		result.insertAttributeAt( newClassAttribute, oldClassIndex );
		result.setClassIndex( oldClassIndex );
		
		return result;
	}

	protected Instances process(Instances inst) {
		Instances result = new Instances(determineOutputFormat(inst), inst.numInstances() );
		for (int i = 0; i < inst.numInstances(); i++) {
			double[] values = new double[result.numAttributes()];
			for (int n = 0; n < inst.numAttributes(); n++) {
				if( n == oldClassIndex ) {
					String oldValue = inst.classAttribute().value( (int) inst.instance(i).classValue() );
					values[n] = result.classAttribute().indexOfValue( oldValue );
				} else {
					values[n] = inst.instance(i).value(n);
				}
			}
			
			if( values[oldClassIndex] < 0.0 ) {
				System.out.println("Discarded (0-based) instance: " + i);
			} else {
				Instance newInstance = new DenseInstance(1, values);
				result.add( newInstance );
			}
		}
		
		return result;
	}

	@Override
	public void setOptions(String[] options) throws Exception {
		threshold = Integer.parseInt(Utils.getOption('T', options));

		if (getInputFormat() != null)
			setInputFormat(getInputFormat());
	}

	// TODO: For some reason, Weka's GUI doesn't pick up these options...
	@Override
	public Enumeration<Option> listOptions() {
		Vector<Option> newVector = new Vector<Option>(1);

		newVector.addElement(new Option(
				"\tSpecifies threshold of occurences. Every value"
						+ " occuring less than T will be removed from class.\n"
						+ "\t(default none)", "T", 1, "-T <int1>"));
		return newVector.elements();
	}

	@Override
	public String[] getOptions() {
		String[] options = new String[2];
		int current = 0;
		options[current++] = "-T";
		options[current++] = threshold + "";
		return options;
	}

	/**
	 * Main method for testing this class.
	 * 
	 * @param argv
	 *            should contain arguments to the filter: use -h for help
	 */
	public static void main(String[] argv) {
		runFilter(new RemoveUseless(), argv);
	}

}