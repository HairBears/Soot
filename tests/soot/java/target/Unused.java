package soot.java.target;

public class Unused {
	
	int a = 1;
	String b = "nothing";
	boolean c = true;
	
	private int d = 42;
	private String e = "lalala";
	private boolean f = false;
	
	public boolean test() {
		boolean out = (1 == 1);
		return out;
	}
	
	private String answerToTheUltimateQuestionOfLifeTheUniverseAndEverything() {
		return "42!";
	}

}
