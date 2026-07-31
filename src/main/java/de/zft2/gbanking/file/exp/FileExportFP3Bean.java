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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.exception.ExportException;
import de.zft2.gbanking.gui.BaseWorker;

public class FileExportFP3Bean extends FileExportBean {

	private static final Logger log = LogManager.getLogger(FileExportFP3Bean.class);
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final Comparator<Booking> BOOKING_ORDER = Comparator.comparing(FileExportFP3Bean::sortDate).thenComparingInt(Booking::getId);

	public FileExportFP3Bean(BaseWorker worker) {
		super(worker);
	}

	@Override
	public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) throws ExportException {
		if (accountList == null || accountList.size() != 1) {
			log.error("FP3 export requires exactly one account.");
			return false;
		}

		try {
			String fp3Content = createFP3Content(accountList.get(0));
			saveToExportFile(fp3Content, fileName);
			return true;
		} catch (Exception e) {
			log.error("Error exporting FP3 file {}", fileNameOnly(fileName), e);
			log.debug("FP3 export path: {}", fileName);
			return false;
		}
	}

	private String createFP3Content(BankAccount account) {
		totalAccounts = 1;
		updateWorkerState(1, true, "UI_PROGRESS_EXPORT_FP3_BOOKINGS_ACCOUNT", account.getAccountName());

		List<Booking> bookings = getBookings(account).stream().sorted(BOOKING_ORDER).toList();
		Map<Integer, BigDecimal> balancesByBookingId = calculateBalances(account, bookings);
		List<Booking> exportRows = new ArrayList<>(bookings);
		exportRows.sort(BOOKING_ORDER.reversed());

		return buildPreparedReport(account, exportRows, balancesByBookingId);
	}

	private Map<Integer, BigDecimal> calculateBalances(BankAccount account, List<Booking> bookings) {
		BigDecimal total = sumBookings(bookings);
		BigDecimal running = endBalance(account, bookings).subtract(total);
		Map<Integer, BigDecimal> balancesByBookingId = new HashMap<>();
		for (Booking booking : bookings) {
			running = running.add(safeAmount(booking.getAmount()));
			balancesByBookingId.put(booking.getId(), running);
		}
		return balancesByBookingId;
	}

	private String buildPreparedReport(BankAccount account, List<Booking> bookings, Map<Integer, BigDecimal> balancesByBookingId) {
		StringBuilder xml = new StringBuilder();
		String currency = firstNonBlank(account.getCurrency(), "EUR");
		Summary summary = summarize(account, bookings);

		xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n");
		xml.append("<preparedreport>\r\n");
		xml.append("\t<previewpages>\r\n");
		xml.append("\t\t<page0>\r\n");
		appendHeader(xml, account, currency);
		appendSummary(xml, currency, summary);
		appendColumnHeader(xml, currency);
		for (int i = 0; i < bookings.size(); i++) {
			appendBookingRow(xml, bookings.get(i), balancesByBookingId.get(bookings.get(i).getId()), i);
		}
		xml.append("\t\t\t<b3 t=\"993,13451\"><m29 u=\"GBanking\" /></b3>\r\n");
		xml.append("\t\t</page0>\r\n");
		xml.append("\t</previewpages>\r\n");
		xml.append("\t<logicalpagenumbers><page n=\"1\" t=\"1\" /></logicalpagenumbers>\r\n");
		xml.append("\t<outline />\r\n");
		xml.append("\t<report><TfrxReport DotMatrixReport=\"0\" ReportOptions.Name=\"GBanking\" /></report>\r\n");
		xml.append("\t<sourcepages />\r\n");
		xml.append("\t<dictionary><b1 name=\"Page0.PageHeader\" /><b2 name=\"Page0.MasterData\" /></dictionary>\r\n");
		xml.append("</preparedreport>\r\n");
		return xml.toString();
	}

	private void appendHeader(StringBuilder xml, BankAccount account, String currency) {
		xml.append("\t\t\t<b1 t=\"0\">\r\n");
		appendMemo(xml, "m3", "Datum:");
		appendMemo(xml, "m4", "Konto:");
		appendMemo(xml, "m5", "IBAN:");
		appendMemo(xml, "m6", "Bank:");
		appendMemo(xml, "m7", DATE_FORMAT.format(LocalDate.now(ZoneId.systemDefault())));
		appendMemo(xml, "m8", firstNonBlank(account.getAccountName(), account.getNumber(), account.getIban()));
		appendMemo(xml, "m9", account.getIban());
		appendMemo(xml, "m10", account.getBankName());
		appendMemo(xml, "m14", "Kontoinhaber:");
		appendMemo(xml, "m15", "BIC:");
		appendMemo(xml, "m16", account.getBic());
		appendMemo(xml, "m18", account.getOwnerName());
		appendMemo(xml, "m19", "Kontoumsaetze");
		appendMemo(xml, "m46", currency + ":");
		xml.append("\t\t\t</b1>\r\n");
	}

	private void appendSummary(StringBuilder xml, String currency, Summary summary) {
		xml.append("\t\t\t<b5 t=\"129\">\r\n");
		appendMemo(xml, "m38", formatAmount(summary.income()));
		appendMemo(xml, "m39", "Zahlungseingaenge");
		appendMemo(xml, "m40", "Zahlungsausgaenge");
		appendMemo(xml, "m41", formatAmount(summary.outgoing()));
		appendMemo(xml, "m42", "Alter Kontostand");
		appendMemo(xml, "m43", formatAmount(summary.startBalance()));
		appendMemo(xml, "m44", "Neuer Kontostand");
		appendMemo(xml, "m45", formatAmount(summary.endBalance()));
		appendMemo(xml, "m46", currency + ":");
		appendMemo(xml, "m47", currency + ":");
		appendMemo(xml, "m48", currency + ":");
		appendMemo(xml, "m49", currency + ":");
		appendMemo(xml, "m51", "Anzahl Eingaenge:");
		appendMemo(xml, "m52", "Anzahl Ausgaenge:");
		appendMemo(xml, "m53", Integer.toString(summary.incomeCount()));
		appendMemo(xml, "m54", Integer.toString(summary.outgoingCount()));
		xml.append("\t\t\t</b5>\r\n");
	}

	private void appendColumnHeader(StringBuilder xml, String currency) {
		xml.append("\t\t\t<b4 t=\"189\">\r\n");
		appendMemo(xml, "m30", "Name\nVerwendungszweck");
		appendMemo(xml, "m31", "Kategorie\n  IBAN/BIC");
		appendMemo(xml, "m32", "Saldo\n" + currency);
		appendMemo(xml, "m33", "Betrag\n" + currency);
		appendMemo(xml, "m34", "Datum\nValuta");
		xml.append("\t\t\t</b4>\r\n");
	}

	private void appendBookingRow(StringBuilder xml, Booking booking, BigDecimal balance, int rowIndex) {
		xml.append("\t\t\t<b2 t=\"").append(217 + rowIndex * 44).append("\" h=\"44\">\r\n");
		appendMemo(xml, "m20", "");
		appendMemo(xml, "m21", nameAndPurpose(booking));
		appendMemo(xml, "m22", booking.getCategory() != null ? booking.getCategory().getFullName() : null);
		appendMemo(xml, "m23", formatAmount(balance));
		appendMemo(xml, "m24", formatAmount(booking.getAmount()));
		appendMemo(xml, "m27", bookingDateText(booking));
		appendMemo(xml, "m28", counterAccountText(booking.getRecipient()));
		xml.append("\t\t\t</b2>\r\n");
	}

	private Summary summarize(BankAccount account, List<Booking> bookings) {
		BigDecimal income = BigDecimal.ZERO;
		BigDecimal outgoing = BigDecimal.ZERO;
		int incomeCount = 0;
		int outgoingCount = 0;
		for (Booking booking : bookings) {
			BigDecimal amount = safeAmount(booking.getAmount());
			if (amount.signum() >= 0) {
				income = income.add(amount);
				incomeCount++;
			} else {
				outgoing = outgoing.add(amount.abs());
				outgoingCount++;
			}
		}
		BigDecimal endBalance = endBalance(account, bookings);
		return new Summary(income, outgoing, incomeCount, outgoingCount, endBalance.subtract(sumBookings(bookings)), endBalance);
	}

	private String nameAndPurpose(Booking booking) {
		String purpose = firstNonBlank(booking.getPurpose(), "");
		Recipient recipient = booking.getRecipient();
		String name = recipient != null ? firstNonBlank(recipient.getName()) : null;
		return name != null ? name + "\n" + purpose : purpose;
	}

	private String bookingDateText(Booking booking) {
		LocalDate dateBooking = sortDate(booking);
		LocalDate dateValue = booking.getDateValue();
		if (dateValue != null && !dateValue.equals(dateBooking)) {
			return DATE_FORMAT.format(dateBooking) + "\n" + DATE_FORMAT.format(dateValue);
		}
		return DATE_FORMAT.format(dateBooking);
	}

	private String counterAccountText(Recipient recipient) {
		if (recipient == null) {
			return null;
		}
		String account = firstNonBlank(recipient.getIban(), recipient.getAccountNumber());
		String bankCode = firstNonBlank(recipient.getBic(), recipient.getBlz());
		return "Kto " + firstNonBlank(account, "") + "\nBlz " + firstNonBlank(bankCode, "");
	}

	private void appendMemo(StringBuilder xml, String tagName, String value) {
		xml.append("\t\t\t\t<").append(tagName).append(" u=\"").append(escapeAttribute(value)).append("\" />\r\n");
	}

	private String escapeAttribute(String value) {
		String text = value != null ? value : "";
		return text.replace("&", "&amp;")
				.replace("\"", "&quot;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replace("\n", "&#10;");
	}

	private String formatAmount(BigDecimal value) {
		return safeAmount(value).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
	}

	private void saveToExportFile(String fp3Content, String fileName) throws IOException {
		Path exportPath = prepareExportPath(fileName);
		Files.writeString(exportPath, fp3Content, StandardCharsets.UTF_8);
		log.info("Finished FP3 booking export. file={}", exportPath.getFileName());
		log.debug("FP3 export path: {}", exportPath);
		updateWorkerState(99, false, "UI_PROGRESS_FINISH");
	}

	private static LocalDate sortDate(Booking booking) {
		return booking.getDateBooking() != null ? booking.getDateBooking() : LocalDate.now(ZoneId.systemDefault());
	}

	private record Summary(BigDecimal income, BigDecimal outgoing, int incomeCount, int outgoingCount, BigDecimal startBalance, BigDecimal endBalance) {
	}
}
