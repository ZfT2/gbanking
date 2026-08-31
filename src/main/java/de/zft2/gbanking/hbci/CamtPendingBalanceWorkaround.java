package de.zft2.gbanking.hbci;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.kapott.hbci.comm.Comm;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

final class CamtPendingBalanceWorkaround {

	private static final Set<String> BALANCE_FOLLOWERS = Set.of("TxsSummry", "Ntry", "AddtlRptInf");
	private static final Set<String> START_BALANCE_CODES = Set.of("PRCD", "ITBD", "OPBD");

	private CamtPendingBalanceWorkaround() {
	}

	static NormalizedCamt normalize(String camt) {
		try {
			Document document = parse(camt);
			NodeList reports = document.getElementsByTagNameNS("*", "Rpt");
			int addedBalances = 0;
			for (int index = 0; index < reports.getLength(); index++) {
				Element report = (Element) reports.item(index);
				if (!hasUsableStartBalance(report) && addBalance(report)) {
					addedBalances++;
				}
			}
			return addedBalances == 0 ? new NormalizedCamt(camt, 0)
					: new NormalizedCamt(serialize(document), addedBalances);
		} catch (IOException | ParserConfigurationException | SAXException | TransformerException exception) {
			throw new HBCI_Exception("Could not prepare unbooked CAMT data for parsing", exception);
		}
	}

	private static Document parse(String camt) throws IOException, ParserConfigurationException, SAXException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(camt)));
	}

	private static boolean addBalance(Element report) {
		Element entry = directChild(report, "Ntry");
		String currency = childText(directChild(report, "Acct"), "Ccy");
		Element amount = directChild(entry, "Amt");
		if (currency == null && amount != null) {
			currency = normalizedText(amount.getAttribute("Ccy"));
		}
		if (currency == null) {
			if (entry != null) {
				throw new HBCI_Exception("Unbooked CAMT entry has no currency");
			}
			currency = "XXX";
		}

		Element balance = element(report, "Bal");
		Element type = append(balance, "Tp");
		Element codeOrProprietary = append(type, "CdOrPrtry");
		append(codeOrProprietary, "Cd").setTextContent("ITBD");
		Element balanceAmount = append(balance, "Amt");
		balanceAmount.setAttribute("Ccy", currency);
		balanceAmount.setTextContent("0");
		append(balance, "CdtDbtInd").setTextContent("CRDT");
		Element date = append(balance, "Dt");
		append(date, "Dt").setTextContent(resolveDate(report, entry));

		Node insertionPoint = firstBalanceFollower(report);
		report.insertBefore(balance, insertionPoint);
		return true;
	}

	private static boolean hasUsableStartBalance(Element report) {
		Element balance = directChild(report, "Bal");
		Element type = directChild(balance, "Tp");
		Element codeOrProprietary = directChild(type, "CdOrPrtry");
		String code = childText(codeOrProprietary, "Cd");
		return code != null && START_BALANCE_CODES.contains(code);
	}

	private static String resolveDate(Element report, Element entry) {
		String date = childText(directChild(entry, "BookgDt"), "Dt");
		if (date == null) {
			date = childText(directChild(entry, "ValDt"), "Dt");
		}
		if (date == null) {
			String creationDateTime = childText(report, "CreDtTm");
			date = creationDateTime != null && creationDateTime.length() >= 10 ? creationDateTime.substring(0, 10) : null;
		}
		return date != null ? date : LocalDate.now().toString();
	}

	private static Node firstBalanceFollower(Element report) {
		for (Node child = report.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element && BALANCE_FOLLOWERS.contains(localName(child))) {
				return child;
			}
		}
		return null;
	}

	private static Element append(Element parent, String localName) {
		Element child = element(parent, localName);
		parent.appendChild(child);
		return child;
	}

	private static Element element(Element parent, String localName) {
		String prefix = parent.getPrefix();
		String qualifiedName = prefix == null ? localName : prefix + ":" + localName;
		return parent.getOwnerDocument().createElementNS(parent.getNamespaceURI(), qualifiedName);
	}

	private static Element directChild(Element parent, String localName) {
		if (parent == null) {
			return null;
		}
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element element && localName.equals(localName(element))) {
				return element;
			}
		}
		return null;
	}

	private static String childText(Element parent, String localName) {
		Element child = directChild(parent, localName);
		return child != null ? normalizedText(child.getTextContent()) : null;
	}

	private static String localName(Node node) {
		return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
	}

	private static String normalizedText(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String serialize(Document document) throws TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, Comm.ENCODING);
		transformer.setOutputProperty(OutputKeys.INDENT, "no");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		transformer.transform(new DOMSource(document), new StreamResult(output));
		return output.toString(Charset.forName(Comm.ENCODING));
	}

	record NormalizedCamt(String content, int addedBalances) {
	}
}
