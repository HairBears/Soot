package soot.java.target;

public class Visibility {
	
	public int a = 0;
	protected int b = 1;
	int c = 2;
	private int d = 3;
	
	public boolean test() {
		return protectedTest();
	}
	protected boolean protectedTest() {
		return unmodifiedTest();
	}
	
	boolean unmodifiedTest() {
		return privateTest();
	}
	
	private boolean privateTest() {
		boolean out = ((a + b + c + d) == 6);
		return out;
	}
	
	

}
