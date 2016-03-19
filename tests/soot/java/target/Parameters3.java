package soot.java.target;

public class Parameters3 {
	
	public boolean test() {
		return generateOutput("a", 23, "b", 42, "c", 0, 8, 15);
	}
	
	private boolean generateOutput(Object...in) {
		String out = "";
		for (Object o : in) {
			if (o instanceof String) {
				out = out + o;
			}
		}
		
		return out.equals("abc");
	}

}
