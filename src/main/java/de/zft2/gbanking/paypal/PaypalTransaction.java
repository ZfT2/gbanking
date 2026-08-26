package de.zft2.gbanking.paypal;

import java.math.BigDecimal;
import java.time.Instant;

public record PaypalTransaction(Instant timestamp, String type, String payerEmail, String payerDisplayName, String transactionId, String status,
		BigDecimal grossAmount, BigDecimal feeAmount, String feeCurrency, BigDecimal netAmount, String currency) {
}
