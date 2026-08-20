package de.zft2.gbanking.enablebanking;

import java.util.List;

public record EnablebankingAspsp(String name, String country, int maximumConsentValidity,
		List<String> psuTypes, List<EnablebankingAuthMethod> authMethods) {

	public EnablebankingAspsp {
		psuTypes = List.copyOf(psuTypes);
		authMethods = List.copyOf(authMethods);
	}

	@Override
	public List<String> psuTypes() {
		return List.copyOf(psuTypes);
	}

	@Override
	public List<EnablebankingAuthMethod> authMethods() {
		return List.copyOf(authMethods);
	}

	@Override
	public String toString() {
		return name;
	}
}
