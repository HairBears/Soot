package soot.java;

import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import soot.G;
import soot.Main;

/*
 * TODO
 * Not finished
 * Loading the class files after building them with soot does not work yet
 */
public class OldTests {
	
	@Rule public ExpectedException thrown = ExpectedException.none();
	
	@Test
	public void test() throws MalformedURLException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException {
		// build class via soot
		buildClass();
		// TODO load class
//		Class<?> klass = prepareClass();
		// TODO invoke test method
//		Method m = klass.getMethod("main", new Class<?>[0]);
//		m.invoke(null, null);
	}
	
	@After
	public void deleteOutput() {
		// current directory should be ../Soot/testclasses
		String p = ClassLoader.getSystemClassLoader().getResource(".").getPath();
		// remove testclasses
		p = p.substring(0, p.length()-12);
		// add sootOutput directory
		p = p + "sootOutput/";
		// instantiate file
		File f = new File(p);
		for (File file : f.listFiles()) {
			file.delete();
		}
	}
	
	// TODO
	private String getTarget() {
		String path = "TODO";
		return path;
	}
	
	// TODO
	private Class<?> prepareClass() throws MalformedURLException {
		ClassLoader c = prepareClassLoader();
		Class<?> klass = null;
		// load class
		try {
			klass = c.loadClass(this.getTarget());
		} catch (ClassNotFoundException e) {
			fail("Could not find the class " + this.getTarget() + ". Make sure the name is correct "
					+ "and soot was able to process the corresponding source code.");
		}

		return klass;
	}
	
	private ClassLoader prepareClassLoader() throws MalformedURLException {
		// current directory should be ../Soot/testclasses
		String p = ClassLoader.getSystemClassLoader().getResource(".").getPath();
		// remove testclasses
		p = p.substring(0, p.length()-12);
		// add sootOutput directory
		p = p + "sootOutput/";
		// instantiate file
		File f = new File(p);
		// convert file to url and put in an array
		URL[] u = {f.toURI().toURL()};
		// create urlClassLoader with the created url array that contains only one url
		ClassLoader c = URLClassLoader.newInstance(u);
		return c;
	}
	
	private void buildClass() {
			String rtJar = System.getProperty("java.home")+File.separator+"lib"+File.separator+"rt.jar";
			G.reset();
			Main.main(new String[] {
				"-cp", rtJar,
				"-pp",
				//"-debug-resolver",
				"-allow-phantom-refs",
				"-f", "class",
				"-validate",
				"-process-dir",
				getClassFolder()
			});
	}
	
	private String getClassFolder() {
		String p = ClassLoader.getSystemClassLoader().getResource(".").getPath();
		p = p.substring(0, p.length()-12);
		p = p + "systests/java_tests/";
		return p;
	}

}
