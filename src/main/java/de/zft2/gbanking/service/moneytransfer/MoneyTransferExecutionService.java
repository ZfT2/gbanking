package de.zft2.gbanking.service.moneytransfer;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRDauerEdit;
import org.kapott.hbci.GV_Result.GVRDauerNew;
import org.kapott.hbci.GV_Result.GVRInstUebSEPA;
import org.kapott.hbci.GV_Result.GVRTermUeb;
import org.kapott.hbci.GV_Result.GVRTermUebEdit;
import org.kapott.hbci.GV_Result.GVRVoP.VoPStatus;
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
import de.zft2.gbanking.db.dao.enu.SepaCancellationCode;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;
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
	private final InstantPaymentStatusService instantPaymentStatusService;
	private final FinTsStatusProtocolService statusProtocolService;
	private final MoneyTransferProtocolWriter protocolWriter = new MoneyTransferProtocolWriter(dbController, log, "money transfer");

	public MoneyTransferExecutionService() {
		this.hbciSessionRunner = new HbciSessionRunner();
		this.instantPaymentStatusService = new InstantPaymentStatusService();
		this.statusProtocolService = new FinTsStatusProtocolService();
	}

	boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {

		boolean result = false;

		if (moneyTransfer == null) {
			log.warn("Abort money transfer execution. moneytransfer is null!");
			return result;
		}
		if (!isPersistedStatusUnchanged(moneyTransfer)) {
			log.warn("Refusing to execute outdated or no longer executable money transfer. transferId={}, status={}", moneyTransfer.getId(),
					moneyTransfer.getMoneytransferStatus());
			HbciSessionRunner.clearSecret(pin);
			return false;
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
			return rejectBeforeSubmission(moneyTransfer, operation, pin);
		}

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		BankAccess bankAccess = hbciSupport.initBankAccess(transferAccount, pin);
		if (bankAccess == null) {
			log.warn("Money transfer execution skipped, no bank access available. transferId={}, accountId={}", moneyTransfer.getId(), transferAccount.getId());
			return rejectBeforeSubmission(moneyTransfer, operation, pin);
		}

		try {
			result = hbciSessionRunner.run(bankAccess, pin,
					session -> executeTransfer(moneyTransfer, transferAccount, operation, communicationState, session));

		} catch (InterruptedException ex) {
			handleAbortedExecution(moneyTransfer, transferAccount, operation, communicationState, ex);
			Thread.currentThread().interrupt();
			throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_EXECUTION_INTERRUPTED"), ex);
		} catch (RuntimeException ex) {
			handleAbortedExecution(moneyTransfer, transferAccount, operation, communicationState, ex);
			throw ex;
		}
		log.info("Finished money transfer execution. transferId={}, type={}, accountId={}, success={}, status={}", moneyTransfer.getId(),
				moneyTransfer.getOrderType(), transferAccount.getId(), result, moneyTransfer.getMoneytransferStatus());
		return result;
	}

	int retrieveInstantPaymentStatuses(List<MoneyTransfer> moneyTransfers, BankAccount bankAccount, char[] pin) {
		try {
			BankAccount transferAccount = resolveBankAccountForTransfer(bankAccount);
			if (!isValidStatusRequest(moneyTransfers, transferAccount)) {
				return 0;
			}

			BankAccess bankAccess = ServiceRegistry.getService(BankAccessService.class).initBankAccess(transferAccount, pin);
			if (bankAccess == null) {
				return 0;
			}
			return hbciSessionRunner.runOrFail(bankAccess, pin, session -> retrieveInstantPaymentStatuses(moneyTransfers, transferAccount, session),
					getText("EXCEPTION_MONEYTRANSFER_INSTANT_STATUS_RETRIEVAL"));
		} finally {
			HbciSessionRunner.clearSecret(pin);
		}
	}

	private int retrieveInstantPaymentStatuses(List<MoneyTransfer> moneyTransfers, BankAccount bankAccount,
			HbciSessionRunner.HbciSession session) {
		MoneyTransferService moneyTransferService = ServiceRegistry.getService(MoneyTransferService.class);
		Konto senderAccount = moneyTransferService.getSenderAccount(session.passport(), bankAccount);
		int successfulRequests = 0;
		for (MoneyTransfer moneyTransfer : moneyTransfers) {
			if (Thread.currentThread().isInterrupted()) {
				break;
			}
			MoneyTransferStatusResolution resolution = reconcileTransferStatus(session.handler(), session.callback(), moneyTransfer,
					senderAccount, findHbciJobId(moneyTransfer));
			if (resolution.requestSuccessful()) {
				successfulRequests++;
			}
		}
		return successfulRequests;
	}

	private boolean isValidStatusRequest(List<MoneyTransfer> moneyTransfers, BankAccount bankAccount) {
		return bankAccount != null && moneyTransfers != null && !moneyTransfers.isEmpty()
				&& moneyTransfers.stream().allMatch(transfer -> transfer != null && transfer.getAccountId() == bankAccount.getId()
						&& transfer.getOrderType() == OrderType.REALTIME_TRANSFER);
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
		// Block a second submission of a new order before sending, so that an unclear abort cannot make it executable again
		MoneyTransferStatus submissionStatus = operation == BankOrderOperation.CREATE ? MoneyTransferStatus.UNCERTAIN
				: moneyTransfer.getMoneytransferStatus();
		if (!updateStatusIfPersistedStatusUnchanged(moneyTransfer, submissionStatus)) {
			log.warn("Refusing to submit money transfer, its persisted status was changed in the meantime. transferId={}", moneyTransfer.getId());
			return false;
		}
		communicationState.start = LocalDateTime.now(ZoneId.systemDefault());
		communicationState.status = session.handler().execute();
		communicationState.finish = LocalDateTime.now(ZoneId.systemDefault());
		communicationState.jobResult = job.getJobResult();

		BankResponseData bankResponse = extractBankResponse(operation, communicationState.jobResult);
		ExecutionOutcome outcome = assessExecution(operation, communicationState.status, communicationState.jobResult, bankResponse,
				session.callback().isSubmissionAbortedByUser());
		boolean recipientNameCorrected = applyRecipientNameFromVoP(moneyTransfer, session.callback());
		updateMoneyTransferAfterExecution(moneyTransfer, operation, session.callback(), communicationState.status,
				communicationState.jobResult, outcome, bankResponse);
		String technicalProtocol = createProtocolText(communicationState.status, communicationState.jobResult, null);
		logTechnicalProtocol(moneyTransfer, technicalProtocol);
		persistExecutionResult(moneyTransfer, operation, outcome == ExecutionOutcome.SUCCESS, communicationState,
				moneyTransfer.getMoneytransferStatus(), evaluate(outcome, technicalProtocol, session.callback().isVopRequired(),
						session.callback().getVopStatus(), recipientNameCorrected), bankResponse);

		if (outcome == ExecutionOutcome.UNCERTAIN) {
			MoneyTransferStatusResolution resolution = reconcileTransferStatus(session.handler(), session.callback(), moneyTransfer,
					hbciSenderAccount, bankResponse.hbciJobId());
			return handleUncertainResolution(session.callback(), moneyTransfer, resolution);
		}
		if (outcome == ExecutionOutcome.SUCCESS && HbciExecutionAssessment.isOnlyDialogEndFailure(communicationState.status)) {
			session.callback().handleRecoveredOperation(getText("UI_DIALOG_HBCI_TRANSFER_STATUS_RECOVERED"));
		}
		if (outcome == ExecutionOutcome.SUCCESS && operation == BankOrderOperation.CREATE
				&& moneyTransfer.getOrderType() == OrderType.REALTIME_TRANSFER) {
			instantPaymentStatusService.retrieveStatusIfNecessary(session.handler(), session.callback(), moneyTransfer, hbciSenderAccount,
					bankResponse.sepaOrderStatus(), communicationState.jobResult);
		}
		return outcome == ExecutionOutcome.SUCCESS;
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

	private boolean applyRecipientNameFromVoP(MoneyTransfer moneyTransfer, GBankingHBCICallback hbciCallback) {
		String confirmedRecipientName = trimToNull(hbciCallback.getConfirmedRecipientName());
		Recipient currentRecipient = moneyTransfer.getRecipient();
		if (confirmedRecipientName == null || currentRecipient == null || confirmedRecipientName.equals(currentRecipient.getName())) {
			return false;
		}

		Recipient confirmedRecipient = createConfirmedRecipient(currentRecipient, confirmedRecipientName);
		Recipient persistedRecipient = dbController.resolveRecipient(confirmedRecipient);
		moneyTransfer.setRecipient(persistedRecipient);
		moneyTransfer.setRecipientId(persistedRecipient.getId());
		log.info("Updated money transfer recipient after VOP confirmation. transferId={}, recipientId={}", moneyTransfer.getId(), persistedRecipient.getId());
		return true;
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
		return String.valueOf(standingorderMode.getIntervalMonths());
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
			BankAccount accountFromDb = dbController.getBankAccountWithBusinessCases(bankAccount.getId());
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

	private void updateMoneyTransferAfterExecution(MoneyTransfer moneyTransfer, BankOrderOperation operation, GBankingHBCICallback hbciCallback,
			HBCIExecStatus status, HBCIJobResult jobResult, ExecutionOutcome outcome, BankResponseData bankResponse) {
		applyBankResponse(moneyTransfer, bankResponse);
		if (outcome == ExecutionOutcome.FAILURE) {
			log.error("HBCI Error, Status: {}", status);
			if (jobResult != null && !jobResult.isOK()) {
				hbciCallback.handleFailure(jobResult.getJobStatus().toString());
			}
			if (status != null) {
				hbciCallback.handleFailure(status.getErrorString());
			}
			moneyTransfer.setMoneytransferStatus(resolveFailedStatus(operation));
			log.info("Money transfer execution ended with error. transferId={}", moneyTransfer.getId());
			return;
		}
		if (outcome == ExecutionOutcome.UNCERTAIN) {
			applyExecutionDate(moneyTransfer, operation);
			moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.UNCERTAIN);
			log.warn("Money transfer execution status is uncertain, the order may have reached the bank. transferId={}", moneyTransfer.getId());
			return;
		}

		applyExecutionDate(moneyTransfer, operation);
		moneyTransfer.setMoneytransferStatus(resolveSuccessfulStatus(operation));
		log.info("Money transfer operation was accepted by bank. transferId={}, type={}, operation={}", moneyTransfer.getId(),
				moneyTransfer.getOrderType(), operation);
	}

	private void applyExecutionDate(MoneyTransfer moneyTransfer, BankOrderOperation operation) {
		if (operation == BankOrderOperation.CREATE && (moneyTransfer.getOrderType() == OrderType.TRANSFER
				|| moneyTransfer.getOrderType() == OrderType.REALTIME_TRANSFER
				|| moneyTransfer.getOrderType() == OrderType.URGENT_TRANSFER
				|| moneyTransfer.getOrderType() == OrderType.FOREIGN_TRANSFER)) {
			moneyTransfer.setExecutionDate(LocalDate.now(ZoneId.systemDefault()));
		}
	}

	private void applyBankResponse(MoneyTransfer moneyTransfer, BankResponseData bankResponse) {
		if (bankResponse.bankOrderId() != null) {
			moneyTransfer.setBankOrderId(bankResponse.bankOrderId());
		}
	}

	private static MoneyTransferStatus resolveFailedStatus(BankOrderOperation operation) {
		return switch (operation) {
		case CREATE -> MoneyTransferStatus.ERROR;
		case EDIT -> MoneyTransferStatus.CHANGED;
		case DELETE -> MoneyTransferStatus.DELETE_PENDING;
		};
	}

	/**
	 * Checks against the database that the order still has the status of the passed instance and that this status is executable,
	 * so that an outdated instance can neither resubmit nor overwrite an order that is locked or completed in the meantime.
	 */
	private boolean isPersistedStatusUnchanged(MoneyTransfer moneyTransfer) {
		MoneyTransfer persistedTransfer = dbController.getById(MoneyTransfer.class, moneyTransfer.getId());
		return persistedTransfer != null && persistedTransfer.getMoneytransferStatus() == moneyTransfer.getMoneytransferStatus()
				&& MoneyTransferService.EXECUTABLE_MONEY_TRANSFER_STATUSES.contains(persistedTransfer.getMoneytransferStatus());
	}

	/**
	 * Atomically checks the persisted status and persists the new status, if it differs from the current one.
	 *
	 * @return {@code false} if the persisted order was changed in the meantime, in which case nothing is written
	 */
	private boolean updateStatusIfPersistedStatusUnchanged(MoneyTransfer moneyTransfer, MoneyTransferStatus status) {
		return dbController.executeInTransaction(() -> {
			if (!isPersistedStatusUnchanged(moneyTransfer)) {
				return false;
			}
			if (moneyTransfer.getMoneytransferStatus() != status) {
				moneyTransfer.setMoneytransferStatus(status);
				dbController.insertOrUpdate(moneyTransfer);
			}
			return true;
		});
	}

	private boolean rejectBeforeSubmission(MoneyTransfer moneyTransfer, BankOrderOperation operation, char[] pin) {
		updateStatusIfPersistedStatusUnchanged(moneyTransfer, resolveFailedStatus(operation));
		HbciSessionRunner.clearSecret(pin);
		return false;
	}

	private MoneyTransferStatus resolveSuccessfulStatus(BankOrderOperation operation) {
		return switch (operation) {
		case CREATE -> MoneyTransferStatus.SENT;
		case EDIT -> MoneyTransferStatus.INVENTORY;
		case DELETE -> MoneyTransferStatus.DELETED;
		};
	}

	private BankResponseData extractBankResponse(BankOrderOperation operation, HBCIJobResult jobResult) {
		String returnedOrderId = null;
		SepaOrderStatus sepaOrderStatus = null;
		SepaCancellationCode sepaCancellationCode = null;
		if (operation == BankOrderOperation.CREATE && jobResult instanceof GVRDauerNew standingOrderResult) {
			returnedOrderId = standingOrderResult.getOrderId();
		} else if (operation == BankOrderOperation.CREATE && jobResult instanceof GVRTermUeb scheduledTransferResult) {
			returnedOrderId = scheduledTransferResult.getOrderId();
		} else if (operation == BankOrderOperation.CREATE && jobResult instanceof GVRInstUebSEPA instantPaymentResult) {
			returnedOrderId = instantPaymentResult.getOrderId();
			sepaOrderStatus = SepaOrderStatus.forCode(instantPaymentResult.getOrderStatus());
			sepaCancellationCode = SepaCancellationCode.forCode(instantPaymentResult.getCancellationCode());
		} else if (operation == BankOrderOperation.EDIT && jobResult instanceof GVRDauerEdit standingOrderResult) {
			returnedOrderId = standingOrderResult.getOrderId();
		} else if (operation == BankOrderOperation.EDIT && jobResult instanceof GVRTermUebEdit scheduledTransferResult) {
			returnedOrderId = scheduledTransferResult.getOrderId();
		}
		return new BankResponseData(trimToNull(returnedOrderId), resolveHbciJobId(jobResult), sepaOrderStatus, sepaCancellationCode);
	}

	private String resolveHbciJobId(HBCIJobResult jobResult) {
		if (jobResult == null || trimToNull(jobResult.getDialogId()) == null || trimToNull(jobResult.getMsgNum()) == null
				|| trimToNull(jobResult.getSegNum()) == null) {
			return null;
		}
		return trimToNull(jobResult.getJobId());
	}

	private ExecutionOutcome assessExecution(BankOrderOperation operation, HBCIExecStatus status, HBCIJobResult jobResult,
			BankResponseData bankResponse, boolean abortedByUser) {
		boolean accepted = (jobResult == null || jobResult.isOK()) && status != null && status.isOK();
		if (operation != BankOrderOperation.CREATE) {
			return accepted ? ExecutionOutcome.SUCCESS : ExecutionOutcome.FAILURE;
		}
		if (HbciExecutionAssessment.isRejectedByBank(jobResult)) {
			return ExecutionOutcome.FAILURE;
		}
		SepaOrderStatus orderStatus = bankResponse.sepaOrderStatus();
		if (orderStatus != null && orderStatus.isFinal()) {
			return orderStatus == SepaOrderStatus.COMPLETED ? ExecutionOutcome.SUCCESS : ExecutionOutcome.FAILURE;
		}
		if (accepted) {
			return ExecutionOutcome.SUCCESS;
		}
		// A new order may only become executable again if the bank cannot have executed it
		return abortedByUser || HbciExecutionAssessment.wasNotSubmitted(status) ? ExecutionOutcome.FAILURE : ExecutionOutcome.UNCERTAIN;
	}

	private static MoneyTransferProtocolEvaluator.Evaluation evaluate(ExecutionOutcome outcome, String technicalProtocol, boolean vopRequired,
			VoPStatus vopStatus, boolean recipientNameCorrected) {
		return outcome == ExecutionOutcome.UNCERTAIN
				? MoneyTransferProtocolEvaluator.evaluateUncertain(technicalProtocol, vopRequired, vopStatus, recipientNameCorrected)
				: MoneyTransferProtocolEvaluator.evaluate(outcome == ExecutionOutcome.SUCCESS, technicalProtocol, vopRequired, vopStatus,
						recipientNameCorrected);
	}

	/**
	 * Persists the result of an execution that was aborted by an exception after the order was handed over to hbci4j. A new
	 * order stays uncertain, because it may have reached the bank. A result that was already persisted is never downgraded.
	 */
	private void handleAbortedExecution(MoneyTransfer moneyTransfer, BankAccount transferAccount, BankOrderOperation operation,
			CommunicationState communicationState, Exception exception) {
		log.warn("Money transfer execution aborted. transferId={}, type={}, accountId={}, cause={}", moneyTransfer.getId(),
				moneyTransfer.getOrderType(), transferAccount.getId(), exception.toString());
		if (communicationState.start == null || communicationState.resultPersisted) {
			return;
		}
		if (communicationState.finish == null) {
			communicationState.finish = LocalDateTime.now(ZoneId.systemDefault());
		}
		ExecutionOutcome outcome = operation == BankOrderOperation.CREATE ? ExecutionOutcome.UNCERTAIN : ExecutionOutcome.FAILURE;
		if (outcome == ExecutionOutcome.UNCERTAIN) {
			applyExecutionDate(moneyTransfer, operation);
			moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.UNCERTAIN);
		} else {
			moneyTransfer.setMoneytransferStatus(resolveFailedStatus(operation));
		}
		String technicalProtocol = createProtocolText(communicationState.status, communicationState.jobResult, exception);
		logTechnicalProtocol(moneyTransfer, technicalProtocol);
		try {
			persistExecutionResult(moneyTransfer, operation, false, communicationState, moneyTransfer.getMoneytransferStatus(),
					evaluate(outcome, technicalProtocol, false, null, false), BankResponseData.EMPTY);
		} catch (RuntimeException persistException) {
			// The status written before sending still blocks a second submission of a new order
			exception.addSuppressed(persistException);
		}
	}

	private MoneyTransferStatusResolution reconcileTransferStatus(HBCIHandler handler, GBankingHBCICallback callback,
			MoneyTransfer moneyTransfer, Konto senderAccount, String hbciJobId) {
		MoneyTransferStatusResolution instantResolution = MoneyTransferStatusResolution.NOT_RESOLVED;
		if (moneyTransfer.getOrderType() == OrderType.REALTIME_TRANSFER && trimToNull(moneyTransfer.getBankOrderId()) != null) {
			instantResolution = instantPaymentStatusService.retrieveStatusResolution(handler, callback, moneyTransfer, senderAccount);
			if (instantResolution.resolved()) {
				return instantResolution;
			}
		}

		MoneyTransferStatusResolution protocolResolution = statusProtocolService.retrieveStatus(handler, callback, moneyTransfer, hbciJobId);
		return protocolResolution.resolved() ? protocolResolution
				: new MoneyTransferStatusResolution(instantResolution.requestSuccessful() || protocolResolution.requestSuccessful(), null);
	}

	private String findHbciJobId(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0) {
			return null;
		}
		return dbController.getAllByParent(MoneyTransferProtocol.class, moneyTransfer.getId()).stream()
				.map(MoneyTransferProtocol::getHbciJobId).map(value -> trimToNull(value)).filter(value -> value != null).findFirst().orElse(null);
	}

	boolean hasStatusReference(MoneyTransfer moneyTransfer) {
		return moneyTransfer != null && (trimToNull(moneyTransfer.getBankOrderId()) != null || findHbciJobId(moneyTransfer) != null);
	}

	private boolean handleUncertainResolution(GBankingHBCICallback callback, MoneyTransfer moneyTransfer,
			MoneyTransferStatusResolution resolution) {
		if (resolution.resolvedStatus() == MoneyTransferStatus.SENT) {
			callback.handleRecoveredOperation(getText("UI_DIALOG_HBCI_TRANSFER_STATUS_RECOVERED"));
			return true;
		}
		if (resolution.resolvedStatus() == MoneyTransferStatus.ERROR) {
			callback.handleFailure(getText("UI_DIALOG_HBCI_TRANSFER_STATUS_REJECTED"));
			return false;
		}
		callback.handleUncertainOperation(getText("UI_DIALOG_HBCI_TRANSFER_STATUS_UNCERTAIN"));
		log.warn("Could not determine final bank status for money transfer. transferId={}, bankOrderIdPresent={}, hbciJobIdPresent={}",
				moneyTransfer.getId(), trimToNull(moneyTransfer.getBankOrderId()) != null, findHbciJobId(moneyTransfer) != null);
		return false;
	}

	private void persistExecutionResult(MoneyTransfer moneyTransfer, BankOrderOperation operation, boolean success,
			CommunicationState communicationState, MoneyTransferStatus protocolStatus, MoneyTransferProtocolEvaluator.Evaluation evaluation,
			BankResponseData bankResponse) {
		dbController.executeInTransaction(() -> {
			if (success && operation == BankOrderOperation.EDIT) {
				archiveHistoryPredecessor(moneyTransfer);
			}
			dbController.insertOrUpdate(moneyTransfer);
			saveProtocol(moneyTransfer, communicationState.start, communicationState.finish, protocolStatus,
					evaluation, bankResponse);
		});
		communicationState.resultPersisted = true;
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
			MoneyTransferProtocolEvaluator.Evaluation evaluation, BankResponseData bankResponse) {
		if (start == null || moneyTransfer.getId() <= 0) {
			return;
		}

		MoneyTransferProtocol protocol = new MoneyTransferProtocol(moneyTransfer.getId(), protocolStatus, start, finish);
		protocol.setBankOrderId(firstNonBlank(bankResponse.bankOrderId(), moneyTransfer.getBankOrderId()));
		protocol.setHbciJobId(bankResponse.hbciJobId());
		protocol.setSepaOrderStatus(bankResponse.sepaOrderStatus());
		protocol.setSepaCancellationCode(bankResponse.sepaCancellationCode());
		evaluation.applyTo(protocol);
		dbController.insertOrUpdate(protocol);
		log.debug("Saved money transfer protocol for transferId={}, status={}", moneyTransfer.getId(), moneyTransfer.getMoneytransferStatus());
	}

	private void logTechnicalProtocol(MoneyTransfer moneyTransfer, String technicalProtocol) {
		protocolWriter.logTechnicalProtocol(moneyTransfer, technicalProtocol);
	}

	private String createProtocolText(HBCIExecStatus status, HBCIJobResult jobResult, Exception exception) {
		return MoneyTransferProtocolText.empty()
				.appendNonBlank("HBCI execution status", status)
				.appendNonBlank("HBCI error text", status != null ? status.getErrorString() : null)
				.appendNonBlank("HBCI job status", jobResult != null ? jobResult.getJobStatus() : null)
				.appendNonBlank("HBCI job result", jobResult)
				.appendNonBlank("Exception", exception != null ? exception.getClass().getName() + ": " + exception.getMessage() : null)
				.build();
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
		private boolean resultPersisted;
	}

	private enum ExecutionOutcome {
		SUCCESS,
		FAILURE,
		UNCERTAIN
	}

	private record BankResponseData(String bankOrderId, String hbciJobId, SepaOrderStatus sepaOrderStatus,
			SepaCancellationCode sepaCancellationCode) {

		private static final BankResponseData EMPTY = new BankResponseData(null, null, null, null);
	}
}
