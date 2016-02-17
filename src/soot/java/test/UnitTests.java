package soot.java.test;

import soot.G;
import soot.Main;

import java.io.File;
import java.lang.ClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class UnitTests {
	
	public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException{
			String rtJar = System.getProperty("java.home")+File.separator+"lib"+File.separator+"rt.jar";
			// TODO improve finding junit .jar
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			String junitJar = classLoader.getResource(".").getPath();
			junitJar = junitJar.substring(0, junitJar.length()-12);
			junitJar = junitJar + "libs/junit-4.11.jar";
			System.out.println("."+File.pathSeparator+rtJar+File.pathSeparator+junitJar);
			G.reset();
			Main.main(new String[] {
				"-cp", "."+File.pathSeparator+rtJar+File.pathSeparator+junitJar,
				"-pp",
				"-debug-resolver",
				"-f", "class",
				"-validate",
				"-process-dir", getPath()
			});
			Class<?> Basic = ClassLoader.getSystemClassLoader().loadClass("soot.java.test.target.unit.SuiteInit");
			for (Method m : Basic.getMethods()) {
				if(m.getName() == "main") {
					m.invoke(null, null);
				}
			}
	}
	
	protected static String getPath() {
		// TODO dirty quickfix - improve
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String path = classLoader.getResource(".").getPath();
		path = path.substring(0, path.length()-12);
		path = path + "src/soot/java/test/target/unit/";
		return path;
	}
	
}
