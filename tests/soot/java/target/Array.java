package soot.java.target;

public class Array {
	
	public boolean test() {
		int[] numbers = new int[10];
		for (int i = 0; i < 10; i++) {
			numbers[i] = i;
		}
		int[] numbers2 = {9,8,7,6,5,4,3,2,1,0};
		int[][] numbers2dim = {numbers, numbers2};
		boolean out = ((numbers2dim[1][1] + numbers2dim[0][1]) == 9);
		return out;
	}

}
