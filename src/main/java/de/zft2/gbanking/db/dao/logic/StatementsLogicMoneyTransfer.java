package de.zft2.gbanking.db.dao.logic;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.MoneyTransferForeignMapper;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicMoneyTransfer extends StatementsLogicDefault<MoneyTransfer> implements StatementsLogic<MoneyTransfer> {

	private static final Logger log = LogManager.getLogger(StatementsLogicMoneyTransfer.class);

	@Override
	public SqlParameter getSqlParameter(MoneyTransfer mt) {
		return new SqlParameter(String.valueOf(mt.getId()), String.valueOf(mt.getAccountId()), false, false);
	}

	@Override
	public MoneyTransfer insertOrUpdateSingle(MoneyTransfer moneyTransfer) {
		MoneyTransferStatus existingStatus = validateAndGetExistingStatus(moneyTransfer);
		MoneyTransfer persistedTransfer = null;
		if (moneyTransfer != null) {
			boolean insert = moneyTransfer.getId() <= 0;
			boolean initiallyReferencedInventory = !insert && moneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.INVENTORY
					&& existingStatus == MoneyTransferStatus.NEW;
			if (moneyTransfer.getId() > 0 && moneyTransfer.getOrderType() != OrderType.FOREIGN_TRANSFER) {
				deleteForeignTransferDetails(moneyTransfer);
			}
			persistedTransfer = super.insertOrUpdateSingle(moneyTransfer);
			if (insert || initiallyReferencedInventory) {
				persistInitialBankOrderReference(persistedTransfer);
			}
		}
		persistForeignTransferDetails(persistedTransfer);
		return persistedTransfer;
	}

	private void persistInitialBankOrderReference(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0 || moneyTransfer.getBankOrderId() == null
				|| moneyTransfer.getBankOrderId().isBlank()) {
			return;
		}
		LocalDateTime now = LocalDateTime.now();
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
		if (foreignTransfer.getCurrency() == null || foreignTransfer.getCurrency().isBlank()) {
			foreignTransfer.setCurrency(moneyTransfer.getCurrency() != null && !moneyTransfer.getCurrency().isBlank() ? moneyTransfer.getCurrency() : "EUR");
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

	private MoneyTransferStatus validateAndGetExistingStatus(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0) {
			return null;
		}
		MoneyTransferStatus existingStatus = getExistingMoneyTransferStatus(moneyTransfer.getId());
		if (existingStatus != null && existingStatus.isArchiveStatus()) {
			throw new GBankingException("Archived money transfers must not be changed");
		}
		return existingStatus;
	}

	private MoneyTransferStatus getExistingMoneyTransferStatus(int moneyTransferId) {
		try {
			return executeCachedQuery(
					DaoSqlStatements.SQL_SELECT_MONEYTRANSFER_STATUS_BY_ID,
					statement -> statement.setInt(1, moneyTransferId),
					resultSet -> resultSet.next()
							? MoneyTransferStatus.forInt(resultSet.getInt("moneytransferStatus"))
							: null);
		} catch (SQLException exception) {
			throw new GBankingException("Error reading money transfer status", exception);
		}
	}

}
