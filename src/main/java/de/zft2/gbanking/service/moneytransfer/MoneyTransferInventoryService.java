package de.zft2.gbanking.service.moneytransfer;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRDauerList;
import org.kapott.hbci.GV_Result.GVRTermUebList;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.GBankingBean;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

public class MoneyTransferInventoryService implements BaseMessagesDb {

	private static final Logger log = LogManager.getLogger(MoneyTransferInventoryService.class);

	private final GBankingBean gBankingBean;
	private final BankAccessService hbciSupport;
	private final HbciSessionRunner hbciSessionRunner;

	public MoneyTransferInventoryService(GBankingBean gBankingBean, BankAccessService hbciSupport) {
		this.gBankingBean = gBankingBean;
		this.hbciSupport = hbciSupport;
		this.hbciSessionRunner = new HbciSessionRunner(hbciSupport);
	}

	public boolean retrieveInventory(BankAccount bankAccount, OrderType orderType, char[] pin) {
		log.info("Starting money transfer inventory retrieval. accountId={}, type={}", bankAccount != null ? bankAccount.getId() : null, orderType);
		if (orderType != OrderType.SCHEDULED_TRANSFER && orderType != OrderType.STANDING_ORDER) {
			log.warn("Money transfer inventory type {} is not supported.", orderType);
			HbciSessionRunner.clearSecret(pin);
			return false;
		}
		if (!gBankingBean.supportsOrderInventory(bankAccount, orderType)) {
			log.warn("Money transfer inventory type {} is not available for account id {}", orderType, bankAccount != null ? bankAccount.getId() : null);
			HbciSessionRunner.clearSecret(pin);
			return false;
		}

		BankAccess bankAccess = hbciSupport.initBankAccess(bankAccount, pin);
		if (bankAccess == null) {
			log.info("Money transfer inventory retrieval skipped, no bank access available. accountId={}, type={}",
					bankAccount != null ? bankAccount.getId() : null, orderType);
			HbciSessionRunner.clearSecret(pin);
			return false;
		}

		try {
			boolean result = hbciSessionRunner.run(bankAccess, pin, session -> retrieveInventory(bankAccount, orderType, session));
			log.info("Finished money transfer inventory retrieval. accountId={}, type={}, success={}",
					() -> getNullableBankAccountId(bankAccount), () -> orderType, () -> result);
			return result;
		} catch (InterruptedException e) {
			log.error("Error retrieving money transfer inventory. accountId={}, type={}", bankAccount != null ? bankAccount.getId() : null, orderType, e);
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private boolean retrieveInventory(BankAccount bankAccount, OrderType orderType, HbciSessionRunner.HbciSession session) {
		Konto senderAccount = gBankingBean.getSenderAccount(session.passport(), bankAccount);
		return orderType == OrderType.STANDING_ORDER ? retrieveStandingOrders(session.handler(), bankAccount, senderAccount, session.callback())
				: retrieveScheduledTransfers(session.handler(), bankAccount, senderAccount, session.callback());
	}

	private boolean retrieveStandingOrders(HBCIHandler handle, BankAccount bankAccount, Konto senderAccount, GBankingHBCICallback hbciCallback) {
		HBCIJob<GVRDauerList> job = createInventoryJob(handle, "DauerSEPAList", senderAccount);
		hbciCallback.registerJobDescription(job, getText("UI_DIALOG_HBCI_JOB_STANDING_ORDERS", hbciCallback.getAccountDescription(bankAccount)));
		HBCIExecStatus status = handle.execute();
		GVRDauerList jobResult = job.getJobResult();
		boolean success = isSuccessful(status, jobResult);

		if (success) {
			InventoryMappingResult mappingResult = mapStandingOrders(bankAccount, jobResult);
			success = saveCompleteInventory(bankAccount, OrderType.STANDING_ORDER, mappingResult);
			if (success) {
				log.info("Retrieved {} standing orders for account id {}", mappingResult.transfers().size(), bankAccount.getId());
			}
		} else {
			handleFailure(hbciCallback, status, jobResult);
		}
		return success;
	}

	private boolean retrieveScheduledTransfers(HBCIHandler handle, BankAccount bankAccount, Konto senderAccount, GBankingHBCICallback hbciCallback) {
		HBCIJob<GVRTermUebList> job = createInventoryJob(handle, "TermUebSEPAList", senderAccount);
		hbciCallback.registerJobDescription(job, getText("UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFERS", hbciCallback.getAccountDescription(bankAccount)));
		HBCIExecStatus status = handle.execute();
		GVRTermUebList jobResult = job.getJobResult();
		boolean success = isSuccessful(status, jobResult);

		if (success) {
			InventoryMappingResult mappingResult = mapScheduledTransfers(bankAccount, jobResult);
			success = saveCompleteInventory(bankAccount, OrderType.SCHEDULED_TRANSFER, mappingResult);
			if (success) {
				log.info("Retrieved {} scheduled transfers for account id {}", mappingResult.transfers().size(), bankAccount.getId());
			}
		} else {
			handleFailure(hbciCallback, status, jobResult);
		}
		return success;
	}

	private <T extends HBCIJobResult> HBCIJob<T> createInventoryJob(HBCIHandler handle, String jobName, Konto senderAccount) {
		HBCIJob<T> job = hbciSupport.newHbciJob(handle, jobName);
		job.setParam("src", senderAccount);
		job.addToQueue();
		return job;
	}

	private boolean isSuccessful(HBCIExecStatus status, HBCIJobResult jobResult) {
		return status != null && status.isOK() && jobResult != null && jobResult.isOK();
	}

	private void handleFailure(GBankingHBCICallback hbciCallback, HBCIExecStatus status, HBCIJobResult jobResult) {
		if (jobResult != null && !jobResult.isOK() && jobResult.getJobStatus() != null) {
			hbciCallback.handleFailure(jobResult.getJobStatus().toString());
		}
		if (status != null && !status.isOK()) {
			hbciCallback.handleFailure(status.getErrorString());
		}
	}

	private InventoryMappingResult mapStandingOrders(BankAccount bankAccount, GVRDauerList result) {
		GVRDauerList.Dauer[] entries = result.getEntries();
		if (entries == null) {
			return new InventoryMappingResult(List.of(), false);
		}
		List<MoneyTransfer> transfers = new ArrayList<>();
		boolean complete = true;
		for (GVRDauerList.Dauer entry : entries) {
			MoneyTransfer transfer = mapStandingOrder(bankAccount, entry);
			if (transfer == null) {
				complete = false;
			} else {
				transfers.add(transfer);
			}
		}
		return new InventoryMappingResult(transfers, complete);
	}

	private MoneyTransfer mapStandingOrder(BankAccount bankAccount, GVRDauerList.Dauer entry) {
		if (entry == null) {
			return null;
		}
		MoneyTransfer transfer = createBaseTransfer(bankAccount, OrderType.STANDING_ORDER, entry.other, entry.value, entry.usage,
				toLocalDate(entry.firstdate));
		if (transfer == null) {
			return null;
		}
		transfer.setBankOrderId(firstNonBlank(entry.orderid, entry.pmtinfid));
		transfer.setPurposeCode(trimToNull(entry.purposecode));
		transfer.setExecutionDay(entry.execday > 0 ? Math.min(entry.execday, 31) : null);
		transfer.setStandingorderMode(toStandingorderMode(entry.timeunit, entry.turnus));
		return transfer.getExecutionDate() != null && transfer.getExecutionDay() != null && transfer.getStandingorderMode() != null ? transfer : null;
	}

	private InventoryMappingResult mapScheduledTransfers(BankAccount bankAccount, GVRTermUebList result) {
		GVRTermUebList.Entry[] entries = result.getEntries();
		if (entries == null) {
			return new InventoryMappingResult(List.of(), false);
		}
		List<MoneyTransfer> transfers = new ArrayList<>();
		boolean complete = true;
		for (GVRTermUebList.Entry entry : entries) {
			MoneyTransfer transfer = mapScheduledTransfer(bankAccount, entry);
			if (transfer == null) {
				complete = false;
			} else {
				transfers.add(transfer);
			}
		}
		return new InventoryMappingResult(transfers, complete);
	}

	private MoneyTransfer mapScheduledTransfer(BankAccount bankAccount, GVRTermUebList.Entry entry) {
		if (entry == null) {
			return null;
		}
		MoneyTransfer transfer = createBaseTransfer(bankAccount, OrderType.SCHEDULED_TRANSFER, entry.other, entry.value, entry.usage,
				toLocalDate(entry.date));
		if (transfer != null) {
			transfer.setBankOrderId(trimToNull(entry.orderid));
		}
		return transfer != null && transfer.getExecutionDate() != null ? transfer : null;
	}

	private MoneyTransfer createBaseTransfer(BankAccount bankAccount, OrderType orderType, Konto recipientAccount, Value value, String[] usage,
			LocalDate executionDate) {
		Recipient recipient = createRecipient(recipientAccount);
		if (recipient == null || value == null) {
			log.warn("Skipping incomplete {} inventory entry for account id {}", orderType, bankAccount.getId());
			return null;
		}

		MoneyTransfer transfer = new MoneyTransfer();
		transfer.setAccountId(bankAccount.getId());
		transfer.setOrderType(orderType);
		transfer.setRecipient(recipient);
		transfer.setPurpose(joinUsage(usage));
		transfer.setAmount(value.getBigDecimalValue());
		transfer.setExecutionDate(executionDate);
		transfer.setMoneytransferStatus(MoneyTransferStatus.INVENTORY);
		return transfer;
	}

	private Recipient createRecipient(Konto recipientAccount) {
		if (recipientAccount == null || isBlank(recipientAccount.iban) && isBlank(recipientAccount.number) && isBlank(recipientAccount.name)) {
			return null;
		}

		Recipient recipient = new Recipient();
		recipient.setName(trimToNull(recipientAccount.name));
		recipient.setIban(trimToNull(recipientAccount.iban));
		recipient.setBic(trimToNull(recipientAccount.bic));
		recipient.setAccountNumber(trimToNull(recipientAccount.number));
		recipient.setBlz(trimToNull(recipientAccount.blz));
		recipient.setSource(Source.MONEYTRANSFER);
		recipient.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return recipient;
	}

	private boolean saveCompleteInventory(BankAccount bankAccount, OrderType orderType, InventoryMappingResult mappingResult) {
		if (!mappingResult.complete() || hasDuplicateBankOrderIds(mappingResult.transfers())) {
			log.warn("Money transfer inventory response is incomplete or ambiguous. No inventory changes are saved. accountId={}, type={}",
					bankAccount.getId(), orderType);
			return false;
		}
		saveRetrievedTransfers(bankAccount, orderType, mappingResult.transfers());
		return true;
	}

	void saveRetrievedTransfers(BankAccount bankAccount, OrderType orderType, List<MoneyTransfer> retrievedTransfers) {
		validateRetrievedTransfers(bankAccount, orderType, retrievedTransfers);
		dbController.executeInTransaction(() -> saveRetrievedTransfersInTransaction(bankAccount, orderType, retrievedTransfers));
	}

	private void validateRetrievedTransfers(BankAccount bankAccount, OrderType orderType, List<MoneyTransfer> retrievedTransfers) {
		if (bankAccount == null || bankAccount.getId() <= 0 || orderType == null || retrievedTransfers == null) {
			throw new GBankingException("Invalid money transfer inventory reconciliation request");
		}
		if (orderType != OrderType.STANDING_ORDER && orderType != OrderType.SCHEDULED_TRANSFER) {
			throw new GBankingException("Unsupported money transfer inventory type: " + orderType);
		}
		for (MoneyTransfer transfer : retrievedTransfers) {
			if (transfer == null || transfer.getAccountId() != bankAccount.getId() || transfer.getOrderType() != orderType) {
				throw new GBankingException("Money transfer inventory contains an entry for another account or order type");
			}
		}
		if (hasDuplicateBankOrderIds(retrievedTransfers)) {
			throw new GBankingException("Money transfer inventory contains duplicate bank order ids");
		}
	}

	private boolean hasDuplicateBankOrderIds(List<MoneyTransfer> transfers) {
		Set<String> bankOrderIds = new HashSet<>();
		return transfers.stream().map(MoneyTransfer::getBankOrderId).map(this::normalizeBankOrderId).filter(Objects::nonNull)
				.anyMatch(bankOrderId -> !bankOrderIds.add(bankOrderId));
	}

	private void saveRetrievedTransfersInTransaction(BankAccount bankAccount, OrderType orderType, List<MoneyTransfer> retrievedTransfers) {
		List<MoneyTransfer> existingTransfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId()).stream()
				.filter(transfer -> transfer.getOrderType() == orderType).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		List<MoneyTransfer> currentInventory = existingTransfers.stream()
				.filter(transfer -> transfer.getMoneytransferStatus() == MoneyTransferStatus.INVENTORY).toList();
		Set<Integer> pendingChangePredecessorIds = existingTransfers.stream()
				.filter(transfer -> transfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED)
				.map(transfer -> transfer.getHistoryorderId()).filter(historyOrderId -> historyOrderId != null)
				.collect(java.util.stream.Collectors.toSet());
		Set<Integer> matchedCurrentIds = new HashSet<>();
		ReconciliationContext context = new ReconciliationContext(existingTransfers, currentInventory, matchedCurrentIds);
		int savedCount = 0;
		int unchangedCount = 0;
		int supersededCount = 0;

		for (MoneyTransfer transfer : retrievedTransfers) {
			ReconciliationResult result = reconcileRetrievedTransfer(context, transfer);
			switch (result) {
			case UNCHANGED -> unchangedCount++;
			case SAVED -> savedCount++;
			case SUPERSEDED -> {
				savedCount++;
				supersededCount++;
			}
			}
		}

		int missingCount = 0;
		for (MoneyTransfer transfer : currentInventory) {
			if (!matchedCurrentIds.contains(transfer.getId()) && !pendingChangePredecessorIds.contains(transfer.getId())) {
				transfer.setMoneytransferStatus(MoneyTransferStatus.NOT_IN_BANK_INVENTORY);
				dbController.insertOrUpdate(transfer);
				missingCount++;
			}
		}
		log.info("Reconciled money transfer inventory for account id {}, type={}, received={}, saved={}, unchanged={}, superseded={}, missing={}",
				bankAccount.getId(), orderType, retrievedTransfers.size(), savedCount, unchangedCount, supersededCount, missingCount);
	}

	private ReconciliationResult reconcileRetrievedTransfer(ReconciliationContext context, MoneyTransfer transfer) {
		String bankOrderId = normalizeBankOrderId(transfer.getBankOrderId());
		transfer.setBankOrderId(bankOrderId);
		Optional<MoneyTransfer> pendingOperation = bankOrderId != null
				? findPendingBankOperation(context.existingTransfers(), bankOrderId) : Optional.empty();
		if (pendingOperation.isPresent()) {
			Integer predecessorId = pendingOperation.get().getHistoryorderId();
			if (predecessorId != null) {
				context.matchedCurrentIds().add(predecessorId);
			}
			return ReconciliationResult.UNCHANGED;
		}

		Optional<MoneyTransfer> current = bankOrderId != null
				? findByBankOrderId(context.currentInventory(), bankOrderId, context.matchedCurrentIds())
				: findSameInventoryTransfer(context.currentInventory(), transfer, context.matchedCurrentIds());
		if (current.isPresent()) {
			MoneyTransfer currentTransfer = current.get();
			context.matchedCurrentIds().add(currentTransfer.getId());
			if (isSameInventoryTransfer(currentTransfer, transfer)) {
				return ReconciliationResult.UNCHANGED;
			}
			currentTransfer.setMoneytransferStatus(MoneyTransferStatus.SUPERSEDED);
			dbController.insertOrUpdate(currentTransfer);
			persistRetrievedTransfer(transfer, currentTransfer.getId());
			context.existingTransfers().add(transfer);
			return ReconciliationResult.SUPERSEDED;
		}

		Integer predecessorId = bankOrderId == null ? null
				: findLatestHistoryEntry(context.existingTransfers(), bankOrderId).map(MoneyTransfer::getId).orElse(null);
		persistRetrievedTransfer(transfer, predecessorId);
		context.existingTransfers().add(transfer);
		return ReconciliationResult.SAVED;
	}

	private enum ReconciliationResult {
		UNCHANGED,
		SAVED,
		SUPERSEDED
	}

	private record ReconciliationContext(List<MoneyTransfer> existingTransfers, List<MoneyTransfer> currentInventory,
			Set<Integer> matchedCurrentIds) {
	}

	private MoneyTransfer persistRetrievedTransfer(MoneyTransfer transfer, Integer predecessorId) {
		Recipient recipient = dbController.resolveRecipient(transfer.getRecipient());
		transfer.setRecipient(recipient);
		transfer.setRecipientId(recipient.getId());
		transfer.setHistoryorderId(predecessorId);
		transfer.setMoneytransferStatus(MoneyTransferStatus.INVENTORY);
		return dbController.insertOrUpdate(transfer);
	}

	private Optional<MoneyTransfer> findPendingBankOperation(List<MoneyTransfer> existingTransfers, String bankOrderId) {
		return existingTransfers.stream().filter(transfer -> transfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED
				|| transfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING)
				.filter(transfer -> bankOrderId.equals(normalizeBankOrderId(transfer.getBankOrderId()))).findFirst();
	}

	private Optional<MoneyTransfer> findByBankOrderId(List<MoneyTransfer> currentInventory, String bankOrderId, Set<Integer> matchedCurrentIds) {
		return currentInventory.stream().filter(transfer -> !matchedCurrentIds.contains(transfer.getId()))
				.filter(transfer -> bankOrderId.equals(normalizeBankOrderId(transfer.getBankOrderId()))).findFirst();
	}

	private Optional<MoneyTransfer> findSameInventoryTransfer(List<MoneyTransfer> currentInventory, MoneyTransfer candidate,
			Set<Integer> matchedCurrentIds) {
		return currentInventory.stream().filter(transfer -> !matchedCurrentIds.contains(transfer.getId()))
				.filter(existing -> isSameInventoryTransfer(existing, candidate)).findFirst();
	}

	private Optional<MoneyTransfer> findLatestHistoryEntry(List<MoneyTransfer> existingTransfers, String bankOrderId) {
		return existingTransfers.stream().filter(transfer -> bankOrderId.equals(normalizeBankOrderId(transfer.getBankOrderId())))
				.max(Comparator.comparingInt(MoneyTransfer::getId));
	}

	private boolean isSameInventoryTransfer(MoneyTransfer existing, MoneyTransfer candidate) {
		return existing.getOrderType() == candidate.getOrderType() && sameAmount(existing.getAmount(), candidate.getAmount())
				&& Objects.equals(existing.getExecutionDate(), candidate.getExecutionDate())
				&& Objects.equals(existing.getExecutionDay(), candidate.getExecutionDay())
				&& Objects.equals(existing.getStandingorderMode(), candidate.getStandingorderMode())
				&& Objects.equals(normalizeText(existing.getPurpose()), normalizeText(candidate.getPurpose()))
				&& Objects.equals(normalizeText(existing.getPurposeCode()), normalizeText(candidate.getPurposeCode()))
				&& isSameRecipient(existing.getRecipient(), candidate.getRecipient());
	}

	private boolean sameAmount(BigDecimal left, BigDecimal right) {
		return left == null && right == null || left != null && right != null && left.compareTo(right) == 0;
	}

	private boolean isSameRecipient(Recipient existing, Recipient candidate) {
		if (existing == null || candidate == null) {
			return false;
		}
		return equalsIgnoreCase(existing.getName(), candidate.getName()) && equalsIgnoreCase(existing.getIban(), candidate.getIban())
				&& equalsIgnoreCase(existing.getBic(), candidate.getBic())
				&& equalsIgnoreCase(existing.getAccountNumber(), candidate.getAccountNumber())
				&& equalsIgnoreCase(existing.getBlz(), candidate.getBlz());
	}

	private StandingorderMode toStandingorderMode(String timeunit, int turnus) {
		if (!isBlank(timeunit) && !"M".equalsIgnoreCase(timeunit)) {
			return null;
		}
		return switch (turnus) {
		case 1 -> StandingorderMode.MONTHLY;
		case 2 -> StandingorderMode.BIMONTHLY;
		case 3 -> StandingorderMode.QUARTERLY;
		case 6 -> StandingorderMode.SEMI_ANNUALLY;
		case 12 -> StandingorderMode.ANNUALLY;
		default -> null;
		};
	}

	private LocalDate toLocalDate(java.util.Date date) {
		return date == null ? null : Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
	}

	private String joinUsage(String[] usage) {
		if (usage == null) {
			return null;
		}
		return String.join("\n", Arrays.stream(usage).filter(line -> line != null && !line.isBlank()).toList());
	}

	private String normalizeBankOrderId(String value) {
		return trimToNull(value);
	}

	private String normalizeText(String value) {
		return value == null ? null : value.trim();
	}

	private boolean equalsIgnoreCase(String left, String right) {
		return left == null && right == null || left != null && right != null && left.equalsIgnoreCase(right);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record InventoryMappingResult(List<MoneyTransfer> transfers, boolean complete) {
	}

}
