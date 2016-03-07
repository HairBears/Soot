package soot.java.target;

public class Recursion {
	
	public boolean test() {
		int val = calc(100);
		return (val == 1);
	}
	
	public int calc(int in) {
		if (in == 1) {
			return in;
		} else {
			return calc(in - 1);
		}
	}

}
