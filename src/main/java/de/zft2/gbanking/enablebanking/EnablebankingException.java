package de.zft2.gbanking.enablebanking;

public class EnablebankingException extends RuntimeException {

	private static final long serialVersionUID = -6665032858824404353L;

	private final int httpStatus;

	public EnablebankingException(String message) {
		this(message, 0, null);
	}

	public EnablebankingException(String message, Throwable cause) {
		this(message, 0, cause);
	}

	public EnablebankingException(String message, int httpStatus) {
		this(message, httpStatus, null);
	}

	private EnablebankingException(String message, int httpStatus, Throwable cause) {
		super(message, cause);
		this.httpStatus = httpStatus;
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public boolean isRateLimited() {
		return httpStatus == 429;
	}

	public boolean isUnauthorized() {
		return httpStatus == 401 || httpStatus == 403 || httpStatus == 404;
	}
}
