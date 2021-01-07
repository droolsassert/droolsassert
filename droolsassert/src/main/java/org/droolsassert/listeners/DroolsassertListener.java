package org.droolsassert.listeners;

public interface DroolsassertListener {
	
	boolean enabled();
	
	default void beforeScenario(String test, String scenario) {
	}
	
	default void afterScenario() {
	}
}
