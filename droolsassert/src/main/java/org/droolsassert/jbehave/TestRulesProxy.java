package org.droolsassert.jbehave;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class TestRulesProxy implements InvocationHandler {
	
	String[] ignore = new String[0];
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		switch (method.getName()) {
		case "ignore":
			return ignore;
		case "hashCode":
			return hashCode();
		case "equals":
			return equals(args[0]);
		case "expected":
		case "expectedCount":
		case "checkScheduled":
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
