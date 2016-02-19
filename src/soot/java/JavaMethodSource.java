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
import soot.jimple.NewExpr;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JimpleLocal;
import soot.util.Chain;

public class JavaMethodSource implements MethodSource {
	
	//the complete method-tree
	JCMethodDecl meth;
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
	Local newclasslocal;
	//Generator for locals
	LocalGenerator locGen;
	//Contains specific soot-method globally, used to find methods in the same class
	SootMethod thisMethod;
	//Contains imports to find package names as type
	Dependencies deps;
	//List of fields which have to be initialized in each constructor 
	ArrayList<JCTree> fieldlist;
	//List that contains the end of all current loops, used to get a target for "break"
	ArrayList<Unit> breakloop = new ArrayList<>();
	
	
	public JavaMethodSource(JCMethodDecl body, Dependencies deps, ArrayList<JCTree> fieldlist) {
		this.meth = body;
		this.deps = deps;
		this.fieldlist = fieldlist;
	}
	
	/*
	 * (non-Javadoc)
	 * @see soot.MethodSource#getBody(soot.SootMethod, java.lang.String)
	 */
	@Override
	public Body getBody(SootMethod m, String phaseName) {
		thisMethod = m;
		JimpleBody jb = Jimple.v().newBody(m);
		locGen = new LocalGenerator(jb);
		if (meth==null) {
			getThisVar(m);
			if (m.getName().equals("<init>"))
				getFields();
		}
		else if (Modifier.isEnum(m.getDeclaringClass().getModifiers())) {
			//TODO enum
		}
		else {
			getParameter(m, meth.params);
			if (m.getName().equals("<init>"))
				getFields();
			getMethodBody(meth.body.stats);
		}
			

		jb.getTraps().addAll(traps);
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
	 * @param stats	list of units
	 * @return the next unit
	 */
	private Unit getMethodBody(com.sun.tools.javac.util.List<JCStatement> stats) {
		Unit ret = getHead(stats.head);
		if (!queue.isEmpty()) {
			JCTree tree = queue.get(0);
			queue.remove(tree);
			getHead(tree);
		}
		while (stats.tail.head != null) {
			stats = stats.tail;
			getHead(stats.head);
			if (!queue.isEmpty()) {
				JCTree tree = queue.get(0);
				queue.remove(tree);
				getHead(tree);
			}
		}
		return ret;
	}
	
	/**
	 * Checks the type of the given node
	 * @param node	current node to translate
	 * @return 		node translated to corresponding Jimple-unit
	 */
	private Unit getHead(JCTree node) {
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
			if (post(node))
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
		com.sun.tools.javac.util.List<JCExpression> args = node.args;
		ArrayList<Value> parameter = new ArrayList<>();
		while (args.head != null) {
			parameter.add(checkBinary(getValue(args.head)));
			args = args.tail;
		}		
		List<Type> parameterTypes=new ArrayList<>();
		for (int i=0; i<parameter.size(); i++)
			parameterTypes.add(parameter.get(i).getType());
		
		Value invoke = null;
		SootMethodRef method = getMethodRef(node.meth, parameterTypes);
		
		if (!method.parameterTypes().equals(parameterTypes)) {					//if parameterized types are used, it needs a value as a class instead of the primitive type
			for (int i=0; i<parameterTypes.size(); i++) {						//e.g. ArrayList<Integer> a; a.add(3); => Integer b=Integer.valueOf(3); a.add(b);
				if (!method.parameterTypes().get(i).equals(parameterTypes.get(i)) && parameterTypes.get(i) instanceof PrimType) {
					Type type=JavaUtil.primToClass(parameterTypes.get(i));
					Local valueoflocal=locGen.generateLocal(type);
					ArrayList<Type> paras=new ArrayList<>();
					paras.add(parameterTypes.get(i));
					SootMethod valueofmethod=Scene.v().getSootClass(valueoflocal.getType().toString()).getMethod("valueOf", paras);
					Value staticinvoke=Jimple.v().newStaticInvokeExpr(valueofmethod.makeRef(), parameter.get(i));
					Unit assign=Jimple.v().newAssignStmt(valueoflocal, staticinvoke);
					units.add(assign);
					parameter.remove(i);
					if (parameter.size()==i)
						parameter.add(valueoflocal);
					else
						parameter.set(i, valueoflocal);
				}
						
			}
		}
	
		if (node.meth instanceof JCIdent) {											//in this class
			if (method.isStatic()) {												//static
				invoke = Jimple.v().newStaticInvokeExpr(method, parameter);
			}
			else {																	//not static
				invoke = Jimple.v().newVirtualInvokeExpr(locals.get("thisLocal"), method, parameter);
			}
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCIdent) {			//in some package
			if (method.isStatic()) {												//static
				invoke = Jimple.v().newStaticInvokeExpr(method, parameter);
			}
			else {		
				Local loc = (Local)getValue(((JCFieldAccess)node.meth).selected);
				if (((RefType)loc.getType()).getSootClass().isInterface())
					invoke = Jimple.v().newInterfaceInvokeExpr(loc, method, parameter);	//interface
				else
					invoke = Jimple.v().newVirtualInvokeExpr(loc, method, parameter);		//"normal"
			}
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCFieldAccess){		//with field access, e.g. System.out.println
			Local refLocal = getLastRefLocal(method.declaringClass().toString());
			invoke = Jimple.v().newVirtualInvokeExpr(refLocal, method, parameter);
		}
		else {																		//chain of invocations
			Value returnvalue = getMethodInvocation((JCMethodInvocation)((JCFieldAccess)node.meth).selected);
			Local save = locGen.generateLocal(returnvalue.getType());
			Unit saveAssign = Jimple.v().newAssignStmt(save, returnvalue);
			units.add(saveAssign);
			invoke = Jimple.v().newVirtualInvokeExpr(save, method, parameter);
		}
		
		return invoke;
		}

	/**
	 * Translates a binary operation into a corresponding Jimple binary statement
	 * @param node	node containing the binary operation
	 * @return		binary operation in Jimple
	 */
	private Value getBinary(JCBinary node) {
		Value left = checkBinary(getValue(node.lhs));
		Value right = checkBinary(getValue(node.rhs));
		if (left.getType().toString().equals("java.lang.String") || right.getType().toString().equals("java.lang.String")) {
			RefType sbref = RefType.v("java.lang.StringBuilder");
			RefType sref = RefType.v("java.lang.String");
			Local stringbuilder = locGen.generateLocal(sbref);
			Value stringbuilderval = Jimple.v().newNewExpr(sbref);
			Unit assign = Jimple.v().newAssignStmt(stringbuilder, stringbuilderval);
			units.add(assign);
			Local valueofleft = locGen.generateLocal(sref);
			ArrayList<Type> valueofpara = new ArrayList<>();
			valueofpara.add(left.getType());
			SootMethodRef valueofMethod = Scene.v().makeMethodRef(sref.getSootClass(), "valueOf", valueofpara, sref, true);	
			Value toString = Jimple.v().newStaticInvokeExpr(valueofMethod, left);
			Unit assignString = Jimple.v().newAssignStmt(valueofleft, toString);
			units.add(assignString);
			ArrayList<Value> parameter = new ArrayList<>();
			ArrayList<Type> parameterTypes = new ArrayList<>();
			parameter.add(valueofleft);
			parameterTypes.add(valueofleft.getType());
			SootMethodRef method = Scene.v().makeMethodRef(sbref.getSootClass(), "<init>", parameterTypes, VoidType.v(), false);
			Value invoke = Jimple.v().newSpecialInvokeExpr(stringbuilder, method, valueofleft);
			Unit specialinvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialinvoke);
			ArrayList<Type> appendTypes = new ArrayList<>();
			appendTypes.add(right.getType());
			SootMethodRef append = Scene.v().makeMethodRef(sbref.getSootClass(), "append", appendTypes, sbref, false);
			Value appendvalue = Jimple.v().newVirtualInvokeExpr(stringbuilder, append, right);
			Local appendlocal = locGen.generateLocal(sbref);
			Unit assignappend = Jimple.v().newAssignStmt(appendlocal, appendvalue);
			units.add(assignappend);
			ArrayList<Type> toStringTypes = new  ArrayList<>();
			SootMethodRef returnstring = Scene.v().makeMethodRef(sbref.getSootClass(), "toString", toStringTypes, sref, false);
			Value returnvalue = Jimple.v().newVirtualInvokeExpr(appendlocal, returnstring);
			return returnvalue;
		}
		else {
			String findOperator = node.toString().replace(node.lhs.toString(), "");
			if (findOperator.charAt(1) == '+')
				return Jimple.v().newAddExpr(left, right);
			else if (findOperator.charAt(1) == '-')
				return Jimple.v().newSubExpr(left, right);
			else if (findOperator.charAt(1) == '&')
				return Jimple.v().newAndExpr(left, right);
			else if (findOperator.charAt(1) == '|')
				return Jimple.v().newOrExpr(left, right);
			else if (findOperator.charAt(1) == '*')
				return Jimple.v().newMulExpr(left, right);
			else if (findOperator.charAt(1) == '/')
				return Jimple.v().newDivExpr(left, right);
			else if (findOperator.charAt(1) == '%')
				return Jimple.v().newRemExpr(left, right);
			else if (findOperator.charAt(1) == '^')
				return Jimple.v().newXorExpr(left, right);
			else if (findOperator.charAt(3) == '>' && findOperator.charAt(2) == '>' && findOperator.charAt(1) == '>')
				return Jimple.v().newUshrExpr(left, right);
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '>')
				return Jimple.v().newGeExpr(left, right);
			else if (findOperator.charAt(2) == '>' && findOperator.charAt(1) == '>')
				return Jimple.v().newShrExpr(left, right);
			else if (findOperator.charAt(1) == '>')
				return Jimple.v().newGtExpr(left, right);
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '<')
				return Jimple.v().newLeExpr(left, right);
			else if (findOperator.charAt(2) == '<' && findOperator.charAt(1) == '<')
				return Jimple.v().newShlExpr(left, right);
			else if (findOperator.charAt(1) == '<')
				return Jimple.v().newLtExpr(left, right);
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '=')
				return Jimple.v().newEqExpr(left, right);
			else if (findOperator.charAt(2) == '=' && findOperator.charAt(1) == '!')
				return Jimple.v().newNeExpr(left, right);
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
		Value value = checkBinary(getValue(treeNode));
		String findOperator = node.toString();
		if (findOperator.charAt(0) == '!') {
			return Jimple.v().newEqExpr(value, IntConstant.v(0));
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
			return value;		
		else
			throw new AssertionError("Unknown unary value in " + node.toString());
	}	
	
	/**
	 * Translate an instance of-expression into a corresponding Jimple statement
	 * @param node	node containing the instance of-expression
	 * @return		equal Jimple instance of-expression
	 */
	private Value getInstanceOf(JCInstanceOf node) {
		Value instance = Jimple.v().newInstanceOfExpr(checkBinary(getValue(node.expr)), JavaUtil.getType(node.clazz, deps, thisMethod.getDeclaringClass()));
		Value local = locGen.generateLocal(instance.getType());
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
		Value typecast = Jimple.v().newCastExpr(getValue(node.expr), JavaUtil.getType(node.clazz, deps, thisMethod.getDeclaringClass()));
		return typecast;
	}
	
	/**
	 * Translates an array access into a corresponding Jimple array access
	 * @param node	node containing the array access
	 * @return		the array access as a value
	 */
	private Value getArrayAccess(JCArrayAccess node) {
		Value array = Jimple.v().newArrayRef(getValue(node.indexed), checkBinary(getValue(node.index)));
		return array;
	}
	
	/**
	 * Translates a new-expression into a corresponding Jimple new-expression
	 * @param node	node containing the new-expression
	 * @return		new-expression in Jimple
	 */
	private Value getNewClass(JCNewClass node) {
		Value newClass = Jimple.v().newNewExpr((RefType) JavaUtil.getType(node.clazz, deps, thisMethod.getDeclaringClass()));
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
		Value truepart = getValue(node.truepart);
		Value falsepart = null;
		if (node.falsepart != null)
			falsepart = getValue(node.falsepart);
		
		Local returnlocal = locGen.generateLocal(truepart.getType() instanceof NullType ? falsepart.getType() : truepart.getType());
		Unit nopTrue = Jimple.v().newNopStmt();
		IfStmt ifstmt = Jimple.v().newIfStmt(condition, nopTrue);
		units.add(ifstmt);
		if (node.falsepart != null) {
			Unit assignfalse = Jimple.v().newAssignStmt(returnlocal, falsepart);
			units.add(assignfalse);
		}
		Unit nopEnd = Jimple.v().newNopStmt();
		Unit elseEnd = Jimple.v().newGotoStmt(nopEnd);
		units.add(elseEnd);
		units.add(nopTrue);
		Unit assigntrue = Jimple.v().newAssignStmt(returnlocal, truepart);
		units.add(assigntrue);
		units.add(nopEnd);
		return returnlocal;
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
		Value loc;
		if (locals.containsKey(node.toString()))
			loc = locals.get(node.toString());
		else
		{
			if (thisMethod.getDeclaringClass().declaresFieldByName(node.toString())) {
				SootField field = thisMethod.getDeclaringClass().getFieldByName(node.toString());
				if (field.isStatic()) 
					loc = Jimple.v().newStaticFieldRef(field.makeRef());
				else
					loc = Jimple.v().newInstanceFieldRef(locals.get("thisLocal"),field.makeRef());
			}
			else			
				throw new AssertionError("Unknown local " + node.toString());
		}
		return loc;
	}
	
	/**
	 * Translates a field access in an other class in Jimple
	 * @param node	node containing the field access
	 * @return		translated field access
	 */
	private Value getFieldAccess(JCFieldAccess node) {
		Value loc;
		if (JavaUtil.isPackageName((JCIdent)node.selected, deps)) {
			SootClass clazz = Scene.v().getSootClass(JavaUtil.getPackage((JCIdent)node.selected, deps, thisMethod.getDeclaringClass() ));
			loc = Jimple.v().newStaticFieldRef(clazz.getFieldByName(node.name.toString()).makeRef());
			return loc;
		}
		else
		{
			Value val = getLocal((JCIdent)node.selected);
			if (val.getType() instanceof ArrayType && node.name.toString().equals("length")) {
				loc=Jimple.v().newLengthExpr(val);
			}
			else {
				SootClass clazz = Scene.v().getSootClass(val.getType().toString());
				loc = Jimple.v().newInstanceFieldRef(val, clazz.getFieldByName(node.name.toString()).makeRef());
			}
			return loc;
		} 		
	}	
	
	/**
	 * Transforms all parameters into locals. If the method isn't static, adds a this-local
	 * @param m			soot-method containing information of the class, used for this-local
	 * @param params	list of all parameters
	 */
	private void getParameter(SootMethod m, com.sun.tools.javac.util.List<JCVariableDecl> params) {
		if (!meth.mods.toString().contains("static")) {
			Local thisLocal = new JimpleLocal("thisLocal", m.getDeclaringClass().getType());
			Unit thisIdent = Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(m.getDeclaringClass().getType()));
			locals.put("thisLocal", thisLocal);
			units.add(thisIdent);
		}
		int paramcount = 0;
		while(params.head != null) {
			Value parameter = Jimple.v().newParameterRef(JavaUtil.getType(params.head.vartype, deps, thisMethod.getDeclaringClass()), paramcount++);
			Local paramLocal = new JimpleLocal(params.head.name.toString(), JavaUtil.getType(params.head.vartype, deps, thisMethod.getDeclaringClass()));
			Unit assign = Jimple.v().newIdentityStmt(paramLocal, parameter);
			locals.put(paramLocal.getName(), paramLocal);
			units.add(assign);
			params = params.tail;
			
			if (thisMethod.getDeclaringClass().hasOuterClass() && paramLocal.getType().equals(RefType.v(thisMethod.getDeclaringClass().getOuterClass()))) {
				Value lhs=Jimple.v().newInstanceFieldRef(locals.get("thisLocal"), thisMethod.getDeclaringClass().getFieldByName("this$0").makeRef());
				Unit assignOuterClass=Jimple.v().newAssignStmt(lhs, paramLocal);
				units.add(assignOuterClass);
			}
		}
		if (m.getName().equals("<init>")) {
			ArrayList<Type> parameterTypes=new ArrayList<>();
			SootMethod method = thisMethod.getDeclaringClass().getSuperclass().getMethod("<init>", parameterTypes);
			Value invoke = Jimple.v().newSpecialInvokeExpr(locals.get("thisLocal"), method.makeRef());
			Unit specialinvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialinvoke);
		}
			
	}
	
	/**
	 * Checks if there exists a constructor and if not, generates one in Jimple with a this-variable
	 * @param m		the constructor as soot-method
	 */
	private void getThisVar(SootMethod m) {
		Local thisLocal = new JimpleLocal("thisLocal", m.getDeclaringClass().getType());
		Unit thisIdent = Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(m.getDeclaringClass().getType()));
		locals.put("thisLocal", thisLocal);
		units.add(thisIdent);
		if (thisMethod.getDeclaringClass().isInnerClass()) {
			SootClass outerClass=thisMethod.getDeclaringClass().getOuterClass();
			Value parameter=Jimple.v().newParameterRef(RefType.v(outerClass), 0);
			Local paramLocal = new JimpleLocal("outerLocal", RefType.v(outerClass));
			locals.put(paramLocal.getName(), paramLocal);
			Unit assign = Jimple.v().newIdentityStmt(paramLocal, parameter);
			units.add(assign);
			Value lhs=Jimple.v().newInstanceFieldRef(locals.get("thisLocal"), thisMethod.getDeclaringClass().getFieldByName("this$0").makeRef());
			Unit assignOuterClass=Jimple.v().newAssignStmt(lhs, paramLocal);
			units.add(assignOuterClass);
		}
		if (m.getName().equals("<init>")) {
			ArrayList<Type> parameterTypes=new ArrayList<>();
			SootMethod method = thisMethod.getDeclaringClass().getSuperclass().getMethod("<init>", parameterTypes);
			Value invoke = Jimple.v().newSpecialInvokeExpr(thisLocal, method.makeRef());
			Unit specialinvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialinvoke);
		}
		
	}
	
	/**
	 * Makes sure, that every field that gets its value outside of the methods, gets it in every Jimple-constructor.
	 */
	private void getFields() {
		while (!fieldlist.isEmpty()) {
			JCTree node = fieldlist.get(0);
			SootField field = thisMethod.getDeclaringClass().getFieldByName(((JCVariableDecl)node).name.toString());
			Value loc;
			if (field.isStatic()) 
				loc = Jimple.v().newStaticFieldRef(field.makeRef());
			else
				loc = Jimple.v().newInstanceFieldRef(locals.get("thisLocal"),field.makeRef());
			Value rhs = checkBinary(getValue(((JCVariableDecl)node).init));
			if (!queue.isEmpty()) {
				newclasslocal = (Local)rhs;
				JCTree tree = queue.get(0);
				queue.remove(tree);
				getHead(tree);
			}
			Unit assign = Jimple.v().newAssignStmt(loc, rhs);
			units.add(assign);
			fieldlist.remove(node);
		}
	}
	
	/**
	 * Creates a method reference in Jimple to an existing method
	 * @param node				node containing a method invocation
	 * @param parameterTypes	list of parameter types
	 * @return					reference to the corresponding method in Jimple
	 */
	private SootMethodRef getMethodRef(JCTree node, List<Type> parameterTypes) {
		SootMethod method=null;
		if (node instanceof JCIdent)
			return searchMethod(thisMethod.getDeclaringClass(), node.toString(), parameterTypes).makeRef();
		else { 
			if (((JCFieldAccess)node).selected instanceof JCIdent) {
				if (JavaUtil.isPackageName((JCIdent)((JCFieldAccess)node).selected,deps)) {
					String packagename = JavaUtil.getPackage((JCIdent)((JCFieldAccess)node).selected, deps, thisMethod.getDeclaringClass());
					SootClass klass = Scene.v().getSootClass(packagename);
					method = searchMethod(klass,((JCFieldAccess)node).name.toString(),parameterTypes);
				}
				else {
					String packagename = locals.get((((JCFieldAccess)node).selected).toString()).getType().toString();
					SootClass klass = Scene.v().getSootClass(packagename);
					method = searchMethod(klass, ((JCFieldAccess)node).name.toString(), parameterTypes);
				}
			}
			else if (((JCFieldAccess)node).selected instanceof JCMethodInvocation){
				Value access = getMethodInvocation((JCMethodInvocation)((JCFieldAccess)node).selected);
				SootClass klass;
				if (access.getType() instanceof PrimType || access.getType() instanceof ArrayType)
					klass=Scene.v().getSootClass("java.lang.Object");
				else
					klass = Scene.v().getSootClass(access.getType().toString());
				method = searchMethod(klass,((JCFieldAccess)node).name.toString(), parameterTypes);
			}
			else if (((JCFieldAccess)node).selected instanceof JCFieldAccess){		//FieldAccess
				Local loc = (Local)checkBinary(getFieldAccess((JCFieldAccess)((JCFieldAccess)node).selected));
				SootClass klass;
				if (loc.getType() instanceof PrimType || loc.getType() instanceof ArrayType)
					klass=Scene.v().getSootClass("java.lang.Object");
				else
					klass = Scene.v().getSootClass(loc.getType().toString());
				method = searchMethod(klass,((JCFieldAccess)node).name.toString(), parameterTypes);
			}				
		}
		if (method!=null)
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
		Unit nopTrue = Jimple.v().newNopStmt();
		IfStmt ifstmt = Jimple.v().newIfStmt(condition, nopTrue);
		units.add(ifstmt);
		if (node.elsepart != null)
			getHead(node.elsepart);
		Unit nopEnd = Jimple.v().newNopStmt();
		Unit elseEnd = Jimple.v().newGotoStmt(nopEnd);
		units.add(elseEnd);
		units.add(nopTrue);
		noBlock(node.thenpart);
		units.add(nopEnd);
		return ifstmt;
	}

	/**
	 * Translates a do-while-loop into an if-jump-loop in Jimple
	 * @param node	node containing the do-while-loop
	 * @return		Jimple if-statement for the loop-jump
	 */
	private Unit addDoWhile(JCDoWhileLoop node) {
		Unit nopContinue = Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		Unit nopbreak = Jimple.v().newNopStmt();
		breakloop.add(nopbreak);
		JCTree treeNode = ignoreNode(node.cond);
		Value condition = getValue(treeNode);
		Unit target = noBlock(node.body);
		units.add(nopContinue);
		IfStmt ifstmt = Jimple.v().newIfStmt(condition, target);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopbreak);
		breakloop.remove(breakloop.size()-1);
		return target;
	}
	
	/**
	 * Translates a while-loop into an if-jump-loop in Jimple
	 * @param node	node containing the while-loop
	 * @return		initial Jimple jump-statement to the condition of the loop-jump
	 */
	private Unit addWhile(JCWhileLoop node) {
		Unit nopbreak = Jimple.v().newNopStmt();
		breakloop.add(nopbreak);
		JCTree treeNode = ignoreNode(node.cond);
		Value condition = getValue(treeNode);
		Unit nop = Jimple.v().newNopStmt();
		loopContinue.add(nop);
		Unit jump = Jimple.v().newGotoStmt(nop);
		units.add(jump);
		Unit target = noBlock(node.body);
		IfStmt ifstmt = Jimple.v().newIfStmt(condition, target);
		units.add(nop);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopbreak);
		breakloop.remove(breakloop.size()-1);
		return jump;
	}
	
	/**
	 * Translates a for-loop into an if-jump-loop with a counter in Jimple
	 * @param node	node containing the for-loop
	 * @return		initial Jimple declaration of the counter
	 */
	private Unit addFor(JCForLoop node) {
		Unit nopbreak = Jimple.v().newNopStmt();
		breakloop.add(nopbreak);
		Unit nopContinue = Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		Unit counter = getHead(node.init.head);
		com.sun.tools.javac.util.List<JCStatement> initlist = node.init.tail;
		while (initlist.head != null) {
			getHead(initlist.head);	
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
			if (pre(steplist.head))
					getHead(steplist.head);
			steplist = steplist.tail;
		}
		noBlock(node.body);
		units.add(nopContinue);	
		steplist = node.step;
		while (steplist.head != null) {
			if (!pre(steplist.head))
					getHead(steplist.head);
			steplist=steplist.tail;
		}
		units.add(nop);
		IfStmt ifstmt = Jimple.v().newIfStmt(condition, nopBlock);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopbreak);
		breakloop.remove(breakloop.size()-1);
		return counter;
	}
	
	/**
	 * Translates an enhanced for-loop (for-each-loop) into an if-jump-loop with a counter in Jimple
	 * @param node	node containing the enhanced for-loop
	 * @return		length-op-assign unit containing the length of the loop as the initial unit for the loop
	 */
	private Unit addEnhancedFor(JCEnhancedForLoop node) {
		Unit nopbreak = Jimple.v().newNopStmt();
		breakloop.add(nopbreak);
		Unit nopContinue = Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		Local max = locGen.generateLocal(IntType.v());
		Value length = Jimple.v().newLengthExpr(getValue(node.expr));
		Unit assignMax = Jimple.v().newAssignStmt(max, length);
		units.add(assignMax);
		Local counter = locGen.generateLocal(IntType.v());
		Value startValue = IntConstant.v(0);
		Unit assignStart = Jimple.v().newAssignStmt(counter, startValue);
		units.add(assignStart);
		Unit nop = Jimple.v().newNopStmt();
		Unit gotoNop = Jimple.v().newGotoStmt(nop);
		units.add(gotoNop);
		Local loopLocal = new JimpleLocal(node.var.name.toString(), JavaUtil.getType(node.var.vartype, deps, thisMethod.getDeclaringClass()));
		locals.put(loopLocal.getName(), loopLocal);
		Value arrayaccess = Jimple.v().newArrayRef(getValue(node.expr), counter);
		Unit assignLoop = Jimple.v().newAssignStmt(loopLocal, arrayaccess);
		units.add(assignLoop);
		noBlock(node.body);
		units.add(nopContinue);	
		Unit increase = Jimple.v().newAssignStmt(counter, Jimple.v().newAddExpr(counter, IntConstant.v(1)));
		units.add(increase);
		units.add(nop);
		Value condition = Jimple.v().newLtExpr(counter, max);
		IfStmt ifstmt = Jimple.v().newIfStmt(condition, assignLoop);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		units.add(nopbreak);
		breakloop.remove(breakloop.size()-1);
		return assignMax;
	}
	
	/**
	 * Translates a continue-statement into a jump-statement in Jimple
	 * @param node	node containing the continue-statement
	 * @return		Jimple jump-statement to the if from the loop-head
	 */
	private Unit addContinue(JCContinue node) {
		Unit jump = Jimple.v().newGotoStmt(loopContinue.get(loopContinue.size()-1));
		units.add(jump);
		return jump;
	}
	
	/**
	 * Translates a break-statement into a Jimple jump-statement
	 * @param node	node containing the break-statement
	 * @return		jump-statement to end of the loop or switch-case
	 */
	private Unit addBreak(JCBreak node) {
		Unit jump = Jimple.v().newGotoStmt(breakloop.get(breakloop.size()-1));
		units.add(jump);
		return jump;
	}
	
	/**
	 * Translates a try-catch-block into a matching Jimple block
	 * @param node	node containing the try-catch-block
	 * @return		first unit of the try-block
	 */
	private Unit addTryCatch(JCTry node) {
		Unit tryunit = noBlock(node.body);
		Unit trysuccess = Jimple.v().newNopStmt();
		Unit trygoto = Jimple.v().newGotoStmt(trysuccess);
		units.add(trygoto);
		com.sun.tools.javac.util.List<JCCatch> catchlist = node.catchers;
		while (catchlist.head != null) {
			Unit catchunit = addCatch((JCCatch)catchlist.head);
			SootClass throwable = Scene.v().getSootClass(JavaUtil.getPackage((JCIdent)catchlist.head.param.vartype, deps, thisMethod.getDeclaringClass() ));
			Trap trap = Jimple.v().newTrap(throwable, tryunit, trygoto, catchunit);
			traps.add(trap);
			catchlist = catchlist.tail;
		}
		Unit finallynop = null;
		if (node.finalizer != null) {
			noBlock(node.finalizer);
			finallynop = Jimple.v().newNopStmt();
			Unit finallygoto = Jimple.v().newGotoStmt(finallynop);
			units.add(finallygoto);
			catchlist = node.catchers;
			while (catchlist.head != null) {
				Value var = locGen.generateLocal(JavaUtil.getType(catchlist.head.param.vartype, deps, thisMethod.getDeclaringClass()));
				Unit catchunit = Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
				units.add(catchunit);
				noBlock(node.finalizer);
				Unit throwstmt = Jimple.v().newThrowStmt(var);
				units.add(throwstmt);
				SootClass throwable = Scene.v().getSootClass(JavaUtil.getPackage((JCIdent)catchlist.head.param.vartype, deps, thisMethod.getDeclaringClass() ));
				Trap trap = Jimple.v().newTrap(throwable, tryunit, finallygoto, catchunit);
				traps.add(trap);
				catchlist = catchlist.tail;
			}
		}
		units.add(trysuccess);
		if (node.finalizer != null) {
			noBlock(node.finalizer);
			units.add(finallynop);
		}
		return tryunit;
	}
	
	/**
	 * Utility-function to add catch-exceptions
	 * @param node	node containing the catch-block
	 * @return		assign-statement of the exception
	 */
	private Unit addCatch(JCCatch node) {
		Value var = locGen.generateLocal(JavaUtil.getType(node.param.vartype, deps, thisMethod.getDeclaringClass()));
		Unit assign = Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
		units.add(assign);
		noBlock(node.body);
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
			Local loc=locGen.generateLocal(val.getType());
			Unit assign=Jimple.v().newAssignStmt(loc, val);
			units.add(assign);
			newclasslocal=loc;
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
	 * @param node	node containing the synchronized-block
	 * @return		enter monitor statement
	 */
	private Unit addSynchronized(JCSynchronized node) {
		Unit entermonitor = Jimple.v().newEnterMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(entermonitor);
		Unit start = noBlock(node.body);
		Unit exitmonitor = Jimple.v().newExitMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(exitmonitor);
		Unit gotonop = Jimple.v().newNopStmt();
		Unit gotostmt = Jimple.v().newGotoStmt(gotonop);
		units.add(gotostmt);
		Value var = locGen.generateLocal(RefType.v("java.lang.Throwable"));
		Unit catchunit = Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
		units.add(catchunit);
		Unit exitmonitor2 = Jimple.v().newExitMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(exitmonitor2);
		Unit endthrow = Jimple.v().newNopStmt();
		units.add(endthrow);
		SootClass throwable = Scene.v().getSootClass("java.lang.Throwable");
		Trap trap = Jimple.v().newTrap(throwable, start, gotostmt, catchunit);
		traps.add(trap);
		Trap trap2 = Jimple.v().newTrap(throwable, catchunit, endthrow, catchunit);
		traps.add(trap2);
		Unit throwstmt = Jimple.v().newThrowStmt(var);
		units.add(throwstmt);
		units.add(gotonop);
		return entermonitor;
		
	}
	
	/**
	 * Translates a new-statement with parameters into a special-invoke-statement
	 * @param node	node containing the new-class-statement
	 * @return		a single special-invoke-statement
	 */
	private Unit addConstructorInvocation(JCNewClass node) {
		com.sun.tools.javac.util.List<JCExpression> args = node.args;
		ArrayList<Value> parameter = new ArrayList<>();
		while (args.head != null) {
			parameter.add(checkBinary(getValue(args.head)));
			args = args.tail;
		}		
		List<Type> parameterTypes = new ArrayList<>();
		for (int i=0; i<parameter.size(); i++)
			parameterTypes.add(parameter.get(i).getType());
		SootClass klass = Scene.v().getSootClass(newclasslocal.getType().toString());
		SootMethodRef method = Scene.v().makeMethodRef(klass, "<init>", parameterTypes, VoidType.v(), false);
		Value invoke = Jimple.v().newSpecialInvokeExpr(newclasslocal, method, parameter);
		Unit specialinvocation = Jimple.v().newInvokeStmt(invoke);
		if (node.clazz instanceof JCTypeApply) {
			com.sun.tools.javac.util.List<JCExpression> taglist = ((JCTypeApply)node.clazz).arguments;
			while (taglist.head!=null) {
				taglist=taglist.tail;
			}
		}
		units.add(specialinvocation);
		return specialinvocation;
	}

	/**
	 * Translates a switch-case into a lookup-switch with jumps to each block after the comparison
	 * @param node	node containing the switch-case-block
	 * @return		lookup-switch-block
	 */
	private Unit addSwitch(JCSwitch node) {
		Unit nopbreak = Jimple.v().newNopStmt();
		breakloop.add(nopbreak);
		Value key = getValue(node.selector);
		Unit defaultTarget = null;
		com.sun.tools.javac.util.List<JCCase> cases = node.cases;
		List<Unit> targets = new ArrayList<>();
		List<IntConstant> lookupValues = new ArrayList<>();
		while (cases.head != null) {
			if (cases.head.pat != null) {
				lookupValues.add((IntConstant) getConstant((JCLiteral)cases.head.pat));
				Unit nop = Jimple.v().newNopStmt();
				targets.add(nop);
			}
			else 
				defaultTarget = Jimple.v().newNopStmt();
			cases = cases.tail;
		}		
		Unit lookupswitch = Jimple.v().newLookupSwitchStmt(key, lookupValues, targets, defaultTarget);
		units.add(lookupswitch);
		cases = node.cases;
		while (!targets.isEmpty()) {
			units.add(targets.get(0));
			getMethodBody(cases.head.stats);
			targets.remove(0);
			cases = cases.tail;
		}
		if (cases.head.pat == null) {
			units.add(defaultTarget);
			getMethodBody(cases.head.stats);
		}
		units.add(nopbreak);
		breakloop.remove(breakloop.size()-1);
		return lookupswitch;
	}
	
	/**
	 * Translates return-statement into a corresponding  return-statement in Jimple
	 * @param node	node containing information of the return-statement
	 * @return		return-statement in Jimple
	 */
	private Unit addReturn (JCReturn node) {
		Value value = checkBinary(getValue(node.expr));
		Unit returnStmt = Jimple.v().newReturnStmt(value);
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
		if (var instanceof ArrayRef)
			right = checkBinary(right);
		Unit assign = Jimple.v().newAssignStmt(var, right);
		units.add(assign);
		if (node.rhs instanceof JCNewClass)
			newclasslocal = (Local)var;
		return assign;
	}

	/**
	 * Translates a variable-declaration into corresponding declaration in Jimple
	 * @param node	node containing information for the variable declaration
	 * @return 		if variable is assigned instantly, returns assign-statement, else returns null
	 */
	private Unit addVariableDecl(JCVariableDecl node) {
		if (node.vartype instanceof JCArrayTypeTree) {
			Local array = new JimpleLocal(node.name.toString(),JavaUtil.getType(node.vartype, deps, thisMethod.getDeclaringClass()));
			Type type;
			Value arrayValue;
			if (node.init instanceof JCNewArray) {
				if (((JCNewArray)node.init).elems != null) {
					type = getConstant((JCLiteral)((JCNewArray)node.init).elems.head).getType();
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
					arrayValue = Jimple.v().newNewArrayExpr(type, getValue(((JCNewArray)node.init).dims.head));
				}
			}
			else
				arrayValue = getValue(node.init);
			Unit assign = Jimple.v().newAssignStmt(array, arrayValue);
			locals.put(array.getName(), array);
			units.add(assign);
			if (node.init instanceof JCNewArray && ((JCNewArray)node.init).elems != null) {
				int i=0;
				com.sun.tools.javac.util.List<JCExpression> list = ((JCNewArray)node.init).elems;
				while (list.head != null) {
					Value arrayAccess = Jimple.v().newArrayRef(array, IntConstant.v(i));
					Value rhs = checkBinary(getValue(list.head));
					Unit assignValue = Jimple.v().newAssignStmt(arrayAccess, rhs);
					units.add(assignValue);
					list = list.tail;
					i++;
				}
			}
			return assign;
		}
		else {
			Type type;
			if (node.init != null && node.init instanceof JCNewClass) {
				type = JavaUtil.getType(((JCNewClass)node.init).clazz, deps, thisMethod.getDeclaringClass());
			}
			else
				type = JavaUtil.getType(node.vartype, deps, thisMethod.getDeclaringClass());
			Local newLocal = new JimpleLocal(node.name.toString(), type);
			Value con = getValue(node.init);
			locals.put(newLocal.getName(), newLocal);
			if (con != null) {
				Unit assign = Jimple.v().newAssignStmt(newLocal, con);
				units.add(assign);
				if (node.init instanceof JCNewClass)
					newclasslocal = newLocal;
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
		Value value = checkBinary(getValue(treeNode));
		String findOperator = node.toString();
		if ((findOperator.charAt(0) == '+' && findOperator.charAt(1) == '+' ) || (findOperator.charAt(findOperator.length()-2) == '+' && findOperator.charAt(findOperator.length()-1) == '+')) {
			Unit increase = Jimple.v().newAssignStmt(value, Jimple.v().newAddExpr(value, IntConstant.v(1)));
			units.add(increase);
			return increase;
		}
		if ((findOperator.charAt(0) == '-' && findOperator.charAt(1) == '-' ) || (findOperator.charAt(findOperator.length()-2) == '-' && findOperator.charAt(findOperator.length()-1) == '-')) {
			Unit decrease = Jimple.v().newAssignStmt(value, Jimple.v().newSubExpr(value, IntConstant.v(1)));
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
		Value right = checkBinary(getValue(node.rhs));
		
		if (var.getType().toString().equals("java.lang.String") || right.getType().toString().equals("java.lang.String")) {
			RefType sbref = RefType.v("java.lang.StringBuilder");
			RefType sref = RefType.v("java.lang.String");
			Local stringbuilder = locGen.generateLocal(sbref);
			Value stringbuilderval = Jimple.v().newNewExpr(sbref);
			Unit assign = Jimple.v().newAssignStmt(stringbuilder, stringbuilderval);
			units.add(assign);	ArrayList<Value> parameter = new ArrayList<>();
			ArrayList<Type> parameterTypes = new ArrayList<>();
			parameter.add(var);
			parameterTypes.add(var.getType());
			SootMethodRef method = Scene.v().makeMethodRef(sbref.getSootClass(), "<init>", parameterTypes, VoidType.v(), false);
			Value invoke = Jimple.v().newSpecialInvokeExpr(stringbuilder, method, var);
			Unit specialinvoke = Jimple.v().newInvokeStmt(invoke);
			units.add(specialinvoke);
			ArrayList<Type> appendTypes = new ArrayList<>();
			appendTypes.add(right.getType());
			SootMethodRef append = Scene.v().makeMethodRef(sbref.getSootClass(), "append", appendTypes, sbref, false);
			Value appendvalue = Jimple.v().newVirtualInvokeExpr(stringbuilder, append, right);
			Local appendlocal = locGen.generateLocal(sbref);
			Unit assignappend = Jimple.v().newAssignStmt(appendlocal, appendvalue);
			units.add(assignappend);
			ArrayList<Type> toStringTypes = new  ArrayList<>();
			SootMethodRef returnstring = Scene.v().makeMethodRef(sbref.getSootClass(), "toString", toStringTypes, sref, false);
			binary = Jimple.v().newVirtualInvokeExpr(appendlocal, returnstring);
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
	 * Checks, if the value is a binary operation. 
	 * If yes, creates a new Jimple-local to save the interim result
	 * @param val	value to check
	 * @return		the new Jimple-local or the value from the parameter
	 */
	private Value checkBinary(Value val) {
		if (val instanceof BinopExpr || val instanceof CastExpr || val instanceof InstanceOfExpr || val instanceof ArrayRef 
				|| val instanceof InvokeExpr || val instanceof NewExpr || val instanceof FieldRef || val instanceof LengthExpr) {
			Local newLocal = locGen.generateLocal(val.getType());
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
	private Unit noBlock(JCTree node) {
		if (node instanceof JCBlock) {
			if (((JCBlock) node).stats.head != null)
				return getMethodBody(((JCBlock) node).stats);
			else
			{
				Unit nop = Jimple.v().newNopStmt();
				units.add(nop);
				return nop;
			}
		}
		else
			return getHead(node);
	}
	

	
	/**
	 * Searches for a matching method, considers all superclasses and interfaces
	 * @param klass				the base class, where either the class itself or its superclass contains the method
	 * @param methodname		name of the wanted method
	 * @param parameterTypes	types of the parameter, which can contain superclasses and interfaces
	 * @return					the found method
	 */
	private SootMethod searchMethod(SootClass klass, String methodname, List<Type> parameterTypes) {
		if (klass.declaresMethod(methodname, parameterTypes))		//class itself has the method with matching parameters
			return klass.getMethod(methodname, parameterTypes);
		else 
		{
			SootClass currentclass = klass;
			while (currentclass.hasSuperclass() && !currentclass.declaresMethod(methodname, parameterTypes))
				currentclass = currentclass.getSuperclass();
			if (currentclass.declaresMethod(methodname, parameterTypes))		//go through all superclasses with exact parameter types
				return currentclass.getMethod(methodname, parameterTypes);
			else
			{
				currentclass = klass;
				List<SootMethod> methodlist = currentclass.getMethods();			//search in class itself with supertypes/interfaces of parameter
				for (int j=0; j<methodlist.size(); j++) {
					if (methodlist.get(j).getName().equals(methodname)) {
						SootMethod method = methodlist.get(j);
						List<Type> paras = method.getParameterTypes();
						boolean matches = false;
						if (paras.size() == parameterTypes.size()) {
								matches = true;
								for (int i=0; i<paras.size(); i++) {
									if (paras.get(i) instanceof PrimType)
										if (paras.get(i) instanceof CharType || paras.get(i) instanceof BooleanType)
											matches = matches && ((parameterTypes.get(i) instanceof IntType)||parameterTypes.get(i).equals(paras.get(i)));
										else
											matches = matches && parameterTypes.get(i).equals(paras.get(i));
									else
									{
										if (paras.get(i) instanceof RefType) {
											if (parameterTypes.get(i) instanceof RefType) {
												RefType rt = (RefType)parameterTypes.get(i);
												Chain<SootClass> interfaces = rt.getSootClass().getInterfaces();
												boolean inInterface=false;
												while ((!rt.equals(paras.get(i))) && rt.getSootClass().hasSuperclass() &&!inInterface) {
													inInterface=isInInterfaces(interfaces,((RefType)paras.get(i)).getSootClass());
													rt = rt.getSootClass().getSuperclass().getType();
													interfaces = rt.getSootClass().getInterfaces();
												}
												matches = matches && (rt.equals(paras.get(i)) || inInterface);
											}
											else
												matches = false;
											
										}
										else
										{
											if (paras.get(i) instanceof ArrayType) {
												if (parameterTypes.get(i) instanceof ArrayType) {
													RefType at = (RefType)((ArrayType) parameterTypes.get(i)).baseType;
													Chain<SootClass> interfaces = at.getSootClass().getInterfaces();
													boolean inInterface=false;
													while ((!at.equals(paras.get(i))) && at.getSootClass().hasSuperclass() &&!inInterface) {
														inInterface=isInInterfaces(interfaces,((RefType)paras.get(i)).getSootClass());
														at = at.getSootClass().getSuperclass().getType();
														interfaces = at.getSootClass().getInterfaces();
													}
													matches = matches && (at.equals(paras.get(i)) || inInterface);
												}
												else
													matches = false;
											}
										}
							
									}
								}
								if (matches)
									return method;
						}
					}
				}
				while (currentclass.hasSuperclass()) {				//searches in superclasses for method with supertypes/interfaces of parameter
					currentclass = currentclass.getSuperclass();
					methodlist = currentclass.getMethods();
					for (int j=0; j<methodlist.size(); j++) {
						if (methodlist.get(j).getName().equals(methodname)) {
							SootMethod method = methodlist.get(j);
							List<Type> paras = method.getParameterTypes();
							boolean matches = false;
							if (paras.size() == parameterTypes.size()) {
								matches = true;
								for (int i=0; i<paras.size(); i++) {
									if (paras.get(i) instanceof PrimType)
										if (paras.get(i) instanceof CharType || paras.get(i) instanceof BooleanType)
											matches = matches && ((parameterTypes.get(i) instanceof IntType)||parameterTypes.get(i).equals(paras.get(i)));
										else
											matches = matches && parameterTypes.get(i).equals(paras.get(i));
									else
									{
										if (paras.get(i) instanceof RefType) {
											if (parameterTypes.get(i) instanceof RefType) {
												RefType rt = (RefType)parameterTypes.get(i);
												Chain<SootClass> interfaces = rt.getSootClass().getInterfaces();
												boolean inInterface=false;
												while ((!rt.equals(paras.get(i))) && rt.getSootClass().hasSuperclass() &&!inInterface) {
													inInterface=isInInterfaces(interfaces,((RefType)paras.get(i)).getSootClass());
													rt = rt.getSootClass().getSuperclass().getType();
													interfaces = rt.getSootClass().getInterfaces();
												}
												matches = matches && (rt.equals(paras.get(i)) || inInterface);
											}
											else
												matches = false;
											
										}
										else
										{
											if (paras.get(i) instanceof ArrayType) {
												if (parameterTypes.get(i) instanceof ArrayType) {
													RefType at = (RefType)((ArrayType) parameterTypes.get(i)).baseType;
													Chain<SootClass> interfaces = at.getSootClass().getInterfaces();
													boolean inInterface=false;
													while ((!at.equals(paras.get(i))) && at.getSootClass().hasSuperclass() &&!inInterface) {
														inInterface=isInInterfaces(interfaces,((RefType)paras.get(i)).getSootClass());
														at = at.getSootClass().getSuperclass().getType();
														interfaces = at.getSootClass().getInterfaces();
													}
													matches = matches && (at.equals(paras.get(i)) || inInterface);
												}
												else
													matches = false;
											}
										}
									}
							
								}
						}
						if (matches)
							return method;
						}
					}
				}
				/*
				 * ABOVE NOT TESTED, old tested code:
				 * 
				 * while (currentclass.hasSuperclass()) {				//search in superclasses for method with supertypes/interfaces of parameter
					currentclass = currentclass.getSuperclass();
					methodlist = currentclass.getMethods();
					for (int j=0; j<methodlist.size(); j++) {
						if (methodlist.get(j).getName().equals(methodname)) {
							SootMethod method = methodlist.get(j);
							List<Type> paras = method.getParameterTypes();
							boolean matches = false;
							if (paras.size() == parameterTypes.size()) {
								matches = true; 
								for (int i=0; i<paras.size(); i++) {
									if (paras.get(i) instanceof PrimType)
										matches = matches && parameterTypes.get(i).equals(paras.get(i));
									else
									{
										if (paras.get(i) instanceof RefType) {
											if (parameterTypes.get(i) instanceof PrimType)
												continue;
											RefType rt = (RefType)parameterTypes.get(i);
											Chain<SootClass> interfaces = rt.getSootClass().getInterfaces();
											while ((!rt.equals(paras.get(i))) && rt.getSootClass().hasSuperclass() && !(interfaces.contains(((RefType)paras.get(i)).getSootClass()))) {
												rt = rt.getSootClass().getSuperclass().getType();
												interfaces = rt.getSootClass().getInterfaces();
											}
									
											matches = matches && (parameterTypes.get(i).equals(paras.get(i)) || interfaces.contains(((RefType)paras.get(i)).getSootClass()));
										}
									}
							
								}
						}
						if (matches)
							return method;
						}
					}
				}
				 */
			}
		}
		
		for (int i=0; i<parameterTypes.size(); i++) { 
			if (parameterTypes.get(i) instanceof PrimType || parameterTypes.get(i) instanceof ArrayType) {
				ArrayList<Type> newparameterlist=new ArrayList<>();
				for (int j=0; j<i; j++)
					newparameterlist.add(parameterTypes.get(j));
				newparameterlist.add(JavaUtil.primToClass(parameterTypes.get(i)));
				for (int j=i+1; j<parameterTypes.size(); j++)
					newparameterlist.add(parameterTypes.get(j));
				SootMethod method=searchMethod(klass, methodname, newparameterlist);
				if (method!=null)
					return method;
			}
		}
		return null;//throw new AssertionError("Can't find method \"" + methodname + "\" in class \"" + klass.toString() + "\" or its superclasses" );
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
	private boolean pre(JCTree node) {
		if (node.toString().charAt(0) == '+' || node.toString().charAt(0) == '-')
			return true;
		return false;
	}
	
	/**
	 * Checks an unary operation-node and returns true if its a postfix-increment/decrement
	 * @param node	node containing the calculation
	 * @return		true, if its a postfix-calculation, else false
	 */
	private boolean post(JCTree node) {
		if (node.toString().charAt(node.toString().length()-1) == '+' || node.toString().charAt(node.toString().length()-1) == '-')
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
				if (c.getInterfaces()!=null && isInInterfaces(c.getInterfaces(),clazz))
					return true;
			}
			return false;
		}
	}

}
