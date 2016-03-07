package soot.java.target;

public class Bools {

	public boolean test() {
		boolean a = true;
		boolean b = !a;
		boolean c = a & b;
		boolean d = a | b;
		boolean e = a ^ b;
		boolean f = a && d && e;
		boolean g = a || b || c;
		return f == g;
	}

}
