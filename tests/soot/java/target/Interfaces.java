package soot.java.target;

import soot.java.target.Nested.Inter;

class Nested {
	
	public interface Inter {
		public boolean test();
	}
	
}

public class Interfaces implements Inter {

	@Override
	public boolean test() {
		return true;
	}
	
}