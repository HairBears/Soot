package soot.java;

import java.util.ArrayList;
import java.util.List;

import soot.ClassSource;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;

public class JavaClassSource extends ClassSource {

	public JavaClassSource(String className) {
		super(className);
	}

	@Override
	public Dependencies resolve(SootClass sc) {
		
		List<Type> parameterTypes=new ArrayList<>();
		parameterTypes.add(VoidType.v());
		
		sc.addMethod(new SootMethod("main",parameterTypes , VoidType.v()));
		sc.getMethodByName("main").setSource(new JavaMethodSource());
		Dependencies deps=new Dependencies();
		return deps;
	}

}
