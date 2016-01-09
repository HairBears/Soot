package soot.java;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;

import soot.ClassSource;
import soot.IntType;
import soot.Modifier;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.SootResolver;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;

public class JavaClassSource extends ClassSource {
	
	String path;

	public JavaClassSource(String className, String path) {
		super(className);
		this.path=path;
	}

	@Override
	public Dependencies resolve(SootClass sc) {
		
		
		
		List<Type> parameterTypes=new ArrayList<>();
	//	List<Type> parameterTypesInt=new ArrayList<>();
	//	parameterTypesInt.add(IntType.v());
		List<SootClass> exceptions=new ArrayList<>();
		exceptions.add(SootResolver.v().makeClassRef("java.lang.NullPointerException"));
		
		String text="";
		try {
			text = new Scanner( new File(path) ).useDelimiter("\\A").next();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		Context context=new Context();
		JavacFileManager jfm= new JavacFileManager(context,true,null);
		ParserFactory parserFactory=ParserFactory.instance(context);
		JavacParser parser=parserFactory.newParser(text, false,false,false);
		JCCompilationUnit jccu=parser.parseCompilationUnit();
		jfm.close();
		JCTree classDecl=jccu.defs.head;
		JCTree.JCMethodDecl method;
		if (classDecl instanceof JCTree.JCClassDecl) {
			method=(JCTree.JCMethodDecl)((JCTree.JCClassDecl) classDecl).defs.head;
		}
		else
			method=null;
	
		
		
		sc.addMethod(new SootMethod(method.name.toString(), parameterTypes, VoidType.v(), Modifier.STATIC));
		sc.getMethodByName(method.name.toString()).setSource(new JavaMethodSource(method.body));
	
		/*sc.addMethod(new SootMethod("r2",parameterTypes,IntType.v(),Modifier.STATIC,exceptions));
		sc.getMethodByName("r2").setSource(new JavaMethodSource());
		sc.addMethod(new SootMethod("ret",parameterTypesInt,IntType.v()));
		sc.getMethodByName("ret").setSource(new JavaMethodSource());
		sc.addMethod(new SootMethod("main", parameterTypes , VoidType.v(),Modifier.STATIC));
		sc.getMethodByName("main").setSource(new JavaMethodSource());
		*/
		Dependencies deps=new Dependencies();
		deps.typesToSignature.add(RefType.v("java.io.PrintStream"));
		return deps;
	}

}
