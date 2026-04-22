package de.zft2.gbanking.exception;

public class GBankingException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	final String message;
	
	final Exception originalException;
	
	final boolean suppressStacktrace;
	
	public GBankingException(String message, boolean suppressStacktrace) {
		super(message, null, suppressStacktrace, !suppressStacktrace);
		this.message = message;
		this.originalException = new Exception();
		this.suppressStacktrace = suppressStacktrace;
	}

	public GBankingException(String message, Exception originalException) {
		super(message);
		this.message = message;
		this.originalException = originalException;
		this.suppressStacktrace = false;
	}
	
	public GBankingException(String message, Object... args) {
		super(message);
		this.message = String.format(message, args);
		this.originalException = null;
		this.suppressStacktrace = false;
	}
	
	public GBankingException(String message) {
		super(message);
		this.message = message;
		this.originalException = null;
		this.suppressStacktrace = false;
	}

	@Override
	public String getMessage() {
		return message + ": " + (originalException != null ? originalException.getMessage() : "");
	}
	
	@Override
	public String toString() {
		if (suppressStacktrace) {
			return getLocalizedMessage();
		} else {
			return super.toString();
		}
	}
}
