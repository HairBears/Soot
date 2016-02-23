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
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;

import soot.ArrayType;
import soot.ClassSource;
import soot.IntType;
import soot.Modifier;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;

public class JavaClassSource extends ClassSource {
	
	//Path to java class file
	File path;
	

	public JavaClassSource(String className, File path) {
		super(className);
		this.path = path;
	}
	
	/*
	 * (non-Javadoc)
	 * @see soot.ClassSource#resolve(soot.SootClass)
	 */
	@Override
	public Dependencies resolve(SootClass sc) {
		//Scanning the file and creating an abstract syntax tree
		String text = "";
		try {
			Scanner scanner = new Scanner( path );
			text = scanner.useDelimiter("\\A").next();
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		Context context = new Context();
		JavacFileManager jfm = new JavacFileManager(context, true, null);
		ParserFactory parserFactory = ParserFactory.instance(context);
		JavacParser parser=parserFactory.newParser(text, false, false, false);
		JCCompilationUnit jccu = parser.parseCompilationUnit();
		jfm.close();
		Dependencies deps = new Dependencies();
		com.sun.tools.javac.util.List<JCTree> classDecl = jccu.defs;
		//Add all imports as dependencies
		while (classDecl.head instanceof JCImport) {
			deps.typesToSignature.add(RefType.v(((JCImport)classDecl.head).qualid.toString()));
			classDecl=classDecl.tail;
		}
		deps.typesToSignature.add(RefType.v("java.lang.Object"));			//TODO suche in methoden nach noetigen imports
		deps.typesToSignature.add(RefType.v("java.lang.Throwable"));
		deps.typesToSignature.add(RefType.v("java.lang.System"));
		deps.typesToSignature.add(RefType.v("java.io.PrintStream"));
		deps.typesToSignature.add(RefType.v("java.lang.Boolean"));
		deps.typesToSignature.add(RefType.v("java.lang.String"));
		deps.typesToSignature.add(RefType.v("java.lang.StringBuilder"));
		deps.typesToSignature.add(RefType.v("java.io.Serializable"));
		deps.typesToSignature.add(RefType.v("java.lang.AssertionError"));
		deps.typesToSignature.add(RefType.v("java.lang.Enum"));
		deps.typesToSignature.add(RefType.v("java.lang.Class"));
		JCClassDecl classsig=(JCClassDecl) classDecl.head;
		if (classsig.extending!=null)
			sc.setSuperclass(Scene.v().getSootClass(JavaUtil.getPackage((JCIdent)classsig.extending, deps, sc)));
		else
			sc.setSuperclass(Scene.v().getSootClass("java.lang.Object"));
		if (classsig.implementing.head!=null) {
			com.sun.tools.javac.util.List<JCExpression> interfacelist = classsig.implementing;
			while (interfacelist.head!=null) {
				sc.addInterface(Scene.v().getSootClass(JavaUtil.getPackage((JCIdent)interfacelist.head, deps, sc)));
				interfacelist=interfacelist.tail;
			}
		}
		sc.setModifiers(getModifiers(classsig.mods));
		com.sun.tools.javac.util.List<JCTree> list = ((JCClassDecl) classDecl.head).defs;
		ArrayList<JCTree> fieldlist = new ArrayList<JCTree>();
		//Add all methods in this class
		while (list.head != null) {
			getHead(list.head, deps, sc, fieldlist);
			
			list = list.tail;
		}
		List<Type> parameterTypes = new ArrayList<>();
		if (!sc.declaresMethod("<init>", parameterTypes)) {
			String methodname="<init>";
			
			Type returntype = VoidType.v();
			sc.addMethod(new SootMethod(methodname, parameterTypes, returntype, Modifier.PUBLIC));
			sc.getMethodByName(methodname).setSource(new JavaMethodSource(null, deps, fieldlist));
		}
		return deps;
	}
	
	
	private void getHead(JCTree node, Dependencies deps, SootClass sc, ArrayList<JCTree> fieldlist) {
		if (node instanceof JCMethodDecl) {
			JCMethodDecl method = (JCMethodDecl)node;
			com.sun.tools.javac.util.List<JCExpression> throwlist = method.thrown;
			ArrayList<SootClass> throwlistmethod=new ArrayList<>();
			while (throwlist.head!=null) {
				String packagename=JavaUtil.getPackage((JCIdent)throwlist.head, deps, sc);
				throwlistmethod.add(Scene.v().getSootClass(packagename));
				throwlist=throwlist.tail;
			}
			List<Type> parameterTypes = new ArrayList<>();
			com.sun.tools.javac.util.List<JCVariableDecl> paramlist = method.params;
			while (paramlist.head != null) {
				Type type = JavaUtil.getType(paramlist.head.vartype, deps, sc);
				if (type != null)
					parameterTypes.add(type);
				paramlist = paramlist.tail;
			}
			String methodname = method.name.toString();
			Type returntype;
			if (methodname.equals("<init>"))
				returntype = VoidType.v();
			else
				returntype = JavaUtil.getType(method.restype, deps, sc);
			int modifier = getModifiers((JCModifiers)method.mods);
			if (sc.isInterface())
				modifier |= Modifier.ABSTRACT;
			sc.addMethod(new SootMethod(methodname, parameterTypes, returntype, modifier, throwlistmethod));
			sc.getMethod(methodname, parameterTypes, returntype).setSource(new JavaMethodSource(method, deps, fieldlist));
		}
		if (node instanceof JCVariableDecl) {
			String fieldname = ((JCVariableDecl) node).getName().toString();
			Type fieldtype = JavaUtil.getType(((JCVariableDecl) node).vartype, deps, sc);
			int fieldmods = getModifiers(((JCVariableDecl) node).getModifiers());
			SootField field = new SootField(fieldname, fieldtype, fieldmods);
			sc.addField(field);
			if (((JCVariableDecl)node).init != null) {
				fieldlist.add(node);
			}
		}
		if (node instanceof JCClassDecl) {
			SootClass innerClass=new SootClass(sc.getName()+"$"+((JCClassDecl)node).name.toString());
			Scene.v().addClass(innerClass);
			Scene.v().getApplicationClasses().add(innerClass);
			innerClass.setOuterClass(sc);
			int mods=getModifiers(((JCClassDecl) node).mods);
			if (node.toString().substring(0, node.toString().indexOf('{')).contains("enum")) {
				mods |= Modifier.ENUM | Modifier.FINAL;
			}
			innerClass.setModifiers(mods);
			if (((JCClassDecl)node).extending!=null)
				innerClass.setSuperclass(Scene.v().getSootClass(JavaUtil.getPackage((JCIdent)((JCClassDecl)node).extending, deps, sc)));
			else if (Modifier.isEnum(innerClass.getModifiers()))
				innerClass.setSuperclass(Scene.v().getSootClass("java.lang.Enum"));
			else
				innerClass.setSuperclass(Scene.v().getSootClass("java.lang.Object"));
			if (((JCClassDecl)node).implementing.head!=null) {
				com.sun.tools.javac.util.List<JCExpression> interfacelist = ((JCClassDecl)node).implementing;
				while (interfacelist.head!=null) {
					innerClass.addInterface(Scene.v().getSootClass(JavaUtil.getPackage((JCIdent)interfacelist.head, deps, sc)));
					interfacelist=interfacelist.tail;
				}
			}
			
			if (!Modifier.isEnum(innerClass.getModifiers())) {
				String fieldname="this$0";
				Type fieldtype=RefType.v(sc);
				int fieldmods=Modifier.FINAL;
				SootField field=new SootField(fieldname, fieldtype, fieldmods);
				innerClass.addField(field);
			} else {
				String fieldname="ENUM$VALUES";
				Type fieldtype=ArrayType.v(RefType.v(innerClass), 1);
				int fieldmods=Modifier.FINAL | Modifier.STATIC | Modifier.PRIVATE;
				SootField field=new SootField(fieldname, fieldtype, fieldmods);
				innerClass.addField(field);
			}
			
			
			com.sun.tools.javac.util.List<JCTree> list = ((JCClassDecl) node).defs;
			ArrayList<JCTree> newfieldlist = new ArrayList<JCTree>();
			//Add all methods in this class
			while (list.head != null) {
				getHead(list.head, deps, innerClass, newfieldlist);
				
				list = list.tail;
			}
			List<Type> parameterTypes = new ArrayList<>();
			if (Modifier.isEnum(innerClass.getModifiers())) {
				parameterTypes.add(RefType.v("java.lang.String"));
				parameterTypes.add(IntType.v());
			}
			else
				parameterTypes.add(RefType.v(sc));
			if (!innerClass.declaresMethod("<init>", parameterTypes)) {
				String methodname="<init>";
				
				Type returntype = VoidType.v();
				innerClass.addMethod(new SootMethod(methodname, parameterTypes, returntype, Modifier.PUBLIC));
				innerClass.getMethodByName(methodname).setSource(new JavaMethodSource(null, deps, newfieldlist));
			}
			if (Modifier.isEnum(innerClass.getModifiers())) {
				List<Type> paraList=new ArrayList<>();
				
				innerClass.addMethod(new SootMethod("<clinit>", paraList, VoidType.v(), Modifier.STATIC));
				innerClass.getMethodByName("<clinit>").setSource(new JavaMethodSource(null, deps, newfieldlist));
				
				innerClass.addMethod(new SootMethod("values", paraList, ArrayType.v(RefType.v(innerClass), 1), Modifier.PUBLIC|Modifier.STATIC));
				innerClass.getMethodByName("values").setSource(new JavaMethodSource(null, deps, newfieldlist));
				
				List<Type> paraList2=new ArrayList<>();
				paraList2.add(RefType.v("java.lang.String"));
				innerClass.addMethod(new SootMethod("valueOf", paraList2, RefType.v(innerClass), Modifier.PUBLIC|Modifier.STATIC));
				innerClass.getMethodByName("valueOf").setSource(new JavaMethodSource(null, deps, newfieldlist));
			}
			
		}
	}
	
	
	/**
	 * Combines the modifiers of the method into one 
	 * @param mods	AST-node containing all modifiers
	 * @return		combined modifier as an integer
	 */
	private int getModifiers(JCModifiers mods) {
		int modsum = 0;
		String modString = mods.toString();
		
		if (modString.contains("abstract"))
			modsum |= Modifier.ABSTRACT;
		if (modString.contains("final"))
			modsum |= Modifier.FINAL;
		if (modString.contains("interface"))
			modsum |= Modifier.INTERFACE;
		if (modString.contains("native"))
			modsum |= Modifier.NATIVE;
		if (modString.contains("private"))
			modsum |= Modifier.PRIVATE;
		if (modString.contains("protected"))
			modsum |= Modifier.PROTECTED;
		if (modString.contains("public"))
			modsum |= Modifier.PUBLIC;
		if (modString.contains("static"))
			modsum |= Modifier.STATIC;
		if (modString.contains("synchronized"))
			modsum |= Modifier.SYNCHRONIZED;
		if (modString.contains("transient"))
			modsum |= Modifier.TRANSIENT;
		if (modString.contains("volatile"))
			modsum |= Modifier.VOLATILE;
		if (modString.contains("strictfp"))
			modsum |= Modifier.STRICTFP;
		if (modString.contains("annotation"))
			modsum |= Modifier.ANNOTATION;
		if (modString.contains("enum"))
			modsum |= Modifier.ENUM;
		
		return modsum;
	}

}
