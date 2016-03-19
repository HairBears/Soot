package soot.java.target;

	
	interface Inter {
		public boolean test();
	}
	


public class Interfaces implements Inter {

	@Override
	public boolean test() {
		return true;
	}
	
}