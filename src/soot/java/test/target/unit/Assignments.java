package soot.java.test.target.unit;

//import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Assignments {

	@Test
	public void IntegerTest() {
		int a;
		int b = 0;
		b = b + 1;
		b++;
		a = b;
		int c = a + b;
		//assertEquals(4, c);
		System.out.println(c);
	}
	
	@Test
	public void StringTest() {
		String a;
		String b = "b";
		a = "a";
		String c = a + b;
		//assertEquals("ab", c);
		System.out.println(c);
	}

}
