package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

class SecureXmlTest {

	@Test
	void shouldParseOrdinaryNamespaceAwareXml() throws Exception {
		String xml = "<root xmlns=\"urn:test\"><value>ok</value></root>";

		var document = SecureXml.newDocumentBuilderFactory(true).newDocumentBuilder()
				.parse(new InputSource(new StringReader(xml)));

		assertEquals("urn:test", document.getDocumentElement().getNamespaceURI());
	}

	@Test
	void shouldRejectDoctypeDeclarations() throws Exception {
		String xml = "<!DOCTYPE root [<!ENTITY external SYSTEM \"file:///does-not-exist\">]><root>&external;</root>";
		var builder = SecureXml.newDocumentBuilderFactory(true).newDocumentBuilder();

		assertThrows(SAXException.class, () -> builder.parse(new InputSource(new StringReader(xml))));
	}
}
