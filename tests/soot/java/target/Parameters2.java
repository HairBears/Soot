package soot.java.target;

public class Parameters2 {
	
	public boolean test() {
		return generateOutput(4, 8, 15, 16, 23 ,42);
	}
	
	private boolean generateOutput(int...in) {
		int out = 0;
		for (int i : in) {
			out += i;
		}
		
		return out == 108;
	}

}
