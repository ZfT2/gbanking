package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.manager.MsgGen;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class InstantPaymentStatusSyntaxExtensionTest {

	@Test
	void applyShouldAddCompleteLowlevelSyntaxOnlyOnce() {
		MsgGen generator = loadSyntax();
		Document syntax = generator.getSyntax();

		InstantPaymentStatusSyntaxExtension.apply(syntax);

		assertNotNull(syntax.getElementById("InstUebSEPAStatus1"));
		assertEquals(List.of("1"), generator.getLowlevelGVs().get("InstUebSEPAStatus"));
		assertContains(generator.getGVParameterNames("InstUebSEPAStatus", "1"), "My.iban", "My.bic", "formats.format", "orderid");
		assertContains(generator.getGVResultNames("InstUebSEPAStatus", "1"), "My.iban", "sepadescr", "sepapain", "orderid", "ccode",
				"orderstatus");
		assertContains(generator.getGVRestrictionNames("InstUebSEPAStatus", "1"), "minwait", "suppformats");

		assertFalse(InstantPaymentStatusSyntaxExtension.apply(syntax));
		assertEquals(1, countReferences(syntax.getElementById("GV"), "InstUebSEPAStatus1"));
		assertEquals(1, countReferences(syntax.getElementById("GVRes"), "InstUebSEPAStatusRes1"));
		assertEquals(1, countReferences(syntax.getElementById("Params"), "InstUebSEPAStatusPar1"));
	}

	private MsgGen loadSyntax() {
		InputStream input = InstantPaymentStatusSyntaxExtensionTest.class.getResourceAsStream("/hbci-300.xml");
		assertNotNull(input);
		return new MsgGen(input);
	}

	private void assertContains(List<String> actual, String... expected) {
		for (String value : expected) {
			assertTrue(actual.contains(value), () -> "Missing lowlevel field " + value + " in " + actual);
		}
	}

	private int countReferences(Element parent, String type) {
		int count = 0;
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && type.equals(element.getAttribute("type"))) {
				count++;
			}
		}
		return count;
	}
}
