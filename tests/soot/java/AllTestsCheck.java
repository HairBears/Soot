package soot.java;

import soot.java.target.Arithmetic;
import soot.java.target.Array;
import soot.java.target.Bools;
import soot.java.target.Condition;
import soot.java.target.Loop;
import soot.java.target.Recursion;
import soot.java.target.StringOperations;
import soot.java.target.Ternary;
import soot.java.target.Unused;
import soot.java.target.Visibility;

public class AllTestsCheck {

	AllTestsCheck() {
		
	}
	/*
	 * 	
	ArithmeticTest.class,
	StringOperationsTest.class,
	BooleanTest.class,
	ConditionTest.class,
	LoopTest.class,
	ArrayTest.class,
	RecursionTest.class
	 */
	public static void main(String[] args) throws InstantiationException, IllegalAccessException  {
		Arithmetic a = new Arithmetic();
		Array b = new Array();
		Bools c = new Bools();
		Condition d = new Condition();
		Loop e = new Loop();
		Recursion f = new Recursion();
		StringOperations g = new StringOperations();
		Visibility h = new Visibility();
		Ternary i = new Ternary();
		Unused j = new Unused();
		
		System.out.println(a.test());
		System.out.println(b.test());
		System.out.println(c.test());
		System.out.println(d.test());
		System.out.println(e.test());
		System.out.println(f.test());
		System.out.println(g.test());
		System.out.println(h.test());
		System.out.println(i.test());
		System.out.println(j.test());
	}


	
}
