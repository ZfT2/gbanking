package de.zft2.gbanking.file.exp;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.exception.ExportException;
import de.zft2.gbanking.gui.BaseWorker;

public class FileExportMT940Bean extends FileExportBean {

	private static final Logger log = LogManager.getLogger(FileExportMT940Bean.class);

	private static final String NONREF = "NONREF";

	private static final String CRLF = "\r\n";
	private static final String[] USAGE_TAGS = { "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "60", "61", "62", "63" };
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");
	private static final DateTimeFormatter BOOKING_DATE = DateTimeFormatter.ofPattern("MMdd");
	private static final Comparator<Booking> BOOKING_ORDER = Comparator.comparing(FileExportMT940Bean::sortDate).thenComparingInt(Booking::getId);

	public FileExportMT940Bean(BaseWorker worker) {
		super(worker);
	}

	@Override
	public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) throws ExportException {
		try {
			String mt940Content = createMT940Content(accountList);
			saveToExportFile(mt940Content, fileName);
			return true;
		} catch (Exception e) {
			log.error("Error exporting MT940 file {}", fileNameOnly(fileName), e);
			log.debug("MT940 export path: {}", fileName);
			return false;
		}
	}

	private String createMT940Content(List<BankAccount> accountList) {
		StringBuilder content = new StringBuilder();
		totalAccounts = accountList.size();
		updateWorkerState(1, true, "UI_PROGRESS_EXPORT_ACCOUNTS", totalAccounts);

		int exportedAccounts = 0;
		for (BankAccount account : accountList) {
			updateWorkerStateAccounts(exportedAccounts++, "UI_PROGRESS_EXPORT_MT940_BOOKINGS_ACCOUNT", account.getAccountName());
			appendStatement(content, account, exportedAccounts);
		}
		return content.toString();
	}

	private void appendStatement(StringBuilder content, BankAccount account, int statementNumber) {
		List<Booking> bookings = getBookings(account).stream().sorted(BOOKING_ORDER).toList();
		LocalDate startDate = statementDate(bookings, true);
		LocalDate endDate = statementDate(bookings, false);
		String currency = firstNonBlank(account.getCurrency(), "EUR");
		BigDecimal endBalance = endBalance(account, bookings);
		BigDecimal startBalance = endBalance.subtract(sumBookings(bookings));

		content.append(":20:").append(reference("GBANKING" + account.getId())).append(CRLF);
		content.append(":25:").append(accountIdentifier(account, currency)).append(CRLF);
		content.append(":28C:").append(statementNumber).append("/1").append(CRLF);
		content.append(":60F:").append(formatBalance(startDate, currency, startBalance)).append(CRLF);
		for (Booking booking : bookings) {
			content.append(createTag61(booking));
			content.append(createTag86(booking));
		}
		content.append(":62F:").append(formatBalance(endDate, currency, endBalance)).append(CRLF);
		content.append("-").append(CRLF);
	}

	private String createTag61(Booking booking) {
		LocalDate valuta = firstNonNull(booking.getDateValue(), booking.getDateBooking(), LocalDate.now(ZoneId.systemDefault()));
		LocalDate bookingDate = firstNonNull(booking.getDateBooking(), valuta);
		BigDecimal amount = safeAmount(booking.getAmount());
		BookingAdditionalDetails details = booking.getAdditionalDetails();
		String instref = details != null ? details.getInstref() : null;
		String debitCredit = amount.signum() < 0 ? "D" : "C";
		return ":61:" + DATE.format(valuta) + BOOKING_DATE.format(bookingDate) + debitCredit + formatAmount(amount) + "NTRF"
				+ reference(firstNonBlank(booking.getSepaCustomerRef(), NONREF)) + "//" + reference(firstNonBlank(instref, "GB" + booking.getId()))
				+ CRLF;
	}

	private String createTag86(Booking booking) {
		StringBuilder tag = new StringBuilder(":86:");
		BookingAdditionalDetails details = booking.getAdditionalDetails();
		String additionalText = details != null ? details.getText() : null;
		tag.append(gvcode(booking));
		appendStructuredValue(tag, "00", firstNonBlank(additionalText, booking.getBookingType() != null ? booking.getBookingType().getGermanName() : null));
		appendUsage(tag, booking.getPurpose());

		Recipient recipient = booking.getRecipient();
		if (recipient != null) {
			appendStructuredValue(tag, "30", firstNonBlank(recipient.getBic(), recipient.getBlz()));
			appendStructuredValue(tag, "31", firstNonBlank(recipient.getIban(), recipient.getAccountNumber()));
			appendStructuredValue(tag, "32", recipient.getName());
			appendStructuredValue(tag, "34", details != null ? details.getKey() : null);
		}
		tag.append(CRLF);
		return tag.toString();
	}

	private String gvcode(Booking booking) {
		BookingAdditionalDetails details = booking.getAdditionalDetails();
		String gvcode = details != null ? details.getGvcode() : null;
		if (gvcode != null && gvcode.length() >= 3) {
			return gvcode.substring(0, 3);
		}
		return "166";
	}

	private void appendUsage(StringBuilder tag, String purpose) {
		List<String> chunks = chunkValues(purpose, 27);
		for (int i = 0; i < chunks.size() && i < USAGE_TAGS.length; i++) {
			appendStructuredValue(tag, USAGE_TAGS[i], chunks.get(i));
		}
	}

	private List<String> chunkValues(String value, int maxLength) {
		String normalized = normalizeUsageValue(value);
		if (normalized == null) {
			return List.of();
		}
		return normalized.lines()
				.flatMap(line -> splitFixed(line, maxLength).stream())
				.filter(line -> !line.isBlank())
				.toList();
	}

	private List<String> splitFixed(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return List.of(value);
		}
		return java.util.stream.IntStream.iterate(0, start -> start < value.length(), start -> start + maxLength)
				.mapToObj(start -> value.substring(start, Math.min(start + maxLength, value.length())))
				.toList();
	}

	private void appendStructuredValue(StringBuilder tag, String key, String value) {
		String normalized = normalizeMultiValue(value);
		if (normalized != null) {
			tag.append("?").append(key).append(normalized);
		}
	}

	private String normalizeMultiValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.replace('\r', ' ').replace('\n', ' ').replace("?", " ").strip();
	}

	private String normalizeUsageValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.replace("\r\n", "\n").replace('\r', '\n').replace("?", " ").strip();
	}

	private String formatBalance(LocalDate date, String currency, BigDecimal value) {
		return (value.signum() < 0 ? "D" : "C") + DATE.format(date) + currency + formatAmount(value);
	}

	private String formatAmount(BigDecimal value) {
		return safeAmount(value).abs().setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
	}

	private String accountIdentifier(BankAccount account, String currency) {
		String iban = firstNonBlank(account.getIban());
		if (iban != null) {
			return iban;
		}
		return firstNonBlank(account.getBlz(), "") + "/" + firstNonBlank(account.getNumber(), "") + currency;
	}

	private String reference(String value) {
		String normalized = normalizeMultiValue(value);
		if (normalized == null) {
			return NONREF;
		}
		String reference = normalized.replaceAll("[^A-Za-z0-9]", "");
		return reference.isBlank() ? NONREF : reference.substring(0, Math.min(reference.length(), 16));
	}

	private LocalDate statementDate(List<Booking> bookings, boolean first) {
		if (bookings.isEmpty()) {
			return LocalDate.now(ZoneId.systemDefault());
		}
		Booking booking = first ? bookings.get(0) : bookings.get(bookings.size() - 1);
		return sortDate(booking);
	}

	private static LocalDate sortDate(Booking booking) {
		return firstNonNull(booking.getDateBooking(), booking.getDateValue(), LocalDate.now(ZoneId.systemDefault()));
	}

	@SafeVarargs
	private static <T> T firstNonNull(T... values) {
		for (T value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private void saveToExportFile(String mt940Content, String fileName) throws IOException {
		Path exportPath = prepareExportPath(fileName);
		Files.writeString(exportPath, mt940Content, StandardCharsets.ISO_8859_1);
		log.info("Finished MT940 booking export. file={}", exportPath.getFileName());
		log.debug("MT940 export path: {}", exportPath);
		updateWorkerState(99, false, "UI_PROGRESS_FINISH");
	}
}
