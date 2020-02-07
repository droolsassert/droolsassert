package org.droolsassert;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Describes the session being constructed.<br>
 * Life-cycle of the session is single test (method)
 */
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
	
	/**
	 * Log resources loaded for the session
	 */
	boolean logResources() default false;
	
	/**
	 * Keep track of all facts ever inserted into the session.<br>
	 * This gives you some additional features, like logging retained facts in insertion order and some additional sanity checks while assertions, but you may want to skip this for data-heavy tests.
	 */
	boolean keeFactsHistory() default true;
	
	/**
	 * Log facts being inserted/deleted/updated
	 */
	boolean logFacts() default true;
}