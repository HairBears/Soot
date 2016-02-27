package soot.java.target;

public class StringOperations {

	public boolean Test() {
		String in = "";
		in = in + "test";
		in = in.substring(2);
		in = "te" + in;
		in+=in;
		System.out.println(in);
		return in.equals("testtest");
	}

}
