package de.zft2.gbanking.paypal;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.enu.BankAccessType;

public final class PaypalSupport {

	public static final String BANK_CODE = "PAYPAL";
	public static final String DISPLAY_NAME = "PayPal";

	private PaypalSupport() {
	}

	public static boolean isPaypal(BankAccess bankAccess) {
		return bankAccess != null && bankAccess.getAccessType() == BankAccessType.PAYPAL;
	}

	public static boolean isPaypalChoice(String value) {
		return value != null && (DISPLAY_NAME.equalsIgnoreCase(value.trim()) || BANK_CODE.equalsIgnoreCase(value.trim()));
	}
}
