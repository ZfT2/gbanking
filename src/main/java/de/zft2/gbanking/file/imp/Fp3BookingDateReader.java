package de.zft2.gbanking.file.imp;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.zft2.fp3xmlextract.data.Fp3XmlBooking;
import de.zft2.gbanking.util.SecureXml;

/**
 * Restores the dates of bookings that were converted from an FP3 file.
 *
 * The upstream converter only parses two digit years in the "m27" element, while bank files and the
 * GBanking FP3 export write four digit years. Therefore the dates are read again from the FP3 file
 * and applied to the already converted bookings. The bookings are matched by their position within
 * the file and verified by their purpose.
 */
final class Fp3BookingDateReader {

	private static final Logger log = LogManager.getLogger(Fp3BookingDateReader.class);

	/** Base year of two digit years, matches the upstream converter. */
	private static final int BASE_YEAR = 2000;

	private Fp3BookingDateReader() {
		// Utility class, not meant to be instantiated.
	}

	static void applyDates(Path fp3File, Collection<Fp3XmlBooking> bookings) {
		if (bookings == null || bookings.isEmpty()) {
			return;
		}

		List<Fp3BookingEntry> entries;
		try {
			entries = readEntries(fp3File);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			// The bookings stay without a date and are skipped by the import.
			log.warn("Could not read the dates of the FP3 file {}.", fp3File, e);
			return;
		}

		List<Fp3XmlBooking> bookingList = new ArrayList<>(bookings);
		if (entries.size() != bookingList.size()) {
			log.warn("The FP3 file contains {} bookings but {} were converted.", entries.size(), bookingList.size());
		}
		for (int index = 0; index < Math.min(bookingList.size(), entries.size()); index++) {
			applyDate(bookingList.get(index), entries.get(index), index);
		}
	}

	private static void applyDate(Fp3XmlBooking booking, Fp3BookingEntry entry, int index) {
		if (!entry.purpose().equals(booking.getPurpose())) {
			log.warn("The FP3 date at position {} was not assigned because the booking does not match the file entry.", index);
			return;
		}
		if (entry.dateBooking() == null && entry.dateValue() == null) {
			return;
		}
		if (entry.dateBooking() != null) {
			booking.setDateBooking(entry.dateBooking());
		}
		if (entry.dateValue() != null) {
			booking.setDateValue(entry.dateValue());
		}
		booking.setDate(booking.getDateValue() != null ? booking.getDateValue() : booking.getDateBooking());
	}

	private static List<Fp3BookingEntry> readEntries(Path fp3File) throws ParserConfigurationException, SAXException, IOException {
		Document document = parseDocument(fp3File);
		NodeList bookingNodes = document.getElementsByTagName("b2");
		List<Fp3BookingEntry> entries = new ArrayList<>();
		for (int index = 0; index < bookingNodes.getLength(); index++) {
			Node node = bookingNodes.item(index);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element bookingElement = (Element) node;
			Element purposeElement = firstElement(bookingElement, "m21");
			if (purposeElement == null) {
				// Bookings without a purpose are not converted by the upstream converter.
				continue;
			}
			Element dateElement = firstElement(bookingElement, "m27");
			entries.add(toEntry(purposeElement.getAttribute("u"), dateElement != null ? dateElement.getAttribute("u") : null));
		}
		return entries;
	}

	private static Fp3BookingEntry toEntry(String purpose, String dateText) {
		String[] dateLines = dateText != null ? dateText.split("\n") : new String[0];
		LocalDate dateBooking = parseDate(dateLines.length > 0 ? dateLines[0] : null, null);
		LocalDate dateValue = dateLines.length > 1 ? parseDate(dateLines[1], dateBooking) : null;
		return new Fp3BookingEntry(purpose.replace("\n", " "), dateBooking, dateValue);
	}

	/**
	 * Parses a date of the FP3 format. Supported are four digit years ("25.07.2024"), two digit years
	 * ("25.07.24") and dates without a year ("25.07."), which belong to the year of the reference date.
	 */
	private static LocalDate parseDate(String dateText, LocalDate referenceDate) {
		if (dateText == null || dateText.isBlank()) {
			return null;
		}
		String[] parts = dateText.trim().split("\\.", -1);
		if (parts.length != 3) {
			log.warn("Could not parse the FP3 date '{}'.", dateText);
			return null;
		}
		Integer year = parseYear(parts[2], referenceDate);
		if (year == null) {
			log.warn("Could not parse the FP3 date '{}'.", dateText);
			return null;
		}
		try {
			return LocalDate.of(year, Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[0].trim()));
		} catch (DateTimeException | NumberFormatException e) {
			log.warn("Could not parse the FP3 date '{}'.", dateText);
			return null;
		}
	}

	private static Integer parseYear(String yearText, LocalDate referenceDate) {
		String trimmed = yearText.trim();
		if (trimmed.isEmpty()) {
			// A date without a year belongs to the year of the reference date, usually the booking date.
			return referenceDate != null ? referenceDate.getYear() : null;
		}
		try {
			int year = Integer.parseInt(trimmed);
			return trimmed.length() == 2 ? BASE_YEAR + year : year;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Element firstElement(Element parent, String tagName) {
		NodeList nodes = parent.getElementsByTagName(tagName);
		return nodes.item(0) != null ? (Element) nodes.item(0) : null;
	}

	private static Document parseDocument(Path fp3File) throws ParserConfigurationException, SAXException, IOException {
		return SecureXml.newDocumentBuilderFactory(false).newDocumentBuilder().parse(fp3File.toFile());
	}

	private record Fp3BookingEntry(String purpose, LocalDate dateBooking, LocalDate dateValue) {
	}
}
