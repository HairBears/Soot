package soot.java;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class AbstractTest {

	@Rule public ExpectedException thrown = ExpectedException.none();
	
	@Test
	public void Test() throws MalformedURLException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException {
		
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
		// load class
		Class<?> klass = c.loadClass(this.getTarget());
		// invoke test method
		Method m = klass.getMethod("Test", new Class<?>[0]);
		Object result = m.invoke(klass.newInstance(), new Object[0]);
		assertTrue((boolean) result);
	}
	
	private String getTarget() {
		String path = this.getClass().getSimpleName();
		path = path.substring(0, path.length()-4);
		return path;
	}

}
