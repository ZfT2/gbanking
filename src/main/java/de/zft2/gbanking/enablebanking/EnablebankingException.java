package de.zft2.gbanking.enablebanking;

public class EnablebankingException extends RuntimeException {

	private static final long serialVersionUID = -6665032858824404353L;

	private final int httpStatus;
	private final String errorCode;

	public EnablebankingException(String message) {
		this(message, 0, null, null);
	}

	public EnablebankingException(String message, Throwable cause) {
		this(message, 0, null, cause);
	}

	public EnablebankingException(String message, int httpStatus) {
		this(message, httpStatus, null, null);
	}

	EnablebankingException(String message, int httpStatus, String errorCode) {
		this(message, httpStatus, errorCode, null);
	}

	private EnablebankingException(String message, int httpStatus, String errorCode, Throwable cause) {
		super(message, cause);
		this.httpStatus = httpStatus;
		this.errorCode = errorCode;
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

	public boolean isWrongTransactionsPeriod() {
		return "WRONG_TRANSACTIONS_PERIOD".equals(errorCode);
	}
}
