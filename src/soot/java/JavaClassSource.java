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

import soot.ClassSource;
import soot.Modifier;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
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
		while (classDecl.head instanceof JCImport) {						//Add all imports as dependencies
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
		JCClassDecl classSig=(JCClassDecl) classDecl.head;
		if (classSig.extending!=null){
			String packageName=JavaUtil.getPackage((JCIdent)classSig.extending, deps, sc);
			SootClass superClass=Scene.v().getSootClass(packageName);
			sc.setSuperclass(superClass);
		}
		else {
			SootClass superClass=Scene.v().getSootClass("java.lang.Object");
			sc.setSuperclass(superClass);
		}
		if (classSig.implementing.head!=null) {
			com.sun.tools.javac.util.List<JCExpression> interfaceList = classSig.implementing;
			while (interfaceList.head!=null) {
				String packageName=JavaUtil.getPackage((JCIdent)interfaceList.head, deps, sc);
				SootClass interfaceClass=Scene.v().getSootClass(packageName);
				sc.addInterface(interfaceClass);
				interfaceList=interfaceList.tail;
			}
		}
		int modifier=JavaUtil.getModifiers(classSig.mods);
		sc.setModifiers(modifier);
		com.sun.tools.javac.util.List<JCTree> classBodyList = ((JCClassDecl) classDecl.head).defs;
		ArrayList<JCTree> fieldList = new ArrayList<JCTree>();
		while (classBodyList.head != null) {								//Add all methods in this class
			JavaUtil.getHead(classBodyList.head, deps, sc, fieldList);
			
			classBodyList = classBodyList.tail;
		}
		List<Type> parameterTypes = new ArrayList<>();
		if (!sc.declaresMethod("<init>", parameterTypes)) {
			String methodName="<init>";
			Type returnType = VoidType.v();
			sc.addMethod(new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC));
			sc.getMethodByName(methodName).setSource(new JavaMethodSource(null, deps, fieldList));
		}
		return deps;
	}
	

}
