package soot.java.target;

public class StringOperations {

	public boolean test() {
		String in;
		in = "";
		in = in + "test";
		in = in.substring(2);
		in = "te" + in;
		in+=in;
		return in.equals("testtest");
	}

}
