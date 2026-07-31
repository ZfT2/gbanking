package de.zft2.gbanking.paypal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class HttpPaypalSoapTransport implements PaypalSoapTransport {

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
	private final HttpClient httpClient;

	HttpPaypalSoapTransport() {
		this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
	}

	HttpPaypalSoapTransport(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public String send(URI endpoint, String requestXml) throws InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(endpoint)
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "text/xml; charset=UTF-8")
				.header("SOAPAction", "")
				.POST(HttpRequest.BodyPublishers.ofString(requestXml, StandardCharsets.UTF_8))
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new PaypalApiException("PayPal SOAP HTTP status " + response.statusCode(), false);
			}
			return response.body();
		} catch (IOException exception) {
			throw new PaypalApiException("PayPal SOAP request failed", exception);
		}
	}
}
