package org.droolsassert.jbehave;

import org.droolsassert.util.MvelProcessor;

public class DefaultMvelProcessor extends MvelProcessor {
	public DefaultMvelProcessor() {
		parserContext.addPackageImport("java.lang");
	}
}
