package soot.java.target;

public class Constructor {
	
	boolean b;
	
	public Constructor() {
		b = false;
	}
	
	public Constructor(boolean in) {
		b = in;
	}
	
	public boolean generateOutput() {
		return this.b;
	}

	public boolean test() {
		Constructor c1 = new Constructor();
		Constructor c2 = new Constructor(true);
		Constructor c3 = new Constructor(false);
		return (!c1.generateOutput() && c2.generateOutput() && !c3.generateOutput());
	}

}
