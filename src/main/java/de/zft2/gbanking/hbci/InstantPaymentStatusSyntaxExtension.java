package de.zft2.gbanking.hbci;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class InstantPaymentStatusSyntaxExtension {

	private static final String DEFINITION_ID = "InstUebSEPAStatus1";
	private static final String FIN_TS_VERSION = "300";
	private static final String RESOURCE = "/hbci/instant-payment-status-syntax.xml";
	private static final AtomicBoolean INFO_LOGGED = new AtomicBoolean();
	private static final Logger log = LogManager.getLogger(InstantPaymentStatusSyntaxExtension.class);

	private InstantPaymentStatusSyntaxExtension() {
	}

	public static void apply(HBCIHandler handler) {
		if (handler != null && FIN_TS_VERSION.equals(handler.getHBCIVersion()) && apply(handler.getMsgGen().getSyntax())
				&& INFO_LOGGED.compareAndSet(false, true)) {
			log.info("Temporarily extended hbci4java FinTS syntax with HKIPS/HIIPS support");
		}
	}

	static boolean apply(Document syntax) {
		if (syntax == null || syntax.getElementById(DEFINITION_ID) != null) {
			return false;
		}
		try (InputStream input = InstantPaymentStatusSyntaxExtension.class.getResourceAsStream(RESOURCE)) {
			if (input == null) {
				throw new HBCI_Exception("Missing GBanking HBCI syntax extension: " + RESOURCE);
			}
			Document extension = createDocumentBuilderFactory().newDocumentBuilder().parse(input);
			appendGroups(syntax, extension.getDocumentElement());
			return true;
		} catch (IOException | ParserConfigurationException | SAXException exception) {
			throw new HBCI_Exception("Could not load GBanking HBCI syntax extension", exception);
		}
	}

	private static DocumentBuilderFactory createDocumentBuilderFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}

	private static void appendGroups(Document syntax, Element extension) {
		NodeList groups = extension.getChildNodes();
		for (int index = 0; index < groups.getLength(); index++) {
			Node group = groups.item(index);
			if (group instanceof Element groupElement) {
				appendGroup(syntax, groupElement);
			}
		}
	}

	private static void appendGroup(Document syntax, Element group) {
		Element target = findTarget(syntax, group.getAttribute("target"));
		Node insertionPoint = findInsertionPoint(target, group.getAttribute("after"));
		NodeList children = group.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element) {
				Element imported = (Element) syntax.importNode(child, true);
				target.insertBefore(imported, insertionPoint);
				registerIds(imported);
			}
		}
	}

	private static Element findTarget(Document syntax, String name) {
		Element target = syntax.getElementById(name);
		if (target == null) {
			NodeList elements = syntax.getElementsByTagName(name);
			target = elements.getLength() == 1 ? (Element) elements.item(0) : null;
		}
		if (target == null) {
			throw new HBCI_Exception("Missing HBCI syntax extension target: " + name);
		}
		return target;
	}

	private static Node findInsertionPoint(Element target, String afterType) {
		if (afterType == null || afterType.isBlank()) {
			return null;
		}
		NodeList children = target.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && afterType.equals(element.getAttribute("type"))) {
				return child.getNextSibling();
			}
		}
		throw new HBCI_Exception("Missing HBCI syntax insertion point: " + afterType);
	}

	private static void registerIds(Element element) {
		if (element.hasAttribute("id")) {
			element.setIdAttribute("id", true);
		}
		NodeList children = element.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element childElement) {
				registerIds(childElement);
			}
		}
	}
}
