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
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
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
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JimpleLocal;

public class JavaMethodSource implements MethodSource {
	
	JCTree.JCBlock body;
	HashMap<String,Local> locals=new HashMap<>();
	ArrayList<Unit> units=new ArrayList<>();
	int varCount=0;
	
	public JavaMethodSource(JCBlock body) {
		this.body=body;
	}
	

	@Override
	public Body getBody(SootMethod m, String phaseName) {
		JimpleBody jb = Jimple.v().newBody(m);
		
		getMethodBody(body.stats);
		
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
		
		jb.getUnits().add(Jimple.v().newReturnVoidStmt()); //TODO ??
		
		return jb;
	}
	
	private void getMethodBody(com.sun.tools.javac.util.List<JCStatement> stats) {
		getHead(stats.head);
		if (stats.tail!=null) {
		getMethodBody(stats.tail);
		}
	}
	
	private Unit getHead(JCTree node) {
		if (node instanceof JCVariableDecl)
			return addVariableDecl((JCVariableDecl)node);
		if (node instanceof JCIf) 
			return addIf((JCIf)node);
		
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

	private Value getValue(JCTree node) {
		if (node instanceof JCBinary)
			return getBinary((JCBinary)node);
		if (node instanceof JCIdent) 
			return locals.get(((JCIdent)node).toString());
		if (node instanceof JCLiteral)
			return getConstant((JCLiteral) node);
		return null;		//TODO
	}

	private Unit addMethodInvocation(JCMethodInvocation node) {		//TODO
		RefType ref=RefType.v("java.io.PrintStream");
		Local refLocal=new JimpleLocal("out", ref);
		Value staticRef=Jimple.v().newStaticFieldRef(Scene.v().makeFieldRef(Scene.v().getSootClass("java.lang.System"), "out", ref, true));
		Unit refAssign=Jimple.v().newAssignStmt(refLocal, staticRef);
		List<Type> parameterTypes=new ArrayList<>();
		parameterTypes.add(IntType.v());
		Unit methodinvoke=Jimple.v().newInvokeStmt((Jimple.v().newVirtualInvokeExpr(refLocal, Scene.v().makeMethodRef(Scene.v().getSootClass("java.io.PrintStream"), "println", parameterTypes, VoidType.v(), false), locals.get("var1"))));
		
		locals.put(refLocal.getName(), refLocal);
		units.add(refAssign);
		units.add(methodinvoke);
		return methodinvoke;
	}

//TODO
	private Unit addIf(JCIf node) {
		Value condition=getBinary((JCBinary)((JCParens)node.cond).expr);
		Unit target=getHead(node.thenpart);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		units.add(ifstmt);
		Unit elsepart=null;
		Unit target2=null;
		if (node.elsepart!=null) {
			target2=getHead(node.elsepart);
			elsepart=Jimple.v().newGotoStmt(target2);
			units.add(elsepart);
		}
	//	units.add(target);
	//	if (node.elsepart!=null)
	//		units.add(target2);
		
		return ifstmt;
	}


	private Unit addAssign(JCAssign node) {
		Local var=locals.get(((JCIdent)node.lhs).toString());
		Value right=getValue(node.rhs);
		Unit assign=Jimple.v().newAssignStmt(var, right);
		units.add(assign);
		return assign;
	}
	
	


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
	
	private Value checkBinary(Value val) {
		if (val instanceof BinopExpr) {
			while (locals.get("var"+varCount)!=null)
				varCount++;
			Local newLocal=new JimpleLocal("var"+(varCount++),val.getType());
			locals.put(newLocal.getName(),newLocal);
			Unit assign=Jimple.v().newAssignStmt(newLocal, val);
			units.add(assign);
			return newLocal;
		}
		else
			return val;
	}

}
