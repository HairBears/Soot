package soot.java;

import java.io.File;
import java.nio.file.Paths;

import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import soot.G;
import soot.Main;


public class UnitTests {
	
	public static void main(String[] args) {
			String rtJar = System.getProperty("java.home")+File.separator+"lib"+File.separator+"rt.jar";
			G.reset();
			Main.main(new String[] {
				"-cp", rtJar,
				"-pp",
				"-debug-resolver",
				"-f", "class",
				"-validate",
				"-process-dir", getPath()
			});
			
			Computer computer = new Computer();
			JUnitCore jUnitCore = new JUnitCore();
			Result r = jUnitCore.run(computer, AllTests.class);
			System.out.println("Number of failed tests:");
			System.out.println(r.getFailureCount());
	}
	
	protected static String getPath() {
		String path = Paths.get(".").toAbsolutePath().normalize().toString();
		path = path + "/tests/soot/java/target/";
		return path;
	}
	
}