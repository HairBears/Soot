package soot.java.test.target.unit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Loops {

	@Test
	public void test() {
		
		int add = 0;
		int mult = 1;
		
		for (int i = 1; i < 10; i++){
			add = add + i;
			mult = mult * i;
		}
		
		assertEquals(45, add);
		assertEquals(362880, mult);
		
		while(add > 0) {
			add--;
		}
		assertEquals(0, add);
	}

}
