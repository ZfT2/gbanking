package de.zft2.gbanking.enablebanking;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.service.Service;

public class EnablebankingAuthorizationService implements Service {

	private static final Duration AUTHORIZATION_TIMEOUT = Duration.ofMinutes(10);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final EnablebankingCallbackMaterialService callbackMaterialService;

	public EnablebankingAuthorizationService() {
		this(new EnablebankingCallbackMaterialService());
	}

	EnablebankingAuthorizationService(EnablebankingCallbackMaterialService callbackMaterialService) {
		this.callbackMaterialService = callbackMaterialService;
	}

	public EnablebankingSession authorize(Psd2ClientConfiguration configuration, EnablebankingAspsp aspsp,
			String psuType, String authMethod) {
		EnablebankingApiClient client = new EnablebankingApiClient(configuration);
		String state = randomState();
		try (EnablebankingCallbackServer callbackServer = new EnablebankingCallbackServer(configuration.getCallbackUrl(), state,
				callbackMaterialService.ensureMaterial(configuration))) {
			callbackServer.start();
			URI authorizationUri = client.createAuthorization(aspsp, psuType, authMethod,
					configuration.getCallbackUrl(), state);
			openBrowser(authorizationUri);
			String authorizationCode = callbackServer.awaitAuthorizationCode(AUTHORIZATION_TIMEOUT);
			return client.createSession(authorizationCode);
		}
	}

	private void openBrowser(URI authorizationUri) {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			throw new EnablebankingException("Zum Anmelden muss ein Systembrowser verfügbar sein.");
		}
		try {
			Desktop.getDesktop().browse(authorizationUri);
		} catch (IOException exception) {
			throw new EnablebankingException("Der Systembrowser konnte nicht geöffnet werden.", exception);
		}
	}

	private String randomState() {
		byte[] state = new byte[32];
		RANDOM.nextBytes(state);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(state);
	}
}
