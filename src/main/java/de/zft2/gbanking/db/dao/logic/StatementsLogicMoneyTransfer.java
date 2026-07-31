package de.zft2.gbanking.db.dao.logic;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
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
		rejectArchivedMoneyTransferUpdate(moneyTransfer);
		MoneyTransfer persistedTransfer = null;
		if (moneyTransfer != null) {
			if (moneyTransfer.getId() > 0 && moneyTransfer.getOrderType() != OrderType.FOREIGN_TRANSFER) {
				deleteForeignTransferDetails(moneyTransfer);
			}
			persistedTransfer = super.insertOrUpdateSingle(moneyTransfer);
		}
		persistForeignTransferDetails(persistedTransfer);
		return persistedTransfer;
	}

	private void persistForeignTransferDetails(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0) {
			return;
		}
		if (moneyTransfer.getOrderType() != OrderType.FOREIGN_TRANSFER) {
			deleteForeignTransferDetails(moneyTransfer);
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
		try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_UPSERT_MONEYTRANSFER_FOREIGN)) {
			mapper.setParamsForUpsert(foreignTransfer, ps);
			ps.executeUpdate();
		} catch (SQLException e) {
			log.error("Error saving foreign money transfer details for transfer id {}", moneyTransfer.getId(), e);
			throw new GBankingException("Error saving foreign money transfer details", e);
		}
	}

	private void deleteForeignTransferDetails(MoneyTransfer moneyTransfer) {
		try (PreparedStatement ps = connection.prepareStatement(StatementsConfig.getSqlStatement(MoneyTransferForeign.class, StatementType.DELETE))) {
			ps.setInt(1, moneyTransfer.getId());
			ps.executeUpdate();
		} catch (SQLException e) {
			log.error("Error deleting foreign money transfer details for transfer id {}", moneyTransfer.getId(), e);
			throw new GBankingException("Error deleting foreign money transfer details", e);
		}
	}

	private void rejectArchivedMoneyTransferUpdate(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0) {
			return;
		}
		MoneyTransferStatus existingStatus = getExistingMoneyTransferStatus(moneyTransfer.getId());
		if (isArchivedStatus(existingStatus)) {
			throw new GBankingException("Archived money transfers must not be changed");
		}
	}

	private MoneyTransferStatus getExistingMoneyTransferStatus(int moneyTransferId) {
		try (PreparedStatement ps = connection.prepareStatement("SELECT moneytransferStatus FROM moneytransfer WHERE id = ?")) {
			ps.setInt(1, moneyTransferId);
			try (var rs = ps.executeQuery()) {
				return rs.next() ? MoneyTransferStatus.forInt(rs.getInt("moneytransferStatus")) : null;
			}
		} catch (SQLException e) {
			throw new GBankingException("Error reading money transfer status", e);
		}
	}

	private boolean isArchivedStatus(MoneyTransferStatus status) {
		return status == MoneyTransferStatus.SENT || status == MoneyTransferStatus.DELETED || status == MoneyTransferStatus.IMPORTED
				|| status == MoneyTransferStatus.SUPERSEDED || status == MoneyTransferStatus.NOT_IN_BANK_INVENTORY;
	}

}
