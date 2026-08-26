package de.zft2.gbanking.file.imp;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.BaseWorker;

public abstract class MoneyTransferImportBean implements BaseMessagesDb {

	private final BaseWorker worker;
	private final MoneyTransferStatus importStatus;

	protected MoneyTransferImportBean(BaseWorker worker, MoneyTransferStatus importStatus) {
		if (importStatus != MoneyTransferStatus.NEW && importStatus != MoneyTransferStatus.IMPORTED) {
			throw new IllegalArgumentException("Import status must be NEW or IMPORTED");
		}
		this.worker = worker;
		this.importStatus = importStatus;
	}

	protected ImportResult importTransfers(List<ParsedTransfer> transfers, BankAccount contextAccount) {
		if (transfers.isEmpty()) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_FILE_EMPTY"));
		}
		List<ImportCandidate> candidates = transfers.stream().map(transfer -> toImportCandidate(transfer, contextAccount)).toList();
		return dbController.executeInTransaction(() -> persistCandidates(candidates));
	}

	private ImportCandidate toImportCandidate(ParsedTransfer transfer, BankAccount contextAccount) {
		BankAccount account = contextAccount != null ? validateContextAccount(transfer, contextAccount) : resolveAccount(transfer);
		return new ImportCandidate(account, transfer);
	}

	private BankAccount validateContextAccount(ParsedTransfer transfer, BankAccount contextAccount) {
		if (!matchesSenderAccount(contextAccount, transfer)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_ACCOUNT_MISMATCH", senderIdentifier(transfer)));
		}
		return contextAccount;
	}

	private BankAccount resolveAccount(ParsedTransfer transfer) {
		List<BankAccount> matchingAccounts = dbController.getAll(BankAccount.class).stream()
				.filter(account -> matchesSenderAccount(account, transfer)).toList();
		if (matchingAccounts.isEmpty()) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_ACCOUNT_NOT_FOUND", senderIdentifier(transfer)));
		}
		if (matchingAccounts.size() > 1) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_ACCOUNT_AMBIGUOUS", senderIdentifier(transfer)));
		}
		return matchingAccounts.get(0);
	}

	private boolean matchesSenderAccount(BankAccount account, ParsedTransfer transfer) {
		String senderIban = normalizeAccountIdentifier(transfer.senderIban());
		String accountIban = normalizeAccountIdentifier(account.getIban());
		if (!senderIban.isBlank() && !accountIban.isBlank()) {
			return accountIban.equals(senderIban);
		}
		String accountNumber = normalizeAccountIdentifier(account.getNumber());
		if (!senderIban.isBlank()) {
			return !accountNumber.isBlank() && senderIban.endsWith(accountNumber);
		}
		return !accountNumber.isBlank() && accountNumber.equals(normalizeAccountIdentifier(transfer.senderAccountNumber()));
	}

	private String senderIdentifier(ParsedTransfer transfer) {
		return transfer.senderIban() != null ? transfer.senderIban() : transfer.senderAccountNumber();
	}

	private ImportResult persistCandidates(List<ImportCandidate> candidates) {
		Map<TransferKey, MoneyTransfer> knownTransfers = loadKnownTransfers(candidates);
		int importedCount = 0;
		int skippedDuplicateCount = 0;
		for (int i = 0; i < candidates.size(); i++) {
			ImportCandidate candidate = candidates.get(i);
			updateWorker(progress(50, 99, i + 1L, candidates.size()), "UI_MONEYTRANSFER_IMPORT_PROGRESS_IMPORTING",
					Integer.toString(i + 1), Integer.toString(candidates.size()));
			TransferKey transferKey = toTransferKey(candidate);
			MoneyTransfer knownTransfer = knownTransfers.get(transferKey);
			if (knownTransfer != null) {
				if (!persistMissingProtocols(knownTransfer, candidate.transfer().protocols())) {
					skippedDuplicateCount++;
				}
				continue;
			}
			MoneyTransfer persistedTransfer = persistCandidate(candidate);
			knownTransfers.put(transferKey, persistedTransfer);
			importedCount++;
		}
		return new ImportResult(importedCount, skippedDuplicateCount);
	}

	private Map<TransferKey, MoneyTransfer> loadKnownTransfers(List<ImportCandidate> candidates) {
		Map<TransferKey, MoneyTransfer> knownTransfers = new HashMap<>();
		candidates.stream().map(candidate -> candidate.account().getId()).distinct()
				.flatMap(accountId -> dbController.getAllByParent(MoneyTransfer.class, accountId).stream())
				.forEach(transfer -> knownTransfers.putIfAbsent(toTransferKey(transfer), transfer));
		return knownTransfers;
	}

	private boolean persistMissingProtocols(MoneyTransfer moneyTransfer, List<ParsedProtocol> protocols) {
		if (protocols.isEmpty()) {
			return false;
		}
		Set<ProtocolKey> existingProtocols = dbController.getAllByParent(MoneyTransferProtocol.class, moneyTransfer.getId()).stream()
				.map(protocol -> toProtocolKey(protocol)).collect(Collectors.toSet());
		boolean protocolPersisted = false;
		for (ParsedProtocol protocol : protocols) {
			if (existingProtocols.add(toProtocolKey(protocol))) {
				persistProtocol(moneyTransfer, protocol);
				protocolPersisted = true;
			}
		}
		return protocolPersisted;
	}

	private MoneyTransfer persistCandidate(ImportCandidate candidate) {
		ParsedTransfer transfer = candidate.transfer();
		Recipient recipient = dbController.resolveRecipient(new Recipient(transfer.recipientName(), transfer.recipientIban(), transfer.recipientBic(),
				null, null, transfer.recipientBank(), Source.MONEYTRANSFER));
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setAccountId(candidate.account().getId());
		moneyTransfer.setOrderType(transfer.orderType());
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setPurpose(transfer.purpose());
		moneyTransfer.setPurposeCode(transfer.purposeCode());
		moneyTransfer.setEndToEndId(transfer.endToEndId());
		moneyTransfer.setAmount(transfer.amount());
		moneyTransfer.setCurrency(Currency.forCodeOrDefault(transfer.currency(), Currency.EUR).name());
		moneyTransfer.setExecutionDate(transfer.executionDate());
		moneyTransfer.setMoneytransferStatus(importStatus);
		MoneyTransfer persistedTransfer = dbController.insertOrUpdate(moneyTransfer);
		persistMissingProtocols(persistedTransfer, transfer.protocols());
		return persistedTransfer;
	}

	private void persistProtocol(MoneyTransfer moneyTransfer, ParsedProtocol parsedProtocol) {
		MoneyTransferProtocol protocol = new MoneyTransferProtocol();
		protocol.setMoneyTransferId(moneyTransfer.getId());
		protocol.setMoneytransferStatus(parsedProtocol.moneytransferStatus());
		protocol.setTimeStart(parsedProtocol.timeStart());
		protocol.setTimeFinish(parsedProtocol.timeFinish());
		protocol.setProtocolText(parsedProtocol.protocolText());
		dbController.insertOrUpdate(protocol);
	}

	private ProtocolKey toProtocolKey(MoneyTransferProtocol protocol) {
		return new ProtocolKey(protocol.getMoneytransferStatus(), protocol.getTimeStart(), protocol.getTimeFinish(),
				normalizeText(protocol.getProtocolText()));
	}

	private ProtocolKey toProtocolKey(ParsedProtocol protocol) {
		return new ProtocolKey(protocol.moneytransferStatus(), protocol.timeStart(), protocol.timeFinish(), normalizeText(protocol.protocolText()));
	}

	private TransferKey toTransferKey(ImportCandidate candidate) {
		ParsedTransfer transfer = candidate.transfer();
		return new TransferKey(candidate.account().getId(), transfer.orderType(), normalizeText(transfer.recipientName()),
				normalizeAccountIdentifier(transfer.recipientIban()), normalizeAccountIdentifier(transfer.recipientBic()),
				transfer.amount().stripTrailingZeros(), transfer.executionDate(), normalizeText(transfer.purpose()), normalizeText(transfer.purposeCode()),
				normalizeText(transfer.endToEndId()));
	}

	private TransferKey toTransferKey(MoneyTransfer moneyTransfer) {
		Recipient recipient = moneyTransfer.getRecipient();
		return new TransferKey(moneyTransfer.getAccountId(), moneyTransfer.getOrderType(),
				normalizeText(recipient != null ? recipient.getName() : null),
				normalizeAccountIdentifier(recipient != null ? recipient.getIban() : null),
				normalizeAccountIdentifier(recipient != null ? recipient.getBic() : null), moneyTransfer.getAmount().stripTrailingZeros(),
				moneyTransfer.getExecutionDate(), normalizeText(moneyTransfer.getPurpose()), normalizeText(moneyTransfer.getPurposeCode()),
				normalizeText(moneyTransfer.getEndToEndId()));
	}

	protected String normalizeAccountIdentifier(String value) {
		return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
	}

	protected int progress(int start, int stop, long current, long total) {
		return total <= 0 ? start : Math.min(stop, start + (int) Math.round((stop - start) * (current / (double) total)));
	}

	protected void updateWorker(int progress, String messageKey, String... values) {
		if (worker != null) {
			worker.setProcessingState(getText(messageKey, values));
			worker.setWorkerProgress(progress);
		}
	}

	protected String fileName(Path path) {
		Path name = path.getFileName();
		return name != null ? name.toString() : path.toString();
	}

	private String normalizeText(String value) {
		return value == null ? "" : value.trim();
	}

	public record ImportResult(int importedCount, int skippedDuplicateCount) {
	}

	protected record ParsedTransfer(String senderIban, String senderAccountNumber, String recipientName, String recipientIban,
			String recipientBic, String recipientBank, BigDecimal amount, String currency, String purpose, String purposeCode, String endToEndId,
			OrderType orderType, LocalDate executionDate, List<ParsedProtocol> protocols) {
	}

	protected record ParsedProtocol(MoneyTransferStatus moneytransferStatus, LocalDateTime timeStart, LocalDateTime timeFinish,
			String protocolText) {
	}

	private record ImportCandidate(BankAccount account, ParsedTransfer transfer) {
	}

	private record TransferKey(int accountId, OrderType orderType, String recipientName, String recipientIban, String recipientBic,
			BigDecimal amount, LocalDate executionDate, String purpose, String purposeCode, String endToEndId) {
	}

	private record ProtocolKey(MoneyTransferStatus moneytransferStatus, LocalDateTime timeStart, LocalDateTime timeFinish, String protocolText) {
	}
}
