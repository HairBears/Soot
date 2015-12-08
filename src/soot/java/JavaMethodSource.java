package soot.java;

import java.util.ArrayList;
import java.util.List;

import soot.Body;
import soot.IntType;
import soot.Local;
import soot.MethodSource;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
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
		
		Type integer=IntType.v();
		Type ref=RefType.v("java.io.PrintStream");
		
		Local exampleLocal1=new JimpleLocal("exL1",integer);
		Local exampleLocal2=new JimpleLocal("exL2",integer);
		Local refLocal=new JimpleLocal("out", ref);
		
		jb.getLocals().add(exampleLocal1);
		jb.getLocals().add(exampleLocal2);
		jb.getLocals().add(refLocal);
		
		IntConstant const5=IntConstant.v(5);
		
		Unit assignLocals=Jimple.v().newAssignStmt(exampleLocal1, const5);
		Unit assignConst=Jimple.v().newAssignStmt(exampleLocal2,exampleLocal1);
		
		Value staticRef=Jimple.v().newStaticFieldRef(Scene.v().makeFieldRef(Scene.v().getSootClass("java.lang.System"), "out", ref, true));
		Unit refAssign=Jimple.v().newAssignStmt(refLocal, staticRef);
		
		List<Type> parameterTypes=new ArrayList<>();
		parameterTypes.add(integer);
		Unit invoke=new JInvokeStmt(Jimple.v().newVirtualInvokeExpr(refLocal, Scene.v().makeMethodRef(Scene.v().getSootClass("java.io.PrintStream"), "println", parameterTypes, VoidType.v(), false), exampleLocal2));
		
		jb.getUnits().add(assignLocals);
		jb.getUnits().add(assignConst);	
		jb.getUnits().add(refAssign);
		jb.getUnits().add(invoke);
		

		
		return jb;
	}

}
