package de.zft2.gbanking.service.moneytransfer;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRDauerEdit;
import org.kapott.hbci.GV_Result.GVRDauerNew;
import org.kapott.hbci.GV_Result.GVRTermUeb;
import org.kapott.hbci.GV_Result.GVRTermUebEdit;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

public class MoneyTransferExecutionService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(MoneyTransferExecutionService.class);

	private final HbciSessionRunner hbciSessionRunner;

	public MoneyTransferExecutionService() {
		this.hbciSessionRunner = new HbciSessionRunner();
	}

	boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {

		boolean result = false;

		if (moneyTransfer == null) {
			log.warn("Abort money transfer execution. moneytransfer is null!");
			return result;
		}

		BankAccount transferAccount = resolveBankAccountForTransfer(bankAccount);
		BankOrderOperation operation = BankOrderOperation.forTransfer(moneyTransfer);
		CommunicationState communicationState = new CommunicationState();
		log.info("Starting money transfer execution. transferId={}, type={}, operation={}, accountId={}, status={}",
				moneyTransfer.getId(), moneyTransfer.getOrderType(), operation, transferAccount != null ? transferAccount.getId() : null,
				moneyTransfer.getMoneytransferStatus());

		logDebug(moneyTransfer);

		if (transferAccount == null || !supportsBankOrderOperation(transferAccount, moneyTransfer, operation)) {
			log.warn("Transfer operation {} for order type {} is not supported for account id {}", operation, moneyTransfer.getOrderType(),
					transferAccount != null ? transferAccount.getId() : null);
			applyFailedOperationStatus(moneyTransfer, operation);
			dbController.insertOrUpdate(moneyTransfer);
			clearSecret(pin);
			return false;
		}

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		BankAccess bankAccess = hbciSupport.initBankAccess(transferAccount, pin);
		if (bankAccess == null) {
			log.warn("Money transfer execution skipped, no bank access available. transferId={}, accountId={}", moneyTransfer.getId(), transferAccount.getId());
			applyFailedOperationStatus(moneyTransfer, operation);
			dbController.insertOrUpdate(moneyTransfer);
			clearSecret(pin);
			return false;
		}

		try {
			result = hbciSessionRunner.run(bankAccess, pin,
					session -> executeTransfer(moneyTransfer, transferAccount, operation, communicationState, session));

		} catch (InterruptedException ex) {
			communicationState.finish = communicationState.start != null && communicationState.finish == null ? LocalDateTime.now(ZoneId.systemDefault())
					: communicationState.finish;
			if (communicationState.start != null) {
				applyFailedOperationStatus(moneyTransfer, operation);
				persistExecutionResult(moneyTransfer, operation, false, communicationState.start, communicationState.finish,
						MoneyTransferStatus.ERROR, createProtocolText(communicationState.status, communicationState.jobResult, ex));
			}
			log.error("Money transfer execution failed. transferId={}, type={}, accountId={}", moneyTransfer.getId(), moneyTransfer.getOrderType(),
					transferAccount.getId(), ex);
			Thread.currentThread().interrupt();
			throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_SENDING_ACCOUNT_NOT_FOUND"), ex);
		}
		log.info("Finished money transfer execution. transferId={}, type={}, accountId={}, success={}, status={}", moneyTransfer.getId(),
				moneyTransfer.getOrderType(), transferAccount.getId(), result, moneyTransfer.getMoneytransferStatus());
		return result;
	}

	private boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount transferAccount, BankOrderOperation operation,
			CommunicationState communicationState,
			HbciSessionRunner.HbciSession session) {
		MoneyTransferService moneyTransferService = ServiceRegistry.getService(MoneyTransferService.class);
		Konto hbciSenderAccount = moneyTransferService.getSenderAccount(session.passport(), transferAccount);
		Konto hbciRecipientAccount = createRecipientAccount(moneyTransfer);
		HBCIJob<HBCIJobResult> job = createTransferJob(session.handler(), moneyTransfer, hbciSenderAccount, hbciRecipientAccount);
		session.callback().registerJobDescription(job, getText(resolveStatusMessageKey(moneyTransfer.getOrderType(), operation),
				session.callback().getAccountDescription(transferAccount)));

		job.addToQueue();
		session.callback().setCurrentMoneyTransfer(moneyTransfer);
		if (log.isDebugEnabled())
			log.debug("Queued HBCI transfer job. transferId={}, jobType={}", moneyTransfer.getId(), resolveJobName(moneyTransfer.getOrderType(), operation));
		communicationState.start = LocalDateTime.now(ZoneId.systemDefault());
		communicationState.status = session.handler().execute();
		communicationState.finish = LocalDateTime.now(ZoneId.systemDefault());
		communicationState.jobResult = job.getJobResult();

		boolean result = communicationState.status.isOK() && (communicationState.jobResult == null || communicationState.jobResult.isOK());
		applyRecipientNameFromVoP(moneyTransfer, session.callback());
		updateMoneyTransferAfterExecution(moneyTransfer, operation, session.callback(), communicationState.status, communicationState.jobResult, result);
		persistExecutionResult(moneyTransfer, operation, result, communicationState.start, communicationState.finish,
				result ? moneyTransfer.getMoneytransferStatus() : MoneyTransferStatus.ERROR,
				createProtocolText(communicationState.status, communicationState.jobResult, null));
		return result;
	}

	private HBCIJob<HBCIJobResult> createTransferJob(HBCIHandler handle, MoneyTransfer moneyTransfer, Konto senderAccount, Konto recipientAccount) {
		BankOrderOperation operation = BankOrderOperation.forTransfer(moneyTransfer);
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		HBCIJob<HBCIJobResult> job = hbciSupport.newHbciJob(handle, resolveJobName(moneyTransfer.getOrderType(), operation));
		job.setParam("src", senderAccount);
		applyBankOrderId(job, moneyTransfer, operation);
		job.setParam("dst", recipientAccount);
		job.setParam("btg.value", moneyTransfer.getAmount().toPlainString());
		job.setParam("btg.curr", getCurrency(moneyTransfer));
		applyPurposeParams(job, moneyTransfer);
		String purposeCode = trimToNull(moneyTransfer.getPurposeCode());
		if (purposeCode != null && moneyTransfer.getOrderType() != OrderType.FOREIGN_TRANSFER
				&& moneyTransfer.getOrderType() != OrderType.URGENT_TRANSFER) {
			job.setParam("purposecode", purposeCode);
		}
		String endToEndId = trimToNull(moneyTransfer.getEndToEndId());
		if (endToEndId != null && moneyTransfer.getOrderType() != OrderType.FOREIGN_TRANSFER
				&& moneyTransfer.getOrderType() != OrderType.URGENT_TRANSFER) {
			job.setParam("endtoendid", endToEndId);
		}

		switch (moneyTransfer.getOrderType()) {
		case SCHEDULED_TRANSFER -> applyScheduledTransferParams(job, moneyTransfer);
		case STANDING_ORDER -> applyStandingOrderParams(job, moneyTransfer);
		case URGENT_TRANSFER -> applyUrgentTransferParams(job, moneyTransfer);
		case FOREIGN_TRANSFER -> applyForeignTransferParams(job, moneyTransfer);
		case TRANSFER, REALTIME_TRANSFER -> {
			// no-op
		}
		default -> throw new GBankingException("Unsupported transfer order type: " + moneyTransfer.getOrderType());
		}

		return job;
	}

	private void applyPurposeParams(HBCIJob<HBCIJobResult> job, MoneyTransfer moneyTransfer) {
		String purpose = moneyTransfer.getPurpose() != null ? moneyTransfer.getPurpose() : "";
		if (moneyTransfer.getOrderType() == OrderType.URGENT_TRANSFER) {
			job.setParam("usage0", purpose.replaceAll("\\R+", " ").trim());
			return;
		}
		job.setParam("usage", purpose);
	}

	private void applyScheduledTransferParams(HBCIJob<HBCIJobResult> job, MoneyTransfer moneyTransfer) {
		if (moneyTransfer.getExecutionDate() == null) {
			throw requiredFieldMissingException("UI_LABEL_EXECUTION_DATE");
		}
		job.setParam("date", toUtilDate(moneyTransfer.getExecutionDate()));
	}

	private void applyStandingOrderParams(HBCIJob<HBCIJobResult> job, MoneyTransfer moneyTransfer) {
		if (moneyTransfer.getExecutionDate() == null) {
			throw requiredFieldMissingException("UI_LABEL_EXECUTION_DATE");
		}
		if (moneyTransfer.getExecutionDay() == null) {
			throw requiredFieldMissingException("UI_LABEL_DAY");
		}
		if (moneyTransfer.getStandingorderMode() == null) {
			throw requiredFieldMissingException("UI_LABEL_INTERVAL");
		}

		job.setParam("firstdate", toUtilDate(moneyTransfer.getExecutionDate()));
		job.setParam("timeunit", "M");
		job.setParam("turnus", determineStandingOrderTurnus(moneyTransfer.getStandingorderMode()));
		job.setParam("execday", formatStandingOrderExecutionDay(moneyTransfer.getExecutionDay()));
	}

	private void applyForeignTransferParams(HBCIJob<HBCIJobResult> job, MoneyTransfer moneyTransfer) {
		String recipientBankName = trimToNull(moneyTransfer.getRecipient() != null ? moneyTransfer.getRecipient().getBank() : null);
		if (recipientBankName == null) {
			throw requiredFieldMissingException("UI_LABEL_BANK");
		}
		job.setParam("dst.kiname", recipientBankName);
		MoneyTransferForeign foreignTransfer = moneyTransfer.getForeignTransfer();
		if (foreignTransfer != null && foreignTransfer.getChargeBearer() != null) {
			job.setParam("kostentraeger", Integer.toString(foreignTransfer.getChargeBearer().getDbStateId()));
		}
	}

	private void applyUrgentTransferParams(HBCIJob<HBCIJobResult> job, MoneyTransfer moneyTransfer) {
		String recipientName = trimToNull(moneyTransfer.getRecipient() != null ? moneyTransfer.getRecipient().getName() : null);
		if (recipientName == null) {
			throw requiredFieldMissingException("UI_LABEL_TRANSFER_RECIPIENT");
		}
		job.setParam("name", recipientName);
	}

	private GBankingException requiredFieldMissingException(String fieldLabelKey) {
		return new GBankingException(getText("ALERT_MONEYTRANSFER_REQUIRED_FIELD_MISSING_DETAIL", getText(fieldLabelKey)));
	}

	private void applyBankOrderId(HBCIJob<HBCIJobResult> job, MoneyTransfer moneyTransfer, BankOrderOperation operation) {
		if (operation == BankOrderOperation.CREATE) {
			return;
		}
		String bankOrderId = trimToNull(moneyTransfer.getBankOrderId());
		if (bankOrderId == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_BANK_ORDER_ID_REQUIRED"));
		}
		job.setParam("orderid", bankOrderId);
	}

	private String resolveJobName(OrderType orderType, BankOrderOperation operation) {
		if (orderType == OrderType.SCHEDULED_TRANSFER) {
			return switch (operation) {
			case CREATE -> "TermUebSEPA";
			case EDIT -> "TermUebSEPAEdit";
			case DELETE -> "TermUebSEPADel";
			};
		}
		if (orderType == OrderType.STANDING_ORDER) {
			return switch (operation) {
			case CREATE -> "DauerSEPANew";
			case EDIT -> "DauerSEPAEdit";
			case DELETE -> "DauerSEPADel";
			};
		}
		if (operation != BankOrderOperation.CREATE) {
			throw new GBankingException("Unsupported bank order operation " + operation + " for type " + orderType);
		}
		return switch (orderType) {
		case TRANSFER -> "UebSEPA";
		case REALTIME_TRANSFER -> "InstUebSEPA";
		case URGENT_TRANSFER -> "UebEil";
		case FOREIGN_TRANSFER -> "UebForeign";
		case SCHEDULED_TRANSFER, STANDING_ORDER -> throw new IllegalStateException("Order type was handled before switch: " + orderType);
		};
	}

	private String resolveStatusMessageKey(OrderType orderType, BankOrderOperation operation) {
		if (orderType == OrderType.SCHEDULED_TRANSFER && operation == BankOrderOperation.EDIT) {
			return "UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFER_EDIT";
		}
		if (orderType == OrderType.SCHEDULED_TRANSFER && operation == BankOrderOperation.DELETE) {
			return "UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFER_DELETE";
		}
		if (orderType == OrderType.STANDING_ORDER && operation == BankOrderOperation.EDIT) {
			return "UI_DIALOG_HBCI_JOB_STANDING_ORDER_EDIT";
		}
		if (orderType == OrderType.STANDING_ORDER && operation == BankOrderOperation.DELETE) {
			return "UI_DIALOG_HBCI_JOB_STANDING_ORDER_DELETE";
		}
		return switch (orderType) {
		case TRANSFER -> "UI_DIALOG_HBCI_JOB_TRANSFER";
		case REALTIME_TRANSFER -> "UI_DIALOG_HBCI_JOB_REALTIME_TRANSFER";
		case URGENT_TRANSFER -> "UI_DIALOG_HBCI_JOB_URGENT_TRANSFER";
		case SCHEDULED_TRANSFER -> "UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFER";
		case STANDING_ORDER -> "UI_DIALOG_HBCI_JOB_STANDING_ORDER";
		case FOREIGN_TRANSFER -> "UI_DIALOG_HBCI_JOB_FOREIGN_TRANSFER";
		};
	}

	private Konto createRecipientAccount(MoneyTransfer moneyTransfer) {
		Konto hbciRecipientAccount = new Konto();
		Recipient recipient = moneyTransfer.getRecipient();
		MoneyTransferForeign foreignTransfer = moneyTransfer.getForeignTransfer();
		hbciRecipientAccount.iban = recipient.getIban();
		hbciRecipientAccount.bic = recipient.getBic();
		hbciRecipientAccount.name = recipient.getName();
		hbciRecipientAccount.country = resolveRecipientCountry(recipient, foreignTransfer);
		hbciRecipientAccount.number = firstNonBlank(foreignTransfer != null ? foreignTransfer.getRecipientAccountNumber() : null,
				recipient.getAccountNumber());
		hbciRecipientAccount.blz = firstNonBlank(foreignTransfer != null ? foreignTransfer.getRecipientBankCode() : null, recipient.getBlz());
		hbciRecipientAccount.subnumber = foreignTransfer != null ? trimToNull(foreignTransfer.getRecipientSubAccount()) : null;
		if (moneyTransfer.getOrderType() == OrderType.URGENT_TRANSFER) {
			applyDomesticAccountFallbacks(hbciRecipientAccount);
		}
		return hbciRecipientAccount;
	}

	private void applyDomesticAccountFallbacks(Konto account) {
		String iban = trimToNull(account.iban);
		if (iban == null) {
			return;
		}

		String normalizedIban = iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
		if (isDomesticAccountNumber(normalizedIban) && trimToNull(account.blz) != null) {
			account.number = firstNonBlank(account.number, normalizedIban);
			account.iban = null;
			account.country = "DE";
			return;
		}

		if (normalizedIban == null || !normalizedIban.matches("DE\\d{20}")) {
			return;
		}
		account.blz = firstNonBlank(account.blz, normalizedIban.substring(4, 12));
		account.number = firstNonBlank(account.number, normalizedIban.substring(12, 22));
		account.country = "DE";
	}

	private boolean isDomesticAccountNumber(String value) {
		return value != null && value.matches("\\d{1,10}");
	}

	private String resolveRecipientCountry(Recipient recipient, MoneyTransferForeign foreignTransfer) {
		String country = foreignTransfer != null ? trimToNull(foreignTransfer.getRecipientCountry()) : null;
		if (country != null) {
			return country.toUpperCase(Locale.ROOT);
		}
		String iban = recipient != null ? trimToNull(recipient.getIban()) : null;
		return iban != null && iban.length() >= 2 ? iban.substring(0, 2).toUpperCase(Locale.ROOT) : null;
	}

	private void applyRecipientNameFromVoP(MoneyTransfer moneyTransfer, GBankingHBCICallback hbciCallback) {
		String confirmedRecipientName = trimToNull(hbciCallback.getConfirmedRecipientName());
		Recipient currentRecipient = moneyTransfer.getRecipient();
		if (confirmedRecipientName == null || currentRecipient == null || confirmedRecipientName.equals(currentRecipient.getName())) {
			return;
		}

		Recipient confirmedRecipient = createConfirmedRecipient(currentRecipient, confirmedRecipientName);
		Recipient persistedRecipient = dbController.resolveRecipient(confirmedRecipient);
		moneyTransfer.setRecipient(persistedRecipient);
		moneyTransfer.setRecipientId(persistedRecipient.getId());
		log.info("Updated money transfer recipient after VOP confirmation. transferId={}, recipientId={}", moneyTransfer.getId(), persistedRecipient.getId());
	}

	private Recipient createConfirmedRecipient(Recipient currentRecipient, String confirmedRecipientName) {
		Recipient confirmedRecipient = new Recipient();
		confirmedRecipient.setName(confirmedRecipientName);
		confirmedRecipient.setIban(currentRecipient.getIban());
		confirmedRecipient.setBic(currentRecipient.getBic());
		confirmedRecipient.setAccountNumber(currentRecipient.getAccountNumber());
		confirmedRecipient.setBlz(currentRecipient.getBlz());
		confirmedRecipient.setBank(currentRecipient.getBank());
		confirmedRecipient.setNote(currentRecipient.getNote());
		confirmedRecipient.setSource(Source.MONEYTRANSFER);
		confirmedRecipient.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return confirmedRecipient;
	}

	private String getCurrency(MoneyTransfer moneyTransfer) {
		String currency = moneyTransfer != null ? trimToNull(moneyTransfer.getCurrency()) : null;
		return currency != null ? currency : "EUR";
	}

	private java.util.Date toUtilDate(LocalDate date) {
		return date == null ? null : java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
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
		return String.format(Locale.ROOT, "%02d", executionDay);
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
		return ServiceRegistry.getService(BankingCapabilityService.class).supportsTransferOrderType(bankAccount, orderType);
	}

	private boolean supportsBankOrderOperation(BankAccount bankAccount, MoneyTransfer moneyTransfer, BankOrderOperation operation) {
		BankingCapabilityService bankingCapabilityService = ServiceRegistry.getService(BankingCapabilityService.class);
		if (operation == BankOrderOperation.CREATE) {
			return bankingCapabilityService.supportsTransferOrderType(bankAccount, moneyTransfer.getOrderType());
		}
		return bankingCapabilityService.supportsBankOrderOperation(bankAccount, moneyTransfer.getOrderType(), operation);
	}

	private void clearSecret(char[] secret) {
		HbciSessionRunner.clearSecret(secret);
	}

	private void updateMoneyTransferAfterExecution(MoneyTransfer moneyTransfer, BankOrderOperation operation, GBankingHBCICallback hbciCallback,
			HBCIExecStatus status,
			HBCIJobResult jobResult, boolean success) {
		if (!success) {
			log.error("HBCI Error, Status: {}", status);
			if (jobResult != null && !jobResult.isOK()) {
				hbciCallback.handleFailure(jobResult.getJobStatus().toString());
			}
			hbciCallback.handleFailure(status.getErrorString());
			applyFailedOperationStatus(moneyTransfer, operation);
			log.info("Money transfer execution ended with error. transferId={}", moneyTransfer.getId());
			return;
		}

		if (operation == BankOrderOperation.CREATE && (moneyTransfer.getOrderType() == OrderType.TRANSFER
				|| moneyTransfer.getOrderType() == OrderType.REALTIME_TRANSFER
				|| moneyTransfer.getOrderType() == OrderType.URGENT_TRANSFER
				|| moneyTransfer.getOrderType() == OrderType.FOREIGN_TRANSFER)) {
			moneyTransfer.setExecutionDate(LocalDate.now(ZoneId.systemDefault()));
		}
		applyReturnedBankOrderId(moneyTransfer, operation, jobResult);
		moneyTransfer.setMoneytransferStatus(resolveSuccessfulStatus(operation));
		log.info("Money transfer operation was accepted by bank. transferId={}, type={}, operation={}", moneyTransfer.getId(),
				moneyTransfer.getOrderType(), operation);
	}

	private void applyFailedOperationStatus(MoneyTransfer moneyTransfer, BankOrderOperation operation) {
		MoneyTransferStatus status = switch (operation) {
		case CREATE -> MoneyTransferStatus.ERROR;
		case EDIT -> MoneyTransferStatus.CHANGED;
		case DELETE -> MoneyTransferStatus.DELETE_PENDING;
		};
		moneyTransfer.setMoneytransferStatus(status);
	}

	private MoneyTransferStatus resolveSuccessfulStatus(BankOrderOperation operation) {
		return switch (operation) {
		case CREATE -> MoneyTransferStatus.SENT;
		case EDIT -> MoneyTransferStatus.INVENTORY;
		case DELETE -> MoneyTransferStatus.DELETED;
		};
	}

	private void applyReturnedBankOrderId(MoneyTransfer moneyTransfer, BankOrderOperation operation, HBCIJobResult jobResult) {
		String returnedOrderId = null;
		if (operation == BankOrderOperation.CREATE && jobResult instanceof GVRDauerNew standingOrderResult) {
			returnedOrderId = standingOrderResult.getOrderId();
		} else if (operation == BankOrderOperation.CREATE && jobResult instanceof GVRTermUeb scheduledTransferResult) {
			returnedOrderId = scheduledTransferResult.getOrderId();
		} else if (operation == BankOrderOperation.EDIT && jobResult instanceof GVRDauerEdit standingOrderResult) {
			returnedOrderId = standingOrderResult.getOrderId();
		} else if (operation == BankOrderOperation.EDIT && jobResult instanceof GVRTermUebEdit scheduledTransferResult) {
			returnedOrderId = scheduledTransferResult.getOrderId();
		}
		String normalizedOrderId = trimToNull(returnedOrderId);
		if (normalizedOrderId != null) {
			moneyTransfer.setBankOrderId(normalizedOrderId);
		}
	}

	private void persistExecutionResult(MoneyTransfer moneyTransfer, BankOrderOperation operation, boolean success, LocalDateTime start,
			LocalDateTime finish, MoneyTransferStatus protocolStatus, String protocolText) {
		dbController.executeInTransaction(() -> {
			if (success && operation == BankOrderOperation.EDIT) {
				archiveHistoryPredecessor(moneyTransfer);
			}
			dbController.insertOrUpdate(moneyTransfer);
			saveProtocol(moneyTransfer, start, finish, protocolStatus, protocolText);
		});
	}

	private void archiveHistoryPredecessor(MoneyTransfer moneyTransfer) {
		Integer predecessorId = moneyTransfer.getHistoryorderId();
		if (predecessorId == null) {
			throw new GBankingException("Changed bank order has no history predecessor");
		}
		MoneyTransfer predecessor = dbController.getAllByParent(MoneyTransfer.class, moneyTransfer.getAccountId()).stream()
				.filter(transfer -> transfer.getId() == predecessorId).findFirst().orElse(null);
		if (predecessor == null || predecessor.getMoneytransferStatus() != MoneyTransferStatus.INVENTORY) {
			throw new GBankingException("Changed bank order history predecessor is not available");
		}
		predecessor.setMoneytransferStatus(MoneyTransferStatus.SUPERSEDED);
		dbController.insertOrUpdate(predecessor);
	}

	private void saveProtocol(MoneyTransfer moneyTransfer, LocalDateTime start, LocalDateTime finish, MoneyTransferStatus protocolStatus,
			String protocolText) {
		if (start == null || moneyTransfer.getId() <= 0) {
			return;
		}

		MoneyTransferProtocol protocol = new MoneyTransferProtocol();
		protocol.setMoneyTransferId(moneyTransfer.getId());
		protocol.setMoneytransferStatus(protocolStatus);
		protocol.setTimeStart(start);
		protocol.setTimeFinish(finish);
		protocol.setProtocolText(protocolText);
		dbController.insertOrUpdate(protocol);
		log.debug("Saved money transfer protocol for transferId={}, status={}", moneyTransfer.getId(), moneyTransfer.getMoneytransferStatus());
	}

	private String createProtocolText(HBCIExecStatus status, HBCIJobResult jobResult, Exception exception) {
		StringBuilder protocol = new StringBuilder();
		appendProtocolLine(protocol, "HBCI execution status", status);
		appendProtocolLine(protocol, "HBCI error text", status != null ? status.getErrorString() : null);
		appendProtocolLine(protocol, "HBCI job status", jobResult != null ? jobResult.getJobStatus() : null);
		appendProtocolLine(protocol, "HBCI job result", jobResult);
		if (exception != null) {
			appendProtocolLine(protocol, "Exception", exception.getClass().getName() + ": " + exception.getMessage());
		}
		return protocol.toString().trim();
	}

	private void appendProtocolLine(StringBuilder protocol, String label, Object value) {
		if (value == null || value.toString().isBlank()) {
			return;
		}
		protocol.append(label).append(": ").append(value).append(System.lineSeparator());
	}

	private void logDebug(MoneyTransfer moneyTransfer) {
		if (log.isDebugEnabled())
			log.debug("Money transfer execution details. transferId={}, amount={}, purpose={}, recipientIban={}", moneyTransfer.getId(),
					SensitiveDataMasker.describeAmount(moneyTransfer.getAmount()), SensitiveDataMasker.describeText(moneyTransfer.getPurpose()),
					moneyTransfer.getRecipient() != null ? SensitiveDataMasker.maskIban(moneyTransfer.getRecipient().getIban()) : null);
	}

	private static final class CommunicationState {

		private LocalDateTime start;
		private LocalDateTime finish;
		private HBCIExecStatus status;
		private HBCIJobResult jobResult;
	}
}
