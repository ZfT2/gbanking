package de.zft2.gbanking.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.zft2.gbanking.util.SimpleJson;

class EnablebankingApiClientTest {

	private HttpServer server;
	private EnablebankingApiClient client;
	private AtomicReference<String> authorizationBody;

	@BeforeEach
	void startServer() throws Exception {
		authorizationBody = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var signer = new EnablebankingJwtSigner("application-id", generator.generateKeyPair().getPrivate());
		client = new EnablebankingApiClient(HttpClient.newHttpClient(),
				URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), signer);
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void shouldMapInstitutionsAuthorizationAndSessionAccounts() {
		EnablebankingAspsp aspsp = client.getAspsps().get(0);
		URI authorizationUri = client.createAuthorization(aspsp, "personal", "redirect",
				"https://127.0.0.1:18443/callback", "state-value");
		EnablebankingSession session = client.createSession("authorization-code");

		assertEquals("Example Bank", aspsp.name());
		assertEquals("Redirect", aspsp.authMethods().get(0).title());
		assertEquals("https://bank.example/authorize", authorizationUri.toString());
		assertTrue(authorizationBody.get().contains("\"redirect_url\":\"https://127.0.0.1:18443/callback\""));
		assertEquals("session-id", session.sessionId());
		assertTrue(session.isAuthorized());
		assertEquals(OffsetDateTime.parse("2026-12-31T00:00:00Z"), session.validUntil());
		assertEquals("identification-hash", session.accounts().get(0).identificationHash());
		assertEquals("DE123", session.accounts().get(0).iban());
		assertEquals("4711", session.accounts().get(0).number());
	}

	@Test
	void shouldMapRetrievedSessionStatusAndValidity() {
		EnablebankingSession session = client.getSession("session-id");

		assertTrue(session.isAuthorized());
		assertEquals(OffsetDateTime.parse("2026-12-31T00:00:00Z"), session.validUntil());
		assertEquals("identification-hash", session.accounts().get(0).identificationHash());
	}

	@Test
	void shouldExposeContinuationKeyEvenForEmptyTransactionPage() {
		EnablebankingTransactionPage first = client.getTransactions("account-uid", LocalDate.of(2026, 8, 1),
				"default", null);
		EnablebankingTransactionPage second = client.getTransactions("account-uid", LocalDate.of(2026, 8, 1),
				"default", first.continuationKey());

		assertTrue(first.transactions().isEmpty());
		assertEquals("next-page", first.continuationKey());
		assertEquals(1, second.transactions().size());
		assertEquals("BOOK", second.transactions().get(0).get("status"));
	}

	private void handle(HttpExchange exchange) throws IOException {
		assertNotNull(exchange.getRequestHeaders().getFirst("Authorization"));
		String path = exchange.getRequestURI().getPath();
		String response;
		if ("/aspsps".equals(path)) {
			response = "{\"aspsps\":[{\"name\":\"Example Bank\",\"country\":\"DE\","
					+ "\"maximum_consent_validity\":15552000,\"psu_types\":[\"personal\"],"
					+ "\"auth_methods\":[{\"name\":\"redirect\",\"title\":\"Redirect\","
					+ "\"psu_type\":\"personal\",\"approach\":\"REDIRECT\"}]}]}";
		} else if ("/auth".equals(path)) {
			authorizationBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			response = "{\"url\":\"https://bank.example/authorize\"}";
		} else if ("/sessions".equals(path)) {
			response = "{\"session_id\":\"session-id\",\"access\":{\"valid_until\":\"2026-12-31T00:00:00Z\"},"
					+ "\"accounts\":[{\"uid\":\"account-uid\"," 
					+ "\"identification_hash\":\"identification-hash\",\"currency\":\"EUR\"," 
					+ "\"account_id\":{\"iban\":\"DE123\",\"other\":{\"identification\":\"4711\"}}}]}";
		} else if ("/sessions/session-id".equals(path)) {
			response = "{\"status\":\"AUTHORIZED\",\"access\":{\"valid_until\":\"2026-12-31T00:00:00Z\"},"
					+ "\"accounts\":[\"account-uid\"],\"accounts_data\":[{\"uid\":\"account-uid\"," 
					+ "\"identification_hash\":\"identification-hash\"}]}";
		} else if (path.endsWith("/transactions")) {
			Map<?, ?> query = parseQuery(exchange.getRequestURI().getRawQuery());
			response = query.containsKey("continuation_key")
					? "{\"transactions\":[{\"status\":\"BOOK\"}]}"
					: "{\"transactions\":[],\"continuation_key\":\"next-page\"}";
		} else {
			response = "{}";
		}
		byte[] content = response.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, content.length);
		exchange.getResponseBody().write(content);
		exchange.close();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseQuery(String query) {
		String json = "{\"" + query.replace("=", "\":\"").replace("&", "\",\"") + "\"}";
		return (Map<String, Object>) SimpleJson.parse(json);
	}
}
