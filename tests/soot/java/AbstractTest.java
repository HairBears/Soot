package soot.java;

import static org.junit.Assert.assertTrue;
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

public abstract class AbstractTest {
	
	@Rule public ExpectedException thrown = ExpectedException.none();
	
	@Test
	public void Test() throws MalformedURLException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException {
		// build class via soot
		buildClass();
		// load class
		Class<?> klass = prepareClass();
		// invoke test method
		Method m = klass.getMethod("test", new Class<?>[0]);
		Object result = m.invoke(klass.newInstance(), new Object[0]);
		assertTrue((boolean) result);
	}
	
	@After
	public void deleteOutput() {
		// current directory should be ../Soot/testclasses
		String p = ClassLoader.getSystemClassLoader().getResource(".").getPath();
		// remove testclasses
		p = p.substring(0, p.length()-12);
		// add sootOutput directory
		p = p + "sootOutput/" +getTarget() + ".class";
		// instantiate file
		File f = new File(p);
		f.delete();
	}
	
	private String getTarget() {
		String path = this.getClass().getSimpleName();
		path = path.substring(0, path.length()-4);
		return path;
	}
	
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
				"-cp", getClassFolder()+File.pathSeparator+rtJar,
				"-pp",
				//"-debug-resolver",
				"-f", "class",
				"-validate",
				"-process-dir",
				getTarget() + ".java"

			});
	}
	
	private String getClassFolder() {
		return "./tests/soot/java/target";
	}

}
