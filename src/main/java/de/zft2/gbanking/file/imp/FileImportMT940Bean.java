package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.BTag;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.core.dto.DefaultCounterpart;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.imp.dto.ImportBankAccount;
import de.zft2.gbanking.file.imp.dto.ImportBooking;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.mapper.ImportDaoMapper;
import de.zft2.gbanking.util.TypeConverter;

public class FileImportMT940Bean extends AbstractBookingImportBean {

	private static final Logger log = LogManager.getLogger(FileImportMT940Bean.class);

	public FileImportMT940Bean(BaseWorker worker) {
		this(worker, null);
	}

	public FileImportMT940Bean(BaseWorker worker, BankAccount contextAccount) {
		super(worker, contextAccount);
	}

	public boolean importFile(String importFile) {
		try {
			importFileToDatabase(importFile);
			return true;
		} catch (IOException e) {
			log.error("Error importing MT940 file: {}", fileName(importFile), e);
			log.debug("MT940 import path: {}", importFile);
			return false;
		}
	}

	public void importFileToDatabase(String importFile) throws IOException {
		Path importPath = prepareImportFile(importFile, "UI_PROGRESS_READ_MT940_FILE");
		List<BTag> statementDays = parseStatementDays(Files.readString(importPath, StandardCharsets.ISO_8859_1));
		dbController.executeInTransaction(() -> importStatementDays(statementDays));
		finishImport();
	}

	private List<BTag> parseStatementDays(String content) {
		boolean initializedParser = false;
		try {
			initializedParser = ensureHbciParserInitialized();
			GVRKUms result = new GVRKUms();
			result.appendMT940Data(normalizeMt940(content));
			return new ArrayList<>(result.getDataPerDay());
		} catch (RuntimeException e) {
			throw new GBankingException("MT940 file could not be parsed.", e);
		} finally {
			if (initializedParser) {
				HBCIUtils.doneThread();
			}
		}
	}

	private boolean ensureHbciParserInitialized() {
		if (HBCIUtilsInternal.locMsgs == null || HBCIUtilsInternal.locMsgs.get(Thread.currentThread().getThreadGroup()) == null) {
			HBCIUtils.initThread(new Properties(), new HBCICallbackConsole());
			return true;
		}
		return false;
	}

	private String normalizeMt940(String content) {
		String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n").trim();
		if (normalized.isBlank()) {
			throw new GBankingException("MT940 file does not contain bookings.");
		}
		if (!normalized.startsWith("\r\n")) {
			normalized = "\r\n" + normalized;
		}
		return normalized.endsWith("\r\n") ? normalized : normalized + "\r\n";
	}

	private void importStatementDays(List<BTag> statementDays) {
		List<Booking> importedBookings = new ArrayList<>();
		Map<Integer, ImportBankAccount> importAccountsByAccountId = new LinkedHashMap<>();
		Map<ImportBankAccount, BankAccount> accountsByImportAccount = new LinkedHashMap<>();
		int totalBookings = statementDays.stream().mapToInt(statement -> statement.lines.size()).sum();
		int importedCount = 0;

		for (BTag statement : statementDays) {
			BankAccount account = resolveAccount(statement);
			ImportBankAccount importAccount = importAccountsByAccountId.computeIfAbsent(account.getId(), ignored -> {
				ImportBankAccount newImportAccount = toImportAccount(account);
				accountsByImportAccount.put(newImportAccount, account);
				return newImportAccount;
			});

			for (UmsLine line : statement.lines) {
				updateWorkerState(progress(importedCount++, totalBookings), "UI_PROGRESS_IMPORT_MT940_BOOKING_COUNT", importedCount, totalBookings);
				importAccount.getBookings().add(mapBooking(importAccount, line));
			}
		}

		importBookings(importAccountsByAccountId.values(), accountsByImportAccount, importedBookings);
		postProcessImportedBookings(importedBookings);
	}

	private ImportBooking mapBooking(ImportBankAccount account, UmsLine line) {
		ImportBooking booking = new ImportBooking();

		booking.setAccountName(account.getNamePP());
		booking.setDateBooking(TypeConverter.toLocalDateFromDate(line.bdate));
		booking.setDateValue(TypeConverter.toLocalDateFromDate(line.valuta));
		booking.setPurpose(resolvePurpose(line));
		booking.setAmount(line.value != null ? line.value.getBigDecimalValue() : null);
		booking.setCurrency(line.value != null ? line.value.getCurr() : null);
		booking.setBookingType(resolveBookingType(booking.getAmount()));
		booking.setSource(Source.IMPORT);
		booking.setSepaCustomerRef(line.customerref);
		booking.setSepaCreditorId(line.other != null ? line.other.creditorid : null);
		booking.setSepaEndToEnd(line.endToEndId);
		booking.setSepaMandate(line.mandateId);
		booking.setSepaPurpose(line.purposecode);
		mapAdditionalFields(booking, line);
		mapRecipient(booking, line.other);
		booking.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return booking;
	}

	private String resolvePurpose(UmsLine line) {
		StringBuilder purpose = new StringBuilder();
		if (line.usage != null) {
			for (String purposeLine : line.usage) {
				purpose.append(purposeLine).append("\n");
			}
		}
		return purpose.toString();
	}

	private BookingType resolveBookingType(BigDecimal amount) {
		if (amount == null) {
			return null;
		}
		return amount.signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT;
	}

	private void mapAdditionalFields(ImportBooking booking, UmsLine line) {
		booking.setAddInstref(line.instref);
		booking.setAddGvcode(line.gvcode);
		booking.setAddText(line.text);
		booking.setAddPrimanota(line.primanota);
		booking.setAddKey(line.addkey);
		booking.setAddIsStorno(line.isStorno);
		booking.setForeignAmount(toBigDecimal(line.orig_value));
		booking.setForeignCurrency(currency(line.orig_value));
		booking.setFeeAmount(toBigDecimal(line.charge_value));
		booking.setFeeCurrency(currency(line.charge_value));
		booking.setAddRawData(line.additional);
		booking.setAddIsSepa(line.isSepa);
		booking.setAddIsCamt(line.isCamt);
		booking.setAddBankSaldo(line.saldo != null ? toBigDecimal(line.saldo.value) : null);
	}

	private BigDecimal toBigDecimal(Value value) {
		return value != null ? value.getBigDecimalValue() : null;
	}

	private String currency(Value value) {
		return value != null ? value.getCurr() : null;
	}

	private void mapRecipient(ImportBooking booking, Konto other) {
		if (other == null || (other.iban == null && other.number == null)) {
			return;
		}

		booking.setCounterpart(DefaultCounterpart.ofNullable(other.name, other.iban, other.bic, other.number, other.blz, null));
	}

	private BankAccount resolveAccount(BTag statement) {
		Konto konto = statement.my;
		if (hasContextAccount()) {
			validateContextAccount(konto);
			return getContextAccount();
		}

		BankAccount account = findExistingAccount(konto);
		return account != null ? account : createAccount(statement);
	}

	private void validateContextAccount(Konto konto) {
		if (!matchesAccount(konto, getContextAccount())) {
			throw new GBankingException("MT940 file does not match selected account " + getContextAccount().getAccountName() + ".");
		}
	}

	private BankAccount findExistingAccount(Konto konto) {
		BankAccount account = lookupByIban(konto.iban);
		if (account == null) {
			account = lookupByIbanSuffix(konto.iban);
		}
		if (account == null) {
			account = lookupByBlzAndNumber(konto.blz, konto.number);
		}
		return account != null ? account : lookupByNumber(konto.number);
	}

	private boolean matchesAccount(Konto konto, BankAccount account) {
		if (matchesIdentifier(konto.iban, account.getIban()) || matchesIbanSuffix(konto.iban, account.getNumber())) {
			return true;
		}
		if (!matchesIdentifier(konto.number, account.getNumber())) {
			return false;
		}
		return konto.blz == null || konto.blz.isBlank() || account.getBlz() == null || account.getBlz().isBlank()
				|| matchesIdentifier(konto.blz, account.getBlz());
	}

	private BankAccount createAccount(BTag statement) {
		Konto konto = statement.my;
		ImportBankAccount importAccount = new ImportBankAccount();
		importAccount.setAccountName(firstNonBlank(konto.name, konto.iban, konto.number, "MT940-Konto"));
		importAccount.setNamePP(importAccount.getAccountName());
		importAccount.setIban(trimToNull(konto.iban));
		importAccount.setNumber(trimToNull(konto.number));
		importAccount.setBlz(trimToNull(konto.blz));
		importAccount.setBic(trimToNull(konto.bic));
		importAccount.setCurrency(firstNonBlank(konto.curr, statement.start != null ? statement.start.value.getCurr() : null,
				statement.end != null ? statement.end.value.getCurr() : null, "EUR"));
		importAccount.setAccountType(AccountType.CURRENT_ACCOUNT);
		importAccount.setAccountState(AccountState.ACTIVE);
		importAccount.setSource(Source.IMPORT_INITIAL);
		importAccount.setOfflineAccount(true);

		BankAccount account = ImportDaoMapper.maptoBankAccountDao(importAccount, Source.IMPORT_INITIAL);

		BankAccount persistedAccount = dbController.insertOrUpdate(account);
		if (persistedAccount == null) {
			throw new GBankingException("Could not create account for MT940 file.");
		}
		registerAccount(persistedAccount);
		return persistedAccount;
	}
}
