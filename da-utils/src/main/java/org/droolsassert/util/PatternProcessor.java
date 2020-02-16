package org.droolsassert.util;

import static java.util.regex.Matcher.quoteReplacement;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class PatternProcessor {
	protected final Pattern pattern;
	
	public PatternProcessor(String pattern) {
		this.pattern = compile(pattern, MULTILINE | DOTALL);
	}
	
	public String process(String string) {
		return process(string, true);
	}
	
	public String process(String string, boolean recursive) {
		if (isEmpty(string))
			return string;
		
		String before, resolved = string;
		do {
			before = resolved;
			StringBuffer buffer = new StringBuffer();
			Matcher matcher = pattern.matcher(resolved);
			while (matcher.find())
				matcher.appendReplacement(buffer, quoteReplacement(resolve(matcher)));
			matcher.appendTail(buffer);
			resolved = buffer.toString();
		} while (!before.equals(resolved) && recursive);
		
		return resolved;
	}
	
	protected abstract String resolve(Matcher matcher);
}
