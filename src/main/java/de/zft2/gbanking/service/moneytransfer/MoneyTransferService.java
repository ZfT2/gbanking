package de.zft2.gbanking.service.moneytransfer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferProtocolResultStatus;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dto.MoneyTransferForm;
import de.zft2.gbanking.hbci.HbciAccountMatcher;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;

public class MoneyTransferService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(MoneyTransferService.class);

	private static final String ERROR_BANK_DELETE_NOT_AVAILABLE = "ERROR_MONEYTRANSFER_BANK_DELETE_NOT_AVAILABLE";

	private final MoneyTransferExecutionService moneyTransferExecutionService;
	private final MoneyTransferInventoryService moneyTransferInventoryService;

	private static final List<MoneyTransferStatus> EXECUTABLE_MONEY_TRANSFER_STATUSES = List.of(MoneyTransferStatus.NEW, MoneyTransferStatus.CHANGED,
			MoneyTransferStatus.ERROR, MoneyTransferStatus.DELETE_PENDING);

	public MoneyTransferService() {
		this.moneyTransferExecutionService = ServiceRegistry.getService(MoneyTransferExecutionService.class);
		this.moneyTransferInventoryService = ServiceRegistry.getService(MoneyTransferInventoryService.class);
	}

	public List<MoneyTransfer> retrieveOpenTransfers() {
		return EXECUTABLE_MONEY_TRANSFER_STATUSES.stream().flatMap(status -> dbController.getAllWithFilter(MoneyTransfer.class, status).stream()).toList();
	}

	public BankAccount getAccountForOpenMoneytransfers(int accountId) {
		return dbController.getBankAccountWithBusinessCases(accountId);
	}

	public boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {
		return moneyTransferExecutionService.executeTransfer(moneyTransfer, bankAccount, pin);
	}

	public int retrieveInstantPaymentStatuses(List<MoneyTransfer> moneyTransfers, BankAccount bankAccount, char[] pin) {
		return moneyTransferExecutionService.retrieveInstantPaymentStatuses(moneyTransfers, bankAccount, pin);
	}

	public boolean hasStatusReference(MoneyTransfer moneyTransfer) {
		return moneyTransferExecutionService.hasStatusReference(moneyTransfer);
	}

	public boolean retrieveMoneyTransferInventory(BankAccount bankAccount, OrderType orderType, char[] pin) {
		return moneyTransferInventoryService.retrieveInventory(bankAccount, orderType, pin);
	}

	public MoneyTransfer saveMoneyTransferToDB(MoneyTransferForm mtf) {
		return saveMoneyTransferToDB(mtf, null);
	}

	public MoneyTransfer saveMoneyTransferToDB(MoneyTransferForm mtf, MoneyTransfer existingMoneyTransfer) {
		return dbController.executeInTransaction(() -> saveMoneyTransferToDBInTransaction(mtf, existingMoneyTransfer));
	}

	public void deleteMoneyTransferFromDB(MoneyTransfer moneytransfer) {
		if (moneytransfer != null && moneytransfer.getMoneytransferStatus() == MoneyTransferStatus.UNCERTAIN) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_UNCERTAIN_IMMUTABLE"));
		}
		if (isBankManagedOrder(moneytransfer)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_BANK_ORDER_LOCAL_DELETE"));
		}
		log.info("Deleting money transfer id {}, type={}, status={}", moneytransfer != null ? moneytransfer.getId() : null,
				moneytransfer != null ? moneytransfer.getOrderType() : null, moneytransfer != null ? moneytransfer.getMoneytransferStatus() : null);
		dbController.delete(moneytransfer, null);
	}

	public MoneyTransfer requestBankOrderDeletion(MoneyTransfer moneyTransfer) {
		return dbController.executeInTransaction(() -> requestBankOrderDeletionInTransaction(moneyTransfer));
	}

	public MoneyTransfer cancelBankOrderDeletion(MoneyTransfer moneyTransfer) {
		return dbController.executeInTransaction(() -> {
			MoneyTransfer persistedTransfer = findPersistedMoneyTransfer(moneyTransfer);
			if (persistedTransfer == null || persistedTransfer.getMoneytransferStatus() != MoneyTransferStatus.DELETE_PENDING) {
				throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
			}
			persistedTransfer.setMoneytransferStatus(MoneyTransferStatus.INVENTORY);
			return dbController.insertOrUpdate(persistedTransfer);
		});
	}

	/**
	 * Resolves an order with an uncertain execution status after the user checked it with the bank.
	 *
	 * @param moneyTransfer the order with the uncertain execution status
	 * @param executed      {@code true} if the bank executed the order, {@code false} if it may be sent again
	 * @return the updated order
	 */
	public MoneyTransfer resolveUncertainTransfer(MoneyTransfer moneyTransfer, boolean executed) {
		return dbController.executeInTransaction(() -> {
			MoneyTransfer persistedTransfer = findPersistedMoneyTransfer(moneyTransfer);
			if (persistedTransfer == null || persistedTransfer.getMoneytransferStatus() != MoneyTransferStatus.UNCERTAIN) {
				throw new GBankingException(getText("ERROR_MONEYTRANSFER_NOT_UNCERTAIN"));
			}
			MoneyTransferStatus status = executed ? MoneyTransferStatus.SENT : MoneyTransferStatus.ERROR;
			persistedTransfer.setMoneytransferStatus(status);
			LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
			MoneyTransferProtocol protocol = new MoneyTransferProtocol(persistedTransfer.getId(), status, now, now);
			protocol.setResultStatus(executed ? MoneyTransferProtocolResultStatus.SUCCESS : MoneyTransferProtocolResultStatus.ERROR_UNKNOWN);
			protocol.setProtocolText(getText(executed ? "UI_DIALOG_MONEYTRANSFER_PROTOCOL_MANUALLY_EXECUTED"
					: "UI_DIALOG_MONEYTRANSFER_PROTOCOL_MANUALLY_NOT_EXECUTED"));
			dbController.insertOrUpdate(protocol);
			log.info("Uncertain money transfer resolved manually. transferId={}, status={}", persistedTransfer.getId(), status);
			return dbController.insertOrUpdate(persistedTransfer);
		});
	}

	public boolean isBankManagedOrder(MoneyTransfer moneyTransfer) {
		if (!isBankOrderType(moneyTransfer)) {
			return false;
		}
		MoneyTransferStatus status = moneyTransfer.getMoneytransferStatus();
		return status == MoneyTransferStatus.INVENTORY || status == MoneyTransferStatus.CHANGED || status == MoneyTransferStatus.DELETE_PENDING;
	}

	Konto getSenderAccount(HBCIPassport passport, BankAccount bankAccount) throws GBankingException {
		Konto konto = HbciAccountMatcher.findAccount(passport.getAccounts(), bankAccount);
		if (konto != null) {
			log.debug("Resolved HBCI sender account for account id {}", bankAccount.getId());
			return konto;
		}
		log.warn("No HBCI sender account found for account id {}, IBAN: {} / Nr.: {}", bankAccount::getId,
				() -> SensitiveDataMasker.maskIban(bankAccount.getIban()), () -> SensitiveDataMasker.maskAccountNumber(bankAccount.getNumber()));
		throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_SENDING_ACCOUNT_NOT_FOUND", SensitiveDataMasker.maskIban(bankAccount.getIban())));
	}

	private MoneyTransfer saveMoneyTransferToDBInTransaction(MoneyTransferForm mtf, MoneyTransfer existingMoneyTransfer) {
		if (existingMoneyTransfer != null && existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.UNCERTAIN) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_UNCERTAIN_IMMUTABLE"));
		}
		if (existingMoneyTransfer != null && existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_DELETE_PENDING_EDIT"));
		}
		MoneyTransferForeign foreignTransfer = mtf.getForeignTransfer();
		Recipient recipient = dbController.resolveRecipient(
				new Recipient(mtf.getRecipientName(), mtf.getIban(), mtf.getBic(), foreignTransfer != null ? foreignTransfer.getRecipientAccountNumber() : null,
						foreignTransfer != null ? foreignTransfer.getRecipientBankCode() : mtf.getRecipientBlz(), mtf.getBank(), Source.MONEYTRANSFER));
		log.info("saveMoneyTransferToDB(): using Recipient with id: {}", recipient.getId());

		MoneyTransferStatus statusAfterSave = resolveMoneyTransferStatusAfterSave(existingMoneyTransfer);
		MoneyTransfer historyPredecessor = resolveHistoryPredecessor(existingMoneyTransfer);
		MoneyTransfer moneyTransfer = selectMoneyTransfer(historyPredecessor, existingMoneyTransfer);
		boolean newMoneyTransfer = moneyTransfer.getId() <= 0;
		moneyTransfer.setAccountId(mtf.getBankAccount().getId());
		moneyTransfer.setOrderType(mtf.getOrderType());
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setPurpose(mtf.getPurpose());
		moneyTransfer.setAmount(mtf.getAmount());
		moneyTransfer.setCurrency(mtf.getCurrency());
		moneyTransfer.setForeignTransfer(foreignTransfer);
		moneyTransfer.setExecutionDate(mtf.getExecutionDate());
		moneyTransfer.setExecutionDay(mtf.getExecutionDay());
		moneyTransfer.setStandingorderMode(mtf.getStandingorderMode());
		moneyTransfer.setMoneytransferStatus(statusAfterSave);

		MoneyTransfer persistedMoneyTransfer = dbController.insertOrUpdate(moneyTransfer);
		log.info("{} money transfer id {}, type={}, accountId={}, status={}", newMoneyTransfer ? "Created" : "Updated",
				persistedMoneyTransfer != null ? persistedMoneyTransfer.getId() : moneyTransfer.getId(), moneyTransfer.getOrderType(),
				moneyTransfer.getAccountId(), moneyTransfer.getMoneytransferStatus());
		return persistedMoneyTransfer;
	}

	private MoneyTransfer selectMoneyTransfer(MoneyTransfer historyPredecessor, MoneyTransfer existingMoneyTransfer) {
		if (historyPredecessor != null) {
			return createSuccessor(historyPredecessor);
		}
		return existingMoneyTransfer != null && existingMoneyTransfer.getId() > 0 ? existingMoneyTransfer : new MoneyTransfer();
	}

	private MoneyTransfer resolveHistoryPredecessor(MoneyTransfer existingMoneyTransfer) {
		if (existingMoneyTransfer == null || existingMoneyTransfer.getId() <= 0
				|| existingMoneyTransfer.getMoneytransferStatus() != MoneyTransferStatus.INVENTORY || !isBankOrderType(existingMoneyTransfer)) {
			return null;
		}
		MoneyTransfer predecessor = dbController.getAllByParent(MoneyTransfer.class, existingMoneyTransfer.getAccountId()).stream()
				.filter(transfer -> transfer.getId() == existingMoneyTransfer.getId()).findFirst().orElse(null);
		if (predecessor == null) {
			throw new GBankingException("Money transfer to be versioned no longer exists");
		}
		return predecessor;
	}

	private MoneyTransfer createSuccessor(MoneyTransfer predecessor) {
		MoneyTransfer successor = new MoneyTransfer();
		successor.setBankOrderId(predecessor.getBankOrderId());
		successor.setPurposeCode(predecessor.getPurposeCode());
		successor.setEndToEndId(predecessor.getEndToEndId());
		successor.setHistoryorderId(predecessor.getId());
		return successor;
	}

	private MoneyTransferStatus resolveMoneyTransferStatusAfterSave(MoneyTransfer existingMoneyTransfer) {
		if (existingMoneyTransfer == null || existingMoneyTransfer.getId() <= 0) {
			return MoneyTransferStatus.NEW;
		}
		if (isBankOrderType(existingMoneyTransfer) && (existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.INVENTORY
				|| existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED)) {
			return MoneyTransferStatus.CHANGED;
		}
		return existingMoneyTransfer.getMoneytransferStatus() != null ? existingMoneyTransfer.getMoneytransferStatus() : MoneyTransferStatus.NEW;
	}

	private MoneyTransfer requestBankOrderDeletionInTransaction(MoneyTransfer moneyTransfer) {
		MoneyTransfer persistedTransfer = findPersistedMoneyTransfer(moneyTransfer);
		if (persistedTransfer == null || !isBankManagedOrder(persistedTransfer)) {
			throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
		}
		if (persistedTransfer.getBankOrderId() == null || persistedTransfer.getBankOrderId().isBlank()) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_BANK_ORDER_ID_REQUIRED"));
		}
		if (persistedTransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			return persistedTransfer;
		}

		if (persistedTransfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED) {
			MoneyTransfer predecessor = findPersistedMoneyTransferById(persistedTransfer.getAccountId(), persistedTransfer.getHistoryorderId());
			if (predecessor == null || predecessor.getMoneytransferStatus() != MoneyTransferStatus.INVENTORY) {
				throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
			}
			persistedTransfer.setMoneytransferStatus(MoneyTransferStatus.SUPERSEDED);
			dbController.insertOrUpdate(persistedTransfer);
			persistedTransfer = predecessor;
		}

		if (persistedTransfer.getMoneytransferStatus() != MoneyTransferStatus.INVENTORY) {
			throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
		}
		persistedTransfer.setMoneytransferStatus(MoneyTransferStatus.DELETE_PENDING);
		return dbController.insertOrUpdate(persistedTransfer);
	}

	private static boolean isBankOrderType(MoneyTransfer moneyTransfer) {
		return moneyTransfer != null
				&& (moneyTransfer.getOrderType() == OrderType.STANDING_ORDER || moneyTransfer.getOrderType() == OrderType.SCHEDULED_TRANSFER);
	}

	private MoneyTransfer findPersistedMoneyTransfer(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0) {
			return null;
		}
		return findPersistedMoneyTransferById(moneyTransfer.getAccountId(), moneyTransfer.getId());
	}

	private MoneyTransfer findPersistedMoneyTransferById(int accountId, Integer moneyTransferId) {
		if (moneyTransferId == null || moneyTransferId <= 0) {
			return null;
		}
		return dbController.getAllByParent(MoneyTransfer.class, accountId).stream().filter(transfer -> transfer.getId() == moneyTransferId).findFirst()
				.orElse(null);
	}
}
