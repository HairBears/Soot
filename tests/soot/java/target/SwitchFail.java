package soot.java.target;

public class SwitchFail {
	
	public boolean test() {
		int a = switchStuff(0);
		int b = switchStuff(1);
		int c = switchStuff(2);
		int d = switchStuff(3);
		int e = switchStuff(4);
		return (a + b + c + d + e) == 5321;
		
	}
	
	public int switchStuff(int in) {
		
		int out = 0;
		switch(in) {
		case 0: out = out + 1;
		case 1: out = out + 10;
		case 3: out = out + 100;
		default: out = out + 1000;
		}
		
		return out;
		
	}

}
