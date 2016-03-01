package soot.java.target;

public class Loop {
	
	public boolean Test() {
		for (int i = 0; i < 10; i++) {
			if(i == 9) {
				break;
			}
		}
		
		int a = 0;
		while (a < 12) {
			a++;
		}
		
		do {
			a = a * 2;
		} while (a < 100);
		
		if (a > 150) {
			return true;
		}
		return false;
		
	}

}
