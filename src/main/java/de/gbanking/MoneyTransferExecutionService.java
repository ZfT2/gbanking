package de.gbanking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.BusinessCase;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.enu.MoneyTransferStatus;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.db.dao.enu.StandingorderMode;
import de.gbanking.exception.GBankingException;
import de.gbanking.hbci.GBankingHBCICallback;

class MoneyTransferExecutionService extends BaseBean {

	private static final Logger log = LogManager.getLogger(MoneyTransferExecutionService.class);

	private final GBankingBean hbciSupport;

	MoneyTransferExecutionService(GBankingBean hbciSupport) {
		this.hbciSupport = hbciSupport;
	}

	boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {

		boolean result = false;
		BankAccount transferAccount = resolveBankAccountForTransfer(bankAccount);

		if (transferAccount == null || !supportsTransferOrderType(transferAccount, moneyTransfer.getOrderType())) {
			log.warn("Transfer order type {} is not supported for account {}", moneyTransfer.getOrderType(),
					transferAccount != null ? transferAccount.getAccountName() : null);
			moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.ERROR);
			dbController.insertOrUpdate(moneyTransfer);
			return false;
		}

		BankAccess bankAccess = hbciSupport.initBankAccess(transferAccount, pin);
		if (bankAccess == null) {
			moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.ERROR);
			dbController.insertOrUpdate(moneyTransfer);
			return false;
		}
		GBankingHBCICallback hbciCallback = new GBankingHBCICallback(bankAccess);
		hbciCallback.startStatusDialog();
		HBCIPassport passport = hbciSupport.initBankConnection(bankAccess, hbciCallback);

		try (HBCIHandler handle = hbciSupport.createHBCIHandler(GBankingBean.getVersion().getId(), passport)) {

			Konto hbciSenderAccount = hbciSupport.getSenderAccount(passport, transferAccount);
			Konto hbciRecipientAccount = createRecipientAccount(moneyTransfer);
			HBCIJob<HBCIJobResult> job = createTransferJob(handle, moneyTransfer, hbciSenderAccount, hbciRecipientAccount);

			job.addToQueue();
			HBCIExecStatus status = handle.execute();
			HBCIJobResult jobResult = job.getJobResult();

			result = status.isOK() && (jobResult == null || jobResult.isOK());
			updateMoneyTransferAfterExecution(moneyTransfer, hbciCallback, status, jobResult, result);
			dbController.insertOrUpdate(moneyTransfer);

		} catch (Exception ex) {
			hbciCallback.handleException(ex);
			ex.printStackTrace();
			throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_SENDING_ACCOUNT_NOT_FOUND"), ex);
		} finally {
			clearSecret(pin);
			hbciCallback.finishStatusDialog();
			passport.close();
		}
		return result;
	}

	private HBCIJob<HBCIJobResult> createTransferJob(HBCIHandler handle, MoneyTransfer moneyTransfer, Konto senderAccount, Konto recipientAccount) {
		HBCIJob<HBCIJobResult> job = hbciSupport.newHbciJob(handle, resolveJobName(moneyTransfer.getOrderType()));
		job.setParam("src", senderAccount);
		job.setParam("dst", recipientAccount);
		job.setParam("btg.value", moneyTransfer.getAmount().toPlainString());
		job.setParam("btg.curr", "EUR");
		job.setParam("usage", moneyTransfer.getPurpose());

		switch (moneyTransfer.getOrderType()) {
		case SCHEDULED_TRANSFER -> job.setParam("date", toUtilDate(moneyTransfer.getExecutionDate()));
		case STANDING_ORDER -> applyStandingOrderParams(job, moneyTransfer);
		case TRANSFER, REALTIME_TRANSFER -> {
			// no-op
		}
		default -> throw new GBankingException("Unsupported transfer order type: " + moneyTransfer.getOrderType());
		}

		return job;
	}

	private void applyStandingOrderParams(HBCIJob<HBCIJobResult> job, MoneyTransfer moneyTransfer) {
		if (moneyTransfer.getExecutionDate() == null || moneyTransfer.getExecutionDay() == null || moneyTransfer.getStandingorderMode() == null) {
			throw new GBankingException(getText("ALERT_MONEYTRANSFER_REQUIRED_FIELD_MISSING"));
		}

		job.setParam("firstdate", toUtilDate(moneyTransfer.getExecutionDate()));
		job.setParam("timeunit", "M");
		job.setParam("turnus", determineStandingOrderTurnus(moneyTransfer.getStandingorderMode()));
		job.setParam("execday", formatStandingOrderExecutionDay(moneyTransfer.getExecutionDay()));
	}

	private String resolveJobName(OrderType orderType) {
		return switch (orderType) {
		case TRANSFER -> "UebSEPA";
		case REALTIME_TRANSFER -> "InstUebSEPA";
		case SCHEDULED_TRANSFER -> "TermUebSEPA";
		case STANDING_ORDER -> "DauerSEPANew";
		};
	}

	private Konto createRecipientAccount(MoneyTransfer moneyTransfer) {
		Konto hbciRecipientAccount = new Konto();
		hbciRecipientAccount.iban = moneyTransfer.getRecipient().getIban();
		hbciRecipientAccount.bic = moneyTransfer.getRecipient().getBic();
		hbciRecipientAccount.name = moneyTransfer.getRecipient().getName();
		return hbciRecipientAccount;
	}

	private Date toUtilDate(LocalDate date) {
		return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private String determineStandingOrderTurnus(StandingorderMode standingorderMode) {
		return switch (standingorderMode) {
		case MONTHLY -> "1";
		case BIMONTHLY -> "2";
		case QUARTERLY -> "3";
		case SEMI_ANNUALLY -> "6";
		case ANNUALLY -> "12";
		};
	}

	private String formatStandingOrderExecutionDay(Integer executionDay) {
		if (executionDay == null) {
			return null;
		}
		if (executionDay >= 31) {
			return "31";
		}
		return String.format("%02d", executionDay);
	}

	private BankAccount resolveBankAccountForTransfer(BankAccount bankAccount) {
		if (bankAccount == null) {
			return null;
		}

		if (bankAccount.getId() > 0) {
			BankAccount accountFromDb = dbController.getByIdFull(BankAccount.class, bankAccount.getId());
			if (accountFromDb != null) {
				return accountFromDb;
			}
		}

		return bankAccount;
	}

	boolean supportsTransferOrderType(BankAccount bankAccount, OrderType orderType) {
		if (bankAccount == null || orderType == null) {
			return false;
		}

		List<BusinessCase> allowedBusinessCases = bankAccount.getAllowedBusinessCases();
		if (allowedBusinessCases == null || allowedBusinessCases.isEmpty()) {
			return true;
		}

		Set<String> supportedCases = allowedBusinessCases.stream().map(BusinessCase::getCaseValue).filter(value -> value != null && !value.isBlank())
				.map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());

		return getRequiredBusinessCases(orderType).stream().anyMatch(supportedCases::contains);
	}

	private void clearSecret(char[] secret) {
		if (secret != null) {
			java.util.Arrays.fill(secret, '\0');
		}
	}

	private void updateMoneyTransferAfterExecution(MoneyTransfer moneyTransfer, GBankingHBCICallback hbciCallback, HBCIExecStatus status,
			HBCIJobResult jobResult, boolean success) {
		if (!success) {
			log.error("HBCI Error, Status: {}", status);
			if (jobResult != null && !jobResult.isOK()) {
				hbciCallback.handleFailure(jobResult.getJobStatus().toString());
			}
			hbciCallback.handleFailure(status.getErrorString());
			moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.ERROR);
			return;
		}

		if (moneyTransfer.getOrderType() == OrderType.TRANSFER || moneyTransfer.getOrderType() == OrderType.REALTIME_TRANSFER) {
			moneyTransfer.setExecutionDate(LocalDate.now());
		}
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.SENT);
	}

	private Set<String> getRequiredBusinessCases(OrderType orderType) {
		return switch (orderType) {
		case TRANSFER -> Set.of("UEBSEPA", "HKCCS");
		case REALTIME_TRANSFER -> Set.of("INSTUEBSEPA", "HKIPZ");
		case SCHEDULED_TRANSFER -> Set.of("TERMUEBSEPA", "HKCSE");
		case STANDING_ORDER -> Set.of("DAUERSEPANEW", "HKDSE");
		};
	}
}
