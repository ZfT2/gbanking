package de.zft2.gbanking.enablebanking;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import de.zft2.gbanking.enablebanking.EnablebankingCallbackMaterialService.CallbackMaterial;

public final class EnablebankingCallbackServer implements AutoCloseable {

	private static final char[] KEYSTORE_PASSWORD = "gbanking-callback".toCharArray();
	private static final String SUCCESS_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>GBanking</title></head>"
			+ "<body><h1>Autorisierung abgeschlossen</h1><p>Dieses Fenster kann geschlossen werden.</p></body></html>";

	private final URI callbackUri;
	private final String expectedState;
	private final HttpsServer server;
	private final ExecutorService executor;
	private final CompletableFuture<String> authorizationCode = new CompletableFuture<>();

	public EnablebankingCallbackServer(String callbackUrl, String expectedState, CallbackMaterial material) {
		callbackUri = validateCallbackUri(callbackUrl);
		this.expectedState = expectedState;
		try {
			InetAddress address = InetAddress.getByName(callbackUri.getHost());
			server = HttpsServer.create(new InetSocketAddress(address, callbackUri.getPort()), 0);
			server.setHttpsConfigurator(new HttpsConfigurator(createSslContext(material)));
			executor = Executors.newSingleThreadExecutor(task -> {
				Thread thread = new Thread(task, "enablebanking-callback");
				thread.setDaemon(true);
				return thread;
			});
			server.setExecutor(executor);
			server.createContext(callbackUri.getPath(), this::handleCallback);
		} catch (IOException | GeneralSecurityException exception) {
			throw new EnablebankingException("Der lokale Enablebanking-Callback konnte nicht gestartet werden.", exception);
		}
	}

	public void start() {
		server.start();
	}

	public String awaitAuthorizationCode(Duration timeout) {
		try {
			return authorizationCode.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new EnablebankingException("Die Enablebanking-Autorisierung wurde abgebrochen.", exception);
		} catch (TimeoutException exception) {
			throw new EnablebankingException("Zeitüberschreitung bei der Enablebanking-Autorisierung.", exception);
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof EnablebankingException enablebankingException) {
				throw enablebankingException;
			}
			throw new EnablebankingException("Die Enablebanking-Autorisierung ist fehlgeschlagen.", cause);
		}
	}

	private void handleCallback(HttpExchange exchange) throws IOException {
		int status = 400;
		String response = "Ungültiger Enablebanking-Callback.";
		try {
			Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
			if (!"GET".equals(exchange.getRequestMethod()) || !exchange.getRemoteAddress().getAddress().isLoopbackAddress()
					|| !callbackUri.getPath().equals(exchange.getRequestURI().getPath())) {
				throw new EnablebankingException("Ungültige lokale Callback-Anfrage.");
			}
			if (!secureEquals(expectedState, query.get("state"))) {
				throw new EnablebankingException("Der Sicherheitsstatus des Enablebanking-Callbacks ist ungültig.");
			}
			String error = query.get("error");
			if (error != null) {
				throw new EnablebankingException("Enablebanking-Autorisierung abgelehnt: " + error);
			}
			String code = query.get("code");
			if (code == null || code.isBlank()) {
				throw new EnablebankingException("Der Enablebanking-Autorisierungscode fehlt.");
			}
			authorizationCode.complete(code);
			status = 200;
			response = SUCCESS_PAGE;
		} catch (EnablebankingException exception) {
			authorizationCode.completeExceptionally(exception);
		}
		sendResponse(exchange, status, response);
	}

	private void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
		byte[] content = response.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(status, content.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(content);
		}
	}

	private SSLContext createSslContext(CallbackMaterial material) throws GeneralSecurityException, IOException {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(null, KEYSTORE_PASSWORD);
		keyStore.setKeyEntry("callback", material.privateKey(), KEYSTORE_PASSWORD,
				new java.security.cert.Certificate[] { material.certificate() });
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		return sslContext;
	}

	private static URI validateCallbackUri(String callbackUrl) {
		URI uri;
		try {
			uri = URI.create(callbackUrl);
		} catch (IllegalArgumentException exception) {
			throw new EnablebankingException("Die Enablebanking-Callback-URL ist ungültig.", exception);
		}
		boolean loopback;
		try {
			loopback = uri.getHost() != null && InetAddress.getByName(uri.getHost()).isLoopbackAddress();
		} catch (IOException exception) {
			loopback = false;
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || !loopback || uri.getPort() <= 0
				|| uri.getPath() == null || uri.getPath().isBlank() || uri.getQuery() != null || uri.getFragment() != null) {
			throw new EnablebankingException("Die Callback-URL muss auf einen lokalen HTTPS-Port und einen festen Pfad zeigen.");
		}
		return uri;
	}

	private static Map<String, String> parseQuery(String rawQuery) {
		Map<String, String> values = new HashMap<>();
		if (rawQuery == null || rawQuery.isBlank()) {
			return values;
		}
		for (String pair : rawQuery.split("&")) {
			String[] parts = pair.split("=", 2);
			values.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
		}
		return values;
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private static boolean secureEquals(String expected, String actual) {
		byte[] expectedBytes = expected != null ? expected.getBytes(StandardCharsets.UTF_8) : new byte[0];
		byte[] actualBytes = actual != null ? actual.getBytes(StandardCharsets.UTF_8) : new byte[0];
		return actual != null && MessageDigest.isEqual(expectedBytes, actualBytes);
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}
}
