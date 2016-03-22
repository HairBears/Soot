package soot.java;

import java.io.File;

import soot.ClassProvider;
import soot.ClassSource;
import soot.SourceLocator;

/**
 * Provides the class source for a Soot class
 * @author Martin Herbers
 * @author Florian Krause
 * @author Joachim Katholing
 *
 */
public class JavaClassProvider implements ClassProvider {
	
	public ClassSource find(String cls) {
		String clsFile = cls.replace('.', '/') + ".java";
		SourceLocator.FoundFile file = SourceLocator.v().lookupInClassPath(clsFile);
		if (file != null)
			return new JavaClassSource(cls, file.inputFile());
		else if (SourceLocator.v().classPath().get(0).contains(clsFile))
			return new JavaClassSource(cls, new File(SourceLocator.v().classPath().get(0)));		//For single file as input
		else
			return null;

	}
}
