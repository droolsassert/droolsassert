package org.droolsassert.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MvelProcessorTest {
	
	private MvelProcessor mvelProcessor = new MvelProcessor();
	
	@Test
	public void testRecursiveResolution() {
		mvelProcessor.define("value", "${placeholder}");
		mvelProcessor.define("placeholder", "a value");
		assertEquals("string with a value", mvelProcessor.process("string with ${value}"));
	}
	
	@Test
	public void testMultiline() {
		mvelProcessor.define("x", "5");
		assertEquals("15", mvelProcessor.process("$${"
				+ "def f(x, y) {x * y};"
				+ "f(5, 2) + ${x};"
				+ "}$"));
	}
	
	@Test
	public void testNonvalidMvelVariables() {
		mvelProcessor.define("messageId-source@dtcc", "55555");
		assertEquals("55555", mvelProcessor.process("${messageId-source@dtcc}"));
	}
}
