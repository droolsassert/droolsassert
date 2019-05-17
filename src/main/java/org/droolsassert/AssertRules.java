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
	 * Rules expeted to be triggered
	 */
	String[] expected() default {};

	/**
	 * Rules activation count is taken into account
	 */
	String[] expectedCount() default {};

	/**
	 * Ignore rules for the test
	 */
	String[] ignore() default {};
}