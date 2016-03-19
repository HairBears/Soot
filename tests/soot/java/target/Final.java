package soot.java.target;

public class Final {
	
	public boolean test() {

		final String a = "asd";
		String b = "test";
		return (a+b).equals("asdtest");
		
	}

}
