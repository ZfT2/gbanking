package de.zft2.gbanking.db.dao.logic;

import static de.zft2.gbanking.db.DaoSqlStatements.SQL_DELETE_BANKACCESS_PARAMETERDATA_BY_KEY;
import static de.zft2.gbanking.db.DaoSqlStatements.SQL_INSERT_PARAMETERDATA;
import static de.zft2.gbanking.db.DaoSqlStatements.SQL_SELECT_PARAMETERDATA_BY_BANKACCESS;
import static de.zft2.gbanking.db.DaoSqlStatements.SQL_UPSERT_BANKACCESS_PARAMETERDATA;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.SqlErrors;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccessFints;
import de.zft2.gbanking.db.dao.BankAccessPaypal;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.ParameterDataType;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.ParameterDataBankAccessMapper;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public class StatementsLogicBankAccess extends StatementsLogicDefault<BankAccess> implements StatementsLogic<BankAccess> {

	private static final Logger log = LogManager.getLogger(StatementsLogicBankAccess.class);

	@Override
	public SqlParameter getSqlParameter(BankAccess bankAccess) {
		if (bankAccess.getId() > 0 || bankAccess.getAccessType() != BankAccessType.HBCI) {
			return new SqlParameter(null, null, false, false);
		}
		return new SqlParameter(bankAccess.getFints().getBlz(), bankAccess.getFints().getUserId());
	}

	@Override
	public BankAccess insertOrUpdateSingle(BankAccess bankAccess) {
		BankAccess persistedAccess = super.insertOrUpdateSingle(bankAccess);
		try {
			persistAccessDetails(persistedAccess);
		} catch (SQLException exception) {
			throw new GBankingException("Error persisting bank access details", exception);
		}
		return persistedAccess;
	}

	private void persistAccessDetails(BankAccess bankAccess) throws SQLException {
		switch (bankAccess.getAccessType()) {
		case HBCI -> {
			upsertFints(bankAccess);
			deleteDetail(DaoSqlStatements.SQL_DELETE_BANKACCESS_PAYPAL, bankAccess);
			deleteDetail(DaoSqlStatements.SQL_DELETE_BANKACCESS_ENABLEBANKING, bankAccess);
		}
		case PAYPAL -> {
			upsertPaypal(bankAccess);
			deleteDetail(DaoSqlStatements.SQL_DELETE_BANKACCESS_FINTS, bankAccess);
			deleteDetail(DaoSqlStatements.SQL_DELETE_BANKACCESS_ENABLEBANKING, bankAccess);
		}
		case ENABLEBANKING -> {
			upsertEnablebanking(bankAccess);
			deleteDetail(DaoSqlStatements.SQL_DELETE_BANKACCESS_FINTS, bankAccess);
			deleteDetail(DaoSqlStatements.SQL_DELETE_BANKACCESS_PAYPAL, bankAccess);
		}
		}
	}

	private void upsertFints(BankAccess bankAccess) throws SQLException {
		BankAccessFints fints = bankAccess.getFints();
		executeCachedUpdateExactlyOnce(DaoSqlStatements.SQL_UPSERT_BANKACCESS_FINTS, statement -> {
			statement.setInt(1, bankAccess.getId());
			statement.setString(2, fints.getCountry());
			statement.setString(3, fints.getBlz());
			statement.setString(4, fints.getHbciURL());
			statement.setObject(5, fints.getPort());
			statement.setString(6, fints.getUserId());
			statement.setString(7, fints.getCustomerId());
			statement.setString(8, fints.getSysId());
			statement.setInt(9, fints.getTanProcedure().getDbStateId());
			statement.setString(10, TypeConverter.toCommaSeparatedString(fints.getAllowedTwostepMechanisms()));
			statement.setString(11, fints.getHbciVersion());
			statement.setString(12, fints.getBpdVersion());
			statement.setString(13, fints.getUpdVersion());
			statement.setInt(14, fints.getFilterType().getDbStateId());
		});
	}

	private void upsertPaypal(BankAccess bankAccess) throws SQLException {
		BankAccessPaypal paypal = bankAccess.getPaypal();
		executeCachedUpdateExactlyOnce(DaoSqlStatements.SQL_UPSERT_BANKACCESS_PAYPAL, statement -> {
			statement.setInt(1, bankAccess.getId());
			statement.setString(2, paypal.getUserId());
			statement.setString(3, paypal.getApiUsername());
			statement.setString(4, paypal.getApiSignature());
		});
	}

	private void upsertEnablebanking(BankAccess bankAccess) throws SQLException {
		BankAccessEnablebanking enablebanking = bankAccess.getEnablebanking();
		if (enablebanking == null) {
			throw new GBankingException("Missing Enablebanking bank access details");
		}
		executeCachedUpdateExactlyOnce(DaoSqlStatements.SQL_UPSERT_BANKACCESS_ENABLEBANKING, statement -> {
			statement.setInt(1, bankAccess.getId());
			statement.setInt(2, enablebanking.getPsd2ClientConfigurationId());
			statement.setString(3, enablebanking.getAspspName());
			statement.setString(4, enablebanking.getAspspCountry());
			statement.setString(5, enablebanking.getPsuType());
			statement.setString(6, enablebanking.getAuthMethod());
			statement.setString(7, enablebanking.getSessionId());
			statement.setString(8, toString(enablebanking.getValidUntil()));
			statement.setString(9, toString(enablebanking.getRateLimitUntil()));
		});
	}

	private static String toString(Object value) {
		return value != null ? value.toString() : null;
	}

	private void deleteDetail(String sql, BankAccess bankAccess) throws SQLException {
		executeCachedUpdate(sql, statement -> statement.setInt(1, bankAccess.getId()));
	}

	@Override
	public boolean updateSpecific(BankAccess bankAccess) {
		BankAccessFints fints = bankAccess.getFints();
		if (fints.getBpd() == null && fints.getUpd() == null) {
			return true;
		}
		Map<ParameterDataType, Map<String, String>> storedValues = getPdMapsByBankAccess(bankAccess);

		for (ParameterDataType type : ParameterDataType.values()) {
			Properties requestedValues = type == ParameterDataType.BPD ? fints.getBpd() : fints.getUpd();
			if (requestedValues == null) {
				continue;
			}
			Map<String, String> storedTypeValues = storedValues.get(type);
			if (storedTypeValues.equals(requestedValues)) {
				log.info("insertOrUpdatePD: PD in database and BankAccess are identical");
			} else {
				updateParameterData(type, bankAccess, storedTypeValues, requestedValues);
			}
		}
		return true;
	}

	private Map<ParameterDataType, Map<String, String>> getPdMapsByBankAccess(BankAccess bankAccess) {
		Map<ParameterDataType, Map<String, String>> valuesByType = new EnumMap<>(ParameterDataType.class);
		for (ParameterDataType type : ParameterDataType.values()) {
			valuesByType.put(type, new HashMap<>());
		}
		try {
			return executeCachedQuery(SQL_SELECT_PARAMETERDATA_BY_BANKACCESS,
					statement -> statement.setInt(1, bankAccess.getId()), resultSet -> {
						while (resultSet.next()) {
							ParameterDataType type = ParameterDataType.forInt(resultSet.getInt("pdType"));
							valuesByType.get(type).put(resultSet.getString("pdKey"), resultSet.getString("pdValue"));
						}
						return valuesByType;
					});
		} catch (SQLException exception) {
			log.error(getText(SqlErrors.ERROR_DB), exception);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), exception);
		}
	}

	private void updateParameterData(ParameterDataType type, BankAccess bankAccess,
			Map<String, String> storedValues, Properties requestedValues) {
		List<ParameterDataBankAccess> upserts = buildChangedParameterData(
				type, bankAccess.getId(), storedValues, requestedValues);
		List<ParameterDataBankAccess> inserts = upserts.stream()
				.filter(parameterData -> !storedValues.containsKey(parameterData.getPdKey()))
				.toList();
		List<ParameterDataBankAccess> deletes = buildRemovedParameterData(
				type, bankAccess.getId(), storedValues, requestedValues);
		persistParameterData(inserts, upserts, deletes);
		log.info("Updated {} {} entries and deleted {} for BankAccess: {}",
				upserts.size(), type.name(), deletes.size(), bankAccess.getBankName());
	}

	private void persistParameterData(List<ParameterDataBankAccess> inserts,
			List<ParameterDataBankAccess> upserts, List<ParameterDataBankAccess> deletes) {
		try {
			insertParameterData(inserts);
			upsertBankAccessParameterData(upserts);
			deleteBankAccessParameterData(deletes);
		} catch (SQLException exception) {
			log.error(getText(SqlErrors.ERROR_DB), exception);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), exception);
		}
	}

	private void insertParameterData(List<ParameterDataBankAccess> parameterDataList) throws SQLException {
		if (parameterDataList.isEmpty()) {
			return;
		}
		AbstractDaoMapper<ParameterData, Void> mapper = StatementsConfig.getMapperForDaoType(ParameterData.class);
		executeCachedBatch(SQL_INSERT_PARAMETERDATA, parameterDataList,
				(parameterData, statement) -> mapper.setParamsFull(parameterData, statement));
	}

	private void upsertBankAccessParameterData(List<ParameterDataBankAccess> upserts) throws SQLException {
		if (upserts.isEmpty()) {
			return;
		}
		AbstractDaoMapper<ParameterDataBankAccess, ParameterData> mapper =
				StatementsConfig.getMapperForDaoType(ParameterDataBankAccess.class);
		executeCachedBatchExactlyOnce(SQL_UPSERT_BANKACCESS_PARAMETERDATA, upserts,
				(parameterData, statement) -> mapper.setParamsFull(parameterData, statement));
	}

	private void deleteBankAccessParameterData(List<ParameterDataBankAccess> deletes) throws SQLException {
		if (deletes.isEmpty()) {
			return;
		}
		AbstractDaoMapper<ParameterDataBankAccess, ParameterData> mapperBase =
				StatementsConfig.getMapperForDaoType(ParameterDataBankAccess.class);
		ParameterDataBankAccessMapper mapper = (ParameterDataBankAccessMapper) mapperBase;
		executeCachedBatchExactlyOnce(SQL_DELETE_BANKACCESS_PARAMETERDATA_BY_KEY, deletes,
				(parameterData, statement) -> mapper.setParamsDeleteByKey(parameterData, statement));
	}

	private static List<ParameterDataBankAccess> buildChangedParameterData(ParameterDataType type, int bankAccessId,
			Map<String, String> storedValues, Properties requestedValues) {
		List<ParameterDataBankAccess> changes = new ArrayList<>();
		for (Map.Entry<Object, Object> property : requestedValues.entrySet()) {
			String key = (String) property.getKey();
			String value = (String) property.getValue();
			if (!storedValues.containsKey(key) || !Objects.equals(storedValues.get(key), value)) {
				changes.add(createParameterData(type, bankAccessId, key, value));
			}
		}
		return changes;
	}

	private static List<ParameterDataBankAccess> buildRemovedParameterData(ParameterDataType type, int bankAccessId,
			Map<String, String> storedValues, Properties requestedValues) {
		List<ParameterDataBankAccess> removals = new ArrayList<>();
		for (String key : storedValues.keySet()) {
			if (!requestedValues.containsKey(key)) {
				removals.add(createParameterData(type, bankAccessId, key, null));
			}
		}
		return removals;
	}

	private static ParameterDataBankAccess createParameterData(ParameterDataType type, int bankAccessId,
			String key, String value) {
		ParameterDataBankAccess parameterData = switch (type) {
		case BPD -> new Bpd(key, value);
		case UPD -> new Upd(key, value);
		};
		parameterData.setBankAccessId(bankAccessId);
		return parameterData;
	}
}
