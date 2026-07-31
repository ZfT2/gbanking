package de.zft2.gbanking.paypal;

public class PaypalApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final boolean authenticationFailure;

	public PaypalApiException(String message, boolean authenticationFailure) {
		super(message);
		this.authenticationFailure = authenticationFailure;
	}

	public PaypalApiException(String message, Throwable cause) {
		super(message, cause);
		this.authenticationFailure = false;
	}

	public boolean isAuthenticationFailure() {
		return authenticationFailure;
	}
}
