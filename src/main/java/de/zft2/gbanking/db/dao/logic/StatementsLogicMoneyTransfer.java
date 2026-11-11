package de.zft2.gbanking.db.dao.logic;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.MoneyTransferStandingorder;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.MoneyTransferForeignMapper;
import de.zft2.gbanking.db.dao.mapper.MoneyTransferStandingorderMapper;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicMoneyTransfer extends StatementsLogicDefault<MoneyTransfer> implements StatementsLogic<MoneyTransfer> {

	private static final Logger log = LogManager.getLogger(StatementsLogicMoneyTransfer.class);

	@Override
	public SqlParameter getSqlParameter(MoneyTransfer mt) {
		return new SqlParameter(String.valueOf(mt.getId()), String.valueOf(mt.getAccountId()), false, false);
	}

	@Override
	public MoneyTransfer insertOrUpdateSingle(MoneyTransfer moneyTransfer) {
		ExistingTransfer existing = validateAndGetExistingTransfer(moneyTransfer);
		MoneyTransfer persistedTransfer = null;
		if (moneyTransfer != null) {
			boolean insert = moneyTransfer.getId() <= 0;
			validateStandingorder(moneyTransfer);
			boolean initiallyReferencedInventory = !insert && moneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.INVENTORY
					&& existing != null && existing.status() == MoneyTransferStatus.NEW;
			boolean existingStandingorder = existing != null && existing.orderType() == OrderType.STANDING_ORDER;
			boolean newStandingorder = moneyTransfer.getOrderType() == OrderType.STANDING_ORDER
					&& !existingStandingorder;
			if (moneyTransfer.getId() > 0 && moneyTransfer.getOrderType() != OrderType.FOREIGN_TRANSFER) {
				deleteForeignTransferDetails(moneyTransfer);
			}
			// Save existing details before archiving the parent; new details need the parent first.
			if (existingStandingorder) {
				persistStandingorderDetails(moneyTransfer);
			}
			persistedTransfer = super.insertOrUpdateSingle(moneyTransfer);
			if (newStandingorder) {
				persistStandingorderDetails(persistedTransfer);
			}
			if (insert || initiallyReferencedInventory) {
				persistInitialBankOrderReference(persistedTransfer);
			}
		}
		persistForeignTransferDetails(persistedTransfer);
		return persistedTransfer;
	}

	private void validateStandingorder(MoneyTransfer transfer) {
		if (transfer.getOrderType() == OrderType.STANDING_ORDER
				&& (transfer.getExecutionDay() == null || transfer.getExecutionDay() < 1 || transfer.getExecutionDay() > 31
						|| transfer.getStandingorderMode() == null)) {
			throw new GBankingException("Standing orders require a valid execution day and interval");
		}
	}

	private void persistStandingorderDetails(MoneyTransfer transfer) {
		try {
			if (transfer.getOrderType() == OrderType.STANDING_ORDER) {
				MoneyTransferStandingorder details = transfer.getStandingorder();
				details.setMoneyTransferId(transfer.getId());
				MoneyTransferStandingorderMapper mapper = (MoneyTransferStandingorderMapper)
						StatementsConfig.<MoneyTransferStandingorder, Void>getMapperForDaoType(MoneyTransferStandingorder.class);
				executeCachedUpdate(DaoSqlStatements.SQL_UPSERT_MONEYTRANSFER_STANDINGORDER,
						statement -> mapper.setParamsForUpsert(details, statement));
			} else {
				executeCachedUpdate(DaoSqlStatements.SQL_DELETE_MONEYTRANSFER_STANDINGORDER_BY_MONEYTRANSFER,
						statement -> statement.setInt(1, transfer.getId()));
				transfer.setStandingorder(null);
			}
		} catch (SQLException exception) {
			log.error("Error saving standing order details for transfer id {}", transfer.getId(), exception);
			throw new GBankingException("Error saving standing order details", exception);
		}
	}

	private void persistInitialBankOrderReference(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0 || moneyTransfer.getBankOrderId() == null
				|| moneyTransfer.getBankOrderId().isBlank()) {
			return;
		}
		LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
		MoneyTransferProtocol protocol = new MoneyTransferProtocol(moneyTransfer.getId(), moneyTransfer.getMoneytransferStatus(), now, now);
		protocol.setBankOrderId(moneyTransfer.getBankOrderId().trim());
		new StatementsLogicMoneyTransferProtocol().insertOrUpdateSingle(protocol);
	}

	private void persistForeignTransferDetails(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0
				|| moneyTransfer.getOrderType() != OrderType.FOREIGN_TRANSFER) {
			return;
		}

		MoneyTransferForeign foreignTransfer = moneyTransfer.getForeignTransfer();
		if (foreignTransfer == null) {
			foreignTransfer = new MoneyTransferForeign();
			moneyTransfer.setForeignTransfer(foreignTransfer);
		}
		foreignTransfer.setMoneyTransferId(moneyTransfer.getId());
		if (foreignTransfer.getCurrency() == null) {
			foreignTransfer.setCurrency(Currency.forCodeOrDefault(moneyTransfer.getCurrency(), Currency.EUR));
		}
		if (foreignTransfer.getChargeBearer() == null) {
			foreignTransfer.setChargeBearer(ForeignChargeBearer.SHARED);
		}

		AbstractDaoMapper<MoneyTransferForeign, Void> mapperBase = StatementsConfig.getMapperForDaoType(MoneyTransferForeign.class);
		MoneyTransferForeignMapper mapper = (MoneyTransferForeignMapper) mapperBase;
		MoneyTransferForeign detailsToSave = foreignTransfer;
		try {
			executeCachedUpdate(DaoSqlStatements.SQL_UPSERT_MONEYTRANSFER_FOREIGN,
					statement -> mapper.setParamsForUpsert(detailsToSave, statement));
		} catch (SQLException exception) {
			log.error("Error saving foreign money transfer details for transfer id {}",
					moneyTransfer.getId(), exception);
			throw new GBankingException("Error saving foreign money transfer details", exception);
		}
	}

	private void deleteForeignTransferDetails(MoneyTransfer moneyTransfer) {
		try {
			executeCachedUpdate(
					StatementsConfig.getSqlStatement(MoneyTransferForeign.class, StatementType.DELETE),
					statement -> statement.setInt(1, moneyTransfer.getId()));
		} catch (SQLException exception) {
			log.error("Error deleting foreign money transfer details for transfer id {}",
					moneyTransfer.getId(), exception);
			throw new GBankingException("Error deleting foreign money transfer details", exception);
		}
	}

	private ExistingTransfer validateAndGetExistingTransfer(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0) {
			return null;
		}
		ExistingTransfer existing = getExistingTransfer(moneyTransfer.getId());
		if (existing != null && existing.status().isArchiveStatus()) {
			throw new GBankingException("Archived money transfers must not be changed");
		}
		return existing;
	}

	private ExistingTransfer getExistingTransfer(int moneyTransferId) {
		try {
			return executeCachedQuery(
					DaoSqlStatements.SQL_SELECT_MONEYTRANSFER_STATUS_BY_ID,
					statement -> statement.setInt(1, moneyTransferId),
					resultSet -> resultSet.next()
							? new ExistingTransfer(MoneyTransferStatus.forInt(resultSet.getInt("moneytransferStatus")),
									OrderType.forInt(resultSet.getInt("moneytransferType")))
							: null);
		} catch (SQLException exception) {
			throw new GBankingException("Error reading money transfer status", exception);
		}
	}

	private record ExistingTransfer(MoneyTransferStatus status, OrderType orderType) {
	}

}
