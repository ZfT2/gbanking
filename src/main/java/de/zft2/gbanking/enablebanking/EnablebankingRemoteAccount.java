package de.zft2.gbanking.enablebanking;

public record EnablebankingRemoteAccount(String uid, String identificationHash, String iban, String bic,
		String blz, String number, String cashAccountType, String currency, String ownerName) {
}
