package de.zft2.gbanking.paypal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class PaypalSoapClientTest {

	private static final URI ENDPOINT = URI.create("https://example.test/2.0/");

	@Test
	void getBalances_shouldReadAllCurrencyHoldingsAndEscapeCredentials() throws InterruptedException {
		AtomicReference<String> request = new AtomicReference<>();
		PaypalSoapClient client = client(request, """
				<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
				 xmlns:ebl="urn:ebay:apis:eBLBaseComponents" xmlns:ns="urn:ebay:api:PayPalAPI">
				 <SOAP-ENV:Body><ns:GetBalanceResponse>
				  <ebl:Ack>Success</ebl:Ack>
				  <ns:Balance currencyID="EUR">12.34</ns:Balance>
				  <ns:BalanceHoldings currencyID="USD">5.67</ns:BalanceHoldings>
				  <ns:BalanceHoldings currencyID="EUR">12.34</ns:BalanceHoldings>
				 </ns:GetBalanceResponse></SOAP-ENV:Body>
				</SOAP-ENV:Envelope>
				""");

		List<PaypalBalance> balances = client.getBalances("api&user", "p<ass".toCharArray(), "sig\"");

		assertEquals(List.of(new PaypalBalance("EUR", new BigDecimal("12.34")), new PaypalBalance("USD", new BigDecimal("5.67"))), balances);
		assertTrue(request.get().contains("<ebl:Username>api&amp;user</ebl:Username>"));
		assertTrue(request.get().contains("<ebl:Password>p&lt;ass</ebl:Password>"));
		assertTrue(request.get().contains("<ebl:Signature>sig&quot;</ebl:Signature>"));
		assertTrue(request.get().contains("<ns:ReturnAllCurrencies>1</ns:ReturnAllCurrencies>"));
	}

	@Test
	void searchTransactions_shouldMapNetAndFeeAmounts() throws InterruptedException {
		AtomicReference<String> request = new AtomicReference<>();
		PaypalSoapClient client = client(request, """
				<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/" xmlns:p="urn:ebay:api:PayPalAPI"
				 xmlns:e="urn:ebay:apis:eBLBaseComponents">
				 <Body><p:TransactionSearchResponse><e:Ack>Success</e:Ack>
				  <p:PaymentTransactions>
				   <p:Timestamp>2026-07-19T12:34:56Z</p:Timestamp><p:Type>Payment</p:Type>
				   <p:Payer>payer@example.org</p:Payer><p:PayerDisplayName>Test Person</p:PayerDisplayName>
				   <p:TransactionID>ABC123</p:TransactionID><p:Status>Success</p:Status>
				   <p:GrossAmount currencyID="EUR">10.00</p:GrossAmount>
				   <p:FeeAmount currencyID="EUR">-0.50</p:FeeAmount>
				   <p:NetAmount currencyID="EUR">9.50</p:NetAmount>
				  </p:PaymentTransactions>
				 </p:TransactionSearchResponse></Body>
				</Envelope>
				""");

		List<PaypalTransaction> transactions = client.searchTransactions("user", "password".toCharArray(), "signature",
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-20T00:00:00Z"));

		assertEquals(1, transactions.size());
		PaypalTransaction transaction = transactions.get(0);
		assertEquals(new BigDecimal("9.50"), transaction.netAmount());
		assertEquals(new BigDecimal("-0.50"), transaction.feeAmount());
		assertEquals("EUR", transaction.currency());
		assertEquals("ABC123", transaction.transactionId());
		assertTrue(request.get().contains("<ns:TransactionClass>BalanceAffecting</ns:TransactionClass>"));
		assertFalse(request.get().contains("<ns:CurrencyCode>"));
	}

	@Test
	void getBalances_shouldIdentifyAuthenticationErrors() {
		PaypalSoapClient client = client(new AtomicReference<>(), """
				<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/" xmlns:e="urn:ebay:apis:eBLBaseComponents">
				 <Body><Response><e:Ack>Failure</e:Ack><e:Errors><e:ErrorCode>10002</e:ErrorCode>
				  <e:LongMessage>Authentication failed</e:LongMessage></e:Errors></Response></Body>
				</Envelope>
				""");

		char[] wrongPassword = "wrong".toCharArray();
		PaypalApiException exception = assertThrows(PaypalApiException.class,
				() -> client.getBalances("user", wrongPassword, "signature"));

		assertTrue(exception.isAuthenticationFailure());
	}

	private PaypalSoapClient client(AtomicReference<String> request, String response) {
		return new PaypalSoapClient(ENDPOINT, (endpoint, requestXml) -> {
			request.set(requestXml);
			return response;
		});
	}
}
