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
	,FinalTest.class
	,ParametersTest.class
	,Parameters2Test.class
	,Parameters3Test.class
	,ThisTest.class
	,StaticTest.class
	,ConstructorTest.class
})
public class AllTests {

}