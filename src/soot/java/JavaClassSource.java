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
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;

import soot.ClassSource;
import soot.Modifier;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;
import soot.jimple.Jimple;

public class JavaClassSource extends ClassSource {
	
	//Path to java class file
	String path;
	ArrayList<JCTree> fieldlist;

	public JavaClassSource(String className, String path) {
		super(className);
		this.path=path;
		fieldlist=new ArrayList<JCTree>();
	}

	@Override
	public Dependencies resolve(SootClass sc) {
		
		//Scanning the file and creating an abstract syntax tree
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
		
		Dependencies deps=new Dependencies();
		
		com.sun.tools.javac.util.List<JCTree> classDecl = jccu.defs;
		
		//Add all imports as dependencies
		while (classDecl.head instanceof JCImport) {
			deps.typesToSignature.add(RefType.v(((JCImport)classDecl.head).qualid.toString()));
			classDecl=classDecl.tail;
		}
		deps.typesToSignature.add(RefType.v("java.lang.Object"));			//TODO suche in methoden nach noetigen imports
		deps.typesToSignature.add(RefType.v("java.lang.Serializable"));
		deps.typesToSignature.add(RefType.v("java.lang.Throwable"));
		deps.typesToSignature.add(RefType.v("java.lang.System"));
		deps.typesToSignature.add(RefType.v("java.io.PrintStream"));
		deps.typesToSignature.add(RefType.v("java.lang.Boolean"));
		deps.typesToSignature.add(RefType.v("java.lang.String"));
		
		

		com.sun.tools.javac.util.List<JCTree> list = ((JCTree.JCClassDecl) classDecl.head).defs;
		
		//Add all methods in this class
		while (list.head!=null) {
			getHead(list.head,deps,sc);
			
			list=list.tail;
		}
	
		return deps;
	}
	
	
	private void getHead(JCTree node, Dependencies deps, SootClass sc) {
		if (node instanceof JCMethodDecl) {
	//		if (node.)
			JCMethodDecl method=(JCMethodDecl)node;
			List<Type> parameterTypes=new ArrayList<>();
			com.sun.tools.javac.util.List<JCVariableDecl> paramlist=method.params;
			while (paramlist.head!=null) {
				Type type=JavaUtil.getType(paramlist.head.vartype,deps);
				if (type!=null)
					parameterTypes.add(type);
				paramlist=paramlist.tail;
			}
			String methodname=method.name.toString();
			Type returntype;
			if (methodname.equals("<init>"))
				returntype=VoidType.v();
			else
				returntype=JavaUtil.getType(method.restype,deps);
			int modifier=getModifiers((JCModifiers)method.mods);
			sc.addMethod(new SootMethod(methodname, parameterTypes, returntype,modifier));
			sc.getMethodByName(methodname).setSource(new JavaMethodSource(method, deps,fieldlist));	
		}
		if (node instanceof JCVariableDecl) {
			String fieldname=((JCVariableDecl) node).getName().toString();
			Type fieldtype=JavaUtil.getType(((JCVariableDecl) node).vartype, deps);
			int fieldmods=getModifiers(((JCVariableDecl) node).getModifiers());
			SootField field=new SootField(fieldname,fieldtype,fieldmods);
			sc.addField(field);
			if (((JCVariableDecl)node).init!=null) {
				fieldlist.add(node);
			}
		}
	}
	
	
	/**
	 * Get all modifiers used for the methods
	 * @param mods	node containing all modifiers
	 * @return		modifiers combined in integer
	 */
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
