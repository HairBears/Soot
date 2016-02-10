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
	 * @return		Jimple-type matching the type of the node
	 * @throws		AssertionError
	 */
	public static Type getType (JCTree node, Dependencies deps, String thispackage) {
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
			return ArrayType.v(getType(((JCArrayTypeTree)node).elemtype, deps, thispackage), node.toString().replace(((JCArrayTypeTree)node).elemtype.toString(), "").length()/2);
		if (node instanceof JCIdent)
			return RefType.v(getPackage((JCIdent)node, deps, thispackage));
		if (node instanceof JCTypeApply)
			return RefType.v(getPackage((JCIdent)((JCTypeApply)node).clazz, deps, thispackage));
		else
			throw new AssertionError("Unknown type " + node.toString());
	}
	
	/**
	 * Searches the imports for a package name matching the class type of the node
	 * @param node	AST-node containing a class type
	 * @param deps	imports of the parsed class
	 * @return		name of matching import-package
	 * @throws		AssertionError
	 */
	public static String getPackage(JCIdent node, Dependencies deps, String thispackage) {
		for (Type ref:deps.typesToSignature) {
			String substring=ref.toString().substring(ref.toString().lastIndexOf('.')+1, ref.toString().length());
			if (substring.equals(node.toString()))
				return ref.toString();
		}
		for (SootClass clazz:Scene.v().getClasses()) {
			String classinpackage=thispackage+node.toString();
			if (clazz.getName().equals(classinpackage))
				return clazz.toString();
		}
		throw new AssertionError("Unknown class " + node.toString());
	}

	public static boolean isPackageName(JCIdent node, Dependencies deps) {
		for (Type ref:deps.typesToSignature) {
			String substring=ref.toString().substring(ref.toString().lastIndexOf('.')+1, ref.toString().length());
			if (substring.equals(node.toString()))
				return true;
		}
		return false;
	}
}
