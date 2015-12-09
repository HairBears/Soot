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
		
		//test
		//Funktion, gibt nur uebergebenen Wert zurueck
		if (m.getName().equals("ret")) {
			
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
			Unit invoke=new JInvokeStmt(Jimple.v().newVirtualInvokeExpr(refLocal, Scene.v().makeMethodRef(Scene.v().getSootClass("java.io.PrintStream"), "println", parameterTypes, VoidType.v(), false), exampleLocal2));
			jb.getLocals().add(refLocal);
			jb.getUnits().add(refAssign);
			

			
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
			Unit go=Jimple.v().newGotoStmt(invoke);
			jb.getUnits().add(ifStmt);
			jb.getUnits().add(go);
			jb.getUnits().add(true2);
			
			//Aufruf erst am Schluss ausfuehren
			jb.getUnits().add(invoke);
			
			
			//leeres return
			Unit ret=Jimple.v().newReturnVoidStmt();
			jb.getUnits().add(ret);
		}
		return jb;
	}

}
