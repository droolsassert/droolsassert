package org.droolsassert.util;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.mvel2.MVEL.compileExpression;
import static org.mvel2.MVEL.executeExpression;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.mvel2.ParserContext;

public class MvelProcessor extends PatternProcessor {
	
	private static final String PATTERN = "\\$\\$\\{(?<long>(?!.*?\\$\\{).*?)\\}\\$|\\$\\{(?<short>(?!.*?\\$\\{).*?)\\}";
	
	protected final ParserContext parserContext;
	protected volatile Map<String, Object> executionContext = executionContext();
	
	public MvelProcessor() {
		super(PATTERN);
		
		try {
			parserContext = parserContext();
		} catch (Exception e) {
			throw new RuntimeException("Cannot create parser context ", e);
		}
	}
	
	public void importPackage(String packageName) {
		parserContext.addPackageImport(packageName);
	}
	
	/**
	 * Define execution context variable
	 * 
	 * @param name
	 * @param value
	 * @return
	 */
	public Object define(String name, Object value) {
		return executionContext.put(name, value);
	}
	
	/**
	 * Reset execution context variables
	 */
	public void reset() {
		executionContext = executionContext();
	}
	
	@Override
	protected String resolve(Matcher matcher) {
		return "" + evaluate(defaultIfEmpty(matcher.group("long"), matcher.group("short")));
	}
	
	@SuppressWarnings("unchecked")
	public <T> T evaluate(String expression) {
		try {
			return (T) executeExpression(compileExpression(expression, parserContext), executionContext);
		} catch (Exception e) {
			throw new RuntimeException("Cannot evaluate " + expression, e);
		}
	}
	
	/**
	 * Execution context which could be reset with {@link #reset()}
	 */
	protected Map<String, Object> executionContext() {
		return new HashMap<>();
	}
	
	/**
	 * Parser context used for evaluation.
	 * 
	 * @throws Exception
	 */
	protected ParserContext parserContext() throws Exception {
		return new ParserContext();
	}
}
