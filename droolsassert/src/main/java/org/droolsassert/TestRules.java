package org.droolsassert;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.util.AntPathMatcher;

/**
 * Provides additional options for the test.<br>
 * Can be used to declare and assert rules being triggered during test run.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface TestRules {
	
	/**
	 * Rules expected to be triggered. Provide empty list to assert no rules where triggered.<br>
	 * This overrides {@link #expectedSource} and {@link #expectedCountSource}
	 */
	String[] expected() default { EMPTY };
	
	/**
	 * Rules expected to be triggered in a line delimited resource file.
	 * 
	 * <pre>
	 * # comment
	 * 
	 * rule 1
	 * rule 2
	 * </pre>
	 */
	String expectedSource() default EMPTY;
	
	/**
	 * Rules activations count are asserted<br>
	 * This overrides {@link #expectedCountSource} and {@link #expected}
	 */
	String[] expectedCount() default {};
	
	/**
	 * Rules activations count are asserted in a line delimited resource file.<br>
	 * This overrides {@link #expectedSource}
	 * 
	 * <pre>
	 * # comment
	 * 
	 * 5	rule 1
	 * 10	rule 2
	 * </pre>
	 */
	String expectedCountSource() default EMPTY;
	
	/**
	 * Ignore rules matching patterns while assertion.<br>
	 * Rules themselves will be executed.<br>
	 * This supplements {@link DroolsSession#ignoreRules()}, {@link #ignoreSource()}
	 * 
	 * @see AntPathMatcher
	 */
	String[] ignore() default {};
	
	/**
	 * Ignore rules matching patterns while assertion in a line delimited resource file.<br>
	 * Rules themselves will be executed.<br>
	 * This supplements {@link #ignore()}
	 * 
	 * @see AntPathMatcher
	 */
	String ignoreSource() default EMPTY;
	
	/**
	 * Check any rules that are scheduled to be triggered later.<br>
	 * <br>
	 * Default - false (do not check)
	 */
	boolean checkScheduled() default false;
}