package de.zft2.gbanking.paypal;

import java.math.BigDecimal;

public record PaypalTransactionDetails(BigDecimal settleAmount, String settleCurrency, BigDecimal exchangeRate) {
}
