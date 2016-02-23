package soot.java;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;

import soot.ArrayType;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.IntegerType;
import soot.LongType;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
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
		if (node instanceof JCArrayTypeTree)
			return ArrayType.v(getType(((JCArrayTypeTree)node).elemtype, deps, sc), node.toString().replace(((JCArrayTypeTree)node).elemtype.toString(), "").length()/2);
		if (node instanceof JCIdent)
			return RefType.v(getPackage((JCIdent)node, deps, sc));
		if (node instanceof JCTypeApply)
			return RefType.v(getPackage((JCIdent)((JCTypeApply)node).clazz, deps, sc));
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
		if (sc.toString().contains(node.toString()))
			return sc.toString();
		for (Type ref:deps.typesToSignature) {
			String substring=ref.toString().substring(ref.toString().lastIndexOf('.')+1, ref.toString().length());
			if (substring.equals(node.toString()))
				return ref.toString();
		}
		for (SootClass clazz:Scene.v().getClasses()) {
			String classinpackage=sc.getPackageName()+node.toString();
			String innerclass=sc.getName()+"$"+node.toString();
			if (clazz.getName().equals(classinpackage) || clazz.getName().equals(innerclass))
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
		if (sc.toString().contains(node.toString()))
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
}
