package soot.java;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import soot.java.ArithmeticTest;

@RunWith(Suite.class)
@SuiteClasses({
	ArithmeticTest.class
	,StringOperationsTest.class
	,BoolsTest.class
	,ConditionTest.class
	,LoopTest.class
	,ArrayTest.class
	,RecursionTest.class
	,VisibilityTest.class
	,UnusedTest.class
	,TernaryTest.class
	,StackOverflowErrorTest.class
	,InterfacesTest.class
	,SwitchStuffTest.class
	,TryCatch1Test.class
	,SwitchFailTest.class
	,TryCatch2Test.class
	,InstanceOfTest.class
//	,Test18Test.class
//	,Test19Test.class
//	,Test20Test.class
//	,Test21Test.class
//	,Test22Test.class
})
public class AllTests {

}