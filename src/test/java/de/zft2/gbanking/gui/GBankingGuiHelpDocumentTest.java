package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class GBankingGuiHelpDocumentTest {

	private static final Pattern LINKED_RESOURCE_PATTERN = Pattern.compile("href=\"([^\"#]+)");

	@Test
	void helpDocuments_shouldIncludeLinkedResources() throws IOException {
		for (String document : GBankingGui.HELP_DOCUMENTS) {
			assertNotNull(getClass().getResource("/doc/" + document), () -> "Missing help resource: " + document);
			if (document.endsWith(".html")) {
				assertLinkedResourcesAreCopied(document);
			}
		}
	}

	private void assertLinkedResourcesAreCopied(String document) throws IOException {
		Matcher matcher = LINKED_RESOURCE_PATTERN.matcher(readResource(document));
		while (matcher.find()) {
			String linkedResource = matcher.group(1);
			if (!linkedResource.contains(":")) {
				assertTrue(GBankingGui.HELP_DOCUMENTS.contains(linkedResource),
						() -> document + " links a resource that is not copied: " + linkedResource);
			}
		}
	}

	private String readResource(String document) throws IOException {
		try (InputStream inputStream = getClass().getResourceAsStream("/doc/" + document)) {
			assertNotNull(inputStream, () -> "Missing help resource: " + document);
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
