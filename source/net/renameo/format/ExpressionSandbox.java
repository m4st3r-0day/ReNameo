package net.renameo.format;

import static java.util.Arrays.*;

import java.util.HashSet;
import java.util.Set;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

/**
 * Format and filter expressions come from presets that people share, so they must not be able to run programs, change files or escape into reflection. Java 18+ has no security manager any more, so the
 * check happens when the expression is compiled.
 *
 * This is a deny list for the dangerous parts of the JDK and the Groovy runtime, not a proof of safety: expressions stay a trusted-ish feature, but a pasted preset can no longer delete your library.
 */
public class ExpressionSandbox extends SecureASTCustomizer {

	private static final Set<String> METHODS = new HashSet<String>(asList(
			// processes and the JVM
			"execute", "exec", "exit", "halt", "load", "loadLibrary", "addShutdownHook", "setSecurityManager", "consumeProcessOutput", "waitForProcessOutput",
			// writing, moving and deleting files
			"delete", "deleteDir", "deleteOnExit", "renameTo", "moveTo", "move", "copy", "createFile", "createDirectory", "createDirectories", "mkdir", "mkdirs", "write", "withWriter", "withWriterAppend",
			"withOutputStream", "withPrintWriter", "withDataOutputStream", "withObjectOutputStream", "newWriter", "newOutputStream", "newPrintWriter", "newDataOutputStream", "newObjectOutputStream", "setText",
			"setBytes", "append", "setLastModified", "setReadOnly", "setWritable", "setExecutable", "setReadable", "leftShift", "transferTo",
			// reflection, class loading and dynamic evaluation
			"forName", "getClassLoader", "loadClass", "defineClass", "newInstance", "getDeclaredMethod", "getDeclaredMethods", "getDeclaredField", "getDeclaredFields", "getDeclaredConstructor", "setAccessible",
			"getMethod", "getField", "getConstructor", "invoke", "invokeMethod", "setProperty", "setMetaClass", "getMetaClass", "evaluate", "parseClass", "setenv", "clearProperty"));

	private static final Set<String> PROPERTIES = new HashSet<String>(asList("metaClass", "class", "classLoader", "declaredMethods", "declaredFields", "runtime"));

	// readable, but assigning them writes the file
	private static final Set<String> WRITE_PROPERTIES = new HashSet<String>(asList("text", "bytes"));

	private static final Set<String> CLASSES = new HashSet<String>(asList(
			"java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.Process", "java.lang.ProcessHandle", "java.lang.System", "java.lang.Thread", "java.lang.ClassLoader", "java.lang.Class",
			"java.lang.reflect.Method", "java.lang.reflect.Field", "java.lang.reflect.Constructor", "java.lang.invoke.MethodHandles", "java.io.FileOutputStream", "java.io.FileWriter", "java.io.PrintWriter",
			"java.io.RandomAccessFile", "java.io.ObjectInputStream", "java.nio.file.Files", "java.nio.channels.FileChannel", "java.net.Socket", "java.net.ServerSocket", "java.net.URLClassLoader",
			"groovy.lang.GroovyShell", "groovy.lang.GroovyClassLoader", "groovy.util.Eval", "groovy.lang.GroovySystem", "groovy.lang.MetaClass", "groovy.lang.ExpandoMetaClass", "javax.script.ScriptEngineManager",
			"org.codehaus.groovy.runtime.InvokerHelper", "sun.misc.Unsafe", "jdk.internal.misc.Unsafe"));

	public ExpressionSandbox() {
		setIndirectImportCheckEnabled(true);
		setImportsBlacklist(asList(CLASSES.toArray(new String[0])));
		setStarImportsBlacklist(asList("java.lang.reflect.*", "java.lang.invoke.*", "java.nio.file.*", "java.net.*", "groovy.lang.*", "sun.misc.*", "jdk.internal.*"));
		setReceiversClassesBlackList(asList(classes()));
		addExpressionCheckers(ExpressionSandbox::isAllowed);
	}

	private static Class<?>[] classes() {
		return CLASSES.stream().map(name -> {
			try {
				return Class.forName(name, false, ExpressionSandbox.class.getClassLoader());
			} catch (Throwable e) {
				return null;
			}
		}).filter(c -> c != null).toArray(Class<?>[]::new);
	}

	private static boolean isAllowed(Expression e) {
		if (e instanceof MethodCallExpression) {
			// "exe" + "cute" style dynamic names can't be checked, so they are not allowed at all
			Expression method = ((MethodCallExpression) e).getMethod();
			return method instanceof ConstantExpression && !METHODS.contains(method.getText());
		}
		if (e instanceof StaticMethodCallExpression) {
			return !METHODS.contains(((StaticMethodCallExpression) e).getMethod()) && !CLASSES.contains(((StaticMethodCallExpression) e).getOwnerType().getName());
		}
		if (e instanceof MethodPointerExpression) {
			Expression method = ((MethodPointerExpression) e).getMethodName();
			return method instanceof ConstantExpression && !METHODS.contains(method.getText());
		}
		if (e instanceof ConstructorCallExpression) {
			return !isBlocked(e.getType());
		}
		if (e instanceof ClassExpression) {
			return !isBlocked(e.getType());
		}
		if (e instanceof PropertyExpression) {
			Expression property = ((PropertyExpression) e).getProperty();
			return property instanceof ConstantExpression && !PROPERTIES.contains(property.getText());
		}
		if (e instanceof BinaryExpression) {
			BinaryExpression b = (BinaryExpression) e;
			// file.text = "…" or file.bytes = … writes the file
			if ("=".equals(b.getOperation().getText()) && b.getLeftExpression() instanceof PropertyExpression) {
				String property = ((PropertyExpression) b.getLeftExpression()).getPropertyAsString();
				return property != null && !WRITE_PROPERTIES.contains(property) && !PROPERTIES.contains(property);
			}
		}
		return true;
	}

	private static boolean isBlocked(ClassNode type) {
		return type != null && CLASSES.contains(type.getName());
	}

}
