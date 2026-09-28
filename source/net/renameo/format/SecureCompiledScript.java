package net.renameo.format;

import java.util.concurrent.Callable;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Evaluates a compiled expression. The protection itself happens at compile time ({@link ExpressionSandbox}), since Java 18+ has no security manager.
 */
public class SecureCompiledScript extends CompiledScript {

	private final CompiledScript compiledScript;

	public SecureCompiledScript(CompiledScript compiledScript) {
		this.compiledScript = compiledScript;
	}

	@Override
	public Object eval(ScriptContext context) throws ScriptException {
		Object value = compiledScript.eval(context);

		if (value instanceof Callable<?>) {
			try {
				return ((Callable<?>) value).call();
			} catch (Exception e) {
				throw new ScriptException(e);
			}
		}

		return value;
	}

	@Override
	public ScriptEngine getEngine() {
		return compiledScript.getEngine();
	}

}
