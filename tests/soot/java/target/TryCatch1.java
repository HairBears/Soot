package soot.java.target;

public class TryCatch1 {

	
	public boolean test() {

		boolean out = false;
		
		try {
			out = true;
		} catch (Exception e) {
			out = false;
		} finally {
			return out;
		}
		
	}
	
}