package de.zft2.gbanking.enablebanking;

import static de.zft2.gbanking.enablebanking.EnablebankingJson.firstText;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.list;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.number;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.object;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.objectList;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.requireObject;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.string;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.util.SimpleJson;

public class EnablebankingApiClient {

	static final URI DEFAULT_API_URI = URI.create("https://api.enablebanking.com/");
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
	private static final long MAXIMUM_CONSENT_SECONDS = Duration.ofDays(179).toSeconds();
	private static final String USER_AGENT = "GBanking Enablebanking client";

	private final HttpClient httpClient;
	private final URI apiUri;
	private final EnablebankingJwtSigner jwtSigner;

	public EnablebankingApiClient(Psd2ClientConfiguration configuration) {
		this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(), DEFAULT_API_URI,
				new EnablebankingJwtSigner(configuration.getApplicationId(),
						EnablebankingPrivateKeyReader.decodePkcs8(configuration.getPrivateKeyPkcs8())));
	}

	EnablebankingApiClient(HttpClient httpClient, URI apiUri, EnablebankingJwtSigner jwtSigner) {
		this.httpClient = httpClient;
		this.apiUri = apiUri;
		this.jwtSigner = jwtSigner;
	}

	public void validateApplication() {
		send("application", "GET", null);
	}

	public List<EnablebankingAspsp> getAspsps() {
		Map<String, Object> response = send("aspsps", "GET", null);
		return objectList(response.get("aspsps")).stream().map(this::mapAspsp).toList();
	}

	public URI createAuthorization(EnablebankingAspsp aspsp, String psuType, String authMethod,
			String callbackUrl, String state) {
		long consentSeconds = Math.min(MAXIMUM_CONSENT_SECONDS, aspsp.maximumConsentValidity());
		OffsetDateTime validUntil = OffsetDateTime.now(ZoneOffset.UTC)
				.plusSeconds(Math.max(60, consentSeconds - 60L));
		Map<String, Object> access = new LinkedHashMap<>();
		access.put("balances", true);
		access.put("transactions", true);
		access.put("valid_until", validUntil.toString());

		Map<String, Object> institution = new LinkedHashMap<>();
		institution.put("name", aspsp.name());
		institution.put("country", aspsp.country());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("access", access);
		body.put("aspsp", institution);
		body.put("state", state);
		body.put("redirect_url", callbackUrl);
		body.put("psu_type", psuType);
		if (authMethod != null && !authMethod.isBlank()) {
			body.put("auth_method", authMethod);
		}
		body.put("language", "de");
		String authorizationUrl = string(send("auth", "POST", body).get("url"));
		if (authorizationUrl == null) {
			throw new EnablebankingException("Enablebanking hat keine Autorisierungs-URL geliefert.");
		}
		return URI.create(authorizationUrl);
	}

	public EnablebankingSession createSession(String authorizationCode) {
		Map<String, Object> response = send("sessions", "POST", Map.of("code", authorizationCode));
		if (firstText(response, "session_id", "id") == null) {
			throw new EnablebankingException("Enablebanking hat keine Session-ID geliefert.");
		}
		return mapSession(response, "AUTHORIZED");
	}

	public EnablebankingSession getSession(String sessionId) {
		return mapSession(send("sessions/" + encodePath(sessionId), "GET", null), null);
	}

	public void deleteSession(String sessionId) {
		if (sessionId != null && !sessionId.isBlank()) {
			send("sessions/" + encodePath(sessionId), "DELETE", null);
		}
	}

	public List<Map<String, Object>> getBalances(String accountUid) {
		Map<String, Object> response = send("accounts/" + encodePath(accountUid) + "/balances", "GET", null);
		return objectList(response.get("balances"));
	}

	public EnablebankingTransactionPage getTransactions(String accountUid, LocalDate dateFrom,
			String strategy, String continuationKey) {
		StringBuilder path = new StringBuilder("accounts/").append(encodePath(accountUid)).append("/transactions");
		List<String> query = new ArrayList<>();
		if (dateFrom != null) {
			query.add("date_from=" + encodeQuery(dateFrom.toString()));
		}
		if (strategy != null && !strategy.isBlank()) {
			query.add("strategy=" + encodeQuery(strategy));
		}
		if (continuationKey != null && !continuationKey.isBlank()) {
			query.add("continuation_key=" + encodeQuery(continuationKey));
		}
		if (!query.isEmpty()) {
			path.append('?').append(String.join("&", query));
		}
		Map<String, Object> response = send(path.toString(), "GET", null);
		return new EnablebankingTransactionPage(objectList(response.get("transactions")),
				string(response.get("continuation_key")));
	}

	private Map<String, Object> send(String path, String method, Map<String, Object> body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(apiUri.resolve(path))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + jwtSigner.createToken())
				.header("User-Agent", USER_AGENT);
		if (body != null) {
			builder.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(SimpleJson.write(body), StandardCharsets.UTF_8));
		} else {
			builder.method(method, HttpRequest.BodyPublishers.noBody());
		}
		try {
			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return parseResponse(response);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new EnablebankingException("Die Enablebanking-Anfrage wurde abgebrochen.", exception);
		} catch (IOException exception) {
			throw new EnablebankingException("Enablebanking ist derzeit nicht erreichbar.", exception);
		}
	}

	private Map<String, Object> parseResponse(HttpResponse<String> response) {
		Map<String, Object> body = response.body() == null || response.body().isBlank()
				? Map.of() : requireObject(SimpleJson.parse(response.body()));
		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return body;
		}
		String details = firstText(body, "detail", "message", "error");
		String message = details != null ? "Enablebanking: " + details
				: "Enablebanking-Anfrage fehlgeschlagen (HTTP " + response.statusCode() + ").";
		throw new EnablebankingException(message, response.statusCode());
	}

	private EnablebankingAspsp mapAspsp(Map<String, Object> value) {
		List<String> psuTypes = list(value.get("psu_types")).stream().map(item -> string(item)).toList();
		List<EnablebankingAuthMethod> authMethods = objectList(value.get("auth_methods")).stream()
				.filter(method -> !Boolean.TRUE.equals(method.get("hidden_method")))
				.map(method -> new EnablebankingAuthMethod(string(method.get("name")), string(method.get("title")),
						string(method.get("psu_type")), string(method.get("approach"))))
				.toList();
		Number maximumValidity = number(value.get("maximum_consent_validity"));
		return new EnablebankingAspsp(string(value.get("name")), string(value.get("country")),
				maximumValidity != null ? maximumValidity.intValue() : 15_552_000, psuTypes, authMethods);
	}

	private EnablebankingSession mapSession(Map<String, Object> value, String defaultStatus) {
		List<Map<String, Object>> accountData = objectList(value.get("accounts_data"));
		if (accountData.isEmpty()) {
			accountData = objectList(value.get("accounts"));
		}
		List<EnablebankingRemoteAccount> accounts = accountData.stream().map(this::mapAccount).toList();
		String validUntil = firstText(object(value.get("access")), "valid_until");
		validUntil = firstText(validUntil, string(value.get("valid_until")));
		return new EnablebankingSession(firstText(value, "session_id", "id"),
				firstText(string(value.get("status")), defaultStatus),
				validUntil != null ? OffsetDateTime.parse(validUntil) : null, accounts);
	}

	private EnablebankingRemoteAccount mapAccount(Map<String, Object> value) {
		Map<String, Object> accountId = object(value.get("account_id"));
		Map<String, Object> accountServicer = object(value.get("account_servicer"));
		Map<String, Object> other = object(accountId.get("other"));
		return new EnablebankingRemoteAccount(string(value.get("uid")), string(value.get("identification_hash")),
				string(accountId.get("iban")), firstText(accountServicer, "bic_fi", "bic"),
				firstText(firstText(accountId, "bban"), string(other.get("identification"))), firstText(value, "name", "display_name"),
				string(value.get("details")), string(value.get("product")), string(value.get("cash_account_type")),
				string(value.get("currency")), ownerName(value.get("owner_name"), other));
	}

	private String ownerName(Object ownerName, Map<String, Object> other) {
		if (ownerName instanceof List<?> owners) {
			return owners.stream().map(value -> string(value)).filter(value -> value != null && !value.isBlank())
					.findFirst().orElse(null);
		}
		String value = string(ownerName);
		return value != null ? value : string(other.get("identification"));
	}

	private static String encodePath(String value) {
		return encodeQuery(value).replace("+", "%20");
	}

	private static String encodeQuery(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
