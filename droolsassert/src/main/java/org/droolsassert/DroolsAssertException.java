package org.droolsassert;

public class DroolsAssertException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public DroolsAssertException(String message) {
		super(message);
	}
	
	public DroolsAssertException(Throwable cause) {
		super(cause);
	}
	
	public DroolsAssertException(String message, Throwable cause) {
		super(message, cause);
	}
}
