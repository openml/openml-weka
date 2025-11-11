package packagemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import weka.core.Environment;
import weka.core.WekaPackageClassLoaderManager;
import weka.core.WekaPackageLibIsolatingClassLoader;
import weka.core.WekaPackageManager;

public class TestIntegeration {

	private static final String PACKAGE_NAME = "OpenmlWeka";
	private static final String CMD_BUILD = "ant -f build_package.xml make_package -Dpackage=" + PACKAGE_NAME;
	private static final String LOC_RESULT = "dist/" + PACKAGE_NAME + ".zip";
	
	@Test
	@Ignore
	public void testBuildAndInstall() throws Exception {
		Environment env = Environment.getSystemWide();
		env.addVariable("WEKA_HOME", "/tmp/wekafiles_unittest_" + UUID.randomUUID().toString());
		
		// builds the package
		Process process = Runtime.getRuntime().exec(CMD_BUILD);
		int returnVal = process.waitFor();
		// String stdOut = IOUtils.toString(p.getInputStream(), "UTF-8");
		String stdErr = IOUtils.toString(process.getErrorStream(), "UTF-8");
		
		if (returnVal != 0) {
			throw new Exception("Build failure. Exit code: Stderr: " + stdErr);
		}
		System.out.println("Package dir: " + WekaPackageManager.PACKAGES_DIR);
		WekaPackageManager.refreshCache(System.out);
		WekaPackageManager.installPackageFromRepository("multisearch", "Latest", System.out);
		WekaPackageManager.installPackageFromArchive(LOC_RESULT, System.out);
		List<weka.core.packageManagement.Package> packages = WekaPackageManager.getInstalledPackages();
		assertEquals(2, packages.size());
		
		File packageDir = new File(WekaPackageManager.PACKAGES_DIR.toString() + "/" + PACKAGE_NAME);
		WekaPackageLibIsolatingClassLoaderSub packageLoader = new WekaPackageLibIsolatingClassLoaderSub(
				WekaPackageClassLoaderManager.getWekaPackageClassLoaderManager(), packageDir);
		assertTrue(packageLoader.integrityCheck());
		packageLoader.close();
		
//		WekaPackageManager.loadPackages(true);
//		for (weka.core.packageManagement.Package wekaPackage : packages) {
//			if (!WekaPackageManager.hasBeenLoaded(wekaPackage)) {
//				throw new Exception("Could not load package: " + wekaPackage.getName());
//			}
//		}
	}
}
