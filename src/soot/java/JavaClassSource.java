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
import soot.SootField;
import soot.SootMethod;
import soot.SourceLocator;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;
import soot.tagkit.SourceFileTag;

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
		SourceFileTag tag=new SourceFileTag(path.getName(), path.toString());
		sc.addTag(tag);
		String text = "";													//Scanning the file and creating an abstract syntax tree
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
		
		String pathToPackage=path.getPath().substring(0, path.getPath().lastIndexOf(File.separator)+1);
		List<String> folder=SourceLocator.v().getClassesUnder(pathToPackage);
		Dependencies deps = new Dependencies();
		for (int i=0; i<folder.size(); i++)									//Add all classes in the same package as dependencies
			deps.typesToSignature.add(RefType.v(folder.get(i)));
		com.sun.tools.javac.util.List<JCTree> classDecl = jccu.defs;
		while (classDecl.head instanceof JCImport) {						//Add all imports as dependencies
			if (((JCFieldAccess)((JCImport)classDecl.head).qualid).name.toString().equals("*")) {
				String pathToStar=SourceLocator.v().classPath().get(0)+File.separator+((JCFieldAccess)((JCImport)classDecl.head).qualid).selected.toString().replace(".", File.separator);
				List<String> folderStar=SourceLocator.v().getClassesUnder(pathToStar);
				for (int i=0; i<folderStar.size(); i++)
					deps.typesToSignature.add(RefType.v(folderStar.get(i)));
			}
			else
				deps.typesToSignature.add(RefType.v(((JCImport)classDecl.head).qualid.toString()));
			classDecl=classDecl.tail;
		}
		
		JCClassDecl classSig=(JCClassDecl) classDecl.head;					//Add super class
		if (classSig.extending!=null){
			String packageName=JavaUtil.getPackage((JCIdent)classSig.extending, deps, sc);
			SootClass superClass=Scene.v().getSootClass(packageName);
			sc.setSuperclass(superClass);
		}
		else {
			SootClass superClass=Scene.v().getSootClass(JavaUtil.addPackageName("Object"));
			sc.setSuperclass(superClass);
		}
		if (classSig.implementing.head!=null) {								//Add interfaces
			com.sun.tools.javac.util.List<JCExpression> interfaceList = classSig.implementing;
			while (interfaceList.head!=null) {
				String packageName;
				if (interfaceList.head instanceof JCTypeApply)
					packageName=JavaUtil.getPackage((JCIdent)((JCTypeApply)interfaceList.head).clazz, deps, sc);
				else
					packageName=JavaUtil.getPackage((JCIdent)interfaceList.head, deps, sc);
				SootClass interfaceClass=Scene.v().getSootClass(packageName);
				sc.addInterface(interfaceClass);
				interfaceList=interfaceList.tail;
			}
		}
		int modifier=JavaUtil.getModifiers(classSig.mods);					//Add modifier
		sc.setModifiers(modifier);
		com.sun.tools.javac.util.List<JCTree> classBodyList = ((JCClassDecl) classDecl.head).defs;
		ArrayList<JCTree> fieldList = new ArrayList<JCTree>();
		while (classBodyList.head != null) {								//Add all methods, fields, or inner classes in this class
			JavaUtil.getHead(classBodyList.head, deps, sc, fieldList);
			classBodyList = classBodyList.tail;
		}
		List<Type> parameterTypes = new ArrayList<>();
		if (!sc.declaresMethod("<init>", parameterTypes)) {					//If this method has no constructor, add a standard one
			String methodName="<init>";
			Type returnType = VoidType.v();
			SootMethod init=new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC);
			sc.addMethod(init);
			init.setSource(new JavaMethodSource(deps, fieldList));
		}
		if (!sc.declaresMethod("<clinit>", parameterTypes)) {				//if class has static fields, add a clinit-constructor
			boolean hasStaticField=false;
			Object[] list=sc.getFields().toArray();
			for (int i=0; i<list.length; i++)
				hasStaticField|=((SootField)list[i]).isStatic();
			if (hasStaticField) {
				String methodName="<clinit>";
				Type returnType = VoidType.v();
				SootMethod clInit=new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC);
				sc.addMethod(clInit);
				clInit.setSource(new JavaMethodSource(deps, fieldList));
			}
		}
		return deps;
	}
	

}
