package soot.java;

import java.util.ArrayList;
import java.util.List;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;

import soot.ArrayType;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.IntegerType;
import soot.LongType;
import soot.Modifier;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;

public class JavaUtil {

	/**
	 * Checks the type of the given node and returns it
	 * @param node	AST-node with a type
	 * @param deps	imports of the parsed class
	 * @param sc	current class, used for package and inner classes
	 * @return		Jimple-type matching the type of the node
	 * @throws		AssertionError
	 */
	public static Type getType (JCTree node, Dependencies deps, SootClass sc) {
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
		if (node instanceof JCArrayTypeTree) {
			Type type=getType(((JCArrayTypeTree)node).elemtype, deps, sc);
			int size=node.toString().replace(((JCArrayTypeTree)node).elemtype.toString(), "").length()/2;
			return ArrayType.v(type, size);
		}
		if (node instanceof JCIdent) {
			String packageName=getPackage((JCIdent)node, deps, sc);
			return RefType.v(packageName);
		}
		if (node instanceof JCTypeApply) {
			String packageName=getPackage((JCIdent)((JCTypeApply)node).clazz, deps, sc);
			return RefType.v(packageName);
		}
		else
			throw new AssertionError("Unknown type " + node.toString());
	}
	
	/**
	 * Searches the imports for a package name matching the class type of the node
	 * @param node	AST-node containing a class type
	 * @param deps	imports of the parsed class
	 * @param sc	current class, used for package and inner classes
	 * @return		name of matching import-package
	 * @throws		AssertionError
	 */
	public static String getPackage(JCIdent node, Dependencies deps, SootClass sc) {
		if (sc.toString().contains("$") && sc.toString().substring(sc.toString().lastIndexOf('$')+1, sc.toString().length()).equals(node.toString()))
			return sc.toString();
		for (Type ref:deps.typesToSignature) {
			String substring=ref.toString().substring(ref.toString().lastIndexOf('.')+1, ref.toString().length());
			if (substring.equals(node.toString()))
				return ref.toString();
		}
		for (SootClass clazz:Scene.v().getClasses()) {
			String classInPackage=sc.getPackageName()+node.toString();
			String innerClass=sc.getName()+"$"+node.toString();
			if (clazz.getName().equals(classInPackage) || clazz.getName().equals(innerClass))
				return clazz.toString();
		}
		throw new AssertionError("Unknown class " + node.toString());
	}

	/**
	 * Checks if the name in this node is a name of a class
	 * @param node	node containing a variable or class name
	 * @param deps	imports of parsed class
	 * @return		true if its a class name, else false
	 */
	public static boolean isPackageName(JCIdent node, Dependencies deps, SootClass sc) {
		if (sc.toString().equals(node.toString()))
			return true;
		for (Type ref:deps.typesToSignature) {
			String substring=ref.toString().substring(ref.toString().lastIndexOf('.')+1, ref.toString().length());
			if (substring.equals(node.toString()))
				return true;
		}
		for (SootClass clazz:Scene.v().getClasses()) {
			String classinpackage=sc.getPackageName()+node.toString();
			String innerclass=sc.getName()+"$"+node.toString();
			if (clazz.getName().equals(classinpackage) || clazz.getName().equals(innerclass))
				return true;
		}
		return false;
	}
	
	/**
	 * Transforms the primitive types or array type into matching classes
	 * @param type	a primitive type or array type
	 * @return		matching class to the primitive type
	 */
	public static Type primToClass(Type type) {
		if (type instanceof IntegerType)
			return RefType.v("java.lang.Integer");
		if (type instanceof CharType)
			return RefType.v("java.lang.Character");
		if (type instanceof BooleanType)
			return RefType.v("java.lang.Boolean");
		if (type instanceof ByteType)
			return RefType.v("java.lang.Byte");
		if (type instanceof DoubleType)
			return RefType.v("java.lang.Double");
		if (type instanceof FloatType)
			return RefType.v("java.lang.Float");
		if (type instanceof LongType)
			return RefType.v("java.lang.Long");
		if (type instanceof ShortType)
			return RefType.v("java.lang.Short");
		if (type instanceof ArrayType)
			return RefType.v("java.lang.Object");
		else
			throw new AssertionError("No primitive Type " + type.toString());
	}
	
	/**
	 * Adds fields, methods and inner classes to a soot class
	 * @param node			node containing an element of the class
	 * @param deps			imports
	 * @param sc			current class
	 * @param fieldList		list, in which the new fields will be added
	 */
	public static void getHead(JCTree node, Dependencies deps, SootClass sc, ArrayList<JCTree> fieldList) {
		if (node instanceof JCMethodDecl) {
			JCMethodDecl method = (JCMethodDecl)node;
			com.sun.tools.javac.util.List<JCExpression> throwListTree = method.thrown;
			ArrayList<SootClass> throwList=new ArrayList<>();
			while (throwListTree.head!=null) {
				String packageName=JavaUtil.getPackage((JCIdent)throwListTree.head, deps, sc);
				SootClass thrownClass=Scene.v().getSootClass(packageName);
				throwList.add(thrownClass);
				throwListTree=throwListTree.tail;
			}
			List<Type> parameterTypes = new ArrayList<>();
			com.sun.tools.javac.util.List<JCVariableDecl> parameterListTree = method.params;
			while (parameterListTree.head != null) {
				Type type = JavaUtil.getType(parameterListTree.head.vartype, deps, sc);
				parameterTypes.add(type);
				parameterListTree = parameterListTree.tail;
			}
			String methodName = method.name.toString();
			Type returnType;
			if (methodName.equals("<init>"))
				returnType = VoidType.v();
			else
				returnType = JavaUtil.getType(method.restype, deps, sc);
			int modifier = getModifiers((JCModifiers)method.mods);
			if (sc.isInterface())
				modifier |= Modifier.ABSTRACT;
			sc.addMethod(new SootMethod(methodName, parameterTypes, returnType, modifier, throwList));
			sc.getMethod(methodName, parameterTypes, returnType).setSource(new JavaMethodSource(method, deps, fieldList));
		}
		if (node instanceof JCVariableDecl) {
			String fieldName = ((JCVariableDecl) node).getName().toString();
			Type fieldType = JavaUtil.getType(((JCVariableDecl) node).vartype, deps, sc);
			int fieldMods = getModifiers(((JCVariableDecl) node).getModifiers());
			SootField field = new SootField(fieldName, fieldType, fieldMods);
			sc.addField(field);
	/*		if (Modifier.isStatic(fieldMods) && !sc.declaresMethodByName("<clinit>")) {
				List<Type> parameterTypes=new ArrayList<>();
				sc.addMethod(new SootMethod("<clinit>", parameterTypes, VoidType.v(), Modifier.STATIC));
				sc.getMethod("<clinit>", parameterTypes, VoidType.v()).setSource(new JavaMethodSource(null, deps, fieldList));
			}
	*/		if (((JCVariableDecl)node).init != null) {
				fieldList.add(node);
			}
		}
		if (node instanceof JCClassDecl) {
			SootClass innerClass=new SootClass(sc.getName()+"$"+((JCClassDecl)node).name.toString());
			Scene.v().addClass(innerClass);
			Scene.v().getApplicationClasses().add(innerClass);
			innerClass.setOuterClass(sc);
			int modifier=getModifiers(((JCClassDecl) node).mods);
			if (node.toString().substring(0, node.toString().indexOf('{')).contains("enum")) {
				modifier |= Modifier.ENUM | Modifier.FINAL;
			}
			innerClass.setModifiers(modifier);
			if (((JCClassDecl)node).extending!=null) {
				String packageName=JavaUtil.getPackage((JCIdent)((JCClassDecl)node).extending, deps, sc);
				SootClass superClass=Scene.v().getSootClass(packageName);
				innerClass.setSuperclass(superClass);
			}
			else if (Modifier.isEnum(innerClass.getModifiers())) {
				SootClass superClass=Scene.v().getSootClass("java.lang.Enum");
				innerClass.setSuperclass(superClass);
			}
			else {
				SootClass superClass=Scene.v().getSootClass("java.lang.Object");
				innerClass.setSuperclass(superClass);
			}
			if (((JCClassDecl)node).implementing.head!=null) {
				com.sun.tools.javac.util.List<JCExpression> interfaceList = ((JCClassDecl)node).implementing;
				while (interfaceList.head!=null) {
					String packageName=JavaUtil.getPackage((JCIdent)interfaceList.head, deps, sc);
					SootClass interfaceClass=Scene.v().getSootClass(packageName);
					innerClass.addInterface(interfaceClass);
					interfaceList=interfaceList.tail;
				}
			}
			if (!Modifier.isEnum(innerClass.getModifiers())) {
				String fieldName="this$0";
				Type fieldType=RefType.v(sc);
				int fieldMods=Modifier.FINAL;
				SootField field=new SootField(fieldName, fieldType, fieldMods);
				innerClass.addField(field);
			} else {
				String fieldName="ENUM$VALUES";
				Type fieldType=ArrayType.v(RefType.v(innerClass), 1);
				int fieldMods=Modifier.FINAL | Modifier.STATIC | Modifier.PRIVATE;
				SootField field=new SootField(fieldName, fieldType, fieldMods);
				innerClass.addField(field);
			}
			com.sun.tools.javac.util.List<JCTree> classBodyList = ((JCClassDecl) node).defs;
			ArrayList<JCTree> newFieldList = new ArrayList<JCTree>();
			while (classBodyList.head != null) {										//Add all methods in this class
				getHead(classBodyList.head, deps, innerClass, newFieldList);
				classBodyList = classBodyList.tail;
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
				
				Type returnType = VoidType.v();
				innerClass.addMethod(new SootMethod(methodname, parameterTypes, returnType, Modifier.PUBLIC));
				innerClass.getMethod(methodname, parameterTypes, returnType).setSource(new JavaMethodSource(deps, newFieldList));
			}
			if (Modifier.isEnum(innerClass.getModifiers())) {
				List<Type> parameterList=new ArrayList<>();
				
				innerClass.addMethod(new SootMethod("<clinit>", parameterList, VoidType.v(), Modifier.STATIC));
				innerClass.getMethod("<clinit>", parameterList, VoidType.v()).setSource(new JavaMethodSource(deps, newFieldList));
				
				innerClass.addMethod(new SootMethod("values", parameterList, ArrayType.v(RefType.v(innerClass), 1), Modifier.PUBLIC|Modifier.STATIC));
				innerClass.getMethod("values", parameterList, ArrayType.v(RefType.v(innerClass), 1)).setSource(new JavaMethodSource(deps, newFieldList));
				
				List<Type> parameterList2=new ArrayList<>();
				parameterList2.add(RefType.v("java.lang.String"));
				innerClass.addMethod(new SootMethod("valueOf", parameterList2, RefType.v(innerClass), Modifier.PUBLIC|Modifier.STATIC));
				innerClass.getMethod("valueOf", parameterList2, RefType.v(innerClass)).setSource(new JavaMethodSource(deps, newFieldList));
			}
			
		}
		if (node instanceof JCBlock) {
			List<Type> parameterTypes=new ArrayList<>();
			sc.addMethod(new SootMethod("<clinit>", parameterTypes, VoidType.v(), Modifier.STATIC));
			sc.getMethod("<clinit>", parameterTypes, VoidType.v()).setSource(new JavaMethodSource(((JCBlock) node).stats, deps, fieldList));
		}
		
	}
	
	/**
	 * Combines the modifiers of the method into one 
	 * @param node	AST-node containing all modifiers
	 * @return		combined modifier as an integer
	 */
	public static int getModifiers(JCModifiers node) {
		int modSum = 0;
		String modString = node.toString();
		
		if (modString.contains("abstract"))
			modSum |= Modifier.ABSTRACT;
		if (modString.contains("final"))
			modSum |= Modifier.FINAL;
		if (modString.contains("interface"))
			modSum |= Modifier.INTERFACE;
		if (modString.contains("native"))
			modSum |= Modifier.NATIVE;
		if (modString.contains("private"))
			modSum |= Modifier.PRIVATE;
		if (modString.contains("protected"))
			modSum |= Modifier.PROTECTED;
		if (modString.contains("public"))
			modSum |= Modifier.PUBLIC;
		if (modString.contains("static"))
			modSum |= Modifier.STATIC;
		if (modString.contains("synchronized"))
			modSum |= Modifier.SYNCHRONIZED;
		if (modString.contains("transient"))
			modSum |= Modifier.TRANSIENT;
		if (modString.contains("volatile"))
			modSum |= Modifier.VOLATILE;
		if (modString.contains("strictfp"))
			modSum |= Modifier.STRICTFP;
		if (modString.contains("annotation"))
			modSum |= Modifier.ANNOTATION;
		if (modString.contains("enum"))
			modSum |= Modifier.ENUM;
		
		return modSum;
	}


}

	