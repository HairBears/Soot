package soot.java.target;

public class InstanceOf {
	
	public boolean test() {
		String a = "";
		
		if (a instanceof String) {
			return true;
		} else {
			return false;
		}
	}

}
