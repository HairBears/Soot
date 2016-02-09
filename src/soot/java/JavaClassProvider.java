package soot.java;

import soot.ClassProvider;
import soot.ClassSource;
import soot.SourceLocator;

public class JavaClassProvider implements ClassProvider {
	
	public ClassSource find(String cls) {
		
		String clsFile = cls.replace('.', '/') + ".java";
		SourceLocator.FoundFile file = SourceLocator.v().lookupInClassPath(clsFile);
		if (file != null)
			return new JavaClassSource(cls, file.inputFile());
		else
			return null;

	}
}
