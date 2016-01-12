package soot.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;

import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.MethodSource;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.BinopExpr;
import soot.jimple.Constant;
import soot.jimple.DoubleConstant;
import soot.jimple.FloatConstant;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LongConstant;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.internal.JimpleLocal;

public class JavaMethodSource implements MethodSource {
	
	//Saving complete method-tree
	JCMethodDecl meth;
	//Saving locals, easier to find with name
	HashMap<String,Local> locals=new HashMap<>();
	//Saving units
	ArrayList<Unit> units=new ArrayList<>();
	//Counter used to name variables that are only used in jimple-part
	int varCount=0;
	
	public JavaMethodSource(JCMethodDecl body) {
		this.meth=body;
	}
	

	@Override
	public Body getBody(SootMethod m, String phaseName) {
		JimpleBody jb = Jimple.v().newBody(m);
		getParameter(m, meth.params);
		getMethodBody(meth.body.stats);
		
		//Funktion, gibt nur uebergebenen Wert zurueck
	/*	if (m.getName().equals("ret")) {
			
			//this-local
			Local thisLocal=new JimpleLocal("thisLocal",m.getDeclaringClass().getType());
			Value returnValue=Jimple.v().newParameterRef(IntType.v(), 0);
			Unit thisIdent=Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(m.getDeclaringClass().getType()));
			jb.getLocals().add(thisLocal);
			jb.getUnits().add(thisIdent);
		
			//Parameter nehmen, zwischenspeichern, zurueckgeben
			Local returnLocal=new JimpleLocal("returnParam",IntType.v());
			Unit assign=Jimple.v().newIdentityStmt(returnLocal, returnValue);
			Unit ret=Jimple.v().newReturnStmt(returnLocal);
			jb.getLocals().add(returnLocal);
			jb.getUnits().add(assign);
			jb.getUnits().add(ret);
		
		}		
		else {
		if (m.getName().equals("r2")) {
			Local returnLocal=new JimpleLocal("returnNumber",IntType.v());
			IntConstant const3=IntConstant.v(3);
			Unit assign=Jimple.v().newAssignStmt(returnLocal, const3);
			Unit ret=Jimple.v().newReturnStmt(returnLocal);
			jb.getLocals().add(returnLocal);
			jb.getUnits().add(assign);
			jb.getUnits().add(ret);
		}
		else {
			Type integer=IntType.v();
			
			
			//int a,b; b=5; a=b;
			Local exampleLocal1=new JimpleLocal("exL1",integer);
			Local exampleLocal2=new JimpleLocal("exL2",integer);
			IntConstant const5=IntConstant.v(5);
			Unit assignConst=Jimple.v().newAssignStmt(exampleLocal1, const5);
			Unit assignLocals=Jimple.v().newAssignStmt(exampleLocal2,exampleLocal1);
			jb.getLocals().add(exampleLocal1);
			jb.getLocals().add(exampleLocal2);
			jb.getUnits().add(assignConst);	
			jb.getUnits().add(assignLocals);
		
			
			//Variable PrintStream, fuellen, println aufrufen mit "exL2"
			RefType ref=RefType.v("java.io.PrintStream");
			Local refLocal=new JimpleLocal("out", ref);
			Value staticRef=Jimple.v().newStaticFieldRef(Scene.v().makeFieldRef(Scene.v().getSootClass("java.lang.System"), "out", ref, true));
			Unit refAssign=Jimple.v().newAssignStmt(refLocal, staticRef);
			List<Type> parameterTypes=new ArrayList<>();
			parameterTypes.add(integer);
			Unit vinvoke=Jimple.v().newInvokeStmt((Jimple.v().newVirtualInvokeExpr(refLocal, Scene.v().makeMethodRef(Scene.v().getSootClass("java.io.PrintStream"), "println", parameterTypes, VoidType.v(), false), exampleLocal2)));
			jb.getLocals().add(refLocal);
			jb.getUnits().add(refAssign);
			
			
			//staticInvoke
			Value sInvokeValue=Jimple.v().newStaticInvokeExpr(m.getDeclaringClass().getMethodByName("r2").makeRef());
			Unit assignStatic=Jimple.v().newAssignStmt(exampleLocal1, sInvokeValue);
			jb.getUnits().add(assignStatic);
			

			
			//new PrintStream
			Local ref2Local=new JimpleLocal("newPS",ref);
			Value newUnit=Jimple.v().newNewExpr(ref);
			Unit newAssign=Jimple.v().newAssignStmt(ref2Local, newUnit);
			jb.getLocals().add(ref2Local);
			jb.getUnits().add(newAssign);
			
		
			//if (a==b) a=a+5; else ueberspringen
			Value eq=Jimple.v().newEqExpr(exampleLocal1, exampleLocal2);
			Value truepart=Jimple.v().newAddExpr(exampleLocal2, const5);
			Unit true2=Jimple.v().newAssignStmt(exampleLocal2, truepart);
			Unit ifStmt=Jimple.v().newIfStmt(eq, true2);
			Unit go=Jimple.v().newGotoStmt(vinvoke);
			jb.getUnits().add(ifStmt);
			jb.getUnits().add(go);
			jb.getUnits().add(true2);
			
			//Aufruf erst am Schluss ausfuehren
			jb.getUnits().add(vinvoke);
			
			
			//Trap
			SootClass throwable=Scene.v().getSootClass("java.lang.Throwable");
			Trap trap=Jimple.v().newTrap(throwable, assignConst, assignLocals, vinvoke);
			jb.getTraps().add(trap);
			
			
			
			
			//leeres return
			Unit ret=Jimple.v().newReturnVoidStmt();
			jb.getUnits().add(ret);
		}}	
									*/
		
		jb.getLocals().addAll(locals.values());
		jb.getUnits().addAll(units);
		if (!(units.get(units.size()-1) instanceof ReturnStmt))
			jb.getUnits().add(Jimple.v().newReturnVoidStmt()); //TODO ??
		
		return jb;
	}
	
	/**
	 * Part unit-list into next unit and list of other units
	 * @param stats	list of units
	 */
	private Unit getMethodBody(com.sun.tools.javac.util.List<JCStatement> stats) {
		Unit ret=getHead(stats.head);
		if (stats.tail!=null)
			getMethodBody(stats.tail);
		return ret;
	}
	
	/**
	 * Check what kind of node the parameter is
	 * @param node	current node to translate
	 * @return 		node translated to jimple-unit
	 */
	private Unit getHead(JCTree node) {
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
		if (node instanceof JCExpressionStatement){
			if (((JCExpressionStatement)node).expr instanceof JCMethodInvocation)
				return addMethodInvocation((JCMethodInvocation)((JCExpressionStatement)node).expr);
			if (((JCExpressionStatement)node).expr instanceof JCAssignOp)
				return addAssignOp((JCAssignOp)((JCExpressionStatement)node).expr);
			if (((JCExpressionStatement)node).expr instanceof JCAssign)
				return addAssign((JCAssign)((JCExpressionStatement)node).expr);
		}
		return null;
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
			return locals.get(((JCIdent)node).toString());
		if (node instanceof JCLiteral)
			return getConstant((JCLiteral) node);
	//	if (node instanceof JCUnary)
	//TODO		return getUnary((JCUnary)node);
		return null;		//TODO
	}

	
	private Unit addMethodInvocation(JCMethodInvocation node) {		//TODO
		RefType ref=RefType.v("java.io.PrintStream");
		while (locals.get("i"+varCount)!=null)
			varCount++;
		Local refLocal=new JimpleLocal("i"+(varCount++), ref);
		Value staticRef=Jimple.v().newStaticFieldRef(Scene.v().makeFieldRef(Scene.v().getSootClass("java.lang.System"), "out", ref, true));
		Unit refAssign=Jimple.v().newAssignStmt(refLocal, staticRef);
		List<Type> parameterTypes=new ArrayList<>();
		parameterTypes.add(IntType.v());
		Unit methodinvoke=Jimple.v().newInvokeStmt((Jimple.v().newVirtualInvokeExpr(refLocal, Scene.v().makeMethodRef(Scene.v().getSootClass("java.io.PrintStream"), "println", parameterTypes, VoidType.v(), false), checkBinary(getValue(node.args.head)))));
		
		locals.put(refLocal.getName(), refLocal);
		units.add(refAssign);
		units.add(methodinvoke);
		return methodinvoke;
	}

//TODO
	/**
	 * Translate if-statement from tree to jimple
	 * @param node	node containing information for if-statement
	 * @return		if-statement in jimple
	 */
	private Unit addIf(JCIf node) {
		Value condition=getValue(((JCParens)node.cond).expr);
		Unit target=noBlock(node.thenpart);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		units.remove(target);
		units.add(ifstmt);
		
		Unit elsepart=null;
		Unit target2=null;
		if (node.elsepart!=null) {
			target2=getHead(node.elsepart);
			elsepart=Jimple.v().newGotoStmt(target2);
			units.add(elsepart);
		}
		units.add(target);
		return ifstmt;
	}

	/**
	 * Translate a do-while-loop into a if-jump-loop
	 * @param node	node containing do-while-loop
	 * @return		if-statement at the end
	 */
	private Unit addDoWhile(JCDoWhileLoop node) {
		Value condition=getValue(((JCParens)node.cond).expr);
		Unit target=noBlock(node.body);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		units.add(ifstmt);
		return ifstmt;
	}
	
	/**
	 * Translate while-loop into a if-jump-loop
	 * @param node	node containing while-loop
	 * @return		initial jump-statement
	 */
	private Unit addWhile(JCWhileLoop node) {
		Value condition=getValue(((JCParens)node.cond).expr);
		Unit target=noBlock(node.body);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		Unit jump=Jimple.v().newGotoStmt(ifstmt);
		units.add(jump);
		units.remove(target);
		units.add(target);
		units.add(ifstmt);
		return jump;
	}
	
	/**
	 * Translate a for-loop into a if-jump-loop with counter
	 * @param node	node containing for-loop
	 * @return		initial declaration for the counter
	 */
	private Unit addFor(JCForLoop node) {
		Unit counter=addVariableDecl((JCVariableDecl)node.init.head);	//TODO 1. List, 2. nicht unbedingt VariableDecl
		Value condition=getValue(node.cond);
		Unit target=noBlock(node.body);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		Unit jump=Jimple.v().newGotoStmt(ifstmt);
		Unit step=getHead(node.step.head); //TODO List<JCExpressionStatement>
		units.add(jump);
		units.remove(target);
		units.add(target);
		units.remove(step);
		units.add(step);
		units.add(ifstmt);
		return counter;
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
		Local var=locals.get(((JCIdent)node.lhs).toString());
		Value right=getValue(node.rhs);
		Unit assign=Jimple.v().newAssignStmt(var, right);
		units.add(assign);
		return assign;
	}

	/**
	 * Translate variable declaration from tree to jimple
	 * @param node	node containing information for variable declaration
	 * @return 		if variable is assigned instantly, return assign-statement, else return null
	 */
	private Unit addVariableDecl(JCVariableDecl node) {
		Local newLocal=new JimpleLocal(node.name.toString(),getType((JCPrimitiveTypeTree)node.vartype));
		Value con=getValue(node.init);
		
		locals.put(newLocal.getName(),newLocal);
		if (con!=null) {
			Unit assign=Jimple.v().newAssignStmt(newLocal, con);
			units.add(assign);
			return assign;
		}
		else
			return null;
	}
	
	/**
	 * Break combined operations into binary expression and assign
	 * @param node	node containing variable, operation and one other value
	 * @return		combined operation as normal assign-statement
	 */
	private Unit addAssignOp(JCAssignOp node) {
		Local var=locals.get(((JCIdent)node.lhs).toString());
		Value binary;
		Value right=getValue(node.rhs);
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
			binary=null;
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
		else if (findOperator.charAt(2)=='>' && findOperator.charAt(1)=='=')
			return Jimple.v().newGeExpr(left, right);
		else if (findOperator.charAt(2)=='>' && findOperator.charAt(1)=='>')
			return Jimple.v().newShrExpr(left, right);
		else if (findOperator.charAt(1)=='>')
			return Jimple.v().newGtExpr(left, right);
		else if (findOperator.charAt(2)=='<' && findOperator.charAt(1)=='=')
			return Jimple.v().newLeExpr(left, right);
		else if (findOperator.charAt(2)=='<' && findOperator.charAt(1)=='<')
			return Jimple.v().newShlExpr(left, right);
		else if (findOperator.charAt(1)=='<')
			return Jimple.v().newLtExpr(left, right);
		else if (findOperator.charAt(2)=='=' && findOperator.charAt(1)=='=')
			return Jimple.v().newEqExpr(left, right);
		else if (findOperator.charAt(2)=='!' && findOperator.charAt(1)=='=')
			return Jimple.v().newNeExpr(left, right);
		else
			return null;
	}
	
	private Value getUnary(JCUnary node) {
		Value value=getValue(((JCParens)node.arg).expr);
		String findOperator=node.toString();
		if (findOperator.charAt(0)=='!')
			return Jimple.v().newNegExpr(checkBinary(value));
		if (findOperator.charAt(findOperator.length()-2)=='+'&&findOperator.charAt(findOperator.length()-1)=='+')
			return null; //TODO
		return null;
	}

	/**
	 * Checks, which type the node has
	 * @param node	node with a simple type
	 * @return		matching jimple-type
	 */
	private Type getType (JCPrimitiveTypeTree node) {
		if (node.typetag.name().equals("INT"))
			return IntType.v();
		if (node.typetag.name().equals("CHAR"))
			return CharType.v();
		if (node.typetag.name().equals("BOOLEAN"))
			return BooleanType.v();
		if (node.typetag.name().equals("BYTE"))
			return ByteType.v();
		if (node.typetag.name().equals("DOUBLE"))
			return DoubleType.v();
		if (node.typetag.name().equals("FLOAT"))
			return FloatType.v();
		if (node.typetag.name().equals("LONG"))
			return LongType.v();
		if (node.typetag.name().equals("SHORT"))
			return ShortType.v();
		return null;
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
		return NullConstant.v();
	}
	
	/**
	 * Checks, if the value is a binary operation. 
	 * If yes, create a new jimple-local to save the interim result
	 * @param val	value to check
	 * @return		the new jimple-local or the value from the parameter
	 */
	private Value checkBinary(Value val) {
		if (val instanceof BinopExpr) {
			while (locals.get("i"+varCount)!=null)
				varCount++;
			Local newLocal=new JimpleLocal("i"+(varCount++),val.getType());
			locals.put(newLocal.getName(),newLocal);
			Unit assign=Jimple.v().newAssignStmt(newLocal, val);
			units.add(assign);
			return newLocal;
		}
		else
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
		}
		int paramcount=0;
		while(params.head!=null&&!(params.head.vartype instanceof JCArrayTypeTree)) {	//TODO instanceof wegmachen, array in body hinzufuegen
			Value parameter=Jimple.v().newParameterRef(getType((JCPrimitiveTypeTree)params.head.vartype), paramcount++);
			Local paramLocal=new JimpleLocal(params.head.name.toString(),getType((JCPrimitiveTypeTree)params.head.vartype));
			Unit assign=Jimple.v().newIdentityStmt(paramLocal, parameter);
			locals.put(paramLocal.getName(), paramLocal);
			units.add(assign);
			params=params.tail;
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

}
