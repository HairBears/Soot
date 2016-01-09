package soot.java;

import soot.ClassProvider;
import soot.ClassSource;
import soot.SourceLocator;

public class JavaClassProvider implements ClassProvider {
	
	public ClassSource find(String cls) {
		
	
        String path =SourceLocator.v().classPath().get(0);
		
		return new JavaClassSource(cls,path);
	}
}
