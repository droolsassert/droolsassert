package org.droolsassert;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.droolsassert.listeners.StateTransitionBuilder;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.AntPathMatcher;

/**
 * Describes the session being constructed for each test.<br>
 * Life-cycle of the session is limited to the test (method)
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface DroolsSession {
	/**
	 * @see #resources
	 */
	String[] value() default {};
	
	/**
	 * @see PathMatchingResourcePatternResolver
	 */
	String[] resources() default {};
	
	/**
	 * Rule source<br>
	 * <br>
	 * 
	 * <pre>
	 * &#64;DroolsSession(source = "DRL inline source here...")
	 * </pre>
	 * 
	 * <pre>
	 * &#64;DroolsSession(source = &#123;
	 * 	"DRL", "DRL inline source here...",
	 * 	"DSL", "DSL inline source here..."
	 * &#125;)
	 * </pre>
	 * 
	 * @see ResourceType
	 */
	String[] source() default {};
	
	/**
	 * @see KieSessionConfiguration
	 * @see #sessionPropertySource
	 * @see DroolsAssert#defaultSessionProperties()
	 * @see DroolsAssert#sessionConfiguration(DroolsSession)
	 */
	String[] sessionProperties() default {};
	
	/**
	 * @see KieSessionConfiguration
	 * @see #sessionProperties
	 * @see DroolsAssert#defaultSessionProperties()
	 * @see DroolsAssert#sessionConfiguration(DroolsSession)
	 */
	String[] sessionPropertySource() default {};
	
	/**
	 * @see KieBaseConfiguration
	 * @see #basePropertySource
	 * @see DroolsAssert#defaultBaseProperties()
	 * @see DroolsAssert#baseConfiguration(DroolsSession)
	 */
	String[] baseProperties() default {};
	
	/**
	 * @see KieBaseConfiguration
	 * @see #baseProperties
	 * @see DroolsAssert#defaultBaseProperties()
	 * @see DroolsAssert#baseConfiguration(DroolsSession)
	 */
	String[] basePropertySource() default {};
	
	/**
	 * @see KnowledgeBuilderConfiguration
	 * @see #builderPropertySource
	 * @see DroolsAssert#defaultBuilderProperties()
	 * @see DroolsAssert#builderConfiguration(DroolsSession)
	 */
	String[] builderProperties() default {};
	
	/**
	 * @see KnowledgeBuilderConfiguration
	 * @see #builderProperties
	 * @see DroolsAssert#defaultBuilderProperties()
	 * @see DroolsAssert#builderConfiguration(DroolsSession)
	 */
	String[] builderPropertySource() default {};
	
	/**
	 * Ignore rules matching patterns while assertion.<br>
	 * Rules themselves will be executed<br>
	 * This supplements {@link #ignoreRulesSource()}
	 * 
	 * @see AntPathMatcher
	 */
	String[] ignoreRules() default {};
	
	/**
	 * Ignore rules matching patterns while assertion from source.<br>
	 * Rules themselves will be executed<br>
	 * This supplements {@link #ignoreRules()}
	 * 
	 * @see AntPathMatcher
	 */
	String ignoreRulesSource() default EMPTY;
	
	/**
	 * Keep track of all facts ever inserted into the session.<br>
	 * This gives you some additional features, like logging retained facts in insertion order and some additional sanity checks while assertions, but you may want to skip this for data-heavy tests.<br>
	 * <br>
	 * Default - true
	 */
	boolean keepFactsHistory() default true;
	
	/**
	 * Log resources loaded for the session<br>
	 * <br>
	 * Default - false
	 */
	boolean logResources() default false;
	
	/**
	 * Log fact attributes or just class simple name<br>
	 * <br>
	 * Default - true
	 */
	boolean logFacts() default true;
	
	/**
	 * Enable / disable all logging.<br>
	 * You may want to disable all logging for performance analysis <br>
	 * Default - true (enable)
	 */
	boolean log() default true;
	
	/**
	 * Enable / disable state transition report on pop-up window.<br>
	 * On development phase it may be helpful to visualize state transition. Close the dialog to continue test execution.<br>
	 * You can enable non-interactive reports using system property, see {@link StateTransitionBuilder} <br>
	 * Default - false (disable)
	 */
	boolean showStateTransitionPopup() default false;
}