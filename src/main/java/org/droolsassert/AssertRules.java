package org.droolsassert;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(METHOD)
public @interface AssertRules {
	/**
	 * @see #expected()
	 */
	String[] value() default {};
	
	/**
	 * Rules expected to be triggered
	 */
	String[] expected() default {};
	
	/**
	 * Rules activation count is taken into account
	 */
	String[] expectedCount() default {};
	
	/**
	 * Ignore rules matching patterns while assertion.<br>
	 * Rules themselves will be executed
	 */
	String[] ignore() default {};
	
	/**
	 * Assert rules that could be triggered later.<br>
	 * By default, those rules will not be checked
	 */
	boolean checkScheduled() default false;
}