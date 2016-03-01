package soot.java;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import soot.java.ArithmeticTest;

@RunWith(Suite.class)
@SuiteClasses({
	ArithmeticTest.class,
	StringOperationsTest.class,
	BooleanTest.class,
	ConditionTest.class,
	LoopTest.class
})
public class AllTests {

}