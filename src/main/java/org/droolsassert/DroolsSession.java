package org.droolsassert;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Retention(RUNTIME)
@Target(TYPE)
public @interface DroolsSession {
	/**
	 * @see #resources()
	 */
	String[] value() default {};

	/**
	 * @see PathMatchingResourcePatternResolver
	 */
	String[] resources() default {};

	/**
	 * @see DroolsAssert#defaultSessionProperties()
	 */
	String[] properties() default {};

	/**
	 * Ignore rules matching patterns while assertion.<br>
	 * Rules themselves will be executed
	 */
	String[] ignoreRules() default {};

	boolean logResources() default false;

	boolean logFacts() default true;
}