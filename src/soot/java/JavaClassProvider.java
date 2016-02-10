package soot.java;

import java.io.File;

import soot.ClassProvider;
import soot.ClassSource;
import soot.Scene;
import soot.SourceLocator;

public class JavaClassProvider implements ClassProvider {
	
	public ClassSource find(String cls) {
		
		String clsFile = cls.replace('.', '/') + ".java";
		SourceLocator.FoundFile file = SourceLocator.v().lookupInClassPath(clsFile);
		if (file != null)
			return new JavaClassSource(cls, file.inputFile());
		else if (SourceLocator.v().classPath().get(0).contains(clsFile))
			return new JavaClassSource(cls,new File(SourceLocator.v().classPath().get(0)));
		else
			return null;

	}
}
