package de.zft2.gbanking.db.dao;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.Objects;

/** Bank-reported codes are independent of the application's supported TAN procedure IDs. */
public record BankAccessTanMechanism(String code, String description) {

	public BankAccessTanMechanism {
		code = Objects.requireNonNull(code, "code").trim();
		if (code.isEmpty() || code.indexOf(':') >= 0) {
			throw new IllegalArgumentException("A TAN mechanism requires a non-blank code without a description separator");
		}
		description = trimToNull(description);
	}

	public static BankAccessTanMechanism fromBankValue(String value) {
		int separator = value.indexOf(':');
		return separator < 0 ? new BankAccessTanMechanism(value, null)
				: new BankAccessTanMechanism(value.substring(0, separator), value.substring(separator + 1));
	}

	public String toBankValue() {
		return description == null ? code : code + ":" + description;
	}
}
