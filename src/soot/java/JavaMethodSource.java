package soot.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;

import soot.ArrayType;
import soot.Body;
import soot.BooleanType;
import soot.CharType;
import soot.IntType;
import soot.Local;
import soot.MethodSource;
import soot.Modifier;
import soot.NullType;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Trap;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.javaToJimple.IInitialResolver.Dependencies;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.ArrayRef;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.ClassConstant;
import soot.jimple.ConditionExpr;
import soot.jimple.Constant;
import soot.jimple.DoubleConstant;
import soot.jimple.FieldRef;
import soot.jimple.FloatConstant;
import soot.jimple.IfStmt;
import soot.jimple.InstanceOfExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LengthExpr;
import soot.jimple.LongConstant;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JimpleLocal;
import soot.tagkit.InnerClassTag;
import soot.tagkit.OuterClassTag;
import soot.util.Chain;

public class JavaMethodSource implements MethodSource {
	
	//the complete method-tree
	JCMethodDecl methodTree;
	//Contains the locals, easier to find with name
	HashMap<String,Local> locals = new HashMap<>();
	//List of Exceptions, Try/Catch
	ArrayList<Trap> traps = new ArrayList<>();
	//List of units
	ArrayList<Unit> units = new ArrayList<>();
	//List used to keep track of the current loop, used to find the jump-target for continue-statements
	ArrayList<Unit> loopContinue = new ArrayList<>();
	//List to solve the problem with a=b++, where b=b+1 comes after a=b or constructor-calls after new
	ArrayList<JCTree> queue = new ArrayList<>();
	//Contains the last new-class-local, used for direct constructor invocation
	Local newClasslocal;
	//Generator for locals
	LocalGenerator localGenerator;
	//Contains specific soot-method globally, used to find methods in the same class
	SootMethod thisMethod;
	//Contains imports to find package names as type
	Dependencies deps;
	//List of fields which have to be initialized in each constructor 
	ArrayList<JCTree> fieldList;
	//List that contains the end of all current loops, used to get a target for "break"
	ArrayList<Unit> breakLoop = new ArrayList<>();
	//Anonymous classes don't have names, so give them a number
	int anonymousClassNumber=1;
	
	com.sun.tools.javac.util.List<JCStatement> methodBodyTree;
	
	/**
	 * Constructor used for standard methods and constructors
	 * @param body			body of the method
	 * @param deps			dependencies for class names
	 * @param fieldlist		list of fields that need to be initialized in the constructor
	 */
	public JavaMethodSource(JCMethodDecl body, Dependencies deps, ArrayList<JCTree> fieldlist) {
		this.methodTree = body;
		this.deps = deps;
		this.fieldList = fieldlist;
		this.methodBodyTree=body.body.stats;
	}
	
	/**
	 * Constructor used for static blocks which only have a list of statements
	 * @param bodylist		list of statements
	 * @param deps			dependencies for class names
	 * @param fieldlist		list of fields that need to be initialized in the constructor
	 */
	public JavaMethodSource(com.sun.tools.javac.util.List<JCStatement> bodylist,  Dependencies deps, ArrayList<JCTree> fieldlist) {
		this.deps = deps;
		this.fieldList = fieldlist;
		this.methodBodyTree=bodylist;
	}
	
	/**
	 * Constructor used for mandatory methods
	 * @param deps			dependencies for class names
	 * @param fieldlist		list of fields that need to be initialized in the constructor
	 */
	public JavaMethodSource(Dependencies deps, ArrayList<JCTree> fieldlist) {
		this.deps = deps;
		this.fieldList = fieldlist;
	}
	
	/*
	 * (non-Javadoc)
	 * @see soot.MethodSource#getBody(soot.SootMethod, java.lang.String)
	 */
	@Override
	public Body getBody(SootMethod m, String phaseName) {
		thisMethod = m;
		JimpleBody jb = Jimple.v().newBody(m);
		localGenerator = new LocalGenerator(jb);
		if (Modifier.isEnum(m.getDeclaringClass().getModifiers())) {		//Add the standard enum method body
			if (m.getName().equals("<init>")) {
				enumInit();
			}
			else if (m.getName().equals("<clinit>")) {
				enumConstructor();
			}
			else if (m.getName().equals("values")) {
				enumValues();
			}
			else if (m.getName().equals("valueOf")) {
				enumValueOf();
			}
		}
		else if (methodBodyTree == null) {									//If the method has no body, add a this-variable if non-static and initialize fields
			if (!m.isStatic()) 
				addThisVariable();
			if (m.getName().equals("<init>"))
				getFields();
			if (m.getName().equals("<clinit>"))
				getStaticFields();
		}
		else {
			if (methodTree != null)											//If there is a body, build the matching jimple body
				addParameter(methodTree.params);
			if (m.getName().equals("<init>"))
				getFields();
			if (m.getName().equals("<clinit>"))
				getStaticFields();
			getNextNode(methodBodyTree);
		}
		jb.getTraps().addAll(traps);										//Add collected traps, locals and units to the jimple body
		Iterator<Local> iterator = locals.values().iterator();
		while (iterator.hasNext()) {
			Local local = iterator.next();
			if (!jb.getLocals().contains(local))
				jb.getLocals().add(local);
		}
		jb.getUnits().addAll(units);
		if (!(units.get(units.size()-1) instanceof ReturnStmt))
			jb.getUnits().add(Jimple.v().newReturnVoidStmt());
		deleteNops(jb);
		return jb;
	}
	
	/**
	 * Separates unit-list into next unit and list of remaining units
	 * @param nodeList	list of units
	 * @return the next unit
	 */
	private Unit getNextNode(com.sun.tools.javac.util.List<JCStatement> nodeList) {
		Unit firstUnit = getUnit(nodeList.head);							//Process first unit in the list
		if (!queue.isEmpty()) {												//If there is an additional unit that needs to be added directly after the previous unit, add it now
			JCTree queueNode = queue.get(0);
			queue.remove(queueNode);
			getUnit(queueNode);
		}
		while (nodeList.tail.head != null) {								//Process the rest of the unit list
			nodeList = nodeList.tail;
			getUnit(nodeList.head);
			if (!queue.isEmpty()) {
				JCTree tree = queue.get(0);
				queue.remove(tree);
				getUnit(tree);
			}
		}
		return firstUnit;
	}
	
	/**
	 * Checks the type of the given node
	 * @param node	current node to translate
	 * @return 		node translated to corresponding Jimple-unit
	 */
	private Unit getUnit(JCTree node) {
		node = ignoreNode(node);
		if (node instanceof JCVariableDecl)
			return addVariableDecl((JCVariableDecl)node);
		if (node instanceof JCIf) 
			return addIf((JCIf)node);
		if (node instanceof JCDoWhileLoop)
			return addDoWhile((JCDoWhileLoop)node);
		if (node instanceof JCWhileLoop)
			return addWhile((JCWhileLoop)node);
		if (node instanceof JCReturn)
			return addReturn((JCReturn)node);
		if (node instanceof JCForLoop)
			return addFor((JCForLoop)node);
		if (node instanceof JCEnhancedForLoop)
			return addEnhancedFor((JCEnhancedForLoop)node);
		if (node instanceof JCSwitch)
	 		return addSwitch((JCSwitch)node);
		if (node instanceof JCMethodInvocation)
			return addMethodInvocation((JCMethodInvocation)node);
		if (node instanceof JCAssignOp)
			return addAssignOp((JCAssignOp)node);
		if (node instanceof JCAssign)
			return addAssign((JCAssign)node);
		if (node instanceof JCUnary)
			return addUnary((JCUnary)node);
		if (node instanceof JCContinue)
			return addContinue((JCContinue)node);
		if (node instanceof JCSynchronized)
			return addSynchronized((JCSynchronized)node);
		if (node instanceof JCTry)
			return addTryCatch((JCTry)node);
		if (node instanceof JCNewClass)
			return addConstructorInvocation((JCNewClass)node);
		if (node instanceof JCBreak)
			return addBreak((JCBreak)node);
		if (node instanceof JCThrow)
			return addThrow((JCThrow) node);
		else
			throw new AssertionError("Unknown node type " + node.getClass().getSimpleName());
	}

	/**
	 * Checks the value of the given node
	 * @param node	node with a value
	 * @return		value translated to Jimple-value
	 */
	private Value getValue(JCTree node) {
		node = ignoreNode(node);
		if (node instanceof JCBinary)
			return getBinary((JCBinary)node);
		if (node instanceof JCIdent) 
			return getLocal((JCIdent) node);
		if (node instanceof JCLiteral)
			return getConstant((JCLiteral) node);
		if (node instanceof JCInstanceOf)
			return getInstanceOf((JCInstanceOf)node);
		if (node instanceof JCTypeCast)
			return getTypeCast((JCTypeCast)node);
		if (node instanceof JCArrayAccess)
			return getArrayAccess((JCArrayAccess)node);
		if (node instanceof JCConditional)
			return getConditional((JCConditional)node);
		if (node instanceof JCMethodInvocation)
			return getMethodInvocation((JCMethodInvocation)node);
		if (node instanceof JCNewClass)
			return getNewClass((JCNewClass)node);
		if (node instanceof JCFieldAccess)
			return getFieldAccess((JCFieldAccess)node);
		if (node instanceof JCUnary) {
			if (isPostfix(node))
				queue.add(node);
			return getUnary((JCUnary)node);	
		}
		else
			throw new AssertionError("Unknown node type " + node.getClass().getSimpleName());
	}

	/**
	 * Translates a method invocation in a matching Jimple invocation (static, virtual, special, interface)
	 * @param node	node containing the method invocation
	 * @return		method invocation as a value
	 */
	private Value getMethodInvocation(JCMethodInvocation node) {
		com.sun.tools.javac.util.List<JCExpression> parameterTree = node.args;
		ArrayList<Value> parameterList = new ArrayList<>();
		while (parameterTree.head != null) {												//Build the list of parameter
			Value val=checkForExprChain(getValue(parameterTree.head));
			parameterList.add(val);
			parameterTree = parameterTree.tail;
		}		
		List<Type> parameterTypes = new ArrayList<>();
		for (int i=0; i<parameterList.size(); i++)
			parameterTypes.add(parameterList.get(i).getType());								//Build matching list of parameter types to search for a matching method
		Value invoke = null;
		SootMethodRef method = getMethodRef(node.meth, parameterTypes);
		if (method.parameterTypes().size()<parameterTypes.size()) {
			Type type = method.parameterTypes().get(method.parameterTypes().size()-1);		//If the found method accepts unlimited parameter of a type (e.g. "int... a"), build an array
			Type baseType = ((ArrayType)type).baseType;
			Local arrayLoc = localGenerator.generateLocal(type);
			Value newArray = Jimple.v().newNewArrayExpr(baseType, IntConstant.v(1));
			Unit assign = Jimple.v().newAssignStmt(arrayLoc, newArray);
			units.add(assign);
			for (int i = method.parameterTypes().size()-1; i<parameterTypes.size(); i++) {	
				Value rhs;
				if (!(baseType instanceof PrimType) && parameterTypes.get(i) instanceof PrimType) {
					RefType classType = (RefType)JavaUtil.primToClass(parameterTypes.get(i));	//If Object is expected but a primitive type is given, transform it to a matching class (e.g. int->java.lang.Integer)
					rhs=localGenerator.generateLocal(classType);
					List<Type> valueOfList = new ArrayList<>();
					valueOfList.add(parameterTypes.get(i));
					SootMethod valueOfMethod = searchMethod(classType.getSootClass(), "valueOf", valueOfList);
					Value valueOf = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), parameterList.get(i));
					Unit assignValueOf = Jimple.v().newAssignStmt(rhs, valueOf);
					units.add(assignValueOf);
				}
				else
					rhs = parameterList.get(i);
				Value arrayAccess = Jimple.v().newArrayRef(arrayLoc, IntConstant.v(i));
				Unit assignValue = Jimple.v().newAssignStmt(arrayAccess, rhs);
				units.add(assignValue);
			}
			int paraDelete = parameterTypes.size();
			for (int i = method.parameterTypes().size()-1; i<paraDelete; i++) {				//Adjust the parameter and parameter types lists
				parameterList.remove(method.parameterTypes().size()-1);
				parameterTypes.remove(method.parameterTypes().size()-1);
			}
			parameterList.add(arrayLoc);
			parameterTypes.add(arrayLoc.getType());
		}
		if (!method.parameterTypes().equals(parameterTypes)) {								//if parameterized types are used, it needs a value as a class instead of the primitive type
			for (int i=0; i<method.parameterTypes().size(); i++) {							//e.g. ArrayList<Integer> a; a.add(3); => Integer b=Integer.valueOf(3); a.add(b);
				Type methodParameter = method.parameterTypes().get(i);						//Or if Object is expected instead of a primitive type
				Type inputParameter = parameterTypes.get(i);
				if (!methodParameter.equals(inputParameter) && inputParameter instanceof PrimType) {
					Type type = JavaUtil.primToClass(inputParameter);
					Local valueOfLocal = localGenerator.generateLocal(type);
					ArrayList<Type> paras = new ArrayList<>();
					paras.add(inputParameter);
					SootClass clazz = Scene.v().getSootClass(valueOfLocal.getType().toString());
					SootMethod valueOfMethod = clazz.getMethod("valueOf", paras);
					Value staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), parameterList.get(i));
					Unit assign = Jimple.v().newAssignStmt(valueOfLocal, staticInvoke);
					units.add(assign);
					parameterList.remove(i);
					if (parameterList.size() == i)
						parameterList.add(valueOfLocal);
					else
						parameterList.set(i, valueOfLocal);
				}																			//if the method expects an array, but a single value is given
				else if (!methodParameter.equals(inputParameter) && methodParameter instanceof ArrayType) {
					Type type = methodParameter;
					Value array = Jimple.v().newNewArrayExpr(type, IntConstant.v(1));
					Local loc = localGenerator.generateLocal(type);
					Unit assign = Jimple.v().newAssignStmt(loc, array);
					units.add(assign);
					Value arrayAccess = Jimple.v().newArrayRef(loc, IntConstant.v(0));
					Unit assign2 = Jimple.v().newAssignStmt(arrayAccess, parameterList.get(i));
					units.add(assign2);
					parameterList.remove(i);
					if (parameterList.size() == i)
						parameterList.add(loc);
					else
						parameterList.set(i, loc);
				}
			}
		}
	
		if (node.meth instanceof JCIdent) {													//Method in this class
			if (method.isStatic()) {														//Method is static
				invoke = Jimple.v().newStaticInvokeExpr(method, parameterList);
			}
			else {																			//Method is not static
				invoke = Jimple.v().newVirtualInvokeExpr(locals.get("thisLocal"), method, parameterList);
			}
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCIdent) {					//Method in and other class
			if (method.isStatic()) {														//Method is static
				invoke = Jimple.v().newStaticInvokeExpr(method, parameterList);
			}
			else {		
				Value val = getValue(((JCFieldAccess)node.meth).selected);	
				Local loc;
				if (!(val instanceof Local)) {
					loc = localGenerator.generateLocal(val.getType());
					Unit assign = Jimple.v().newAssignStmt(loc, val);
					units.add(assign);
				}
				else
					loc = (Local) val;
				if (((RefType)loc.getType()).getSootClass().isInterface())
					invoke = Jimple.v().newInterfaceInvokeExpr(loc, method, parameterList);	//Method is in interface
				else
					invoke = Jimple.v().newVirtualInvokeExpr(loc, method, parameterList);	//Standard method invocation
			}
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCFieldAccess){				//With field access, e.g. System.out.println
			Local refLocal = getLastRefLocal(method.declaringClass().toString());
			invoke = Jimple.v().newVirtualInvokeExpr(refLocal, method, parameterList);
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCNewClass) {				//anonymous class [constructor with parameter?]
			SootClass clazz = method.declaringClass();
			Local loc = localGenerator.generateLocal(RefType.v(clazz));
			Value newClass = Jimple.v().newNewExpr(RefType.v(clazz));
			Unit assign = Jimple.v().newAssignStmt(loc, newClass);
			units.add(assign);
			SootMethod constructor = clazz.getMethodByName("<init>");
			Value specialInvoke = Jimple.v().newSpecialInvokeExpr(loc, constructor.makeRef());
			Unit constructorInvoke = Jimple.v().newInvokeStmt(specialInvoke);
			units.add(constructorInvoke);
			invoke = Jimple.v().newVirtualInvokeExpr(loc, method, parameterList);
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCLiteral) {				//Method is used on a direct input, e.g. "abc".size();
			Constant cons = getConstant((JCLiteral)((JCFieldAccess)node.meth).selected);
			Type type = cons.getType();
			Local loc = localGenerator.generateLocal(type);
			Unit assign = Jimple.v().newAssignStmt(loc, cons);
			units.add(assign);
			invoke = Jimple.v().newVirtualInvokeExpr(loc, method, parameterList);
		}
		else
		{																					//Chain of invocations
			Value returnValue = getMethodInvocation((JCMethodInvocation)((JCFieldAccess)node.meth).selected);
			Local loc = localGenerator.generateLocal(returnValue.getType());
			Unit saveAssign = Jimple.v().newAssignStmt(loc, returnValue);
			units.add(saveAssign);
			invoke = Jimple.v().newVirtualInvokeExpr(loc, method, parameterList);
		}
		return invoke;
		}

	/**
	 * Translates a binary operation into a corresponding Jimple binary statement
	 * @param node	node containing the binary operation
	 * @return		binary operation in Jimple
	 */
	private Value getBinary(JCBinary node) {
		Value leftValue = checkForExprChain(getValue(node.lhs));
		Value rightValue = checkForExprChain(getValue(node.rhs));
		if (leftValue.getType().toString().equals(JavaUtil.addPackageName("String")) || rightValue.getType().toString().equals(JavaUtil.addPackageName("String"))) {
			RefType stringBuilderRef = RefType.v(JavaUtil.addPackageName("StringBuilder"));
			RefType stringRef = RefType.v(JavaUtil.addPackageName("String"));				//String-addition
			Local stringBuilder = localGenerator.generateLocal(stringBuilderRef);
			Value stringBuilderVal = Jimple.v().newNewExpr(stringBuilderRef);
			Unit assign = Jimple.v().newAssignStmt(stringBuilder, stringBuilderVal);
			units.add(assign);
			Local valueOfLeft = localGenerator.generateLocal(stringRef);
			ArrayList<Type> valueOfPara = new ArrayList<>();
			valueOfPara.add(leftValue.getType());
			SootMethodRef valueofMethod = searchMethod(stringRef.getSootClass(), "valueOf", valueOfPara).makeRef();	
			Value toString = Jimple.v().newStaticInvokeExpr(valueofMethod, leftValue);
			Unit assignString = Jimple.v().newAssignStmt(valueOfLeft, toString);
			units.add(assignString);
			ArrayList<Value> parameter = new ArrayList<>();
			ArrayList<Type> parameterTypes = new ArrayList<>();
			parameter.add(valueOfLeft);
			parameterTypes.add(valueOfLeft.getType());
			SootMethodRef method = Scene.v().makeMethodRef(stringBuilderRef.getSootClass(), "<init>", parameterTypes, VoidType.v(), false);
			Value invoke = Jimple.v().newSpecialInvokeExpr(stringBuilder, method, valueOfLeft);
			Unit specialinvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialinvoke);
			ArrayList<Type> appendTypes = new ArrayList<>();
			appendTypes.add(rightValue.getType());
			SootMethodRef append = Scene.v().makeMethodRef(stringBuilderRef.getSootClass(), "append", appendTypes, stringBuilderRef, false);
			Value appendValue = Jimple.v().newVirtualInvokeExpr(stringBuilder, append, rightValue);
			Local appendLocal = localGenerator.generateLocal(stringBuilderRef);
			Unit assignAppend = Jimple.v().newAssignStmt(appendLocal, appendValue);
			units.add(assignAppend);
			ArrayList<Type> toStringTypes = new  ArrayList<>();
			SootMethodRef returnString = Scene.v().makeMethodRef(stringBuilderRef.getSootClass(), "toString", toStringTypes, stringRef, false);
			Value returnValue = Jimple.v().newVirtualInvokeExpr(appendLocal, returnString);
			return returnValue;
		}
		else {
			String findOperator = node.toString().replace(node.lhs.toString(), "");
			if (findOperator.charAt(1) == '+')
				return Jimple.v().newAddExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '-')
				return Jimple.v().newSubExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '&' && findOperator.charAt(2) == '&') {
				Unit nop = Jimple.v().newNopStmt();											//&& needs to be a chain of ifs that need to be passed
				Value not = Jimple.v().newEqExpr(leftValue, IntConstant.v(0));
				Unit firstIf = Jimple.v().newIfStmt(not, nop);
				units.add(firstIf);
				Value not2 = Jimple.v().newEqExpr(rightValue, IntConstant.v(0));
				Unit secondIf = Jimple.v().newIfStmt(not2, nop);
				units.add(secondIf);
				Local loc = localGenerator.generateLocal(IntType.v());
				Unit assign = Jimple.v().newAssignStmt(loc, IntConstant.v(1));
				units.add(assign);
				Unit nop2 = Jimple.v().newNopStmt();
				Unit gotoStmt = Jimple.v().newGotoStmt(nop2);
				units.add(gotoStmt);
				units.add(nop);
				Unit assign2 = Jimple.v().newAssignStmt(loc, IntConstant.v(0));
				units.add(assign2);
				units.add(nop2);
				return loc;
			}
			else if (findOperator.charAt(1) == '&')
				return Jimple.v().newAndExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '|' && findOperator.charAt(2) == '|') {		//|| needs to be a chain of ifs that jump to the end if one is true
				Unit nop = Jimple.v().newNopStmt();
				Value not = Jimple.v().newEqExpr(leftValue, IntConstant.v(1));
				Unit firstIf = Jimple.v().newIfStmt(not, nop);
				units.add(firstIf);
				Unit nop2 = Jimple.v().newNopStmt();
				Value not2 = Jimple.v().newEqExpr(rightValue, IntConstant.v(0));
				Unit secondIf = Jimple.v().newIfStmt(not2, nop2);
				units.add(secondIf);
				units.add(nop);
				Local loc = localGenerator.generateLocal(IntType.v());
				Unit assign = Jimple.v().newAssignStmt(loc, IntConstant.v(1));
				units.add(assign);
				Unit nop3 = Jimple.v().newNopStmt();
				Unit gotoStmt = Jimple.v().newGotoStmt(nop3);
				units.add(gotoStmt);
				units.add(nop2);
				Unit assign2 = Jimple.v().newAssignStmt(loc, IntConstant.v(0));
				units.add(assign2);
				units.add(nop3);
				return loc;
			}
			else if (findOperator.charAt(1) == '|')
				return Jimple.v().newOrExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '*')
				return Jimple.v().newMulExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '/')
				return Jimple.v().newDivExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '%')
				return Jimple.v().newRemExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '^')
				return Jimple.v().newXorExpr(leftValue, rightValue);
			else if (findOperator.charAt(3) == '>' && findOperator.charAt(2) == '>' && findOperator.charAt(1) == '>')
				return Jimple.v().newUshrExpr(leftValue, rightValue);
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '>')
				return Jimple.v().newGeExpr(leftValue, rightValue);
			else if (findOperator.charAt(2) == '>' && findOperator.charAt(1) == '>')
				return Jimple.v().newShrExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '>')
				return Jimple.v().newGtExpr(leftValue, rightValue);
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '<')
				return Jimple.v().newLeExpr(leftValue, rightValue);
			else if (findOperator.charAt(2) == '<' && findOperator.charAt(1) == '<')
				return Jimple.v().newShlExpr(leftValue, rightValue);
			else if (findOperator.charAt(1) == '<')
				return Jimple.v().newLtExpr(leftValue, rightValue);
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '=') {		//== needs to be an if that returns 1 or 0
				Local loc = localGenerator.generateLocal(IntType.v());
				Value con = Jimple.v().newEqExpr(leftValue, rightValue);
				Unit nopTrue = Jimple.v().newNopStmt();
				Unit ifStmt = Jimple.v().newIfStmt(con, nopTrue);
				units.add(ifStmt);
				Unit assignFalse = Jimple.v().newAssignStmt(loc, IntConstant.v(0));
				units.add(assignFalse);
				Unit nopFalse = Jimple.v().newNopStmt();
				Unit gotoFalse = Jimple.v().newGotoStmt(nopFalse);
				units.add(gotoFalse);
				units.add(nopTrue);
				Unit assignTrue = Jimple.v().newAssignStmt(loc, IntConstant.v(1));
				units.add(assignTrue);
				units.add(nopFalse);
				return loc;
			}
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '!') {		//!= needs to be an if, similar to ==
				Local loc = localGenerator.generateLocal(IntType.v());
				Value con = Jimple.v().newNeExpr(leftValue, rightValue);
				Unit nopTrue = Jimple.v().newNopStmt();
				Unit ifStmt = Jimple.v().newIfStmt(con, nopTrue);
				units.add(ifStmt);
				Unit assignFalse = Jimple.v().newAssignStmt(loc, IntConstant.v(0));
				units.add(assignFalse);
				Unit nopFalse = Jimple.v().newNopStmt();
				Unit gotoFalse = Jimple.v().newGotoStmt(nopFalse);
				units.add(gotoFalse);
				units.add(nopTrue);
				Unit assignTrue = Jimple.v().newAssignStmt(loc, IntConstant.v(1));
				units.add(assignTrue);
				units.add(nopFalse);
				return loc;
			}
			else
				throw new AssertionError("Unknown binary operation in " + node.toString());
		}
	}
	
	/**
	 * Translates a unary operation into a binary Jimple statement
	 * @param node	node containing the unary operation
	 * @return		corresponding binary operation in Jimple
	 */
	private Value getUnary(JCUnary node) {
		JCTree treeNode = ignoreNode(node.arg);
		Value value = checkForExprChain(getValue(treeNode));
		String findOperator = node.toString();
		if (findOperator.charAt(0) == '!') {												//!a needs to be if with a==0
			Local loc = localGenerator.generateLocal(IntType.v());
			Value con = Jimple.v().newEqExpr(value, IntConstant.v(0));
			Unit nopTrue = Jimple.v().newNopStmt();
			Unit ifStmt = Jimple.v().newIfStmt(con, nopTrue);
			units.add(ifStmt);
			Unit assignFalse = Jimple.v().newAssignStmt(loc, IntConstant.v(0));
			units.add(assignFalse);
			Unit nopFalse = Jimple.v().newNopStmt();
			Unit gotoFalse = Jimple.v().newGotoStmt(nopFalse);
			units.add(gotoFalse);
			units.add(nopTrue);
			Unit assignTrue = Jimple.v().newAssignStmt(loc, IntConstant.v(1));
			units.add(assignTrue);
			units.add(nopFalse);
			return loc;
		}
		if (findOperator.charAt(0) == '~')
			return Jimple.v().newXorExpr(value, IntConstant.v(-1));
		if (findOperator.charAt(0) == '+' && findOperator.charAt(1) == '+') {
			Unit increase = Jimple.v().newAssignStmt(value, Jimple.v().newAddExpr(value, IntConstant.v(1)));
			units.add(increase);
			return value;
		}
		if (findOperator.charAt(0) == '-' && findOperator.charAt(1) == '-') {
			Unit increase = Jimple.v().newAssignStmt(value, Jimple.v().newSubExpr(value, IntConstant.v(1)));
			units.add(increase);
			return value;
		}
		if ((findOperator.charAt(findOperator.length()-2) == '+' && findOperator.charAt(findOperator.length()-1) == '+' ) 
					|| (findOperator.charAt(findOperator.length()-2) == '-' && findOperator.charAt(findOperator.length()-1) == '-'))
			return value;																	//a++ needs a=a+1 after the current unit, saved in queue via "getValue"
		else
			throw new AssertionError("Unknown unary operation in " + node.toString());
	}	
	
	/**
	 * Translate an instance of-expression into a corresponding Jimple statement
	 * @param node	node containing the instance of-expression
	 * @return		equal Jimple instance of-expression
	 */
	private Value getInstanceOf(JCInstanceOf node) {
		Value val = checkForExprChain(getValue(node.expr));
		Type type = JavaUtil.getType(node.clazz, deps, thisMethod.getDeclaringClass());
		Value instance = Jimple.v().newInstanceOfExpr(val, type);
		Value local = localGenerator.generateLocal(instance.getType());
		Unit assign = Jimple.v().newAssignStmt(local, instance);
		units.add(assign);
		Value returnInstOf = Jimple.v().newEqExpr(local, IntConstant.v(1));
		return returnInstOf;
	}
	
	/**
	 * Translates a type cast into a corresponding Jimple type cast
	 * @param node	node containing the type cast
	 * @return		type cast in Jimple
	 */
	private Value getTypeCast(JCTypeCast node) {
		Value val = getValue(node.expr);
		Type type = JavaUtil.getType(node.clazz, deps, thisMethod.getDeclaringClass());
		Value typecast = Jimple.v().newCastExpr(val, type);
		return typecast;
	}
	
	/**
	 * Translates an array access into a corresponding Jimple array access
	 * @param node	node containing the array access
	 * @return		the array access as a value
	 */
	private Value getArrayAccess(JCArrayAccess node) {
		Value array = checkForExprChain(getValue(node.indexed));
		Value index = checkForExprChain(getValue(node.index));
		Value arrayAccess = Jimple.v().newArrayRef(array, index);
		return arrayAccess;
	}
	
	/**
	 * Translates a new-expression into a corresponding Jimple new-expression
	 * @param node	node containing the new-expression
	 * @return		new-expression in Jimple
	 */
	private Value getNewClass(JCNewClass node) {
		RefType type = (RefType) JavaUtil.getType(node.clazz, deps, thisMethod.getDeclaringClass());
		Value newClass = Jimple.v().newNewExpr(type);
		queue.add(node);
		return newClass;
	}
	
	/**
	 * Translates a ternary operator (x?x:x) into an if-statement in Jimple
	 * @param node	node containing the ternary term
	 * @return		first if-statement in Jimple
	 */
	private Value getConditional (JCConditional node) {
		JCTree treeNode = ignoreNode(node.cond);
		Value condition = getValue(treeNode);
		Value truePart = getValue(node.truepart);
		Value falsePart = null;
		if (node.falsepart != null)
			falsePart = getValue(node.falsepart);
		Type type;
		if (truePart.getType() instanceof NullType)
			type = falsePart.getType();
		else
			type = truePart.getType();
		Local returnLocal = localGenerator.generateLocal(type);
		Unit nopTrue = Jimple.v().newNopStmt();
		IfStmt ifStmt = Jimple.v().newIfStmt(condition, nopTrue);
		units.add(ifStmt);
		if (node.falsepart != null) {
			Unit assignFalse = Jimple.v().newAssignStmt(returnLocal, falsePart);
			units.add(assignFalse);
		}
		Unit nopEnd = Jimple.v().newNopStmt();
		Unit elseEnd = Jimple.v().newGotoStmt(nopEnd);
		units.add(elseEnd);
		units.add(nopTrue);
		Unit assignTrue = Jimple.v().newAssignStmt(returnLocal, truePart);
		units.add(assignTrue);
		units.add(nopEnd);
		return returnLocal;
	}
	
	/**
	 * Translates number into a jimple-constant
	 * @param node	node containing the value
	 * @return		matching jimple-constant with value
	 */
	private Constant getConstant(JCLiteral node) {
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
	 * Searches for a local from this method or from a field
	 * @param node	node containing the name of the variable
	 * @return		the found local
	 */
	private Value getLocal(JCIdent node) {
		if (locals.containsKey(node.toString()))							//Variable in this method
			return locals.get(node.toString());
		else if (thisMethod.getDeclaringClass().declaresFieldByName(node.toString())) {
				SootField field = thisMethod.getDeclaringClass().getFieldByName(node.toString());
				if (field.isStatic())										//Variable in this class as field, static or non-static
					return Jimple.v().newStaticFieldRef(field.makeRef());
				else
					return Jimple.v().newInstanceFieldRef(locals.get("thisLocal"),field.makeRef());
			}
		else {
			SootClass thisClass = thisMethod.getDeclaringClass();				//Variable in outer class
			if (thisClass.hasOuterClass()) {
				SootClass outerClass = thisClass.getOuterClass();
				if (outerClass.declaresFieldByName(node.toString())) {
					SootField field = outerClass.getFieldByName(node.toString());
					SootField outerClassField = thisClass.getFieldByName("this$0");
					Value outerClassInstance = Jimple.v().newInstanceFieldRef(locals.get("thisLocal"), outerClassField.makeRef());
					Local outerClassLocal = localGenerator.generateLocal(outerClassInstance.getType());
					Unit assign = Jimple.v().newAssignStmt(outerClassLocal, outerClassInstance);
					units.add(assign);
					Value fieldInstance = Jimple.v().newInstanceFieldRef(outerClassLocal, field.makeRef());
					Local fieldLocal = localGenerator.generateLocal(fieldInstance.getType());
					Unit assign2 = Jimple.v().newAssignStmt(fieldLocal, fieldInstance);
					units.add(assign2);
					return fieldLocal;
				}
			}
		}
		throw new AssertionError("Unknown local " + node.toString());
	}
	
	/**
	 * Translates a field access in an other class in Jimple
	 * @param node	node containing the field access
	 * @return		translated field access
	 */
	private Value getFieldAccess(JCFieldAccess node) {
		Value loc;															//Field access via class name, static
		if (JavaUtil.isPackageName((JCIdent)node.selected, deps, thisMethod.getDeclaringClass())) {
			String packageName = JavaUtil.getPackage((JCIdent)node.selected, deps, thisMethod.getDeclaringClass());
			SootClass clazz = Scene.v().getSootClass(packageName);
			SootField field = clazz.getFieldByName(node.name.toString());
			loc = Jimple.v().newStaticFieldRef(field.makeRef());
			return loc;
		}
		else
		{																	//Field access via variable, non-static
			Value val;
			if (node.selected.toString().equals("this"))
				val = locals.get("thisLocal");
			else
				val = getLocal((JCIdent)node.selected);
			if (val.getType() instanceof ArrayType && node.name.toString().equals("length")) {
				loc = Jimple.v().newLengthExpr(val);
			}
			else {
				SootClass clazz = Scene.v().getSootClass(val.getType().toString());
				SootField field=clazz.getFieldByName(node.name.toString());
				loc = Jimple.v().newInstanceFieldRef(val, field.makeRef());
			}
			return loc;
		} 		
	}	
	
	/**
	 * Transforms all parameters into locals. If the method isn't static, adds a this-local
	 * @param parameterTree	list of all parameters
	 */
	private void addParameter(com.sun.tools.javac.util.List<JCVariableDecl> parameterTree) {
		if (!methodTree.mods.toString().contains("static")) {						//If non-static, add a "this" variable
			RefType type = thisMethod.getDeclaringClass().getType();
			Local thisLocal = new JimpleLocal("thisLocal", type);
			Unit thisIdent = Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(type));
			locals.put("thisLocal", thisLocal);
			units.add(thisIdent);
		}
		int parameterCount = 0;
		while(parameterTree.head != null) {											//Create a variable for each parameter
			Type type = JavaUtil.getType(parameterTree.head.vartype, deps, thisMethod.getDeclaringClass());
			Value parameter = Jimple.v().newParameterRef(type, parameterCount++);
			Local paramLocal = new JimpleLocal(parameterTree.head.name.toString(), type);
			Unit assign = Jimple.v().newIdentityStmt(paramLocal, parameter);
			locals.put(paramLocal.getName(), paramLocal);
			units.add(assign);
			parameterTree = parameterTree.tail;
																					//If this is a inner class, add variable for the outer class
			if (thisMethod.getDeclaringClass().hasOuterClass() && paramLocal.getType().equals(RefType.v(thisMethod.getDeclaringClass().getOuterClass()))) {
				SootField field = thisMethod.getDeclaringClass().getFieldByName("this$0");
				Value lhs = Jimple.v().newInstanceFieldRef(locals.get("thisLocal"), field.makeRef());
				Unit assignOuterClass = Jimple.v().newAssignStmt(lhs, paramLocal);
				units.add(assignOuterClass);
			}
		}
		if (thisMethod.getName().equals("<init>")) {								//Call constructor of the super class, if "super" is used, use parameter
			JCTree node = ((JCExpressionStatement)methodBodyTree.head).expr;
			ArrayList<Type> parameterTypes = new ArrayList<>();
			ArrayList<Value> parameter = new ArrayList<>();
			if (node instanceof JCMethodInvocation && ((JCMethodInvocation) node).meth.toString().equals("super")) {
				com.sun.tools.javac.util.List<JCExpression> superTree = ((JCMethodInvocation)node).args;
				while (superTree.head != null) {
					Value val = checkForExprChain(getValue(superTree.head));
					parameter.add(val);
					parameterTypes.add(val.getType());
					superTree=superTree.tail;
				}
			}
			SootMethod method = thisMethod.getDeclaringClass().getSuperclass().getMethod("<init>", parameterTypes);
			Value invoke = Jimple.v().newSpecialInvokeExpr(locals.get("thisLocal"), method.makeRef(), parameter);
			Unit specialinvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialinvoke);
		}
			
	}
	
	/**
	 * Checks if there exists a constructor and if not, generates one in Jimple with a this-variable
	 */
	private void addThisVariable() {
		RefType type = thisMethod.getDeclaringClass().getType();
		Local thisLocal = new JimpleLocal("thisLocal", type);
		Unit thisIdent = Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(type));
		locals.put("thisLocal", thisLocal);										//Add a this-local
		units.add(thisIdent);
		if (thisMethod.getDeclaringClass().isInnerClass() && thisMethod.getDeclaringClass().declaresField("this$0", RefType.v(thisMethod.getDeclaringClass().getOuterClass()))) {
			SootClass outerClass = thisMethod.getDeclaringClass().getOuterClass();
			Value parameter = Jimple.v().newParameterRef(RefType.v(outerClass), 0);
			Local paramLocal = new JimpleLocal("outerLocal", RefType.v(outerClass));
			locals.put(paramLocal.getName(), paramLocal);
			Unit assign = Jimple.v().newIdentityStmt(paramLocal, parameter);
			units.add(assign);
			Value field = Jimple.v().newInstanceFieldRef(locals.get("thisLocal"), thisMethod.getDeclaringClass().getFieldByName("this$0").makeRef());
			Unit assignOuterClass = Jimple.v().newAssignStmt(field, paramLocal);
			units.add(assignOuterClass);
		}
		if (thisMethod.getName().equals("<init>")) {							//Call constructor of the super class
			ArrayList<Type> parameterTypes=new ArrayList<>();
			SootMethod method = thisMethod.getDeclaringClass().getSuperclass().getMethod("<init>", parameterTypes);
			Value invoke = Jimple.v().newSpecialInvokeExpr(thisLocal, method.makeRef());
			Unit specialInvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialInvoke);
		}
		
	}
	
	/**
	 * Makes sure, that every field that gets its value outside of the methods, gets it in every Jimple-constructor.
	 */
	private void getFields() {
		int i=0;
		while (i<fieldList.size()) {
			JCTree node = fieldList.get(i);
			if (((JCVariableDecl)node).mods.toString().contains("static")) {
				i++;
				continue;														//Skip all static fields
			}
			String fieldName = ((JCVariableDecl)node).name.toString();
			SootField field = thisMethod.getDeclaringClass().getFieldByName(fieldName);
			Value loc = Jimple.v().newInstanceFieldRef(locals.get("thisLocal"),field.makeRef());
			Value val = checkForExprChain(getValue(((JCVariableDecl)node).init));
			if (!queue.isEmpty()) {
				newClasslocal = (Local)val;
				JCTree tree = queue.get(0);
				queue.remove(tree);
				getUnit(tree);
			}
			Unit assign = Jimple.v().newAssignStmt(loc, val);
			units.add(assign);
			fieldList.remove(node);
		}
	}
	
	/**
	 * Initialize all static fields
	 */
	private void getStaticFields() {
		int i=0;
		while (i<fieldList.size()) {
			JCTree node = fieldList.get(i);
			if (!((JCVariableDecl)node).mods.toString().contains("static")) {
				i++;															//Skip all non-static fields
				continue;
			}
			String fieldName = ((JCVariableDecl)node).name.toString();
			SootField field = thisMethod.getDeclaringClass().getFieldByName(fieldName);
			Value loc = Jimple.v().newStaticFieldRef(field.makeRef());
			Value val = checkForExprChain(getValue(((JCVariableDecl)node).init));
			if (!queue.isEmpty()) {
				newClasslocal = (Local)val;
				JCTree tree = queue.get(0);
				queue.remove(tree);
				getUnit(tree);
			}
			Unit assign = Jimple.v().newAssignStmt(loc, val);
			units.add(assign);
			fieldList.remove(node);
		}
	}
	
	/**
	 * Creates a method reference in Jimple to an existing method
	 * @param node				node containing a method invocation
	 * @param parameterTypes	list of parameter types
	 * @return					reference to the corresponding method in Jimple
	 */
	private SootMethodRef getMethodRef(JCTree node, List<Type> parameterTypes) {
		SootMethod method = null;
		if (node instanceof JCIdent)
			return searchMethod(thisMethod.getDeclaringClass(), node.toString(), parameterTypes).makeRef();		//Method in this class
		else { 
			JCFieldAccess fieldAccessTree=(JCFieldAccess) node;
			if (fieldAccessTree.selected instanceof JCIdent) {													
				if (JavaUtil.isPackageName((JCIdent)fieldAccessTree.selected,deps, thisMethod.getDeclaringClass())) {	//Method is static and in an other class
					String packageName = JavaUtil.getPackage((JCIdent)fieldAccessTree.selected, deps, thisMethod.getDeclaringClass());
					SootClass clazz = Scene.v().getSootClass(packageName);
					method = searchMethod(clazz, fieldAccessTree.name.toString(),parameterTypes);
				}
				else {
					Value loc = getLocal((JCIdent)fieldAccessTree.selected);										//Method is non-static and in an other class
					String packageName = loc.getType().toString();
					SootClass clazz = Scene.v().getSootClass(packageName);
					method = searchMethod(clazz, fieldAccessTree.name.toString(), parameterTypes);
				}
			}
			else if (fieldAccessTree.selected instanceof JCMethodInvocation){									//Chain of Invocations
				Value access = getMethodInvocation((JCMethodInvocation)fieldAccessTree.selected);
				SootClass clazz;
				if (access.getType() instanceof PrimType || access.getType() instanceof ArrayType)
					clazz = Scene.v().getSootClass(JavaUtil.addPackageName("Object"));
				else
					clazz = Scene.v().getSootClass(access.getType().toString());
				method = searchMethod(clazz, fieldAccessTree.name.toString(), parameterTypes);
			}
			else if (fieldAccessTree.selected instanceof JCFieldAccess){										//Method called on a field
				Local loc = (Local)checkForExprChain(getFieldAccess((JCFieldAccess)fieldAccessTree.selected));
				SootClass clazz;
				if (loc.getType() instanceof PrimType || loc.getType() instanceof ArrayType)
					clazz = Scene.v().getSootClass(JavaUtil.addPackageName("Object"));
				else
					clazz = Scene.v().getSootClass(JavaUtil.addFieldPackageName(loc.getType().toString()));
				method = searchMethod(clazz, fieldAccessTree.name.toString(), parameterTypes);
			}
			else if (fieldAccessTree.selected instanceof JCNewClass){											//Method called on a new, temporary class
				SootClass clazz = addAnonymousClass((JCNewClass)fieldAccessTree.selected);
				method = searchMethod(clazz, fieldAccessTree.name.toString(), parameterTypes);
			}
			else if (fieldAccessTree.selected instanceof JCLiteral) {											//Method called on a direct input, e.g. "abc".length()
				Type type = getConstant((JCLiteral)fieldAccessTree.selected).getType();
				if (type instanceof RefType) {
					SootClass clazz = Scene.v().getSootClass(type.toString());
					method = searchMethod(clazz, fieldAccessTree.name.toString(), parameterTypes);
				}
			}
		}
		if (method != null)
			return method.makeRef();
		else
			throw new AssertionError("Can't find method " + node.toString() + " " + parameterTypes.toString());
	}
	
	/**
	 * Searches for the last added local with a reference to the given class
	 * @param ref	name of the class
	 * @return		last local with reference to class
	 */
	private Local getLastRefLocal(String ref) {					//TODO unsortiert
		Iterator<Local> iter = locals.values().iterator();
		Local ret = null;
		while (iter.hasNext()) {
			Local next = iter.next();
			if (next.getType().toString().equals(ref))
				ret = next;
		}
		return ret;
	}
	
	/**
	 * Translates a method invocation without a return
	 * @param node	node containing the method invocation
	 * @return		A Jimple method invocation as a separate line
	 */
	private Unit addMethodInvocation(JCMethodInvocation node) {
		if (node.meth.toString().equals("super")) {													//"super" is shown as a method invocation but is processed in another method
			Unit nop = Jimple.v().newNopStmt();
			units.add(nop);
			return nop;
		}
		Value invokeExpr = getMethodInvocation(node);
		Unit methodinvoke = Jimple.v().newInvokeStmt(invokeExpr);
		units.add(methodinvoke);
		return methodinvoke;
	}
	
	/**
	 * Translates an if-statement from the AST to Jimple
	 * @param node	node containing information of the if-statement
	 * @return		Corresponding if-statement in Jimple
	 */
	private Unit addIf(JCIf node) {
		JCTree treeNode = ignoreNode(node.cond);
		Value condition = getValue(treeNode);
		if (!(condition instanceof ConditionExpr)) {												//If the test-value isn't a condition, do value==1
			Value bin = checkForExprChain(condition);
			condition = Jimple.v().newEqExpr(bin, IntConstant.v(1));
		}
		Unit nopTrue = Jimple.v().newNopStmt();
		IfStmt ifStmt = Jimple.v().newIfStmt(condition, nopTrue);
		units.add(ifStmt);
		if (node.elsepart != null)
			processBlock(node.elsepart);
		Unit nopEnd = Jimple.v().newNopStmt();
		Unit elseEnd = Jimple.v().newGotoStmt(nopEnd);
		units.add(elseEnd);
		units.add(nopTrue);
		processBlock(node.thenpart);
		units.add(nopEnd);
		return ifStmt;
	}

	/**
	 * Translates a do-while-loop into an if-jump-loop in Jimple
	 * @param node	node containing the do-while-loop
	 * @return		Jimple if-statement for the loop-jump
	 */
	private Unit addDoWhile(JCDoWhileLoop node) {
		Unit nopContinue = Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		Unit nopBreak = Jimple.v().newNopStmt();
		breakLoop.add(nopBreak);
		JCTree treeNode = ignoreNode(node.cond);
		Value condition = getValue(treeNode);
		Unit target = processBlock(node.body);
		units.add(nopContinue);
		IfStmt ifstmt = Jimple.v().newIfStmt(condition, target);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopBreak);
		breakLoop.remove(breakLoop.size()-1);
		return target;
	}
	
	/**
	 * Translates a while-loop into an if-jump-loop in Jimple
	 * @param node	node containing the while-loop
	 * @return		initial Jimple jump-statement to the condition of the loop-jump
	 */
	private Unit addWhile(JCWhileLoop node) {
		Unit nopBreak = Jimple.v().newNopStmt();
		breakLoop.add(nopBreak);
		JCTree treeNode = ignoreNode(node.cond);
		Value condition = getValue(treeNode);
		Unit nop = Jimple.v().newNopStmt();
		loopContinue.add(nop);
		Unit jump = Jimple.v().newGotoStmt(nop);
		units.add(jump);
		Unit target = processBlock(node.body);
		IfStmt ifStmt = Jimple.v().newIfStmt(condition, target);
		units.add(nop);
		units.add(ifStmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopBreak);
		breakLoop.remove(breakLoop.size()-1);
		return jump;
	}
	
	/**
	 * Translates a for-loop into an if-jump-loop with a counter in Jimple
	 * @param node	node containing the for-loop
	 * @return		initial Jimple declaration of the counter
	 */
	private Unit addFor(JCForLoop node) {
		Unit nopBreak = Jimple.v().newNopStmt();
		breakLoop.add(nopBreak);
		Unit nopContinue = Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		Unit counter = getUnit(node.init.head);
		com.sun.tools.javac.util.List<JCStatement> initlist = node.init.tail;
		while (initlist.head != null) {																//Initialize all count variables
			getUnit(initlist.head);	
			initlist = initlist.tail;
		}
		JCTree treeNode = ignoreNode(node.cond);
		Value condition = getValue(treeNode);
		Unit nop = Jimple.v().newNopStmt();
		Unit jump = Jimple.v().newGotoStmt(nop);
		units.add(jump);
		Unit nopBlock = Jimple.v().newNopStmt();
		units.add(nopBlock);
		com.sun.tools.javac.util.List<JCExpressionStatement> steplist = node.step; 
		while (steplist.head != null) {
			if (isPrefix(steplist.head))
					getUnit(steplist.head);															//Process all ++i first
			steplist = steplist.tail;
		}
		processBlock(node.body);																	//Body of the loop
		units.add(nopContinue);	
		steplist = node.step;
		while (steplist.head != null) {
			if (!isPrefix(steplist.head))
					getUnit(steplist.head);															//Process the remaining count variables afterwards
			steplist = steplist.tail;
		}
		units.add(nop);
		IfStmt ifStmt = Jimple.v().newIfStmt(condition, nopBlock);
		units.add(ifStmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopBreak);
		breakLoop.remove(breakLoop.size()-1);
		return counter;
	}
	
	/**
	 * Translates an enhanced for-loop (for-each-loop) into an if-jump-loop with a counter in Jimple
	 * @param node	node containing the enhanced for-loop
	 * @return		length-op-assign unit containing the length of the loop as the initial unit for the loop
	 */
	private Unit addEnhancedFor(JCEnhancedForLoop node) {
		Unit nopBreak = Jimple.v().newNopStmt();
		breakLoop.add(nopBreak);
		Unit nopContinue = Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		Local max = localGenerator.generateLocal(IntType.v());										//Endvalue
		Value length = Jimple.v().newLengthExpr(getValue(node.expr));
		Unit assignMax = Jimple.v().newAssignStmt(max, length);
		units.add(assignMax);
		Local counter = localGenerator.generateLocal(IntType.v());
		Value startValue = IntConstant.v(0);	
		Unit assignStart = Jimple.v().newAssignStmt(counter, startValue);							//Startvalue
		units.add(assignStart);
		Unit nop = Jimple.v().newNopStmt();
		Unit gotoNop = Jimple.v().newGotoStmt(nop);
		units.add(gotoNop);
		Type type = JavaUtil.getType(node.var.vartype, deps, thisMethod.getDeclaringClass());
		Local loopLocal = new JimpleLocal(node.var.name.toString(), type);
		locals.put(loopLocal.getName(), loopLocal);
		Value arrayAccess = Jimple.v().newArrayRef(getValue(node.expr), counter);
		Unit assignLoop = Jimple.v().newAssignStmt(loopLocal, arrayAccess);
		units.add(assignLoop);
		processBlock(node.body);																	//Body of the loop
		units.add(nopContinue);	
		Value add = Jimple.v().newAddExpr(counter, IntConstant.v(1));	
		Unit increase = Jimple.v().newAssignStmt(counter, add);										//Counter
		units.add(increase);
		units.add(nop);
		Value condition = Jimple.v().newLtExpr(counter, max);										//Condition
		IfStmt ifStmt = Jimple.v().newIfStmt(condition, assignLoop);
		units.add(ifStmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopBreak);
		breakLoop.remove(breakLoop.size()-1);
		return assignMax;
	}
	
	/**
	 * Translates a continue-statement into a jump-statement in Jimple
	 * @param node	node containing the continue-statement
	 * @return		Jimple jump-statement to the if from the loop-head
	 */
	private Unit addContinue(JCContinue node) {
		Unit target = loopContinue.get(loopContinue.size()-1);
		Unit jump = Jimple.v().newGotoStmt(target);
		units.add(jump);
		return jump;
	}
	
	/**
	 * Translates a break-statement into a Jimple jump-statement
	 * @param node	node containing the break-statement
	 * @return		jump-statement to end of the loop or switch-case
	 */
	private Unit addBreak(JCBreak node) {
		Unit target = breakLoop.get(breakLoop.size()-1);
		Unit jump = Jimple.v().newGotoStmt(target);
		units.add(jump);
		return jump;
	}
	
	/**
	 * Translates a try-catch-block into a matching Jimple block
	 * @param node	node containing the try-catch-block
	 * @return		first unit of the try-block
	 */
	private Unit addTryCatch(JCTry node) {
		Unit tryUnit = processBlock(node.body);
		Unit trySuccess = Jimple.v().newNopStmt();
		Unit tryGoto = Jimple.v().newGotoStmt(trySuccess);
		units.add(tryGoto);
		com.sun.tools.javac.util.List<JCCatch> catchList = node.catchers;
		while (catchList.head != null) {															//Create catcher
			Unit catchUnit = addCatch((JCCatch)catchList.head);
			String packageName=JavaUtil.getPackage((JCIdent)catchList.head.param.vartype, deps, thisMethod.getDeclaringClass());
			SootClass throwable = Scene.v().getSootClass(packageName);
			Trap trap = Jimple.v().newTrap(throwable, tryUnit, tryGoto, catchUnit);
			traps.add(trap);
			catchList = catchList.tail;
		}
		Unit finallyNop = null;
		if (node.finalizer != null) {																//Finally block
			processBlock(node.finalizer);
			finallyNop = Jimple.v().newNopStmt();
			Unit finallygoto = Jimple.v().newGotoStmt(finallyNop);
			units.add(finallygoto);
			catchList = node.catchers;
			while (catchList.head != null) {														//Catcher for finally block
				Type type=JavaUtil.getType(catchList.head.param.vartype, deps, thisMethod.getDeclaringClass());
				Value var = localGenerator.generateLocal(type);
				Unit catchUnit = Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
				units.add(catchUnit);
				processBlock(node.finalizer);
				Unit throwStmt = Jimple.v().newThrowStmt(var);
				units.add(throwStmt);
				String packageName=JavaUtil.getPackage((JCIdent)catchList.head.param.vartype, deps, thisMethod.getDeclaringClass());
				SootClass throwable = Scene.v().getSootClass(packageName);
				Trap trap = Jimple.v().newTrap(throwable, tryUnit, finallygoto, catchUnit);
				traps.add(trap);
				catchList = catchList.tail;
			}
		}
		units.add(trySuccess);
		if (node.finalizer != null) {
			processBlock(node.finalizer);
			units.add(finallyNop);
		}
		return tryUnit;
	}
	
	/**
	 * Utility-function to add catch-exceptions
	 * @param node	node containing the catch-block
	 * @return		assign-statement of the exception
	 */
	private Unit addCatch(JCCatch node) {
		Type type=JavaUtil.getType(node.param.vartype, deps, thisMethod.getDeclaringClass());
	//	Value var = localGenerator.generateLocal(type);													//TODO no param-var?
		Local var=new JimpleLocal(node.param.name.toString(), type);
		locals.put(var.toString(), var);
		Unit assign = Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
		units.add(assign);
		processBlock(node.body);
		return assign;
	}
	
	/**
	 * Translates a throw statement into Jimple
	 * @param node	node containing the throw statement
	 * @return		throw statement in Jimple
	 */
	private Unit addThrow (JCThrow node) {
		Value val=getValue(node.expr);
		if (node.expr instanceof JCNewClass) {
			Local loc=localGenerator.generateLocal(val.getType());
			Unit assign=Jimple.v().newAssignStmt(loc, val);
			units.add(assign);
			newClasslocal=loc;
			addConstructorInvocation((JCNewClass)node.expr);
			Unit thrower=Jimple.v().newThrowStmt(loc);
			units.add(thrower);
			return thrower;
		}
		else {
			Unit thrower=Jimple.v().newThrowStmt(val);
			units.add(thrower);
			return thrower;
		}
	}
	
	/**
	 * Translates a synchronized-block into a try-catch-like block with monitor-statements
	 * (It's important that the monitor-exit statement always triggers, even if an exception is thrown)
	 * @param node	node containing the synchronized-block
	 * @return		enter monitor statement
	 */
	private Unit addSynchronized(JCSynchronized node) {
		Unit enterMonitor = Jimple.v().newEnterMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(enterMonitor);
		Unit start = processBlock(node.body);
		Unit exitMonitor = Jimple.v().newExitMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(exitMonitor);
		Unit gotoNop = Jimple.v().newNopStmt();
		Unit gotoStmt = Jimple.v().newGotoStmt(gotoNop);
		units.add(gotoStmt);
		Value var = localGenerator.generateLocal(RefType.v(JavaUtil.addPackageName("Throwable")));
		Unit catchUnit = Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
		units.add(catchUnit);
		Unit exitMonitor2 = Jimple.v().newExitMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(exitMonitor2);
		Unit endThrow = Jimple.v().newNopStmt();
		units.add(endThrow);
		SootClass throwable = Scene.v().getSootClass(JavaUtil.addPackageName("Throwable"));
		Trap trap = Jimple.v().newTrap(throwable, start, gotoStmt, catchUnit);
		traps.add(trap);
		Trap trap2 = Jimple.v().newTrap(throwable, catchUnit, endThrow, catchUnit);
		traps.add(trap2);
		Unit throwStmt = Jimple.v().newThrowStmt(var);
		units.add(throwStmt);
		units.add(gotoNop);
		return enterMonitor;
		
	}
	
	/**
	 * Translates a new-statement with parameters into a special-invoke-statement
	 * @param node	node containing the new-class-statement
	 * @return		a single special-invoke-statement
	 */
	private Unit addConstructorInvocation(JCNewClass node) {
		com.sun.tools.javac.util.List<JCExpression> parameterTree = node.args;
		ArrayList<Value> parameter = new ArrayList<>();
		while (parameterTree.head != null) {								//Parameter
			parameter.add(checkForExprChain(getValue(parameterTree.head)));
			parameterTree = parameterTree.tail;
		}		
		List<Type> parameterTypes = new ArrayList<>();
		for (int i=0; i<parameter.size(); i++)
			parameterTypes.add(parameter.get(i).getType());					//Parameter types to search matching constructor
		SootClass clazz = Scene.v().getSootClass(newClasslocal.getType().toString());
		SootMethodRef method = Scene.v().makeMethodRef(clazz, "<init>", parameterTypes, VoidType.v(), false);
		Value invoke = Jimple.v().newSpecialInvokeExpr(newClasslocal, method, parameter);
		Unit specialInvoke = Jimple.v().newInvokeStmt(invoke);
		if (node.clazz instanceof JCTypeApply) {
			com.sun.tools.javac.util.List<JCExpression> taglist = ((JCTypeApply)node.clazz).arguments;
			while (taglist.head!=null) {									//TODO tags for parameterized types
				taglist=taglist.tail;
			}
		}
		units.add(specialInvoke);
		return specialInvoke;
	}

	/**
	 * Translates a switch-case into a lookup-switch with jumps to each block after the comparison
	 * @param node	node containing the switch-case-block
	 * @return		lookup-switch-block
	 */
	private Unit addSwitch(JCSwitch node) {
		Unit nopBreak = Jimple.v().newNopStmt();
		breakLoop.add(nopBreak);
		Value key = getValue(node.selector);
		Unit defaultTarget = null;
		com.sun.tools.javac.util.List<JCCase> cases = node.cases;
		List<Unit> targets = new ArrayList<>();
		List<IntConstant> lookupValues = new ArrayList<>();
		while (cases.head != null) {											//Cases
			if (cases.head.pat != null) {
				lookupValues.add((IntConstant) getConstant((JCLiteral)cases.head.pat));
				Unit nop = Jimple.v().newNopStmt();
				targets.add(nop);
			}
			else 
				defaultTarget = Jimple.v().newNopStmt();
			cases = cases.tail;
		}		
		Unit lookupSwitch = Jimple.v().newLookupSwitchStmt(key, lookupValues, targets, defaultTarget);
		units.add(lookupSwitch);
		cases = node.cases;
		while (!targets.isEmpty()) {
			units.add(targets.get(0));
			getNextNode(cases.head.stats);
			targets.remove(0);
			cases = cases.tail;
		}
		if (cases.head.pat == null) {
			units.add(defaultTarget);											//Default
			getNextNode(cases.head.stats);
		}
		units.add(nopBreak);
		breakLoop.remove(breakLoop.size()-1);
		return lookupSwitch;
	}
	
	/**
	 * Translates return-statement into a corresponding  return-statement in Jimple
	 * @param node	node containing information of the return-statement
	 * @return		return-statement in Jimple
	 */
	private Unit addReturn (JCReturn node) {
		Unit returnStmt;
		if (node.expr!=null) {
			Value value = checkForExprChain(getValue(node.expr));
			returnStmt = Jimple.v().newReturnStmt(value);
		}
		else
			returnStmt=Jimple.v().newReturnVoidStmt();
		units.add(returnStmt);
		return returnStmt;
	}
	
	/**
	 * Translates an assign-statement into a corresponding assign-statement in Jimple
	 * @param node	node containing information for the assign-statement
	 * @return 		assign-statement in Jimple
	 */
	private Unit addAssign(JCAssign node) {
		Value var = getValue(node.lhs);
		Value right = getValue(node.rhs);
		if (!(var instanceof Local))
			right = checkForExprChain(right);
		Unit assign = Jimple.v().newAssignStmt(var, right);
		units.add(assign);
		if (node.rhs instanceof JCNewClass)
			newClasslocal = (Local)var;
		return assign;
	}

	/**
	 * Translates a variable-declaration into corresponding declaration in Jimple
	 * @param node	node containing information for the variable declaration
	 * @return 		if variable is assigned instantly, returns assign-statement, else returns null
	 */
	private Unit addVariableDecl(JCVariableDecl node) {
		if (node.vartype instanceof JCArrayTypeTree) {												//Array
			Type arrayType=JavaUtil.getType(node.vartype, deps, thisMethod.getDeclaringClass());
			Local array = new JimpleLocal(node.name.toString(), arrayType);
			Type type;
			Value arrayValue;
			if (node.init instanceof JCNewArray) {													//Values as input, e.g. int[] a={1,2,3};
				if (((JCNewArray)node.init).elems != null) {
					if (((JCNewArray)node.init).elems.head instanceof JCLiteral)
						type = getConstant((JCLiteral)((JCNewArray)node.init).elems.head).getType();
					else
						type = locals.get(((JCNewArray)node.init).elems.head.toString()).getType();
					int i=0;
					com.sun.tools.javac.util.List<JCExpression> list = ((JCNewArray)node.init).elems;
					while (list.head != null) {
						i++;
						list = list.tail;
					}
					arrayValue = Jimple.v().newNewArrayExpr(type, IntConstant.v(i));
				}
				else {
					type = JavaUtil.getType(((JCNewArray)node.init).elemtype, deps, thisMethod.getDeclaringClass());
					Value val=getValue(((JCNewArray)node.init).dims.head);
					arrayValue = Jimple.v().newNewArrayExpr(type, val);
				}
			}
			else
				arrayValue = getValue(node.init);
			Unit assign = Jimple.v().newAssignStmt(array, arrayValue);
			locals.put(array.getName(), array);
			units.add(assign);
			if (node.init instanceof JCNewArray && ((JCNewArray)node.init).elems != null) {
				int i=0;
				com.sun.tools.javac.util.List<JCExpression> directValueList = ((JCNewArray)node.init).elems;
				while (directValueList.head != null) {
					Value arrayAccess = Jimple.v().newArrayRef(array, IntConstant.v(i));
					Value rhs = checkForExprChain(getValue(directValueList.head));
					Unit assignValue = Jimple.v().newAssignStmt(arrayAccess, rhs);
					units.add(assignValue);
					directValueList = directValueList.tail;
					i++;
				}
			}
			return assign;
		}
		else {	
			Type type;
			Value con = null;																	//If initialized, use type from right side, else what declaration says
			if (node.init != null && node.init instanceof JCNewClass) {									//New class variable
				type = JavaUtil.getType(((JCNewClass)node.init).clazz, deps, thisMethod.getDeclaringClass());
				con = getValue(node.init);
			}
			else if (node.init != null) {
				type = JavaUtil.getType(node.vartype, deps, thisMethod.getDeclaringClass());			//Other initialized variable
				con = getValue(node.init);
			}
			else
				type = JavaUtil.getType(node.vartype, deps, thisMethod.getDeclaringClass());			//Uninitialized variable
			Local newLocal = new JimpleLocal(node.name.toString(), type);
			locals.put(newLocal.getName(), newLocal);
			if (con != null) {
				Unit assign = Jimple.v().newAssignStmt(newLocal, con);
				units.add(assign);
				if (node.init instanceof JCNewClass)
					newClasslocal = newLocal;
				return assign;
			}
		}
		Unit nop = Jimple.v().newNopStmt();
		units.add(nop);
		return nop;
	}
	
	/**
	 * Translates an unary operation as a single Jimple statement
	 * @param node	node containing the unary operation
	 * @return		a binary assign-statement in Jimple matching the unary operation
	 */
	private Unit addUnary (JCUnary node) {
		JCTree treeNode = ignoreNode(node.arg);
		Value value = checkForExprChain(getValue(treeNode));
		String findOperator = node.toString();
		if ((findOperator.charAt(0) == '+' && findOperator.charAt(1) == '+' ) 
				|| (findOperator.charAt(findOperator.length()-2) == '+' && findOperator.charAt(findOperator.length()-1) == '+')) {
			Value add=Jimple.v().newAddExpr(value, IntConstant.v(1));
			Unit increase = Jimple.v().newAssignStmt(value, add);
			units.add(increase);
			return increase;
		}
		if ((findOperator.charAt(0) == '-' && findOperator.charAt(1) == '-' ) 
				|| (findOperator.charAt(findOperator.length()-2) == '-' && findOperator.charAt(findOperator.length()-1) == '-')) {
			Value sub= Jimple.v().newSubExpr(value, IntConstant.v(1));
			Unit decrease = Jimple.v().newAssignStmt(value, sub);
			units.add(decrease);
			return decrease;
		}
		throw new AssertionError("Unknown unary operation in " + node.toString());
	}
	
	
	/**
	 * Breaks combined operations into a binary expression and an assign
	 * @param node	node containing a variable, an operation and one other value
	 * @return		combined operation as normal assign-statement
	 */
	private Unit addAssignOp(JCAssignOp node) {
		Local var = locals.get(((JCIdent)node.lhs).toString());
		Value binary;
		Value right = checkForExprChain(getValue(node.rhs));
		
		if (var.getType().toString().equals(JavaUtil.addPackageName("String")) || right.getType().toString().equals(JavaUtil.addPackageName("String"))) {
			RefType stringBuilderRef = RefType.v(JavaUtil.addPackageName("StringBuilder"));			//String addition
			RefType stringRef = RefType.v(JavaUtil.addPackageName("String"));
			Local stringBuilder = localGenerator.generateLocal(stringBuilderRef);
			Value stringBuilderVal = Jimple.v().newNewExpr(stringBuilderRef);
			Unit assign = Jimple.v().newAssignStmt(stringBuilder, stringBuilderVal);
			units.add(assign);	ArrayList<Value> parameter = new ArrayList<>();
			ArrayList<Type> parameterTypes = new ArrayList<>();
			parameter.add(var);
			parameterTypes.add(var.getType());
			SootMethodRef method = Scene.v().makeMethodRef(stringBuilderRef.getSootClass(), "<init>", parameterTypes, VoidType.v(), false);
			Value invoke = Jimple.v().newSpecialInvokeExpr(stringBuilder, method, var);
			Unit specialInvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialInvoke);
			ArrayList<Type> appendTypes = new ArrayList<>();
			appendTypes.add(right.getType());
			SootMethodRef append = Scene.v().makeMethodRef(stringBuilderRef.getSootClass(), "append", appendTypes, stringBuilderRef, false);
			Value appendValue = Jimple.v().newVirtualInvokeExpr(stringBuilder, append, right);
			Local appendLocal = localGenerator.generateLocal(stringBuilderRef);
			Unit assignAppend = Jimple.v().newAssignStmt(appendLocal, appendValue);
			units.add(assignAppend);
			ArrayList<Type> toStringTypes = new  ArrayList<>();
			SootMethodRef returnString = Scene.v().makeMethodRef(stringBuilderRef.getSootClass(), "toString", toStringTypes, stringRef, false);
			binary = Jimple.v().newVirtualInvokeExpr(appendLocal, returnString);
		}
		else {
			String findOperator = node.toString().replace(node.lhs.toString(), "");
			if (findOperator.charAt(1) == '+')
				binary = Jimple.v().newAddExpr(var, right);
			else if (findOperator.charAt(1) == '-')
				binary = Jimple.v().newSubExpr(var, right);
			else if (findOperator.charAt(1) == '&')
				binary = Jimple.v().newAndExpr(var, right);
			else if (findOperator.charAt(1) == '|')
				binary = Jimple.v().newOrExpr(var, right);
			else if (findOperator.charAt(1) == '*')
				binary = Jimple.v().newMulExpr(var, right);
			else if (findOperator.charAt(1) == '/')
				binary = Jimple.v().newDivExpr(var, right);
			else if (findOperator.charAt(1) == '%')
				binary = Jimple.v().newRemExpr(var, right);
			else if (findOperator.charAt(1) == '^')
				binary = Jimple.v().newXorExpr(var, right);
			else if (findOperator.charAt(3) == '>' && findOperator.charAt(2) == '>' && findOperator.charAt(1) == '>')
				binary = Jimple.v().newUshrExpr(var, right);
			else if (findOperator.charAt(2) == '>' && findOperator.charAt(1) == '>')
				binary = Jimple.v().newShrExpr(var, right);
			else if (findOperator.charAt(2) == '<' && findOperator.charAt(1) == '<')
				binary = Jimple.v().newShlExpr(var, right);
			else
				throw new AssertionError("Unknown assign operation in " + node.toString());
			}
		Unit assign = Jimple.v().newAssignStmt(var, binary);
		units.add(assign);
		return assign;
		
	}

	
	/**
	 * Adds an anonymous class as an inner class
	 * @param node	node containing the anonymous class
	 * @return		the created soot class
	 */
	private SootClass addAnonymousClass(JCNewClass node) {
		SootClass innerClass=new SootClass(thisMethod.getDeclaringClass().getName()+"$"+(anonymousClassNumber++));
		SootClass sc=thisMethod.getDeclaringClass();
		innerClass.addTag(sc.getTag("SourceFileTag"));
		OuterClassTag outerTag=new OuterClassTag(sc, sc.getName(), true);
		innerClass.addTag(outerTag);
		InnerClassTag innerTag=new InnerClassTag(innerClass.getName(), sc.getName(), innerClass.getShortName(), 0);
		sc.addTag(innerTag);
		Scene.v().addClass(innerClass);
		Scene.v().getApplicationClasses().add(innerClass);
		innerClass.setOuterClass(sc);
		String packageName=JavaUtil.getPackage((JCIdent)node.clazz, deps, thisMethod.getDeclaringClass());
		SootClass superClass=Scene.v().getSootClass(packageName);
		innerClass.setSuperclass(superClass); 
		com.sun.tools.javac.util.List<JCTree> list = ((JCClassDecl) node.def).defs;
		ArrayList<JCTree> newFieldList = new ArrayList<JCTree>();
		while (list.head != null) {																//Add all methods in this class
			JavaUtil.getHead(list.head, deps, innerClass, newFieldList);
			list = list.tail;
		}
		List<Type> parameterTypes = new ArrayList<>();
		if (!innerClass.declaresMethod("<init>", parameterTypes)) {								//Constructor for anonymous class
			String methodName="<init>";
			Type returnType = VoidType.v();
			SootMethod newMethod=new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC);
			innerClass.addMethod(newMethod);
			newMethod.setSource(new JavaMethodSource(deps, newFieldList));
		}
		return innerClass;
	}

	
	/**
	 * Checks, if the value is a binary operation. 
	 * If yes, creates a new Jimple-local to save the interim result
	 * @param val	value to check
	 * @return		the new Jimple-local or the value from the parameter
	 */
	private Value checkForExprChain(Value val) {
		if (val instanceof BinopExpr || val instanceof CastExpr || val instanceof InstanceOfExpr || val instanceof ArrayRef 
				|| val instanceof InvokeExpr || val instanceof NewExpr || val instanceof FieldRef || val instanceof LengthExpr
				|| val instanceof NewArrayExpr) {
			Type type = val.getType();
			Local newLocal = localGenerator.generateLocal(type);
			locals.put(newLocal.getName(), newLocal);
			Unit assign = Jimple.v().newAssignStmt(newLocal, val);
			units.add(assign);
			return newLocal;
		}		
		return val;
	}
	

	
	/**
	 * If the node is a block, transforms it into single statements or else returns its head
	 * @param node	node containing the block or a single statement
	 * @return		either the first statement of the block or the single statement
	 */
	private Unit processBlock(JCTree node) {
		if (node instanceof JCBlock) {
			if (((JCBlock) node).stats.head != null)
				return getNextNode(((JCBlock) node).stats);
			else
			{
				Unit nop = Jimple.v().newNopStmt();
				units.add(nop);
				return nop;
			}
		}
		else
			return getUnit(node);
	}
	

	
	/**
	 * Searches for a matching method, considers all superclasses and interfaces
	 * @param klass				the base class, where either the class itself or its superclass contains the method
	 * @param methodname		name of the wanted method
	 * @param givenParas	types of the parameter, which can contain superclasses and interfaces
	 * @return					the found method
	 */
	private SootMethod searchMethod(SootClass klass, String methodname, List<Type> givenParas) {
		if (klass.declaresMethod(methodname, givenParas))		//class itself has the method with matching parameters
			return klass.getMethod(methodname, givenParas);
		else 
		{
			SootClass currentClass = klass;
			while (currentClass.hasSuperclass() && !currentClass.declaresMethod(methodname, givenParas))
				currentClass = currentClass.getSuperclass();
			if (currentClass.declaresMethod(methodname, givenParas))		//go through all superclasses with exact parameter types
				return currentClass.getMethod(methodname, givenParas);
			else
			{
				currentClass = klass;
				List<SootMethod> methodList = currentClass.getMethods();			//search in class itself with supertypes/interfaces of parameter
				for (int j=0; j<methodList.size(); j++) {
					if (methodList.get(j).getName().equals(methodname)) {
						SootMethod foundMethod = methodList.get(j);
						List<Type> foundMethodParas = foundMethod.getParameterTypes();
						boolean matches = false;
						if (foundMethodParas.size() == givenParas.size()) {
								matches = true;
								for (int i=0; i<foundMethodParas.size(); i++) {
									Type foundType=foundMethodParas.get(i);
									Type givenType=givenParas.get(i);
									if (foundType instanceof PrimType)					
										if (foundType instanceof CharType || foundType instanceof BooleanType)				//if a char or boolean is expected, integer is also allowed
											matches = matches && ((givenType instanceof IntType)||givenType.equals(foundType));
										else
											matches = matches && givenType.equals(foundType);
									else
									{
										if (foundType instanceof RefType) {
											if (givenType instanceof RefType) {
												RefType rt = (RefType)givenType;
												Chain<SootClass> interfaces = rt.getSootClass().getInterfaces();
												boolean inInterface=false;													//go through all superclasses of the given types
												while ((!rt.equals(foundType)) && rt.getSootClass().hasSuperclass() &&!inInterface) {
													inInterface=isInInterfaces(interfaces,((RefType)foundType).getSootClass());	//check if an interface matches the type
													rt = rt.getSootClass().getSuperclass().getType();
													interfaces = rt.getSootClass().getInterfaces();
												}
												matches = matches && (rt.equals(foundType) || inInterface);
											}
											else
												matches = false;
										}
										else
										{
											if (foundType instanceof ArrayType) {
												if (givenType instanceof ArrayType) {					//if both are arrays
													Type at = ((ArrayType) givenType).baseType;
													if (at instanceof RefType && ((ArrayType)foundType).baseType instanceof RefType) {	//if base is a ref type
														Chain<SootClass> interfaces = ((RefType)at).getSootClass().getInterfaces();
														boolean inInterface=false;
														while ((!at.equals(foundType)) && ((RefType)at).getSootClass().hasSuperclass() &&!inInterface) {
															inInterface=isInInterfaces(interfaces,((RefType)((ArrayType)foundType).baseType).getSootClass());
															at = ((RefType)at).getSootClass().getSuperclass().getType();
															interfaces = ((RefType)at).getSootClass().getInterfaces();
														}
														matches = matches && (at.equals(foundType) || inInterface);
													}
													else if (at instanceof PrimType && ((ArrayType)foundType).baseType instanceof PrimType) {	//if base is a primitive type
														matches = matches && (at.equals(((ArrayType)foundType).baseType));
													}
												}
												else if (givenType instanceof RefType && ((ArrayType)foundType).baseType instanceof RefType){	//if an array is expected, but only a ref type is given
													Type at = givenType;
													Chain<SootClass> interfaces = ((RefType)at).getSootClass().getInterfaces();
													boolean inInterface = false;
													while ((!at.equals(((ArrayType)foundType).baseType)) && ((RefType)at).getSootClass().hasSuperclass() &&!inInterface) {
														inInterface = isInInterfaces(interfaces,((RefType)((ArrayType)foundType).baseType).getSootClass());
														at = ((RefType)at).getSootClass().getSuperclass().getType();
														interfaces = ((RefType)at).getSootClass().getInterfaces();
													}
													matches = matches && (at.equals(((ArrayType)foundType).baseType) || inInterface);
												}
												else																				//if an array is expected, but only a primitive type is given
													matches = matches && (((ArrayType)foundType).baseType.equals(givenType));
											}
										}
							
									}
								}
								if (matches)
									return foundMethod;
						}
					}
				}
				while (currentClass.hasSuperclass()) {				//searches in superclasses for method with supertypes/interfaces of parameter
					currentClass = currentClass.getSuperclass();
					methodList = currentClass.getMethods();
					for (int j=0; j<methodList.size(); j++) {
						if (methodList.get(j).getName().equals(methodname)) {
							SootMethod foundMethod = methodList.get(j);
							List<Type> foundMethodParas = foundMethod.getParameterTypes();
							boolean matches = false;
							if (foundMethodParas.size() == givenParas.size()) {
								matches = true;
								for (int i=0; i<foundMethodParas.size(); i++) {
									Type foundType = foundMethodParas.get(i);
									Type givenType = givenParas.get(i);
									if (foundType instanceof PrimType)
										if (foundType instanceof CharType || foundType instanceof BooleanType)
											matches = matches && ((givenType instanceof IntType)||givenType.equals(foundType));
										else
											matches = matches && givenType.equals(foundType);
									else
									{
										if (foundType instanceof RefType) {
											if (givenType instanceof RefType) {
												RefType rt = (RefType)givenType;
												Chain<SootClass> interfaces = rt.getSootClass().getInterfaces();
												boolean inInterface=false;
												while ((!rt.equals(foundType)) && rt.getSootClass().hasSuperclass() &&!inInterface) {
													inInterface = isInInterfaces(interfaces,((RefType)foundType).getSootClass());
													rt = rt.getSootClass().getSuperclass().getType();
													interfaces = rt.getSootClass().getInterfaces();
												}
												matches = matches && (rt.equals(foundType) || inInterface);
											}
											else
												matches = false;
										}
										else
										{
											if (foundType instanceof ArrayType) {
												if (givenType instanceof ArrayType) {
													Type at = ((ArrayType) givenType).baseType;
													if (at instanceof RefType && ((ArrayType)foundType).baseType instanceof RefType) {
														Chain<SootClass> interfaces = ((RefType)at).getSootClass().getInterfaces();
														boolean inInterface=false;
														while ((!at.equals(foundType)) && ((RefType)at).getSootClass().hasSuperclass() &&!inInterface) {
															inInterface=isInInterfaces(interfaces,((RefType)((ArrayType)foundType).baseType).getSootClass());
															at = ((RefType)at).getSootClass().getSuperclass().getType();
															interfaces = ((RefType)at).getSootClass().getInterfaces();
														}
														matches = matches && (at.equals(foundType) || inInterface);
													}
													else if (at instanceof PrimType && ((ArrayType)foundType).baseType instanceof PrimType) {
														matches = matches && (at.equals(((ArrayType)foundType).baseType));
													}
												}
												else if (givenType instanceof RefType && ((ArrayType)foundType).baseType instanceof RefType){
													Type at = givenType;
													Chain<SootClass> interfaces = ((RefType)at).getSootClass().getInterfaces();
													boolean inInterface = false;
													while ((!at.equals(((ArrayType)foundType).baseType)) && ((RefType)at).getSootClass().hasSuperclass() &&!inInterface) {
														inInterface = isInInterfaces(interfaces,((RefType)((ArrayType)foundType).baseType).getSootClass());
														at = ((RefType)at).getSootClass().getSuperclass().getType();
														interfaces = ((RefType)at).getSootClass().getInterfaces();
													}
													matches = matches && (at.equals(((ArrayType)foundType).baseType) || inInterface);
												}
												else
													matches = matches && (((ArrayType)foundType).baseType.equals(givenType));
											}
										}
									}
							
								}
						}
						if (matches)
							return foundMethod;
						}
					}
				}
			}
		}
		for (int i=0; i<givenParas.size(); i++) { 							//if nothing matches, try with classes instead of primtive types 
			if (givenParas.get(i) instanceof PrimType || givenParas.get(i) instanceof ArrayType) {	//e.g. java.lang.Integer instead of int
				ArrayList<Type> newparameterlist=new ArrayList<>();			//also try Object instead of array
				for (int j=0; j<i; j++)
					newparameterlist.add(givenParas.get(j));
				newparameterlist.add(JavaUtil.primToClass(givenParas.get(i)));
				for (int j=i+1; j<givenParas.size(); j++)
					newparameterlist.add(givenParas.get(j));
				SootMethod method = searchMethod(klass, methodname, newparameterlist);
				if (method!=null)
					return method;
			}
		}
		ArrayList<Type> newparameterlist=new ArrayList<>();
		if (givenParas.size()>2)
			for (int i=0; i<givenParas.size()-2; i++)							//If the last two or more parameter have the same type, combine them
				newparameterlist.add(givenParas.get(i));						//to an array and look again, e.g. "int... a"
		if (givenParas.get(givenParas.size()-1) instanceof ArrayType)
			newparameterlist.add(givenParas.get(givenParas.size()-1));
		else {
			Type newType = ArrayType.v(givenParas.get(givenParas.size()-1),1);
			newparameterlist.add(newType);
		}
		SootMethod method = searchMethod(klass, methodname, newparameterlist);
		if (method != null)
			return method;
		return null;
	}
	
	
	/**
	 * Deletes all nop-statements used as a placeholder for jumps
	 * @param jb	Jimple-body containing the method
	 */
	private void deleteNops(JimpleBody jb) {
		Iterator<Unit> iterator = jb.getUnits().iterator();
		ArrayList<Unit> list = new ArrayList<>();
		while (iterator.hasNext()) {
			Unit unit = iterator.next();
			if (unit instanceof NopStmt)
				list.add(unit);
		}
		jb.getUnits().removeAll(list);
	}
	
	/**
	 * Checks an unary operation-node and returns true if its a prefix-increment/decrement
	 * @param node	node containing the calculation
	 * @return		true, if its a prefix-calculation, else false
	 */
	private boolean isPrefix(JCTree node) {
		if (node.toString().charAt(0) == '+' || node.toString().charAt(0) == '-')
			return true;
		return false;
	}
	
	/**
	 * Checks an unary operation-node and returns true if its a postfix-increment/decrement
	 * @param node	node containing the calculation
	 * @return		true, if its a postfix-calculation, else false
	 */
	private boolean isPostfix(JCTree node) {
		int lastIndex = node.toString().length()-1;
		if (node.toString().charAt(lastIndex) == '+' || node.toString().charAt(lastIndex) == '-')
			return true;
		return false;
	}
	
	/**
	 * Checks if the node is a Parens- or ExpressionStatment-node and ignores them because they are irrelevant for the Jimple-code
	 * @param node	node to check 
	 * @return		if true returns the child-node, else the node itself
	 */
	private JCTree ignoreNode(JCTree node) {
		if (node instanceof JCParens)
			return ((JCParens)node).expr;
		if (node instanceof JCExpressionStatement)
			return ((JCExpressionStatement)node).expr;
		return node;
	}
	
	 /**
	  * Runs through all interfaces and its superinterfaces and checks if an interface matches the given class
	  * @param interfaces	list of interfaces
	  * @param clazz		class to compare with
	  * @return				true, if class is in one of the interfaces or its superinterfaces, else return false
	  */
	private boolean isInInterfaces(Chain<SootClass> interfaces, SootClass clazz) {
		if (interfaces.contains(clazz))
			return true;
		else {
			for (SootClass c:interfaces) {
				if (c.getInterfaces() != null && isInInterfaces(c.getInterfaces(),clazz))
					return true;
			}
			return false;
		}
	}
	
	/**
	 * Adds the valueOf method to the enum class
	 */
	private void enumValueOf() {
		Type type = RefType.v(JavaUtil.addPackageName("String"));
		Value parameter = Jimple.v().newParameterRef(type, 0);
		Local loc = localGenerator.generateLocal(type);
		Unit ident = Jimple.v().newIdentityStmt(loc, parameter);
		units.add(ident);
		List<Type> parameterTypes = new ArrayList<Type>();
		parameterTypes.add(RefType.v(JavaUtil.addPackageName("Class")));
		parameterTypes.add(RefType.v(JavaUtil.addPackageName("String")));
		SootMethod method = searchMethod(Scene.v().getSootClass(JavaUtil.addPackageName("Enum")), "valueOf", parameterTypes);
		List<Value> parameterList = new ArrayList<Value>();
		parameterList.add(ClassConstant.v(thisMethod.getDeclaringClass().getName()));
		parameterList.add(loc);
		Value invoke = Jimple.v().newStaticInvokeExpr(method.makeRef(), parameterList);
		invoke = checkForExprChain(invoke);
		Value cast = Jimple.v().newCastExpr(invoke, RefType.v(thisMethod.getDeclaringClass()));
		cast = checkForExprChain(cast);
		Unit returnValue = Jimple.v().newReturnStmt(cast);
		units.add(returnValue);
	}
	
	/**
	 * Adds the visible constructor to the enum class
	 */
	private void enumInit() {
		Local thisLocal = new JimpleLocal("thisLocal", thisMethod.getDeclaringClass().getType());
		Unit thisIdent = Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(thisMethod.getDeclaringClass().getType()));
		locals.put("thisLocal", thisLocal);
		units.add(thisIdent);
		Type string = RefType.v(JavaUtil.addPackageName("String"));
		Value parameter1 = Jimple.v().newParameterRef(string, 0);
		Local loc1 = localGenerator.generateLocal(string);
		Unit ident1 = Jimple.v().newIdentityStmt(loc1, parameter1);
		units.add(ident1);
		Value parameter2 = Jimple.v().newParameterRef(IntType.v(), 1);
		Local loc2 = localGenerator.generateLocal(IntType.v());
		Unit ident2 = Jimple.v().newIdentityStmt(loc2, parameter2);
		units.add(ident2);
		List<Type> parameterTypes = new ArrayList<>();
		parameterTypes.add(string);
		parameterTypes.add(IntType.v());
		SootMethod method = searchMethod(thisMethod.getDeclaringClass().getSuperclass(), "<init>", parameterTypes);
		List<Value> parameterList = new ArrayList<>();
		parameterList.add(loc1);
		parameterList.add(loc2);
		Value specialinvoke = Jimple.v().newSpecialInvokeExpr(thisLocal, method.makeRef(), parameterList);
		Unit invoke = Jimple.v().newInvokeStmt(specialinvoke);
		units.add(invoke);
	}

	/**
	 * Adds the values method to the enum class
	 */
	private void enumValues() {
		SootFieldRef fieldref = thisMethod.getDeclaringClass().getFieldByName("ENUM$VALUES").makeRef();
		Type type = fieldref.type();
		Value field = Jimple.v().newStaticFieldRef(fieldref);
		Local loc = localGenerator.generateLocal(type);
		Unit assign = Jimple.v().newAssignStmt(loc, field);
		units.add(assign);
		Value lengthof = Jimple.v().newLengthExpr(loc);
		lengthof = checkForExprChain(lengthof);
		Value array = Jimple.v().newNewArrayExpr(RefType.v(thisMethod.getDeclaringClass()), lengthof);
		array = checkForExprChain(array);
		List<Type> parameterTypes = new ArrayList<>();
		parameterTypes.add(RefType.v(JavaUtil.addPackageName("Object")));
		parameterTypes.add(IntType.v());
		parameterTypes.add(RefType.v(JavaUtil.addPackageName("Object")));
		parameterTypes.add(IntType.v());
		parameterTypes.add(IntType.v());
		SootMethod method = searchMethod(Scene.v().getSootClass(JavaUtil.addPackageName("System")), "arraycopy", parameterTypes);
		List<Value> parameterList = new ArrayList<>();
		parameterList.add(loc);
		parameterList.add(IntConstant.v(0));
		parameterList.add(array);
		parameterList.add(IntConstant.v(0));
		parameterList.add(lengthof);
		Value staticinvoke = Jimple.v().newStaticInvokeExpr(method.makeRef(), parameterList);
		Unit invoke = Jimple.v().newInvokeStmt(staticinvoke);
		units.add(invoke);
		Unit returnvalue = Jimple.v().newReturnStmt(array);
		units.add(returnvalue);
	}
	
	/**
	 * Adds the hidden constructor to the enum class
	 */
	private void enumConstructor() {
		RefType type = RefType.v(thisMethod.getDeclaringClass());
		for (int i=0; i<fieldList.size(); i++) {
			Local loc = localGenerator.generateLocal(type);
			Value val = Jimple.v().newNewExpr(type);
			Unit assign = Jimple.v().newAssignStmt(loc, val);
			units.add(assign);
			List<Value> parameterList=new ArrayList<>();
			String name = ((JCVariableDecl)fieldList.get(i)).name.toString();
			parameterList.add(StringConstant.v(name));
			parameterList.add(IntConstant.v(i));
			SootMethod method = thisMethod.getDeclaringClass().getMethodByName("<init>");
			Value specialinvoke = Jimple.v().newSpecialInvokeExpr(loc, method.makeRef(), parameterList);
			Unit invoke = Jimple.v().newInvokeStmt(specialinvoke);
			units.add(invoke);
			SootFieldRef fieldref = thisMethod.getDeclaringClass().getFieldByName(name).makeRef();
			Value field = Jimple.v().newStaticFieldRef(fieldref);
			Unit assign2 = Jimple.v().newAssignStmt(field, loc);
			units.add(assign2);
		}
		SootField fieldarray = thisMethod.getDeclaringClass().getFieldByName("ENUM$VALUES");
		Local loc = localGenerator.generateLocal(fieldarray.getType());
		Value newarray = Jimple.v().newNewArrayExpr(type, IntConstant.v(fieldList.size()));
		Unit assign = Jimple.v().newAssignStmt(loc, newarray);
		units.add(assign);
		for (int i=0; i<fieldList.size(); i++) {
			String name = ((JCVariableDecl)fieldList.get(i)).name.toString();
			SootFieldRef fieldref = thisMethod.getDeclaringClass().getFieldByName(name).makeRef();
			Value field = Jimple.v().newStaticFieldRef(fieldref);
			Value arrayaccess = Jimple.v().newArrayRef(loc, IntConstant.v(i));
			field = checkForExprChain(field);
			Unit assign2 = Jimple.v().newAssignStmt(arrayaccess, field);
			units.add(assign2);
		}
		Value field = Jimple.v().newStaticFieldRef(fieldarray.makeRef());
		Unit assign2 = Jimple.v().newAssignStmt(field, loc);
		units.add(assign2);
	}
}

/*
 * UNSUPPORTED:
 * Lambda (JCLambda)
 * Member Reference (JCMemberReference, e.g. System.out::println)
 * Labeled Statement (JCLabeledStatement), goto reserved for java but unused
 * Erroneous (JCErroneous)
 */