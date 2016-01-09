package soot.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;

import soot.Body;
import soot.CharType;
import soot.IntType;
import soot.Local;
import soot.MethodSource;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JimpleLocal;

public class JavaMethodSource implements MethodSource {
	
	JCTree.JCBlock body;
	HashMap<String,Local> locals=new HashMap<>();
	ArrayList<Unit> units=new ArrayList<>();
	
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
	
	private void getHead(JCTree node) {
		if (node instanceof JCVariableDecl)
			addVariableDecl((JCVariableDecl)node);
		if (node instanceof JCIf) 
			addIf((JCIf)node);
		if (node instanceof JCExpressionStatement)
		if (((JCExpressionStatement)node).expr instanceof JCMethodInvocation)
			addMethodInvocation((JCMethodInvocation)((JCExpressionStatement)node).expr);
	}


	private Unit addMethodInvocation(JCMethodInvocation node) {		//TODO
		RefType ref=RefType.v("java.io.PrintStream");
		Local refLocal=new JimpleLocal("out", ref);
		Value staticRef=Jimple.v().newStaticFieldRef(Scene.v().makeFieldRef(Scene.v().getSootClass("java.lang.System"), "out", ref, true));
		Unit refAssign=Jimple.v().newAssignStmt(refLocal, staticRef);
		List<Type> parameterTypes=new ArrayList<>();
		parameterTypes.add(IntType.v());
		Unit methodinvoke=Jimple.v().newInvokeStmt((Jimple.v().newVirtualInvokeExpr(refLocal, Scene.v().makeMethodRef(Scene.v().getSootClass("java.io.PrintStream"), "println", parameterTypes, VoidType.v(), false), locals.get("a"))));
		
		locals.put(refLocal.getName(), refLocal);
		units.add(refAssign);
		units.add(methodinvoke);
		return methodinvoke;
	}


	private Unit addIf(JCIf node) {
		Value condition=getBinary((JCBinary)((JCParens)node.cond).expr);
		
		
		Unit target=addAssign((JCAssign)((JCExpressionStatement)node.thenpart).expr);
		IfStmt ifstmt=Jimple.v().newIfStmt(condition, target);
		units.add(ifstmt);
		Unit elsepart=null;
		Unit target2=null;
		if (node.elsepart!=null) {
			target2=addAssign((JCAssign)((JCExpressionStatement)node.elsepart).expr);	
			elsepart=Jimple.v().newGotoStmt(target2);
			units.add(elsepart);
		}
		units.add(target);
		if (node.elsepart!=null)
			units.add(target2);
		
		return ifstmt;
	}


	private Unit addAssign(JCAssign node) {
		Local var=locals.get(((JCIdent)node.lhs).toString());
		Value right=getBinary((JCBinary)node.rhs);
		Unit assign=Jimple.v().newAssignStmt(var, right);
	//	units.add(assign);
		return assign;
	}


	private Unit addVariableDecl(JCVariableDecl node) {
		Local newLocal=new JimpleLocal(node.name.toString(),getType((JCPrimitiveTypeTree)node.vartype));
		Value con;
		if (node.init instanceof JCLiteral) {
			con=IntConstant.v((int)((JCLiteral)node.init).value);
		}
		else
		{
			con=getBinary((JCBinary)node.init);
		}
		Unit assign=Jimple.v().newAssignStmt(newLocal, con);
		locals.put(newLocal.getName(),newLocal);
		units.add(assign);
		return assign;
	}
	
	private Value getBinary(JCBinary init) {
		Value left;
		Value right;
		if (init.lhs instanceof JCBinary){
			left=getBinary((JCBinary)init.lhs);
		}
		else if (init.lhs instanceof JCIdent)
		{
			left=locals.get(((JCIdent)init.lhs).toString());
		}
		else
		{
			left=IntConstant.v((int)((JCLiteral)init.lhs).value);
		}
		
		if (init.rhs instanceof JCBinary) {
			right=getBinary((JCBinary)init.rhs);
		}
		else if (init.rhs instanceof JCIdent) {
			right=locals.get(((JCIdent)init.rhs).toString());
		}
		else
		{
			right=IntConstant.v((int)((JCLiteral)init.rhs).value);
		}
		String a=init.toString();
		int index=a.lastIndexOf(" ");
		if (a.charAt(index-1)=='+')
			return Jimple.v().newAddExpr(left, right);
		else if (a.charAt(index-1)=='*')
			return Jimple.v().newMulExpr(left, right);
		else if (a.charAt(index-1)=='<')
			return Jimple.v().newLtExpr(left, right);
		else
			return null;
	}


	


	private Type getType (JCPrimitiveTypeTree node) {
		if (node.typetag.name().equals("INT"))
			return IntType.v();
		if (node.typetag.name().equals("CHAR"))
			return CharType.v();
		return null;
	}

}
