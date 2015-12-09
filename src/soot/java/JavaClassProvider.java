package soot.java;

import soot.ClassProvider;
import soot.ClassSource;

public class JavaClassProvider implements ClassProvider {
	
	// test whether the commits are working
	public ClassSource find(String cls) {
		
		return new JavaClassSource(cls);
	}
}
