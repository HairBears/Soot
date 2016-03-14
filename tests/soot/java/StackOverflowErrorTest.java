package soot.java;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

public class StackOverflowErrorTest extends AbstractTest {
	
	@Rule public ExpectedException thrown = ExpectedException.none();
	
	@Before
	public void prepareException() {
		thrown.expect(java.lang.reflect.InvocationTargetException.class);
	}
	
}
