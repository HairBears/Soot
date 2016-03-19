package soot.java.target;

public class Parameters {
	
	public boolean test() {
		String st = "asd";
		int i = 42; 
		short sh = 5;
		int[] intarr = {1,3,5,7,9};
		boolean b = false;
		return generateOutput(st, i, sh, intarr, b);
	}
	
	private boolean generateOutput(String st, int i, short sh, int[] intarr, boolean b) {
		if (!st.equals("asd")) {
			return false;
		} else if (i != 42) {
			return false;
		} else if (sh != 5) {
			return false;
		} else if (intarr[0] + intarr[2] != 6) {
			return false;
		} else if (b) {
			return false;
		} else {
			return true;
		}
	}

}
