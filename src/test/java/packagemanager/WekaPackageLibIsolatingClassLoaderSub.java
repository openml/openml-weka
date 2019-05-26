package packagemanager;

import java.io.File;

import weka.core.WekaPackageClassLoaderManager;
import weka.core.WekaPackageLibIsolatingClassLoader;

public class WekaPackageLibIsolatingClassLoaderSub extends WekaPackageLibIsolatingClassLoader {
	// to use in unittesting, to access the "integrityCheck" method
	public WekaPackageLibIsolatingClassLoaderSub(WekaPackageClassLoaderManager repo,
			File packageDir) throws Exception {
		super(repo, packageDir);
	 }
	
	public boolean integrityCheck() throws Exception {
		return super.integrityCheck();
	}
}
