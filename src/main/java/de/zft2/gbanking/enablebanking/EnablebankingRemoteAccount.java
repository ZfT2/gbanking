package de.zft2.gbanking.enablebanking;

public record EnablebankingRemoteAccount(String uid, String identificationHash, String iban, String bic,
		String number, String name, String details, String product, String cashAccountType, String currency,
		String ownerName) {
}
