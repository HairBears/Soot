package soot.java;

import java.util.ArrayList;
import java.util.List;

import soot.Body;
import soot.IntType;
import soot.MethodSource;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.VoidType;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JimpleLocal;

public class JavaMethodSource implements MethodSource {

	@Override
	public Body getBody(SootMethod m, String phaseName) {
		JimpleBody jb = Jimple.v().newBody(m);
		
		
		IntType integer=IntType.v();
		
		JimpleLocal b=new JimpleLocal("b",integer);
		JimpleLocal a=new JimpleLocal("a",integer);
		
		IntConstant c=IntConstant.v(5);
		
	
		jb.getLocals().add(a);
		jb.getLocals().add(b);
		
		Unit u2=Jimple.v().newAssignStmt(b, c);
		
		Unit u=Jimple.v().newAssignStmt(a,b);
		
		jb.getUnits().add(u2);
		jb.getUnits().add(u);					
		
		RefType ref=RefType.v("java.io.PrintStream");
		
		JimpleLocal d=new JimpleLocal("output",ref);
		
		jb.getLocals().add(d);
		
		List<Type> parameterTypes=new ArrayList<>();
		parameterTypes.add(integer);
		
		JInvokeStmt e=new JInvokeStmt(Jimple.v().newVirtualInvokeExpr(d, Scene.v().makeMethodRef(new SootClass("java.io.PrintStream"), "println", parameterTypes, VoidType.v(), false), a));

		jb.getUnits().add(e);
		
		return jb;
	}

}
