package de.zft2.gbanking.enablebanking;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EnablebankingTransactionPage(List<Map<String, Object>> transactions, String continuationKey) {

	public EnablebankingTransactionPage {
		transactions = copy(transactions);
	}

	@Override
	public List<Map<String, Object>> transactions() {
		return copy(transactions);
	}

	private static List<Map<String, Object>> copy(List<Map<String, Object>> transactions) {
		return transactions.stream()
				.map(transaction -> Collections.unmodifiableMap(new HashMap<>(transaction)))
				.toList();
	}
}
