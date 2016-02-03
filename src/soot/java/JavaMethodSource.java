package soot.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;

import soot.ArrayType;
import soot.Body;
import soot.Local;
import soot.MethodSource;
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
import soot.jimple.Constant;
import soot.jimple.DoubleConstant;
import soot.jimple.FloatConstant;
import soot.jimple.IfStmt;
import soot.jimple.InstanceOfExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LongConstant;
import soot.jimple.NewExpr;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JimpleLocal;
import soot.util.Chain;

public class JavaMethodSource implements MethodSource {
	
	//Saving complete method-tree
	JCMethodDecl meth;
	//Saving locals, easier to find with name
	HashMap<String,Local> locals=new HashMap<>();
	//Saving Exceptions, Try/Catch
	ArrayList<Trap> traps=new ArrayList<>();
	//Saving units
	ArrayList<Unit> units=new ArrayList<>();
	//List used to keep track of current loop, used to find jump-target for continue-statements
	ArrayList<Unit> loopContinue=new ArrayList<>();
	//List to solve problem with a=b++ where b=b+1 comes after a=b and constructor after new
	ArrayList<JCTree> queue=new ArrayList<>();
	//Save the last new-class-local, used for direct constructor invocation
	Local newclasslocal;
	//Generator for locals
	LocalGenerator locGen;
	//Save this soot-method globally, used to find methods in the same class
	SootMethod thisMethod;
	//Save imports to find package names as type
	Dependencies deps;
	
	ArrayList<JCTree> fieldlist;
	
	
	public JavaMethodSource(JCMethodDecl body, Dependencies deps, ArrayList<JCTree> fieldlist) {
		this.meth=body;
		this.deps=deps;
		this.fieldlist=fieldlist;
	}
	

	@Override
	public Body getBody(SootMethod m, String phaseName) {
		thisMethod=m;
		JimpleBody jb = Jimple.v().newBody(m);
		locGen=new LocalGenerator(jb);
		getParameter(m, meth.params);
		if (m.getName().equals("<init>"))
			getFields();
		getMethodBody(meth.body.stats);
		
		
		jb.getTraps().addAll(traps);
		Iterator<Local> iterator=locals.values().iterator();
		while (iterator.hasNext()) {
			Local local=iterator.next();
			if (!jb.getLocals().contains(local))
				jb.getLocals().add(local);
		}
		jb.getUnits().addAll(units);
		if (!(units.get(units.size()-1) instanceof ReturnStmt))
			jb.getUnits().add(Jimple.v().newReturnVoidStmt()); //TODO ??
		deleteNops(jb);
		return jb;
	}
	
	/**
	 * Part unit-list into next unit and list of other units
	 * @param stats	list of units
	 */
	private Unit getMethodBody(com.sun.tools.javac.util.List<JCStatement> stats) {
		Unit ret=getHead(stats.head);
		if (!queue.isEmpty()) {
			JCTree tree=queue.get(0);
			queue.remove(tree);
			getHead(tree);
		}
		while (stats.tail.head!=null) {
			stats=stats.tail;
			getHead(stats.head);
			if (!queue.isEmpty()) {
				JCTree tree=queue.get(0);
				queue.remove(tree);
				getHead(tree);
			}
		}
		return ret;
	}
	
	/**
	 * Check what kind of node the parameter is
	 * @param node	current node to translate
	 * @return 		node translated to jimple-unit
	 */
	private Unit getHead(JCTree node) {
		node=ignoreNode(node);
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
	//	if (node instanceof JCSwitch)
	// 		return addSwitch((JCSwitch)node);
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
		else
			throw new AssertionError("Unknown node type " + node.getClass().getSimpleName());
	}

	/**
	 * Check what value the node has
	 * @param node	node with a value
	 * @return		value translated to jimple-value
	 */
	private Value getValue(JCTree node) {
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
		if (node instanceof JCUnary) {
			if (post(node))
				queue.add(node);
			return getUnary((JCUnary)node);	
		}
		else
			throw new AssertionError("Unknown node type " + node.getClass().getSimpleName());
	}

	/**
	 * Translate a method invocation in matching jimple invocation (static, virtual, special, interface)
	 * @param node	node containing the method invocation
	 * @return		method invocation as a value
	 */
	private Value getMethodInvocation(JCMethodInvocation node) {
		com.sun.tools.javac.util.List<JCExpression> args = node.args;
		ArrayList<Value> parameter=new ArrayList<>();
		while (args.head!=null) {
			parameter.add(checkBinary(getValue(args.head)));
			args=args.tail;
		}		
		List<Type> parameterTypes=new ArrayList<>();
		for (int i=0; i<parameter.size();i++)
			parameterTypes.add(parameter.get(i).getType());
		
		Value invoke=null;
		SootMethodRef method=getMethodRef(node.meth, parameterTypes);
	
		if (node.meth instanceof JCIdent) {											//in this class
			if (method.isStatic()) {												//static
				invoke=Jimple.v().newStaticInvokeExpr(method, parameter);
			}
			else {																	//not static
				invoke=Jimple.v().newVirtualInvokeExpr(locals.get("thisLocal"),method,parameter);
			}
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCIdent) {			//in some package
			if (method.isStatic()) {												//static
				invoke=Jimple.v().newStaticInvokeExpr(method,parameter);
			}
			else {		
				Local loc=(Local)getValue(((JCFieldAccess)node.meth).selected);
				if (((RefType)loc.getType()).getSootClass().isInterface())
					invoke=Jimple.v().newInterfaceInvokeExpr(loc, method, parameter);	//interface
				else
					invoke=Jimple.v().newVirtualInvokeExpr(loc, method, parameter);		//"normal"
			}
		}
		else if (((JCFieldAccess)node.meth).selected instanceof JCFieldAccess){		//with field access, e.g. System.out.println
			Local refLocal=getLastRefLocal(method.declaringClass().toString());
			invoke=Jimple.v().newVirtualInvokeExpr(refLocal,method, parameter);
		}
		else {																		//chain of invocations
			Value returnvalue=getMethodInvocation((JCMethodInvocation)((JCFieldAccess)node.meth).selected);
			Local save=locGen.generateLocal(returnvalue.getType());
			Unit saveAssign=Jimple.v().newAssignStmt(save, returnvalue);
			units.add(saveAssign);
			invoke=Jimple.v().newVirtualInvokeExpr(save, method, parameter);
		}
		
		return invoke;
		}

	/**
	 * Translate a method invocation without a return
	 * @param node	node containing the method invocation
	 * @return		method invocation in jimple as separate line
	 */
	private Unit addMethodInvocation(JCMethodInvocation node) {
		Value invokeExpr=getMethodInvocation(node);
		Unit methodinvoke=Jimple.v().newInvokeStmt(invokeExpr);
		units.add(methodinvoke);
		return methodinvoke;
	}
	
	/**
	 * Translate if-statement from tree to jimple
	 * @param node	node containing information for if-statement
	 * @return		if-statement in jimple
	 */
	private Unit addIf(JCIf node) {
		JCTree treeNode=ignoreNode(node.cond);
		Value condition=getValue(treeNode);
		Unit nopTrue=Jimple.v().newNopStmt();
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, nopTrue);
		units.add(ifstmt);
		if (node.elsepart!=null)
			getHead(node.elsepart);
		Unit nopEnd=Jimple.v().newNopStmt();
		Unit elseEnd=Jimple.v().newGotoStmt(nopEnd);
		units.add(elseEnd);
		units.add(nopTrue);
		noBlock(node.thenpart);
		units.add(nopEnd);
		return ifstmt;
	}

	/**
	 * Translate a do-while-loop into a if-jump-loop
	 * @param node	node containing do-while-loop
	 * @return		if-statement at the end
	 */
	private Unit addDoWhile(JCDoWhileLoop node) {
		Unit nopContinue=Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		JCTree treeNode=ignoreNode(node.cond);
		Value condition=getValue(treeNode);
		Unit target=noBlock(node.body);
		units.add(nopContinue);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		return target;
	}
	
	/**
	 * Translate while-loop into a if-jump-loop
	 * @param node	node containing while-loop
	 * @return		initial jump-statement
	 */
	private Unit addWhile(JCWhileLoop node) {
		JCTree treeNode=ignoreNode(node.cond);
		Value condition=getValue(treeNode);
		Unit nop=Jimple.v().newNopStmt();
		loopContinue.add(nop);
		Unit jump=Jimple.v().newGotoStmt(nop);
		units.add(jump);
		Unit target=noBlock(node.body);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		units.add(nop);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		return jump;
	}
	
	/**
	 * Translate a for-loop into a if-jump-loop with counter
	 * @param node	node containing for-loop
	 * @return		initial declaration for the counter
	 */
	private Unit addFor(JCForLoop node) {
		Unit nopContinue=Jimple.v().newNopStmt();
		loopContinue.add(nopContinue);
		Unit counter=addVariableDecl((JCVariableDecl)node.init.head);			//TODO nicht unbedingt VariableDecl
		com.sun.tools.javac.util.List<JCStatement> varlist = node.init.tail;
		while (varlist.head!=null) {
			addVariableDecl((JCVariableDecl)varlist.head);
			varlist=varlist.tail;
		}
		JCTree treeNode=ignoreNode(node.cond);
		Value condition=getValue(treeNode);
		Unit nop=Jimple.v().newNopStmt();
		Unit jump=Jimple.v().newGotoStmt(nop);
		units.add(jump);
		Unit nopBlock=Jimple.v().newNopStmt();
		units.add(nopBlock);
		com.sun.tools.javac.util.List<JCExpressionStatement> steplist = node.step; 
		while (steplist.head!=null) {
			if (pre(steplist.head))
					getHead(steplist.head);
			steplist=steplist.tail;
		}
		noBlock(node.body);
		units.add(nopContinue);	
		steplist=node.step;
		while (steplist.head!=null) {
			if (!pre(steplist.head))
					getHead(steplist.head);
			steplist=steplist.tail;
		}
		
		units.add(nop);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, nopBlock);
		units.add(ifstmt);
		loopContinue.remove(loopContinue.size()-1);
		return counter;
	}
	
	/**
	 * Translate continue into a jump-statement
	 * @param node	node containing the continue
	 * @return		jump-statement to if from the loop-head
	 */
	private Unit addContinue(JCContinue node) {
		Unit jump=Jimple.v().newGotoStmt(loopContinue.get(loopContinue.size()-1));
		units.add(jump);
		return jump;
	}
	
	/**
	 * Translate a Try-Catch-Block into a matching jimple-part
	 * @param node	node containing the try-catch-block
	 * @return		first unit from the try-block
	 */
	private Unit addTryCatch(JCTry node) {
		Unit tryunit=noBlock(node.body);
		Unit trysuccess=Jimple.v().newNopStmt();
		Unit trygoto=Jimple.v().newGotoStmt(trysuccess);
		units.add(trygoto);
		
		com.sun.tools.javac.util.List<JCCatch> catchlist = node.catchers;
		while (catchlist.head!=null) {
			Unit catchunit=addCatch((JCCatch)catchlist.head);
			SootClass throwable = Scene.v().getSootClass("java.lang.Throwable");		//TODO allgemein
			Trap trap=Jimple.v().newTrap(throwable, tryunit, trygoto, catchunit);
			traps.add(trap);
			catchlist=catchlist.tail;
		}
		Unit finallynop=null;
		if (node.finalizer!=null) {
			noBlock(node.finalizer);
			finallynop=Jimple.v().newNopStmt();
			Unit finallygoto=Jimple.v().newGotoStmt(finallynop);
			units.add(finallygoto);
			catchlist=node.catchers;
			while (catchlist.head!=null) {
				Value var=locGen.generateLocal(JavaUtil.getType(catchlist.head.param.vartype,deps));
				Unit catchunit=Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
				units.add(catchunit);
				noBlock(node.finalizer);
				Unit throwstmt=Jimple.v().newThrowStmt(var);
				units.add(throwstmt);
				SootClass throwable = Scene.v().getSootClass("java.lang.Throwable");		//TODO allgemein
				Trap trap=Jimple.v().newTrap(throwable, tryunit, finallygoto, catchunit);
				traps.add(trap);
				catchlist=catchlist.tail;
			}
		}
		units.add(trysuccess);
		if (node.finalizer!=null) {
			noBlock(node.finalizer);
			units.add(finallynop);
		}

		return tryunit;
	}
	
	/**
	 * Utility-function to add the catch-exceptions
	 * @param node	node containing the catch-block
	 * @return		assign-statement of the exception
	 */
	private Unit addCatch(JCCatch node) {
		Value var=locGen.generateLocal(JavaUtil.getType(node.param.vartype,deps));
		Unit assign=Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
		units.add(assign);
		noBlock(node.body);
		return assign;
	}
	
	/**
	 * Translate a synchronized-block into a try-catch-like block with monitor-statements
	 * @param node	node containing the synchronized-block
	 * @return		enter monitor statemenmt
	 */
	private Unit addSynchronized(JCSynchronized node) {
		Unit entermonitor=Jimple.v().newEnterMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(entermonitor);
		Unit start=noBlock(node.body);
		Unit exitmonitor=Jimple.v().newExitMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(exitmonitor);
		Unit gotonop=Jimple.v().newNopStmt();
		Unit gotostmt=Jimple.v().newGotoStmt(gotonop);
		units.add(gotostmt);
		Value var=locGen.generateLocal(RefType.v("java.lang.Throwable"));			//TODO allgemein
		Unit catchunit=Jimple.v().newIdentityStmt(var, Jimple.v().newCaughtExceptionRef());
		units.add(catchunit);
		Unit exitmonitor2=Jimple.v().newExitMonitorStmt(getValue(ignoreNode(node.lock)));
		units.add(exitmonitor2);
		Unit endthrow=Jimple.v().newNopStmt();
		units.add(endthrow);
		SootClass throwable = Scene.v().getSootClass("java.lang.Throwable");		//TODO allgemein
		Trap trap=Jimple.v().newTrap(throwable, start, gotostmt, catchunit);
		traps.add(trap);
		Trap trap2=Jimple.v().newTrap(throwable, catchunit, endthrow, catchunit);
		traps.add(trap2);
		Unit throwstmt=Jimple.v().newThrowStmt(var);
		units.add(throwstmt);
		units.add(gotonop);
		return entermonitor;
		
	}
	
	/**
	 * Translate a new-statement with parameter to a special invoke statement
	 * @param node	node containing the new-class-statement
	 * @return		a single special invoke statement
	 */
	private Unit addConstructorInvocation(JCNewClass node) {
		com.sun.tools.javac.util.List<JCExpression> args = node.args;
		ArrayList<Value> parameter=new ArrayList<>();
		while (args.head!=null) {
			parameter.add(checkBinary(getValue(args.head)));
			args=args.tail;
		}		
		List<Type> parameterTypes=new ArrayList<>();
		for (int i=0; i<parameter.size();i++)
			parameterTypes.add(parameter.get(i).getType());
		
		SootClass klass=Scene.v().getSootClass(newclasslocal.getType().toString());
		SootMethodRef method=Scene.v().makeMethodRef(klass, "<init>", parameterTypes, VoidType.v(), false);
		Value invoke=Jimple.v().newSpecialInvokeExpr(newclasslocal, method, parameter);
		Unit specialinvocation=Jimple.v().newInvokeStmt(invoke);
		units.add(specialinvocation);
		return specialinvocation;
	}

	
	private Unit addSwitch(JCSwitch node) {
/*		com.sun.tools.javac.util.List<JCCase> cases = node.cases;		//TODO
		List<? extends Unit> targets;
		while (cases.head!=null) {
			Unit newTarget=getMethodBody(cases.head.stats);
			cases=cases.tail;
		}
		
		
		Unit tableswitch=Jimple.v().newTableSwitchStmt(key, lowIndex, highIndex, targets, defaultTarget)
		return tableswitch;	*/
		return null;
	}
	
	/**
	 * Translate return-statement from tree to jimple
	 * @param node	node containing information for return-statement
	 * @return		return-statement in jimple
	 */
	private Unit addReturn (JCReturn node) {
		Value value=checkBinary(getValue(node.expr));
		Unit returnStmt=Jimple.v().newReturnStmt(value);
		units.add(returnStmt);
		return returnStmt;
	}
	
	/**
	 * Translate assign-statement from tree to jimple
	 * @param node	node containing information for assign-statement
	 * @return 		assign-statement in jimple
	 */
	private Unit addAssign(JCAssign node) {
		Value var=getValue(node.lhs);
		Value right=getValue(node.rhs);
		if (var instanceof ArrayRef)
			right=checkBinary(right);
		Unit assign=Jimple.v().newAssignStmt(var, right);
		units.add(assign);
		if (node.rhs instanceof JCNewClass)
			newclasslocal=(Local)var;
		return assign;
	}

	/**
	 * Translate variable declaration from tree to jimple
	 * @param node	node containing information for variable declaration
	 * @return 		if variable is assigned instantly, return assign-statement, else return null
	 */
	private Unit addVariableDecl(JCVariableDecl node) {
		if (node.vartype instanceof JCArrayTypeTree) {
			Local array=new JimpleLocal(node.name.toString(),JavaUtil.getType(node.vartype,deps));
			Type type=JavaUtil.getType(((JCNewArray)node.init).elemtype,deps);
			Value arrayValue=Jimple.v().newNewArrayExpr(type, getValue(((JCNewArray)node.init).dims.head));	//TODO dims List, mehrdimensional
			Unit assign=Jimple.v().newAssignStmt(array, arrayValue);
			locals.put(array.getName(), array);
			units.add(assign);
			return assign;
		}
		else {
			Local newLocal=new JimpleLocal(node.name.toString(),JavaUtil.getType(node.vartype,deps));
			Value con=getValue(node.init);
		
			locals.put(newLocal.getName(),newLocal);
			if (con!=null) {
				Unit assign=Jimple.v().newAssignStmt(newLocal, con);
				units.add(assign);
				if (node.init instanceof JCNewClass)
					newclasslocal=newLocal;
				return assign;
			}
		}
		Unit nop=Jimple.v().newNopStmt();
		units.add(nop);
		return nop;
	}
	
	/**
	 * Translate an unary operation as a single statement
	 * @param node	node containing the unary operations
	 * @return		unary operation as an binary assign statement
	 */
	private Unit addUnary (JCUnary node) {
		JCTree treeNode=ignoreNode(node.arg);
		Value value=checkBinary(getValue(treeNode));
		String findOperator=node.toString();
		if ((findOperator.charAt(0)=='+'&&findOperator.charAt(1)=='+')||(findOperator.charAt(findOperator.length()-2)=='+'&&findOperator.charAt(findOperator.length()-1)=='+')) {
			Unit increase=Jimple.v().newAssignStmt(value, Jimple.v().newAddExpr(value, IntConstant.v(1)));
			units.add(increase);
			return increase;
		}
		if ((findOperator.charAt(0)=='-'&&findOperator.charAt(1)=='-')||(findOperator.charAt(findOperator.length()-2)=='-'&&findOperator.charAt(findOperator.length()-1)=='-')) {
			Unit decrease=Jimple.v().newAssignStmt(value, Jimple.v().newSubExpr(value, IntConstant.v(1)));
			units.add(decrease);
			return decrease;
		}
		throw new AssertionError("Unknown unary operation in " + node.toString());
	}
	
	
	/**
	 * Break combined operations into binary expression and assign
	 * @param node	node containing variable, operation and one other value
	 * @return		combined operation as normal assign-statement
	 */
	private Unit addAssignOp(JCAssignOp node) {
		Local var=locals.get(((JCIdent)node.lhs).toString());
		Value binary;
		Value right=checkBinary(getValue(node.rhs));
		String findOperator=node.toString().replace(node.lhs.toString(), "");
		if (findOperator.charAt(1)=='+')
			binary=Jimple.v().newAddExpr(var, right);
		else if (findOperator.charAt(1)=='-')
			binary=Jimple.v().newSubExpr(var, right);
		else if (findOperator.charAt(1)=='&')
			binary=Jimple.v().newAndExpr(var, right);
		else if (findOperator.charAt(1)=='|')
			binary=Jimple.v().newOrExpr(var, right);
		else if (findOperator.charAt(1)=='*')
			binary=Jimple.v().newMulExpr(var, right);
		else if (findOperator.charAt(1)=='/')
			binary=Jimple.v().newDivExpr(var, right);
		else if (findOperator.charAt(1)=='%')
			binary=Jimple.v().newRemExpr(var, right);
		else if (findOperator.charAt(1)=='^')
			binary=Jimple.v().newXorExpr(var, right);
		else if (findOperator.charAt(3)=='>' && findOperator.charAt(2)=='>' && findOperator.charAt(1)=='>')
			binary=Jimple.v().newUshrExpr(var, right);
		else if (findOperator.charAt(2)=='>' && findOperator.charAt(1)=='>')
			binary=Jimple.v().newShrExpr(var, right);
		else if (findOperator.charAt(2)=='<' && findOperator.charAt(1)=='<')
			binary=Jimple.v().newShlExpr(var, right);
		else
			throw new AssertionError("Unknown assign operation in " + node.toString());
		Unit assign=Jimple.v().newAssignStmt(var, binary);
		units.add(assign);
		return assign;
	}

	
	/**
	 * Translate binary operations from tree to jimple
	 * @param node	node containing the binary operation
	 * @return		binary operation in jimple
	 */
	private Value getBinary(JCBinary node) {
		Value left=checkBinary(getValue(node.lhs));
		Value right=checkBinary(getValue(node.rhs));
				
		String findOperator=node.toString().replace(node.lhs.toString(), "");
		if (findOperator.charAt(1)=='+')
			return Jimple.v().newAddExpr(left, right);
		else if (findOperator.charAt(1)=='-')
			return Jimple.v().newSubExpr(left, right);
		else if (findOperator.charAt(1)=='&')
			return Jimple.v().newAndExpr(left, right);
		else if (findOperator.charAt(1)=='|')
			return Jimple.v().newOrExpr(left, right);
		else if (findOperator.charAt(1)=='*')
			return Jimple.v().newMulExpr(left, right);
		else if (findOperator.charAt(1)=='/')
			return Jimple.v().newDivExpr(left, right);
		else if (findOperator.charAt(1)=='%')
			return Jimple.v().newRemExpr(left, right);
		else if (findOperator.charAt(1)=='^')
			return Jimple.v().newXorExpr(left, right);
		else if (findOperator.charAt(3)=='>' && findOperator.charAt(2)=='>' && findOperator.charAt(1)=='>')
			return Jimple.v().newUshrExpr(left, right);
		else if (findOperator.charAt(2)=='=' && findOperator.charAt(1)=='>')
			return Jimple.v().newGeExpr(left, right);
		else if (findOperator.charAt(2)=='>' && findOperator.charAt(1)=='>')
			return Jimple.v().newShrExpr(left, right);
		else if (findOperator.charAt(1)=='>')
			return Jimple.v().newGtExpr(left, right);
		else if (findOperator.charAt(2)=='=' && findOperator.charAt(1)=='<')
			return Jimple.v().newLeExpr(left, right);
		else if (findOperator.charAt(2)=='<' && findOperator.charAt(1)=='<')
			return Jimple.v().newShlExpr(left, right);
		else if (findOperator.charAt(1)=='<')
			return Jimple.v().newLtExpr(left, right);
		else if (findOperator.charAt(2)=='=' && findOperator.charAt(1)=='=')
			return Jimple.v().newEqExpr(left, right);
		else if (findOperator.charAt(2)=='=' && findOperator.charAt(1)=='!')
			return Jimple.v().newNeExpr(left, right);
		else
			throw new AssertionError("Unknown binary operation in " + node.toString());
	}
	
	/**
	 * Translate unary operations into binary operations
	 * @param node	node containing the unary operation
	 * @return		similar binary operation
	 */
	private Value getUnary(JCUnary node) {
		JCTree treeNode=ignoreNode(node.arg);
		Value value=checkBinary(getValue(treeNode));
		String findOperator=node.toString();
		if (findOperator.charAt(0)=='!') {
			return Jimple.v().newEqExpr(value, IntConstant.v(0));
		}
		if (findOperator.charAt(0)=='~')
			return Jimple.v().newXorExpr(value, IntConstant.v(-1));
		if (findOperator.charAt(0)=='+'&&findOperator.charAt(1)=='+') {
			Unit increase=Jimple.v().newAssignStmt(value, Jimple.v().newAddExpr(value, IntConstant.v(1)));
			units.add(increase);
			return value;
		}
		if (findOperator.charAt(0)=='-'&&findOperator.charAt(1)=='-') {
			Unit increase=Jimple.v().newAssignStmt(value, Jimple.v().newSubExpr(value, IntConstant.v(1)));
			units.add(increase);
			return value;
		}
		if ((findOperator.charAt(findOperator.length()-2)=='+'&&findOperator.charAt(findOperator.length()-1)=='+')|| (findOperator.charAt(findOperator.length()-2)=='-'&&findOperator.charAt(findOperator.length()-1)=='-'))
			return value;		
		else
			throw new AssertionError("Unknown unary value in " + node.toString());
	}	
	
	/**
	 * Translate instance of-expression from tree to jimple
	 * @param node	node containing instance of-expression
	 * @return		instance of in equal expression
	 */
	private Value getInstanceOf(JCInstanceOf node) {
		Value instance=Jimple.v().newInstanceOfExpr(checkBinary(getValue(node.expr)), JavaUtil.getType(node.clazz,deps));
		Value local=locGen.generateLocal(instance.getType());
		Unit assign=Jimple.v().newAssignStmt(local, instance);
		units.add(assign);
		Value returnInstOf=Jimple.v().newEqExpr(local, IntConstant.v(1));
		return returnInstOf;
	}
	
	/**
	 * Translate type cast from tree to jimple
	 * @param node	node containing the type cast
	 * @return		type cast in jimple
	 */
	private Value getTypeCast(JCTypeCast node) {
		Value typecast=Jimple.v().newCastExpr(getValue(node.expr), JavaUtil.getType(node.clazz,deps));
		return typecast;
	}
	
	/**
	 * Translate an array access from tree to jimple
	 * @param node	node containing the array access
	 * @return		the array access as a value
	 */
	private Value getArrayAccess(JCArrayAccess node) {
		Value array=Jimple.v().newArrayRef(getValue(node.indexed), getValue(node.index));
		return array;
	}
	
	/**
	 * Translate a new expression
	 * @param node	node containing the new expression
	 * @return		new expression in jimple
	 */
	private Value getNewClass(JCNewClass node) {
		Value newClass=Jimple.v().newNewExpr((RefType) JavaUtil.getType(node.clazz, deps));
		queue.add(node);
		return newClass;
	}
	
	/**
	 * Translates a ternary operator (x?x:x) into an if-statement 
	 * @param node	node containing the ternary term
	 * @return		first if-statement
	 */
	private Value getConditional (JCConditional node) {
		JCTree treeNode=ignoreNode(node.cond);
		Value condition=getValue(treeNode);
		Value truepart=getValue(node.truepart);
		Value falsepart=null;
		if (node.falsepart!=null)
			falsepart=getValue(node.falsepart);
		
		Local returnlocal=locGen.generateLocal(truepart.getType() instanceof NullType ? falsepart.getType():truepart.getType());
		Unit nopTrue=Jimple.v().newNopStmt();
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, nopTrue);
		units.add(ifstmt);
		if (node.falsepart!=null) {
			Unit assignfalse=Jimple.v().newAssignStmt(returnlocal, falsepart);
			units.add(assignfalse);
		}
		Unit nopEnd=Jimple.v().newNopStmt();
		Unit elseEnd=Jimple.v().newGotoStmt(nopEnd);
		units.add(elseEnd);
		units.add(nopTrue);
		Unit assigntrue=Jimple.v().newAssignStmt(returnlocal, truepart);
		units.add(assigntrue);
		units.add(nopEnd);
		return returnlocal;
	}
	
	/**
	 * Translate number into jimple-constant
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
		if (node.toString().charAt(0)=='"')
			return StringConstant.v((String)node.value);
		if (node.typetag.name().equals("BOT"))
			return NullConstant.v();
		else
			throw new AssertionError("Unknown type of constant " + node.toString());
	}
	
	private Value getLocal(JCIdent node) {
		Value loc;
		if (locals.containsKey(node.toString()))
			loc=locals.get(node.toString());
		else
		{
			if (thisMethod.getDeclaringClass().declaresFieldByName(node.toString())) {
				SootField field=thisMethod.getDeclaringClass().getFieldByName(node.toString());
				if (field.isStatic()) 
					loc=Jimple.v().newStaticFieldRef(field.makeRef());
				else
					loc=Jimple.v().newInstanceFieldRef(locals.get("thisLocal"),field.makeRef());
			}
			else																//TODO Felder aus anderen Klassen
				throw new AssertionError("Unknown local " + node.toString());
		}
		return loc;
	}
	/**
	 * Checks, if the value is a binary operation. 
	 * If yes, create a new jimple-local to save the interim result
	 * @param val	value to check
	 * @return		the new jimple-local or the value from the parameter
	 */
	private Value checkBinary(Value val) {
		if (val instanceof BinopExpr || val instanceof CastExpr || val instanceof InstanceOfExpr || val instanceof ArrayRef || val instanceof InvokeExpr || val instanceof NewExpr) {
			Local newLocal=locGen.generateLocal(val.getType());
			locals.put(newLocal.getName(),newLocal);
			Unit assign=Jimple.v().newAssignStmt(newLocal, val);
			units.add(assign);
			return newLocal;
		}		
		return val;
	}
	
	/**
	 * Transform all parameters into locals. If the method isn't static, add a this-local
	 * @param m			soot-method containing information of the class, used for this-local
	 * @param params	list of all parameters
	 */
	private void getParameter(SootMethod m,com.sun.tools.javac.util.List<JCVariableDecl> params) {
		if (!meth.mods.toString().contains("static")) {
			Local thisLocal=new JimpleLocal("thisLocal",m.getDeclaringClass().getType());
			Unit thisIdent=Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(m.getDeclaringClass().getType()));
			locals.put("thisLocal", thisLocal);
			units.add(thisIdent);
			if (m.getName().equals("<init>")) {
				SootMethod method=m.getDeclaringClass().getMethodByName("<init>");
				Value invoke=Jimple.v().newSpecialInvokeExpr(thisLocal,method.makeRef());
				Unit specialinvoke=Jimple.v().newInvokeStmt(invoke);
				units.add(specialinvoke);
			}
		}
		int paramcount=0;
		while(params.head!=null) {
			Value parameter=Jimple.v().newParameterRef(JavaUtil.getType(params.head.vartype,deps), paramcount++);
			Local paramLocal=new JimpleLocal(params.head.name.toString(),JavaUtil.getType(params.head.vartype,deps));
			Unit assign=Jimple.v().newIdentityStmt(paramLocal, parameter);
			locals.put(paramLocal.getName(), paramLocal);
			units.add(assign);
			params=params.tail;
		}
			
	}
	
	private void getFields() {
		while (!fieldlist.isEmpty()) {
			JCTree node=fieldlist.get(0);
			SootField field=thisMethod.getDeclaringClass().getFieldByName(((JCVariableDecl)node).name.toString());
			Value loc;
			if (field.isStatic()) 
				loc=Jimple.v().newStaticFieldRef(field.makeRef());
			else
				loc=Jimple.v().newInstanceFieldRef(locals.get("thisLocal"),field.makeRef());
			Value rhs=checkBinary(getValue(((JCVariableDecl)node).init));
			if (!queue.isEmpty()) {
				newclasslocal=(Local)rhs;
				JCTree tree=queue.get(0);
				queue.remove(tree);
				getHead(tree);
			}
			Unit assign=Jimple.v().newAssignStmt(loc, rhs);
			units.add(assign);
			fieldlist.remove(node);
		}
	}
	
	/**
	 * If the node is a block, transform it into single statements
	 * @param node	node containing the block or a single statement
	 * @return		either the first statement of the block or the only statement
	 */
	private Unit noBlock(JCTree node) {
		if (node instanceof JCBlock) {
			return getMethodBody(((JCBlock) node).stats);
		}
		else
			return getHead(node);
	}
	
	/**
	 * Creates a method reference to an existing method
	 * @param node				node containing a method invocation
	 * @param parameterTypes	list of parameter types
	 * @return					reference to matching method
	 */
	private SootMethodRef getMethodRef(JCTree node, List<Type> parameterTypes) {
		if (node instanceof JCIdent)
			return thisMethod.getDeclaringClass().getMethod(node.toString(), parameterTypes).makeRef();
		else { 
			if (((JCFieldAccess)node).selected instanceof JCIdent) {
				if (JavaUtil.getPackage((JCIdent)((JCFieldAccess)node).selected,deps)!=null) {
					String packagename=JavaUtil.getPackage((JCIdent)((JCFieldAccess)node).selected,deps);
					SootClass klass=Scene.v().getSootClass(packagename);
					SootMethod method=searchMethod(klass,((JCFieldAccess)node).name.toString(),parameterTypes);
					return method.makeRef();
				}
				else {
					String packagename=locals.get((((JCFieldAccess)node).selected).toString()).getType().toString();
					SootClass klass=Scene.v().getSootClass(packagename);
					SootMethod method=searchMethod(klass,((JCFieldAccess)node).name.toString(),parameterTypes);
					return method.makeRef();
				}
			}
			else if (((JCFieldAccess)node).selected instanceof JCMethodInvocation){
				Value access=getMethodInvocation((JCMethodInvocation)((JCFieldAccess)node).selected);
				SootClass klass=Scene.v().getSootClass(access.getType().toString());
				SootMethod method=searchMethod(klass,((JCFieldAccess)node).name.toString(), parameterTypes);
				return method.makeRef();
			}
			else if (((JCFieldAccess)node).selected instanceof JCFieldAccess){		//TODO FieldAccess allgemein?	
				JCFieldAccess fieldnode=(JCFieldAccess)((JCFieldAccess)node).selected;
				String packagename=JavaUtil.getPackage((JCIdent)fieldnode.selected, deps);
				String fieldname=fieldnode.name.toString();
				SootFieldRef klassref=Scene.v().getSootClass(packagename).getFieldByName(fieldname).makeRef();
				Value staticRef=Jimple.v().newStaticFieldRef(klassref);		//TODO chain-field access
				Local refLocal=locGen.generateLocal(staticRef.getType());
				Unit refAssign=Jimple.v().newAssignStmt(refLocal, staticRef);
				units.add(refAssign);
				locals.put(refLocal.getName(), refLocal);
				packagename=refLocal.getType().toString();
				SootClass klass=Scene.v().getSootClass(packagename);
				SootMethod method=searchMethod(klass,((JCFieldAccess)node).name.toString(), parameterTypes);
				return method.makeRef();
			} 
			else
				throw new AssertionError("Can't find method " + node.toString() + " " + parameterTypes.toString());
		}
	}
	
	/**
	 * Searches for a matching method, considers all superclasses and interfaces
	 * @param klass				the base class, where either the class itself or its superclass contaings the method
	 * @param methodname		name of the wanted method
	 * @param parameterTypes	types of the parameter, which can contain superclasses and interfaces
	 * @return					the matching method
	 */
	private SootMethod searchMethod(SootClass klass, String methodname, List<Type> parameterTypes) {
		if (klass.declaresMethod(methodname, parameterTypes))		//class itself has the method with matching parameters
			return klass.getMethod(methodname,parameterTypes);
		else 
		{
			SootClass currentclass=klass;
			while (currentclass.hasSuperclass() && !currentclass.declaresMethod(methodname, parameterTypes))
				currentclass=currentclass.getSuperclass();
			if (currentclass.declaresMethod(methodname, parameterTypes))		//go through all superclasses with exact parameter types
				return currentclass.getMethod(methodname, parameterTypes);
			else
			{
				currentclass=klass;
				List<SootMethod> methodlist=currentclass.getMethods();			//search in class itself with supertypes/interfaces of parameter
				for (int j=0; j<methodlist.size(); j++) {
					if (methodlist.get(j).getName().equals(methodname)) {
						SootMethod method=methodlist.get(j);
						List<Type> paras=method.getParameterTypes();
						boolean matches=false;
						if (paras.size()==parameterTypes.size()) {
								matches=true;
								for (int i=0; i<paras.size(); i++) {
									if (paras.get(i) instanceof PrimType)
										matches=matches &&parameterTypes.get(i).equals(paras.get(i));
									else
									{
										if (paras.get(i) instanceof RefType) {
											if (parameterTypes.get(i) instanceof RefType) {
												RefType rt=(RefType)parameterTypes.get(i);
												Chain<SootClass> interfaces = rt.getSootClass().getInterfaces();
												while ((!rt.equals(paras.get(i)))&&rt.getSootClass().hasSuperclass()&&!(interfaces.contains(((RefType)paras.get(i)).getSootClass()))) {
													rt=rt.getSootClass().getSuperclass().getType();
													interfaces=rt.getSootClass().getInterfaces();
												}
												matches=matches && (rt.equals(paras.get(i))||interfaces.contains(((RefType)paras.get(i)).getSootClass()));
											}
											else
												matches=false;
											
										}
										else
										{
											if (paras.get(i) instanceof ArrayType) {
												if (parameterTypes.get(i) instanceof ArrayType) {
													RefType at=(RefType)((ArrayType) parameterTypes.get(i)).baseType;
													Chain<SootClass> interfaces = at.getSootClass().getInterfaces();
													while ((!at.equals(paras.get(i)))&&at.getSootClass().hasSuperclass()&&!(interfaces.contains(((RefType)paras.get(i)).getSootClass()))) {
														at=at.getSootClass().getSuperclass().getType();
														interfaces=at.getSootClass().getInterfaces();
													}
													matches=matches && (at.equals(paras.get(i))||interfaces.contains(((RefType)paras.get(i)).getSootClass()));
												}
												else
													matches=false;
											}
										}
							
									}
								}
								if (matches)
									return method;
						}
					}
				}
				while (currentclass.hasSuperclass()) {				//search in superclasses for method with supertypes/interfaces of parameter
					currentclass=currentclass.getSuperclass();
					methodlist=currentclass.getMethods();
					for (int j=0; j<methodlist.size(); j++) {
						if (methodlist.get(j).getName().equals(methodname)) {
							SootMethod method=methodlist.get(j);
							List<Type> paras=method.getParameterTypes();
							boolean matches=false;
							if (paras.size()==parameterTypes.size()) {
								matches=true;
								for (int i=0; i<paras.size(); i++) {
									if (paras.get(i) instanceof PrimType)
										matches=matches &&parameterTypes.get(i).equals(paras.get(i));
									else
									{
										if (paras.get(i) instanceof RefType) {
											if (parameterTypes.get(i) instanceof PrimType)
												continue;
											RefType rt=(RefType)parameterTypes.get(i);
											Chain<SootClass> interfaces = rt.getSootClass().getInterfaces();
											while ((!rt.equals(paras.get(i)))&&rt.getSootClass().hasSuperclass()&&!(interfaces.contains(((RefType)paras.get(i)).getSootClass()))) {
												rt=rt.getSootClass().getSuperclass().getType();
												interfaces=rt.getSootClass().getInterfaces();
											}
									
											matches=matches && (parameterTypes.get(i).equals(paras.get(i))||interfaces.contains(((RefType)paras.get(i)).getSootClass()));
										}
									}
							
								}
						}
						if (matches)
							return method;
						}
					}
				}
			}
		}
		throw new AssertionError("Can't find method \"" + methodname + "\" in class \"" + klass.toString() + "\" or its superclasses" );
	}
	
	
	/**
	 * Delets all nop-statements used as a placeholder for jumps
	 * @param jb	jimple-body containing the method
	 */
	private void deleteNops(JimpleBody jb) {
		Iterator<Unit> iterator=jb.getUnits().iterator();
		ArrayList<Unit> list=new ArrayList<>();
		while (iterator.hasNext()) {
			Unit unit=iterator.next();
			if (unit instanceof NopStmt)
				list.add(unit);
		}
		jb.getUnits().removeAll(list);
	}
	
	/**
	 * Checks if the node is an unary operation and returns true if its a prefix-increment/decrement
	 * @param node	node containing the calculation
	 * @return		true, if its a prefix-calculation, else false
	 */
	private boolean pre(JCTree node) {
		if (node.toString().charAt(0)=='+' || node.toString().charAt(0)=='-')
			return true;
		return false;
	}
	
	/**
	 * Checks if the node is an unary operation and returns true if its a postfix-increment/decrement
	 * @param node	node containing the calculation
	 * @return		true, if its a postfix-calculation, else false
	 */
	private boolean post(JCTree node) {
		if (node.toString().charAt(node.toString().length()-1)=='+' || node.toString().charAt(node.toString().length()-1)=='-')
			return true;
		return false;
	}
	
	/**
	 * Parens- and ExpressionStatment-nodes are irrelevant so ignore them
	 * @param node	node to check if its a Parens- or ExpressionStatement-node
	 * @return		child-node if it is one of them, else the node itself
	 */
	private JCTree ignoreNode(JCTree node) {
		if (node instanceof JCParens)
			return ((JCParens)node).expr;
		if (node instanceof JCExpressionStatement)
			return ((JCExpressionStatement)node).expr;
		return node;
	}
	
	/**
	 * Search for last added local with a reference to the given class
	 * @param ref	classname
	 * @return		last local with reference to class
	 */
	private Local getLastRefLocal(String ref) {
		Iterator<Local> iter = locals.values().iterator();
		Local ret=null;
		while (iter.hasNext()) {
			Local next=iter.next();
			if (next.getType().toString().equals(ref))
				ret=next;
		}
		return ret;
	}
	

}
