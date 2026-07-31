package de.zft2.gbanking.paypal;

import java.math.BigDecimal;

public record PaypalBalance(String currency, BigDecimal amount) {
}
