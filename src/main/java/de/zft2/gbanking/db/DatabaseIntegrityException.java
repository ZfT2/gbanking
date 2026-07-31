package de.zft2.gbanking.db;

import de.zft2.gbanking.exception.GBankingException;

public class DatabaseIntegrityException extends GBankingException {

	private static final long serialVersionUID = 1L;

	public DatabaseIntegrityException(String message) {
		super(message);
	}

	public DatabaseIntegrityException(String message, Exception cause) {
		super(message, cause);
	}
}
