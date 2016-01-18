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
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;

import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.ClassSource;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.LongType;
import soot.Modifier;
import soot.RefType;
import soot.ShortType;
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
		
		
		/*
		List<Type> parameterTypes=new ArrayList<>();
		List<Type> parameterTypesInt=new ArrayList<>();
		parameterTypesInt.add(IntType.v());
		List<SootClass> exceptions=new ArrayList<>();
		exceptions.add(SootResolver.v().makeClassRef("java.lang.NullPointerException"));
		*/
		String text="";
		try {
			Scanner scanner = new Scanner( new File(path) );
			text = scanner.useDelimiter("\\A").next();
			scanner.close();
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
		
		
		com.sun.tools.javac.util.List<JCTree> list = ((JCTree.JCClassDecl) classDecl).defs;
		
		
		while (list.head!=null) {
			method=(JCTree.JCMethodDecl)list.head;
			List<Type> parameterTypes=new ArrayList<>();
			com.sun.tools.javac.util.List<JCVariableDecl> paramlist=method.params;
			while (paramlist.head!=null) {
				Type type=getType(paramlist.head.vartype);
				if (type!=null)
					parameterTypes.add(type);
				paramlist=paramlist.tail;
			}
			
			sc.addMethod(new SootMethod(method.name.toString(), parameterTypes, getType((JCPrimitiveTypeTree)method.restype), getModifiers((JCModifiers)method.mods)));
			sc.getMethodByName(method.name.toString()).setSource(new JavaMethodSource(method));	
			list=list.tail;
		}
	
		/*sc.addMethod(new SootMethod("r2",parameterTypes,IntType.v(),Modifier.STATIC,exceptions));
		sc.getMethodByName("r2").setSource(new JavaMethodSource());
		sc.addMethod(new SootMethod("ret",parameterTypesInt,IntType.v()));
		sc.getMethodByName("ret").setSource(new JavaMethodSource());
		sc.addMethod(new SootMethod("main", parameterTypes , VoidType.v(),Modifier.STATIC));
		sc.getMethodByName("main").setSource(new JavaMethodSource());
		*/
		Dependencies deps=new Dependencies();
		deps.typesToSignature.add(RefType.v("java.lang.System"));
		deps.typesToSignature.add(RefType.v("java.io.PrintStream"));
		deps.typesToSignature.add(RefType.v("java.lang.Boolean"));
		deps.typesToSignature.add(RefType.v("java.lang.String"));
		return deps;
	}
	
	private Type getType (JCTree node) {
		if (node instanceof JCPrimitiveTypeTree) {
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("INT"))
			return IntType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("CHAR"))
			return CharType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("BOOLEAN"))
			return BooleanType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("BYTE"))
			return ByteType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("DOUBLE"))
			return DoubleType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("FLOAT"))
			return FloatType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("LONG"))
			return LongType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("SHORT"))
			return ShortType.v();
		if (((JCPrimitiveTypeTree)node).typetag.name().equals("VOID"))
			return VoidType.v();
		}
		return null;	//TODO
	}
	
	private int getModifiers(JCModifiers mods) {
		int modsum=0;
		String modString=mods.toString();
		
		if (modString.contains("abstract"))
			modsum|=Modifier.ABSTRACT;
		if (modString.contains("final"))
			modsum|=Modifier.FINAL;
		if (modString.contains("interface"))
			modsum|=Modifier.INTERFACE;
		if (modString.contains("native"))
			modsum|=Modifier.NATIVE;
		if (modString.contains("private"))
			modsum|=Modifier.PRIVATE;
		if (modString.contains("protected"))
			modsum|=Modifier.PROTECTED;
		if (modString.contains("public"))
			modsum|=Modifier.PUBLIC;
		if (modString.contains("static"))
			modsum|=Modifier.STATIC;
		if (modString.contains("synchronized"))
			modsum|=Modifier.SYNCHRONIZED;
		if (modString.contains("transient"))
			modsum|=Modifier.TRANSIENT;
		if (modString.contains("volatile"))
			modsum|=Modifier.VOLATILE;
		if (modString.contains("strictfp"))
			modsum|=Modifier.STRICTFP;
		if (modString.contains("annotation"))
			modsum|=Modifier.ANNOTATION;
		if (modString.contains("enum"))
			modsum|=Modifier.ENUM;
		
		return modsum;
	}

}
