package soot.java.target;

public class StackOverflowError {
	
	public boolean test() {
		test();
		return true;
	}

}
