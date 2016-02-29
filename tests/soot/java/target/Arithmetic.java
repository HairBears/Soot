package soot.java.target;

public class Arithmetic {
	
	public boolean Test() {
		int a = 0;
		int b = 0;
		b = b + 1;
		b++;
		a = b;
		int c = a + b;
		c--;
		c-=1;
		c+=2;
		return c==4;
	}

}
