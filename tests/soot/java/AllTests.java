package soot.java;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import soot.java.ArithmeticTest;

@RunWith(Suite.class)
@SuiteClasses({
	ArithmeticTest.class,
	StringOperationsTest.class,
	BoolsTest.class,
	ConditionTest.class,
	LoopTest.class,
	ArrayTest.class,
	RecursionTest.class,
	VisibilityTest.class,
	UnusedTest.class,
	TernaryTest.class,
	StackOverflowErrorTest.class,
	InterfacesTest.class,
	SwitchStuffTest.class
//	,Test14.class
//	,Test15.class
//	,Test16.class
//	,Test17.class
//	,Test18.class
//	,Test19.class
//	,Test20.class
//	,Test21.class
//	,Test22.class
})
public class AllTests {

}