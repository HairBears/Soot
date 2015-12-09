package soot.java;

import java.util.ArrayList;
import java.util.List;

import soot.ClassSource;
import soot.IntType;
import soot.Modifier;
import soot.RefType;
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
		
		sc.addMethod(new SootMethod("main", parameterTypes , VoidType.v(),Modifier.STATIC));
		sc.getMethodByName("main").setSource(new JavaMethodSource());
		parameterTypes.add(IntType.v());
		sc.addMethod(new SootMethod("ret",parameterTypes,IntType.v()));
		sc.getMethodByName("ret").setSource(new JavaMethodSource());
		Dependencies deps=new Dependencies();
		deps.typesToSignature.add(RefType.v("java.io.PrintStream"));
		return deps;
	}

}
