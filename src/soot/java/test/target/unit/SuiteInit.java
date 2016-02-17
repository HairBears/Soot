package soot.java.test.target.unit;

import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class SuiteInit {

	public static void main() {
		System.out.println("Setting up tests");
		// TODO run junit testsuite programatically
		Computer computer = new Computer();
		JUnitCore jUnitCore = new JUnitCore();
		// TODO how to handle the result
		Result r = jUnitCore.run(computer, AllTests.class);
		System.out.println(r.getFailureCount());
	}
	
}
