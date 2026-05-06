package de.zft2.gbanking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.mapper.HbciMapper;

class AccountTransactionService extends BaseBean {

	private static final Logger log = LogManager.getLogger(AccountTransactionService.class);

	private final GBankingBean hbciSupport;
	private final GBankingLoggingHandler logHandler;

	AccountTransactionService(GBankingBean hbciSupport, GBankingLoggingHandler logHandler) {
		this.hbciSupport = hbciSupport;
		this.logHandler = logHandler;
	}

	boolean retrieveAccountTransactions(BankAccount bankAccount, char[] pin) {

		boolean result = false;

		BankAccess bankAccess = hbciSupport.initBankAccess(bankAccount, pin);
		if (bankAccess == null) {
			clearSecret(pin);
			return false;
		}

		LocalDate lastBookingDate = getAccountLastBookingDate(bankAccount);

		GBankingHBCICallback hbciCallback = new GBankingHBCICallback(bankAccess);
		hbciCallback.startStatusDialog();
		HBCIPassport passport = hbciSupport.initBankConnection(bankAccess, hbciCallback);

		refreshBankAccount(bankAccount);
		updatePreviousNewBookings(bankAccount);

		try (HBCIHandler handle = hbciSupport.createHBCIHandler(GBankingBean.getVersion().getId(), passport)) {

			logHandler.logRetrivedBankAccessInfo(passport, false);

			Konto kontoMatched = null;
			for (Konto konto : getHbciAccountsFromPassport(passport)) {
				if (hbciKontosMatches(bankAccount, konto)) {
					kontoMatched = konto;
					break;
				}
			}

			logHandler.logRetrievedAccountInfo(kontoMatched);

			HBCIJob<GVRSaldoReq> saldoJob = createAndAddHbciJob(handle, "SaldoReq", Map.of("my", kontoMatched));
			HBCIJob<GVRKUms> umsatzJob = createUmsatzJob(handle, kontoMatched, lastBookingDate);

			HBCIExecStatus status = handle.execute();
			result = status.isOK();

			if (!result) {
				log.error("HBCI Error, Status: {}", status);
				hbciCallback.handleFailure(status.getErrorString());
			}

			readSaldo(saldoJob);
			saveHbciBookingsForAccount(bankAccount, readUms(umsatzJob));

		} catch (Exception e) {
			hbciCallback.handleException(e);
			log.error("Error in handling HBCI calls: ", e);
			result = false;
		} finally {
			clearSecret(pin);
			hbciCallback.finishStatusDialog();
			passport.close();
		}

		return result;
	}

	void saveHbciBookingsForAccount(BankAccount bankAccount, List<UmsLine> buchungen) {
		List<Booking> newBookingsList = new java.util.ArrayList<>();
		Map<Recipient, Set<Integer>> recipientBookingMap = new HashMap<>();

		for (UmsLine buchung : buchungen) {

			logHandler.logRetrivedBookingInfo(buchung);

			Booking newBooking = HbciMapper.mapUmsLineToBooking(bankAccount.getId(), buchung);

			log.debug("Konto other: {}", buchung.other);

			Recipient recipient = HbciMapper.mapUmsLineKontoToRecipient(buchung.other);

			if (recipient != null) {
				Recipient recipientDb = dbController.find(Recipient.class, recipient);
				if (recipientDb == null) {
					recipientDb = dbController.insertOrUpdate(recipient);
				}
				newBooking.setRecipient(recipientDb);
			}

			newBookingsList.add(newBooking);
		}

		dbController.insertAccountBookings(newBookingsList);

		for (Booking booking : newBookingsList) {
			Recipient recipient = booking.getRecipient();
			if (recipient == null) {
				continue;
			}

			recipientBookingMap.computeIfAbsent(recipient, ignored -> new HashSet<>()).add(booking.getId());
		}

		dbController.updateBookingsWithRecipients(recipientBookingMap);
	}

	private HBCIJob<GVRKUms> createUmsatzJob(HBCIHandler handle, Konto konto, LocalDate lastBookingDate) {
		if (lastBookingDate == null) {
			return createAndAddHbciJob(handle, "KUmsAllCamt", Map.of("my", konto));
		}
		return createAndAddHbciJob(handle, "KUmsAllCamt",
				Map.of("my", konto, "startdate", Date.from(lastBookingDate.atStartOfDay(ZoneId.systemDefault()).toInstant())));
	}

	private void refreshBankAccount(BankAccount bankAccount) {
		bankAccount.setBookings(dbController.getAllByParent(Booking.class, bankAccount.getId()));
	}

	private void updatePreviousNewBookings(BankAccount bankAccount) {
		for (Booking booking : bankAccount.getBookings()) {
			booking.setSource(booking.getSource().getCorresponding());
		}

		dbController.executeSimpleUpdate(Arrays.asList(bankAccount), StatementsConfig.StatementType.UPDATE_BOOKING_SOURCE, Booking.class);
	}

	private void readSaldo(HBCIJob<GVRSaldoReq> saldoJob) {
		GVRSaldoReq saldoResult = saldoJob.getJobResult();
		if (!saldoResult.isOK()) {
			log.error("Error in retrieving Saldo: {}", saldoResult);
		}

		Value saldo = saldoResult.getEntries()[0].ready.value;
		log.info("Saldo: {}", saldo);
	}

	private List<UmsLine> readUms(HBCIJob<GVRKUms> umsatzJob) {
		GVRKUms umsResult = umsatzJob.getJobResult();

		if (!umsResult.isOK()) {
			log.error("Error in retrieving Umsatz: {}", umsResult);
		}

		return umsResult.getFlatData();
	}

	private boolean hbciKontosMatches(BankAccount bankAccount, Konto konto) {
		return bankAccount.getIban() != null && bankAccount.getIban().equalsIgnoreCase(konto.iban)
				|| bankAccount.getNumber() != null && bankAccount.getNumber().equalsIgnoreCase(konto.number);
	}

	private <T extends HBCIJobResult> HBCIJob<T> createAndAddHbciJob(HBCIHandler handle, String jobDescription, Map<String, Object> params) {
		HBCIJob<T> job = hbciSupport.newHbciJob(handle, jobDescription);

		for (Entry<String, Object> param : params.entrySet()) {
			Object value = param.getValue();

			if (value instanceof String s) {
				job.setParam(param.getKey(), s);
			} else if (value instanceof Date d) {
				job.setParam(param.getKey(), d);
			} else if (value instanceof Integer i) {
				job.setParam(param.getKey(), i);
			} else if (value instanceof Konto k) {
				job.setParam(param.getKey(), k);
			} else if (value == null) {
				log.log(Level.ERROR, () -> getText("HBCI_PARAM_NULL", param.getKey()));
			} else {
				log.log(Level.ERROR, () -> getText("HBCI_PARAM_UNKNOWN_TYPE", param.getKey(), value.getClass().getName()));
			}
		}
		job.addToQueue();

		return job;
	}

	private Konto[] getHbciAccountsFromPassport(HBCIPassport passport) {
		Konto[] konten = passport.getAccounts();
		if (konten == null || konten.length == 0) {
			log.error("No Accounts were found on bank site");
		} else {
			log.info("Number of accounts found: {}", konten.length);
		}
		return konten;
	}

	private LocalDate getAccountLastBookingDate(BankAccount bankAccount) {
		return dbController.getSingleResultField(bankAccount, StatementsConfig.StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, LocalDate.class);
	}

	private void clearSecret(char[] secret) {
		if (secret != null) {
			Arrays.fill(secret, '\0');
		}
	}
}
