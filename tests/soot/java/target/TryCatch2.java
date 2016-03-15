package soot.java.target;

public class TryCatch2 {

	
	public boolean test() {

		boolean out = false;
		
		try {
			out = false;
			throw new Exception();
		} catch (Exception e) {
			out = false;
		} finally {
			out = true;
		}
		
		return out;
		
	}
	
}