package soot.java.target;

public class SwitchStuff {
	
	public boolean test() {
		int a = switchStuff(0);
		int b = switchStuff(1);
		int c = switchStuff(2);
		int d = switchStuff(3);
		int e = switchStuff(4);
		return (a + b + c + d + e) == 53;
		
	}
	
	public int switchStuff(int in) {
		
		int out = 0;
		switch(in) {
		case 0: out = -10; break;
		case 1: out = 23; break;
		case 3: out = 42; break;
		default: out = -1;
		}
		
		return out;
		
	}

}
