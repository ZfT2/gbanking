package de.zft2.gbanking.service.stock;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Price;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Account;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Portfolio;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Security;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Transaction;
import de.zft2.gbanking.util.SecureXml;

final class PortfolioPerformanceXmlReader {

	private static final Pattern SECURITY_REFERENCE = Pattern.compile("(?:^|/)securities/security(?:\\[(\\d+)])?$");
	private static final Pattern REFERENCE_SEGMENT = Pattern.compile("([\\w-]+)(?:\\[(\\d+)])?");
	private static final BigDecimal AMOUNT_SCALE = BigDecimal.valueOf(100);
	private static final BigDecimal VALUE_SCALE = BigDecimal.valueOf(100_000_000L);

	PortfolioPerformanceData.Document read(Path file) throws IOException {
		Document document = parse(file);
		Element client = document.getDocumentElement();
		List<Security> securities = readSecurities(directChild(client, "securities"));
		Map<Integer, String> externalIds = new HashMap<>();
		for (int index = 0; index < securities.size(); index++) {
			externalIds.put(index + 1, securities.get(index).externalId());
		}
		List<Element> portfolioElements = referencedChildren(client, "portfolios", "portfolio");
		if (portfolioElements.isEmpty()) {
			throw new GBankingException("Die XML-Datei enthält kein Depot");
		}
		List<Element> accountElements = referencedChildren(client, "accounts", "account");
		List<Account> accounts = accountElements.stream().map(element -> readAccount(element, externalIds)).toList();
		List<Portfolio> portfolios = new ArrayList<>();
		for (Element portfolio : portfolioElements) {
			int settlementAccountIndex = findSettlementAccountIndex(portfolio, accountElements, accounts);
			String settlementAccountId = settlementAccountIndex >= 0
					? accounts.get(settlementAccountIndex).externalId() : null;
			portfolios.add(new Portfolio(requiredText(portfolio, "uuid", "Depotkennung"),
					requiredText(portfolio, "name", "Depotname"), Boolean.parseBoolean(text(portfolio, "isRetired")),
					settlementAccountId, readTransactions(portfolio, "portfolio-transaction", externalIds)));
		}
		return new PortfolioPerformanceData.Document(text(client, "version"), securities,
				List.copyOf(portfolios), accounts);
	}

	private static Account readAccount(Element element, Map<Integer, String> externalIds) {
		return new Account(requiredText(element, "uuid", "Kontokennung"),
				requiredText(element, "name", "Kontoname"), Currency.forCode(requiredText(element, "currencyCode", "Kontowährung")),
				Boolean.parseBoolean(text(element, "isRetired")),
				readTransactions(element, "account-transaction", externalIds));
	}

	private static int findSettlementAccountIndex(Element portfolio, List<Element> accountElements,
			List<Account> accounts) {
		Element referenceAccount = resolveReference(directChild(portfolio, "referenceAccount"));
		String accountUuid = referenceAccount != null ? text(referenceAccount, "uuid") : null;
		if (accountUuid != null) {
			for (int index = 0; index < accounts.size(); index++) {
				if (accountUuid.equals(accounts.get(index).externalId())) {
					return index;
				}
			}
		}
		for (int index = 0; index < accountElements.size(); index++) {
			if (isAncestor(accountElements.get(index), portfolio)) {
				return index;
			}
		}
		if (accounts.size() <= 1) {
			return accounts.isEmpty() ? -1 : 0;
		}
		throw new GBankingException("Das Verrechnungskonto des Depots '" + requiredText(portfolio, "name", "Depotname")
				+ "' kann in der XML-Datei nicht eindeutig bestimmt werden");
	}

	private static boolean isAncestor(Element possibleAncestor, Element element) {
		Node current = element.getParentNode();
		while (current != null) {
			if (current == possibleAncestor) {
				return true;
			}
			current = current.getParentNode();
		}
		return false;
	}

	private static Document parse(Path file) throws IOException {
		try (InputStream input = Files.newInputStream(file)) {
			return SecureXml.newDocumentBuilderFactory(false).newDocumentBuilder().parse(input);
		} catch (ParserConfigurationException | SAXException exception) {
			throw new GBankingException("Die Portfolio-Performance-XML-Datei ist ungültig", exception);
		}
	}

	private static List<Security> readSecurities(Element securitiesElement) {
		if (securitiesElement == null) {
			return List.of();
		}
		List<Security> result = new ArrayList<>();
		for (Element element : directChildren(securitiesElement, "security")) {
			String uuid = text(element, "uuid");
			if (uuid == null) {
				continue;
			}
			Currency currency = Currency.forCode(text(element, "currencyCode"));
			List<Price> prices = new ArrayList<>(readPrices(directChild(element, "prices")));
			addLatestPrice(prices, directChild(element, "latest"));
			result.add(new Security(uuid, requiredText(element, "name", "Wertpapiername"), currency,
					text(element, "isin"), text(element, "wkn"), text(element, "tickerSymbol"), null,
					List.copyOf(prices)));
		}
		return result;
	}

	private static void addLatestPrice(List<Price> prices, Element latest) {
		if (latest == null || latest.getAttribute("t").isBlank() || latest.getAttribute("v").isBlank()) {
			return;
		}
		Price latestPrice = new Price(LocalDate.parse(latest.getAttribute("t")),
				new BigDecimal(latest.getAttribute("v")).divide(VALUE_SCALE));
		if (prices.stream().noneMatch(price -> price.date().equals(latestPrice.date())
				&& price.value().compareTo(latestPrice.value()) == 0)) {
			prices.add(latestPrice);
		}
	}

	private static List<Price> readPrices(Element pricesElement) {
		if (pricesElement == null) {
			return List.of();
		}
		List<Price> result = new ArrayList<>();
		for (Element price : directChildren(pricesElement, "price")) {
			String date = price.getAttribute("t");
			String value = price.getAttribute("v");
			if (!date.isBlank() && !value.isBlank()) {
				result.add(new Price(LocalDate.parse(date), new BigDecimal(value).divide(VALUE_SCALE)));
			}
		}
		return result;
	}

	private static List<Transaction> readTransactions(Element owner, String elementName,
			Map<Integer, String> externalIds) {
		Element transactions = directChild(owner, "transactions");
		if (transactions == null) {
			return List.of();
		}
		List<Transaction> result = new ArrayList<>();
		for (Element element : directChildren(transactions, elementName)) {
			if (text(element, "uuid") == null) {
				continue;
			}
			String currencyCode = text(element, "currencyCode");
			BigDecimal fees = unitAmount(element, "FEE");
			BigDecimal taxes = unitAmount(element, "TAX");
			result.add(new Transaction(text(element, "uuid"), LocalDateTime.parse(requiredText(element, "date", "Datum")),
					requiredText(element, "type", "Transaktionsart"), minorAmount(text(element, "amount")),
					Currency.forCode(currencyCode), null, null, null, fees, taxes,
					scaledValue(text(element, "shares")), resolveSecurityReference(directChild(element, "security"), externalIds),
					null, null, null, null, text(element, "note")));
		}
		return result;
	}

	private static BigDecimal unitAmount(Element transaction, String type) {
		Element units = directChild(transaction, "units");
		if (units == null) {
			return null;
		}
		for (Element unit : directChildren(units, "unit")) {
			if (type.equals(unit.getAttribute("type"))) {
				Element amount = directChild(unit, "amount");
				return amount != null ? minorAmount(amount.getAttribute("amount")) : null;
			}
		}
		return null;
	}

	private static String resolveSecurityReference(Element security, Map<Integer, String> externalIds) {
		if (security == null) {
			return null;
		}
		String inlineUuid = text(security, "uuid");
		if (inlineUuid != null) {
			return inlineUuid;
		}
		Matcher matcher = SECURITY_REFERENCE.matcher(security.getAttribute("reference"));
		if (!matcher.find()) {
			return null;
		}
		int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 1;
		return externalIds.get(index);
	}

	private static List<Element> referencedChildren(Element client, String collectionName, String elementName) {
		Element collection = directChild(client, collectionName);
		if (collection == null) {
			return List.of();
		}
		return directChildren(collection, elementName).stream().map(PortfolioPerformanceXmlReader::resolveReference)
				.filter(java.util.Objects::nonNull).distinct().toList();
	}

	private static Element resolveReference(Element element) {
		if (element == null || element.getAttribute("reference").isBlank()) {
			return element;
		}
		Node current = element;
		for (String segment : element.getAttribute("reference").split("/")) {
			if (segment.isBlank() || ".".equals(segment)) {
				continue;
			}
			if ("..".equals(segment)) {
				current = current != null ? current.getParentNode() : null;
				continue;
			}
			Matcher matcher = REFERENCE_SEGMENT.matcher(segment);
			if (!matcher.matches()) {
				return null;
			}
			int childIndex = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
			current = directChild(current, matcher.group(1), childIndex);
		}
		return current instanceof Element resolved ? resolved : null;
	}

	private static Element directChild(Node parent, String name, int requestedIndex) {
		if (parent == null) {
			return null;
		}
		int matchingIndex = 0;
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && name.equals(element.getTagName())
					&& ++matchingIndex == requestedIndex) {
				return element;
			}
		}
		return null;
	}

	private static BigDecimal minorAmount(String value) {
		return value != null && !value.isBlank() ? new BigDecimal(value).divide(AMOUNT_SCALE) : null;
	}

	private static BigDecimal scaledValue(String value) {
		return value != null && !value.isBlank() ? new BigDecimal(value).divide(VALUE_SCALE) : null;
	}

	private static String requiredText(Element element, String childName, String description) {
		String value = text(element, childName);
		if (value == null) {
			throw new GBankingException(description + " fehlt in der Portfolio-Performance-XML-Datei");
		}
		return value;
	}

	private static String text(Element parent, String childName) {
		Element child = directChild(parent, childName);
		if (child == null) {
			return null;
		}
		String value = child.getTextContent();
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static Element directChild(Element parent, String name) {
		if (parent == null) {
			return null;
		}
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && name.equals(element.getTagName())) {
				return element;
			}
		}
		return null;
	}

	private static List<Element> directChildren(Element parent, String name) {
		List<Element> result = new ArrayList<>();
		if (parent == null) {
			return result;
		}
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && name.equals(element.getTagName())) {
				result.add(element);
			}
		}
		return result;
	}
}
