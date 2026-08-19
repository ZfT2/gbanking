package de.zft2.gbanking.paypal;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class PaypalSoapClient {

	static final int MAX_TRANSACTION_RESULTS = 100;
	private static final URI LIVE_ENDPOINT = URI.create("https://api-3t.paypal.com/2.0/");
	private static final String API_VERSION = "204.0";
	private static final String SUCCESS = "SUCCESS";
	private static final String SUCCESS_WITH_WARNING = "SUCCESSWITHWARNING";

	private final URI endpoint;
	private final PaypalSoapTransport transport;

	public PaypalSoapClient() {
		this(LIVE_ENDPOINT, new HttpPaypalSoapTransport());
	}

	PaypalSoapClient(URI endpoint, PaypalSoapTransport transport) {
		this.endpoint = endpoint;
		this.transport = transport;
	}

	public List<PaypalBalance> getBalances(String apiUsername, char[] apiPassword, String apiSignature) throws InterruptedException {
		Document response = execute("GetBalance", "<ns:ReturnAllCurrencies>1</ns:ReturnAllCurrencies>", apiUsername, apiPassword, apiSignature);
		Map<String, PaypalBalance> balances = new LinkedHashMap<>();
		for (Element balance : elements(response, "Balance")) {
			addBalance(balances, balance);
		}
		for (Element holding : elements(response, "BalanceHoldings")) {
			addBalance(balances, holding);
		}
		return List.copyOf(balances.values());
	}

	public List<PaypalTransaction> searchTransactions(String apiUsername, char[] apiPassword, String apiSignature, Instant start, Instant end)
			throws InterruptedException {
		String requestFields = element("StartDate", DateTimeFormatter.ISO_INSTANT.format(start))
				+ element("EndDate", DateTimeFormatter.ISO_INSTANT.format(end))
				+ element("TransactionClass", "BalanceAffecting");
		Document response = execute("TransactionSearch", requestFields, apiUsername, apiPassword, apiSignature);
		List<PaypalTransaction> transactions = new ArrayList<>();
		for (Element transaction : elements(response, "PaymentTransactions")) {
			transactions.add(mapTransaction(transaction));
		}
		return transactions;
	}

	private Document execute(String operation, String requestFields, String apiUsername, char[] apiPassword, String apiSignature)
			throws InterruptedException {
		String request = soapEnvelope(operation, requestFields, apiUsername, apiPassword, apiSignature);
		Document response = parse(transport.send(endpoint, request));
		validateResponse(response);
		return response;
	}

	private String soapEnvelope(String operation, String requestFields, String apiUsername, char[] apiPassword, String apiSignature) {
		return MessageFormat.format("""
				<?xml version="1.0" encoding="UTF-8"?>
				<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
				 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				 xmlns:ebl="urn:ebay:apis:eBLBaseComponents"
				 xmlns:ns="urn:ebay:api:PayPalAPI">
				 <SOAP-ENV:Header>
				  <ns:RequesterCredentials xsi:type="ebl:CustomSecurityHeaderType">
				   <ebl:Credentials xsi:type="ebl:UserIdPasswordType">
				    <ebl:Username>{0}</ebl:Username>
				    <ebl:Password>{1}</ebl:Password>
				    <ebl:Signature>{2}</ebl:Signature>
				   </ebl:Credentials>
				  </ns:RequesterCredentials>
				 </SOAP-ENV:Header>
				 <SOAP-ENV:Body>
				  <ns:{3}Req>
				   <ns:{3}Request>
				    <ebl:Version>{4}</ebl:Version>
				    {5}
				   </ns:{3}Request>
				  </ns:{3}Req>
				 </SOAP-ENV:Body>
				</SOAP-ENV:Envelope>
				""", escape(apiUsername), escape(apiPassword), escape(apiSignature), operation, API_VERSION, requestFields);
	}

	private PaypalTransaction mapTransaction(Element transaction) {
		Element netAmount = child(transaction, "NetAmount");
		if (netAmount == null || text(netAmount).isBlank()) {
			throw new PaypalApiException("PayPal transaction has no net amount", false);
		}
		return new PaypalTransaction(Instant.parse(childText(transaction, "Timestamp")), childText(transaction, "Type"),
				childText(transaction, "Payer"), childText(transaction, "PayerDisplayName"), childText(transaction, "TransactionID"),
				childText(transaction, "Status"), decimal(transaction, "GrossAmount"), decimal(transaction, "FeeAmount"),
				new BigDecimal(text(netAmount)), currency(netAmount));
	}

	private void validateResponse(Document document) {
		String fault = firstText(document, "faultstring");
		if (!fault.isBlank()) {
			throw new PaypalApiException(fault, isAuthenticationFailure("", fault));
		}
		String acknowledgement = firstText(document, "Ack").toUpperCase(Locale.ROOT);
		if (SUCCESS.equals(acknowledgement) || SUCCESS_WITH_WARNING.equals(acknowledgement)) {
			return;
		}
		String errorCode = firstText(document, "ErrorCode");
		String message = firstText(document, "LongMessage");
		if (message.isBlank()) {
			message = firstText(document, "ShortMessage");
		}
		String error = message.isBlank() ? "PayPal SOAP response was not successful" : message;
		throw new PaypalApiException(error, isAuthenticationFailure(errorCode, error));
	}

	private boolean isAuthenticationFailure(String errorCode, String message) {
		String normalized = message.toUpperCase(Locale.ROOT);
		return "10002".equals(errorCode) || normalized.contains("AUTHENTICATION") || normalized.contains("SECURITY HEADER");
	}

	private void addBalance(Map<String, PaypalBalance> balances, Element element) {
		String currency = currency(element);
		if (!currency.isBlank()) {
			balances.put(currency, new PaypalBalance(currency, new BigDecimal(text(element))));
		}
	}

	private BigDecimal decimal(Element parent, String localName) {
		Element element = child(parent, localName);
		return element != null && !text(element).isBlank() ? new BigDecimal(text(element)) : null;
	}

	private String currency(Element amount) {
		return amount.getAttribute("currencyID").trim().toUpperCase(Locale.ROOT);
	}

	private static Document parse(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
		} catch (Exception exception) {
			throw new PaypalApiException("Invalid PayPal SOAP response", exception);
		}
	}

	private static List<Element> elements(Document document, String localName) {
		NodeList nodes = document.getElementsByTagNameNS("*", localName);
		List<Element> result = new ArrayList<>(nodes.getLength());
		for (int index = 0; index < nodes.getLength(); index++) {
			result.add((Element) nodes.item(index));
		}
		return result;
	}

	private static Element child(Element parent, String localName) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && localName.equals(element.getLocalName())) {
				return element;
			}
		}
		return null;
	}

	private static String firstText(Document document, String localName) {
		NodeList nodes = document.getElementsByTagNameNS("*", localName);
		return nodes.getLength() > 0 ? text(nodes.item(0)) : "";
	}

	private static String childText(Element parent, String localName) {
		Element element = child(parent, localName);
		return element != null ? text(element) : "";
	}

	private static String text(Node node) {
		String value = node.getTextContent();
		return value != null ? value.trim() : "";
	}

	private static String element(String name, String value) {
		return "<ns:" + name + ">" + escape(value) + "</ns:" + name + ">";
	}

	private static String escape(char[] value) {
		return escape(value != null ? new String(value) : "");
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
	}
}
