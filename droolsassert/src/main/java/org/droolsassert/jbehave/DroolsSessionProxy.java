package org.droolsassert.jbehave;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class DroolsSessionProxy implements InvocationHandler {
	
	String[] resources = new String[0];
	String[] properties = new String[0];
	String[] ignoreRules = new String[0];
	boolean logResources;
	boolean keepFactsHistory = true;
	boolean logFacts = true;
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		switch (method.getName()) {
		case "value":
		case "resources":
			return resources;
		case "properties":
			return properties;
		case "ignoreRules":
			return ignoreRules;
		case "logResources":
			return logResources;
		case "keepFactsHistory":
			return keepFactsHistory;
		case "logFacts":
			return logFacts;
		case "hashCode":
			return hashCode();
		case "equals":
			return equals(args[0]);
		default:
			throw new IllegalAccessError(method.getName());
		}
	}
	
	@Override
	public int hashCode() {
		return reflectionHashCode(this, false);
	}
	
	@Override
	public boolean equals(Object obj) {
		return reflectionEquals(this, obj, false);
	}
}
