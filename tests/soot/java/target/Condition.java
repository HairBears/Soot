package soot.java.target;

public class Condition {

	public boolean test() {
		if(true) {
			if(false) {
				return false;
			} else if (false) {
				return false;
			} else {
				if (false) {
					return false;
				}
			}
		} else {
			if (false) {
				return false;
			}
			
		}
		
		return true;
	}

}
