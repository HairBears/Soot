package soot.java.target;

public class This {
	
	int i = 0;
	
	public boolean test() {
		this.i = 5;
		i = 3;
		return ((this.i == 3) && (i == 3));
	}
	

}
