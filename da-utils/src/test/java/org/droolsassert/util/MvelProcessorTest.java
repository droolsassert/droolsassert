package org.droolsassert.util;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

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
		System.out.println(Arrays.asList(5).get(0));
		assertEquals("15", mvelProcessor.process("$${"
				+ "def f(x, y) {x * y};"
				+ "f(5, 2) + ${x};"
				+ "}$"));
	}
}
