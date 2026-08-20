package de.zft2.gbanking.enablebanking;

public record EnablebankingAuthMethod(String name, String title, String psuType, String approach) {

	@Override
	public String toString() {
		return title != null && !title.isBlank() ? title : name;
	}
}
