package org.droolsassert;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.util.AntPathMatcher;

/**
 * Marks a method to be a drools test.<br>
 * Provides additional options limited to the current test only.<br>
 * Can be used to declare and assert all rules being triggered during test run.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface TestRules {
	
	/**
	 * Rules expected to be triggered. Provide empty list do assert no rules where triggered.
	 */
	String[] expected() default { "null" };
	
	/**
	 * Rules activations count are asserted
	 */
	String[] expectedCount() default {};
	
	/**
	 * Ignore rules matching patterns while assertion.<br>
	 * Rules themselves will be executed.
	 * 
	 * @see AntPathMatcher
	 */
	String[] ignore() default {};
	
	/**
	 * Assert rules that could be triggered later.<br>
	 * By default, those rules will not be checked
	 */
	boolean checkScheduled() default false;
}