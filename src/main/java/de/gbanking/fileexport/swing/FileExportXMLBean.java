package de.gbanking.fileexport.swing;

import java.io.File;
import java.math.RoundingMode;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.exception.GBankingException;
import de.gbanking.swing.BaseWorker;
import de.gbanking.util.TypeConverter;

public class FileExportXMLBean extends FileExportBean {

	private static Logger log = LogManager.getLogger(FileExportXMLBean.class);
	
	public FileExportXMLBean(BaseWorker worker) {
		super(worker);
	}

	@Override
	public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) {

		boolean result = true;

		try {
			Document doc = createXMLDocument(accountList);
			saveDocumentToExportFile(fileName, doc);

		} catch (ParserConfigurationException | TransformerException e) {
			result = false;
			log.error("Error exporting file: ", new GBankingException(e.getMessage(), true));;
			//throw new ExportException(e.getMessage());
		}

		return result;
	}

	private Document createXMLDocument(List<BankAccount> accountList) throws ParserConfigurationException {
		
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("KONTEN");
		doc.appendChild(rootElement);
		
		totalAccounts = accountList.size();
		//totalBookings = accountList.stream().flatMap(bankAccount -> bankAccount.getBookings().stream()).count();
		int exportedAccountsCount = 0;
		updateWorkerState(1, true, "Exportiere Konten (Anzahl: %d)", totalAccounts);

		for (BankAccount account : accountList) {
			
			updateWorkerState(1, true, "Exportiere Konto %s", account.getAccountName());
			
			Element accountNode = doc.createElement(TAG_KONTO);

			addElementTextNode(doc, accountNode, TAG_BEZEICHNUNG, account.getAccountName());
			addElementTextNode(doc, accountNode, TAG_TYPE, account.getAccountType().toString());
			addElementTextNode(doc, accountNode, TAG_IBAN, account.getIban());
			addElementTextNode(doc, accountNode, TAG_BIC, account.getBic());
			addElementTextNode(doc, accountNode, TAG_KONTONR, account.getNumber());
			addElementTextNode(doc, accountNode, TAG_BLZ, account.getBlz());
			addElementTextNode(doc, accountNode, TAG_BANKNAME, account.getBankName());
			addElementTextNode(doc, accountNode, TAG_WAEHRUNG, account.getCurrency());
			if (account.getBalance() != null)
				addElementTextNode(doc, accountNode, TAG_KONTOSTAND, String.format("%.2f", account.getBalance().setScale(2, RoundingMode.HALF_UP)));

			Element accountBook = doc.createElement("KONTOBUCH");
			accountNode.appendChild(accountBook);
			
			//updateWorkerState(1, true, "Exportiere Buchungen");
			long exportedBookingsCount = 0;

			List<Booking> bookingList = dbController.getAllByParentFull(Booking.class, account.getId());
			updateWorkerStateBookings(exportedBookingsCount, "Exportiere Buchungen für Konto: %s (Anzahl: %d)", account.getAccountName(), bookingList.size());
			
			for (Booking booking : bookingList) {
				Element bookingNode = doc.createElement("BUCHUNG");

				addElementTextNode(doc, bookingNode, "DATUM", TypeConverter.toDateStringShort(booking.getDateBooking()));
				addElementTextNode(doc, bookingNode, "VALUTA", TypeConverter.toDateStringShort(booking.getDateValue()));

				Element receiver = doc.createElement("EMPFAENGER");
				bookingNode.appendChild(receiver);
				
				Recipient recipient = booking.getRecipient();
				if (recipient != null) {
					addElementTextNode(doc, receiver, "NAME", recipient.getName());
					addElementTextNode(doc, receiver, TAG_IBAN, recipient.getIban());
					addElementTextNode(doc, receiver, TAG_KONTONR, recipient.getAccountNumber());
					addElementTextNode(doc, receiver, TAG_BIC, recipient.getBic());
					addElementTextNode(doc, receiver, TAG_BLZ, recipient.getBlz());
					addElementTextNode(doc, receiver, TAG_BANKNAME, recipient.getBank());
				}

				addElementTextNode(doc, bookingNode, "ZWECK", buildPurpose(booking));
				addElementTextNode(doc, bookingNode, "BETRAG", booking.getAmountStr());
				addElementTextNode(doc, bookingNode, TAG_WAEHRUNG, "EUR");
				if (booking.getCategory() != null)
					addElementTextNode(doc, bookingNode, "KATEGORIE", booking.getCategory().getFullName());
				addElementTextNode(doc, bookingNode, "QUELLE", "0");
				
				accountBook.appendChild(bookingNode);
				
				exportedBookingsCount++;
			}
			
			rootElement.appendChild(accountNode);
		}
		return doc;
	}
	
	private String buildPurpose(Booking booking) {
		
		StringBuilder sb = new StringBuilder(booking.getPurpose());
		
		if (booking.getSepaEndToEnd() != null)
			sb.append(" EndtoEnd: " + booking.getSepaEndToEnd());
		if (booking.getSepaCustomerRef() != null)
			sb.append(" Kundenref.: " + booking.getSepaCustomerRef());
		if (booking.getSepaMandate() != null)
			sb.append(" Mandatsref.: " + booking.getSepaMandate());
		if (booking.getSepaCreditorId() != null)
			sb.append(" Creditor-ID: " + booking.getSepaCreditorId());
		if (booking.getSepaPurpose() != null)
			sb.append(" Purpose: " + booking.getSepaPurpose());
		if (booking.getSepaPersonId() != null)
			sb.append(" Personen-ID: " + booking.getSepaPersonId());
			
		return sb.toString();
		
	}
	
	private void addElementTextNode(Document doc, Element parent, String elementTagName, String text) {
		if (text != null) {
			Element element = doc.createElement(elementTagName);
			element.appendChild(doc.createTextNode(text));
			parent.appendChild(element);
		}
	}
	
	private void saveDocumentToExportFile(String fileName, Document doc) throws TransformerFactoryConfigurationError, TransformerException {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		//transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		DOMSource source = new DOMSource(doc);
		StreamResult resultFile = new StreamResult(new File(fileName));
		transformer.transform(source, resultFile);
		log.info("File saved: {}", fileName);
		
		updateWorkerState(99, false, "beende...");
		//updateWorkerState(100, false, "fertig.");
	}

}
