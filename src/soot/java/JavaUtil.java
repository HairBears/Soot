package soot.java;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
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
import soot.NullType;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SourceLocator;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;
import soot.jimple.Constant;
import soot.jimple.DoubleConstant;
import soot.jimple.FloatConstant;
import soot.jimple.IntConstant;
import soot.jimple.LongConstant;
import soot.jimple.NullConstant;
import soot.jimple.StringConstant;
import soot.tagkit.EnclosingMethodTag;
import soot.tagkit.InnerClassTag;
import soot.tagkit.OuterClassTag;

/**
 * Provides functions that are needed in both, the class source and the method source
 * @author Martin Herbers
 * @author Florian Krause
 * @author Joachim Katholing
 *
 */
public class JavaUtil {

	/**
	 * Checks the type of the given node and returns it
	 * @param node	AST-node with a type
	 * @param deps	imports of the parsed class
	 * @param sc	current class, used for package and inner classes
	 * @return		Jimple-type matching the type of the node
	 * @throws		AssertionError	if it's an unknown type
	 */
	public static Type getType (JCTree node, Dependencies deps, SootClass sc) {
		if (node instanceof JCPrimitiveTypeTree) {								//Primitive types
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
		if (node instanceof JCArrayTypeTree) {									//Array type
			int dimension = 1;
			Type type = null;
			if (((JCArrayTypeTree)node).elemtype instanceof JCArrayTypeTree) {	//Mutli-dimensional array
				JCTree dims = ((JCArrayTypeTree) node).elemtype;
				while (dims instanceof JCArrayTypeTree) {
					dimension++;
					dims = ((JCArrayTypeTree) dims).elemtype;
				}
				type = getType(dims, deps, sc);
			}
			else
				type = getType(((JCArrayTypeTree)node).elemtype, deps, sc);		//Standard array (dimension=1)
			return ArrayType.v(type, dimension);
		}
		if (node instanceof JCIdent) {											//Class
			String packageName = getPackage(node.toString(), deps, sc);
			return RefType.v(packageName);
		}
		if (node instanceof JCTypeApply) {										//Parameterized class, e.g. List<Integer>
			String packageName = getPackage(((JCTypeApply)node).clazz.toString(), deps, sc);
			return RefType.v(packageName);
		}
		if (node instanceof JCFieldAccess) {									//Inner class
			String standLib = addPackageName(node.toString());
			if (standLib != null)
				return RefType.v(standLib);
			if (Scene.v().containsClass(node.toString()))
				return RefType.v(node.toString());
			String packageName = getPackage(((JCFieldAccess)node).selected.toString(), deps, sc);
			if (Scene.v().containsClass(packageName + "." + ((JCFieldAccess)node).name))
				return RefType.v(packageName + "." + ((JCFieldAccess)node).name);
			else
				return RefType.v(packageName + "$" + ((JCFieldAccess)node).name);
		}
		if (node instanceof JCNewArray)
			if (((JCNewArray)node).elems != null)
				return getType(((JCNewArray)node).elems.head, deps, sc);
			else
				return getType(((JCNewArray) node).elemtype, deps, sc);
		if (node instanceof JCLiteral)
			return getConstant((JCLiteral)node).getType();
		if (node==null)
			return NullType.v();
		else
			throw new AssertionError("Unknown type " + node.toString());
	}
	
	/**
	 * Searches the imports for a package name matching the class type of the node
	 * @param node	AST-node containing a class type
	 * @param deps	imports of the parsed class
	 * @param sc	current class, used for package and inner classes
	 * @return		name of matching import-package
	 */
	public static String getPackage(String node, Dependencies deps, SootClass sc) {
		if (sc.getName().endsWith(node)) {
			int index = sc.toString().lastIndexOf(node) - 1;
			if (index == -1 || sc.toString().charAt(index) == '$' || sc.toString().charAt(index) == '.')
				return sc.toString();
		}
		for (Type ref: deps.typesToSignature) {
			if (ref.toString().endsWith(node)) {
				int index = ref.toString().lastIndexOf(node) - 1;
				if (index == -1 || ref.toString().charAt(index) == '$' || ref.toString().charAt(index) == '.')
					return ref.toString();
			}
		}
		String newClass = addPackageName(node);								//Look in standard package for the class
		if (newClass != null)
			return newClass;
		SootClass phantomClass = new SootClass(node);
		phantomClass.setPhantom(true);							
		Scene.v().addClass(phantomClass);
		Scene.v().getApplicationClasses().add(phantomClass);
		return node;
	}

	/**
	 * Checks if the name in this node is a name of a class
	 * @param node	node containing a variable or class name
	 * @param deps	imports of parsed class
	 * @param sc	current soot class
	 * @return		true if its a class name, else false
	 */
	public static boolean isPackageName(String node, Dependencies deps, SootClass sc) {
		if (sc.getName().endsWith(node)){
			int index = sc.toString().lastIndexOf(node) - 1;
			if (index == -1 || sc.toString().charAt(index) == '$' || sc.toString().charAt(index) == '.')
				return true;
		}
		for (Type ref: deps.typesToSignature) {
			if (ref.toString().endsWith(node)) {
				int index = ref.toString().lastIndexOf(node) - 1; 
				if (index == -1 || ref.toString().charAt(index) == '$' || ref.toString().charAt(index) == '.')
					return true;
			}
		}
		String newClass = addPackageName(node);								//Look in standard package for the class
		if (newClass != null)
			return true;
		return false;
		}
	
	/**
	 * Returns the complete name of a standard-package-class and adds it to the basic classes if necessary
	 * E.g. String to java.lang.String
	 * @param className		name of a class in a standard package
	 * @return				name with package-name
	 */
	public static String addPackageName(String className) {
		String packagePart = "";
		if (className.contains(".")) {
			packagePart = className.substring(0, className.lastIndexOf('.'));
			className = className.substring(className.lastIndexOf('.')+1);
		}
		JarFile rt = null;
		String returnString = null;
		try {
			rt = new JarFile(SourceLocator.v().classPath().get(SourceLocator.v().classPath().size()-1));
			Enumeration<JarEntry> entries = rt.entries();
			while (entries.hasMoreElements()) {									//Check all classes in rt.jar for a matching name 
				JarEntry entry = entries.nextElement();
				if (entry.toString().contains(".class")) {
					String substring = entry.toString().substring(entry.toString().lastIndexOf("/") + 1, entry.toString().lastIndexOf("."));
					if (substring.equals(className) && entry.toString().replace('/', '.').contains(packagePart))
						returnString = entry.toString().substring(0, entry.toString().lastIndexOf(".")).replace('/', '.');
					else if (substring.contains("$")) {
						String subSubstring = substring.substring(substring.lastIndexOf('$') + 1);
						if (subSubstring.equals(className) && entry.toString().replace('/', '.').contains(packagePart) && returnString==null)
							returnString = entry.toString().substring(0, entry.toString().lastIndexOf(".")).replace('/', '.');
					}
				}
			}
			rt.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (returnString != null) {												//Add the new class to the basic classes and force its resolving
			if (Scene.v().getSootClass(returnString).resolvingLevel()<SootClass.SIGNATURES) {
				Scene.v().addBasicClass(returnString, SootClass.SIGNATURES);
				Scene.v().forceResolve(returnString, SootClass.SIGNATURES);
			}
			return returnString;
		}
		return null;
	}
	
	/**
	 * If a new class is needed via a field access, check if this class needs to be resolved
	 * @param className		name of the class from a field
	 * @return				same name
	 */
	public static String addFieldPackageName(String className) {
		if (Scene.v().getSootClass(className).resolvingLevel()<SootClass.SIGNATURES) {
			Scene.v().addBasicClass(className, SootClass.SIGNATURES);
			Scene.v().forceResolve(className, SootClass.SIGNATURES);
		}
		return className;
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
	 * Translates number into a jimple-constant
	 * @param node	node containing the value
	 * @return		matching jimple-constant with value
	 */
	public static Constant getConstant(JCLiteral node) {
		if (node.typetag.name().equals("INT"))
			return IntConstant.v((int)node.value);
		if (node.typetag.name().equals("LONG"))
			return LongConstant.v((long)node.value);
		if (node.typetag.name().equals("DOUBLE"))
			return DoubleConstant.v((double)node.value);
		if (node.typetag.name().equals("FLOAT"))
			return FloatConstant.v((float)node.value);
		if (node.typetag.name().equals("BOOLEAN"))
			return IntConstant.v((int)node.value);
		if (node.toString().charAt(0) == '"')
			return StringConstant.v((String)node.value);
		if (node.typetag.name().equals("BOT"))
			return NullConstant.v();
		if (node.typetag.name().equals("CHAR"))
			return IntConstant.v((int)node.value);
		else
			throw new AssertionError("Unknown type of constant " + node.toString());
	}
	
	
	/**
	 * Adds fields, methods and inner classes to a soot class
	 * @param node			node containing an element of the class
	 * @param deps			imports
	 * @param sc			current class
	 * @param fieldList		list, in which the new fields will be added
	 */
	public static void getHead(JCTree node, Dependencies deps, SootClass sc, ArrayList<JCTree> fieldList) {
		if (node instanceof JCMethodDecl) {										//Add a method to this class
			JCMethodDecl method = (JCMethodDecl)node;
			com.sun.tools.javac.util.List<JCExpression> throwListTree = method.thrown;
			ArrayList<SootClass> throwList = new ArrayList<>();
			while (throwListTree.head != null) {									//Add "throws"
				String packageName = JavaUtil.getPackage(throwListTree.head.toString(), deps, sc);
				SootClass thrownClass = Scene.v().getSootClass(packageName);
				throwList.add(thrownClass);
				throwListTree = throwListTree.tail;
			}
			List<Type> parameterTypes = new ArrayList<>();
			com.sun.tools.javac.util.List<JCVariableDecl> parameterListTree = method.params;
			while (parameterListTree.head != null) {							//Add parameter
				Type type = JavaUtil.getType(parameterListTree.head.vartype, deps, sc);
				parameterTypes.add(type);
				parameterListTree = parameterListTree.tail;
			}
			String methodName = method.name.toString();
			if (methodName.equals("<init>") && sc.isInnerClass())
				parameterTypes.add(RefType.v(sc.getOuterClass()));
			Type returnType;
			if (methodName.equals("<init>"))									//Add return type
				returnType = VoidType.v();
			else
				returnType = JavaUtil.getType(method.restype, deps, sc);
			int modifier = getModifiers((JCModifiers)method.mods);				//Add modifier
			if (sc.isInterface())
				modifier |= Modifier.ABSTRACT;
			SootMethod newMethod = new SootMethod(methodName, parameterTypes, returnType, modifier, throwList);
			sc.addMethod(newMethod);
			if (newMethod.isAbstract())											//Set source for method body
				newMethod.setSource(new JavaMethodSource(deps, fieldList));
			else
				newMethod.setSource(new JavaMethodSource(method, deps, fieldList));
			if (sc.getTag("OuterClassTag") != null && ((OuterClassTag)sc.getTag("OuterClassTag")).isAnon()) {
				EnclosingMethodTag tag = new EnclosingMethodTag(sc.toString(), newMethod.getName(), newMethod.toString());
				sc.addTag(tag);													//Add tag for anonymous method
			}
		}
		if (node instanceof JCVariableDecl) {									//Add fields
			String fieldName = ((JCVariableDecl) node).getName().toString();
			Type fieldType = JavaUtil.getType(((JCVariableDecl) node).vartype, deps, sc);
			int fieldMods = getModifiers(((JCVariableDecl) node).getModifiers());
			SootField field = new SootField(fieldName, fieldType, fieldMods);
			sc.addField(field);
			if (((JCVariableDecl)node).init != null) {
				fieldList.add(node);											//If initialized, add to list for constructor
			}
		}
		if (node instanceof JCClassDecl) {										//Add inner class, anonymous class, or enum class
			SootClass innerClass = new SootClass(sc.getName()+"$"+((JCClassDecl)node).name.toString());
			Iterator<Type> iter = deps.typesToSignature.iterator();
			List<Type> remove=new ArrayList<>();
			List<Type> add=new ArrayList<>();
			while (iter.hasNext()) {
				Type type=iter.next();
				if (type.toString().endsWith(innerClass.toString().replace('$', '.')) && !type.toString().equals(innerClass.toString().replace('$', '.'))) {
					remove.add(type);
					add.add(RefType.v(innerClass));
				}
			}
			deps.typesToSignature.removeAll(remove);
			deps.typesToSignature.addAll(add);
			innerClass.addTag(sc.getTag("SourceFileTag"));						//Add tag for source file, inner classes and outer classes
			OuterClassTag outerTag = new OuterClassTag(sc, sc.getName(), false);
			innerClass.addTag(outerTag);
			InnerClassTag innerTag = new InnerClassTag(innerClass.getName(), sc.getName(), innerClass.getShortName(), 0);
			sc.addTag(innerTag);
			Scene.v().addClass(innerClass);
			Scene.v().getApplicationClasses().add(innerClass);					//Add inner class to class list
			innerClass.setOuterClass(sc);
			deps.typesToSignature.add(RefType.v(innerClass));
			int modifier = getModifiers(((JCClassDecl) node).mods);				//Add modifiers
			if (node.toString().substring(0, node.toString().indexOf('{')).contains("enum")) {
				modifier |= Modifier.ENUM | Modifier.FINAL;
			}
			innerClass.setModifiers(modifier);
			if (((JCClassDecl)node).extending!=null) {							//Add super class
				String packageName = JavaUtil.getPackage(((JCClassDecl)node).extending.toString(), deps, sc);
				SootClass superClass = Scene.v().getSootClass(packageName);
				innerClass.setSuperclass(superClass);
			}
			else if (Modifier.isEnum(innerClass.getModifiers())) {
				SootClass superClass = Scene.v().getSootClass(addPackageName("Enum"));
				innerClass.setSuperclass(superClass);
			}
			else {
				SootClass superClass=Scene.v().getSootClass(addPackageName("Object"));
				innerClass.setSuperclass(superClass);
			}
			if (((JCClassDecl)node).implementing.head != null) {					//Add interfaces
				com.sun.tools.javac.util.List<JCExpression> interfaceList = ((JCClassDecl)node).implementing;
				while (interfaceList.head != null) {
					String packageName;
					if (interfaceList.head instanceof JCTypeApply) 
						packageName = JavaUtil.getPackage(((JCTypeApply)interfaceList.head).clazz.toString(), deps, sc);
					else
						packageName = JavaUtil.getPackage(interfaceList.head.toString(), deps, sc);
					SootClass interfaceClass = Scene.v().getSootClass(packageName);
					innerClass.addInterface(interfaceClass);
					interfaceList = interfaceList.tail;
				}
			}
			if (!Modifier.isEnum(innerClass.getModifiers()) && !innerClass.isInterface()) {					//Add needed field
				String fieldName = "this$0";
				Type fieldType = RefType.v(sc);
				int fieldMods = Modifier.FINAL;
				SootField field = new SootField(fieldName, fieldType, fieldMods);
				innerClass.addField(field);
			} else if (Modifier.isEnum(innerClass.getModifiers())){
				String fieldName = "ENUM$VALUES";
				Type fieldType = ArrayType.v(RefType.v(innerClass), 1);
				int fieldMods = Modifier.FINAL | Modifier.STATIC | Modifier.PRIVATE;
				SootField field = new SootField(fieldName, fieldType, fieldMods);
				innerClass.addField(field);
			}
			com.sun.tools.javac.util.List<JCTree> classBodyList = ((JCClassDecl) node).defs;
			ArrayList<JCTree> newFieldList = new ArrayList<JCTree>();
			while (classBodyList.head != null) {								//Add all methods, inner classes, or fields in this class
				getHead(classBodyList.head, deps, innerClass, newFieldList);
				classBodyList = classBodyList.tail;
			}
			List<Type> parameterTypes = new ArrayList<>();
			if (Modifier.isEnum(innerClass.getModifiers())) {
				parameterTypes.add(RefType.v(addPackageName("String")));
				parameterTypes.add(IntType.v());
			}
			else
				parameterTypes.add(RefType.v(sc));								//If class has no constructor, add a standard one
			if (!innerClass.declaresMethod("<init>", parameterTypes) && !innerClass.isInterface()) {
				String methodname = "<init>";
				
				Type returnType = VoidType.v();
				innerClass.addMethod(new SootMethod(methodname, parameterTypes, returnType, Modifier.PUBLIC));
				innerClass.getMethod(methodname, parameterTypes, returnType).setSource(new JavaMethodSource(deps, newFieldList));
			}
			if (Modifier.isEnum(innerClass.getModifiers())) {					//Add necessary methods for an enum class
				List<Type> parameterList = new ArrayList<>();
				
				innerClass.addMethod(new SootMethod("<clinit>", parameterList, VoidType.v(), Modifier.STATIC));
				innerClass.getMethod("<clinit>", parameterList, VoidType.v()).setSource(new JavaMethodSource(deps, newFieldList));
				
				innerClass.addMethod(new SootMethod("values", parameterList, ArrayType.v(RefType.v(innerClass), 1), Modifier.PUBLIC|Modifier.STATIC));
				innerClass.getMethod("values", parameterList, ArrayType.v(RefType.v(innerClass), 1)).setSource(new JavaMethodSource(deps, newFieldList));
				
				List<Type> parameterList2 = new ArrayList<>();
				parameterList2.add(RefType.v(addPackageName("String")));
				innerClass.addMethod(new SootMethod("valueOf", parameterList2, RefType.v(innerClass), Modifier.PUBLIC|Modifier.STATIC));
				innerClass.getMethod("valueOf", parameterList2, RefType.v(innerClass)).setSource(new JavaMethodSource(deps, newFieldList));
			}
			if (!sc.declaresMethod("<clinit>", parameterTypes) && !innerClass.isInterface()) {				//if class has static fields, add a clinit-constructor
				boolean hasStaticField = false;
				Object[] list = sc.getFields().toArray();
				for (int i=0; i<list.length; i++)
					hasStaticField |= ((SootField)list[i]).isStatic();
				if (hasStaticField) {
					String methodName = "<clinit>";
					Type returnType = VoidType.v();
					SootMethod clInit = new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC);
					sc.addMethod(clInit);
					clInit.setSource(new JavaMethodSource(deps, fieldList));
				}
			}
		}
		if (node instanceof JCBlock) {											//Add a static block
			if (!sc.declaresMethodByName("<clinit>")) {
				List<Type> parameterTypes = new ArrayList<>();
				sc.addMethod(new SootMethod("<clinit>", parameterTypes, VoidType.v(), Modifier.STATIC));
				sc.getMethod("<clinit>", parameterTypes, VoidType.v()).setSource(new JavaMethodSource(((JCBlock) node).stats, deps, fieldList));
			}
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

	