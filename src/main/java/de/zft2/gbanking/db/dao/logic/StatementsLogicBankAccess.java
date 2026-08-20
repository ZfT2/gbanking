package de.zft2.gbanking.db.dao.logic;

import static de.zft2.gbanking.db.DaoSqlStatements.SQL_INSERT_BANKACCESS_PARAMETERDATA;
import static de.zft2.gbanking.db.DaoSqlStatements.SQL_INSERT_PARAMETERDATA;
import static de.zft2.gbanking.db.DaoSqlStatements.SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccessFints;
import de.zft2.gbanking.db.dao.BankAccessPaypal;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.ParameterDataType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public class StatementsLogicBankAccess extends StatementsLogicDefault<BankAccess> implements StatementsLogic<BankAccess> {
	
	private static Logger log = LogManager.getLogger(StatementsLogicBankAccess.class);
	
	@Override
	public SqlParameter getSqlParameter(BankAccess bA) {
		if (bA.getId() > 0 || bA.getAccessType() != BankAccessType.HBCI) {
			return new SqlParameter(null, null, false, false);
		}
		return new SqlParameter(bA.getFints().getBlz());
	}

	@Override
	public BankAccess insertOrUpdateSingle(BankAccess bankAccess) {
		BankAccess persistedAccess = super.insertOrUpdateSingle(bankAccess);
		persistAccessDetails(persistedAccess);
		return persistedAccess;
	}

	private void persistAccessDetails(BankAccess bankAccess) {
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

	private void upsertFints(BankAccess bankAccess) {
		BankAccessFints fints = bankAccess.getFints();
		try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_UPSERT_BANKACCESS_FINTS)) {
			ps.setInt(1, bankAccess.getId());
			ps.setString(2, fints.getCountry());
			ps.setString(3, fints.getBlz());
			ps.setString(4, fints.getHbciURL());
			ps.setObject(5, fints.getPort());
			ps.setString(6, fints.getUserId());
			ps.setString(7, fints.getCustomerId());
			ps.setString(8, fints.getSysId());
			ps.setInt(9, fints.getTanProcedure().getDbStateId());
			ps.setString(10, TypeConverter.toCommaSeparatedString(fints.getAllowedTwostepMechanisms()));
			ps.setString(11, fints.getHbciVersion());
			ps.setString(12, fints.getBpdVersion());
			ps.setString(13, fints.getUpdVersion());
			ps.setInt(14, fints.getFilterType().getDbStateId());
			ps.executeUpdate();
		} catch (SQLException exception) {
			throw new GBankingException("Error persisting FinTS bank access details", exception);
		}
	}

	private void upsertPaypal(BankAccess bankAccess) {
		BankAccessPaypal paypal = bankAccess.getPaypal();
		try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_UPSERT_BANKACCESS_PAYPAL)) {
			ps.setInt(1, bankAccess.getId());
			ps.setString(2, paypal.getUserId());
			ps.setString(3, paypal.getApiUsername());
			ps.setString(4, paypal.getApiSignature());
			ps.executeUpdate();
		} catch (SQLException exception) {
			throw new GBankingException("Error persisting PayPal bank access details", exception);
		}
	}

	private void upsertEnablebanking(BankAccess bankAccess) {
		BankAccessEnablebanking enablebanking = bankAccess.getEnablebanking();
		if (enablebanking == null) {
			throw new GBankingException("Missing Enablebanking bank access details");
		}
		try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_UPSERT_BANKACCESS_ENABLEBANKING)) {
			ps.setInt(1, bankAccess.getId());
			ps.setInt(2, enablebanking.getPsd2ClientConfigurationId());
			ps.setString(3, enablebanking.getAspspName());
			ps.setString(4, enablebanking.getAspspCountry());
			ps.setString(5, enablebanking.getPsuType());
			ps.setString(6, enablebanking.getAuthMethod());
			ps.setString(7, enablebanking.getSessionId());
			ps.setString(8, toString(enablebanking.getValidUntil()));
			ps.setString(9, toString(enablebanking.getRateLimitUntil()));
			ps.executeUpdate();
		} catch (SQLException exception) {
			throw new GBankingException("Error persisting Enablebanking bank access details", exception);
		}
	}

	private String toString(Object value) {
		return value != null ? value.toString() : null;
	}

	private void deleteDetail(String sql, BankAccess bankAccess) {
		executeSqlDeleteStatement(sql, bankAccess);
	}

	@Override
	public boolean updateSpecific(BankAccess bankAccess) {

		boolean result = true;

		for (ParameterDataType typ : ParameterDataType.values()) {
			
			Properties bpdUpdMapBa = typ == ParameterDataType.BPD ? bankAccess.getFints().getBpd() : bankAccess.getFints().getUpd();
			if (bpdUpdMapBa == null) {
				continue;
			}

			Map<String, String> bpdUpdMapDb = getPdMapByBankAccess(bankAccess, typ);

			if (bpdUpdMapDb.equals(bpdUpdMapBa)) {
				log.info("insertOrUpdatePD: PD in database and BankAcces are identical");
			} else {
				result = updatePD(typ, bankAccess);
			}
		}

		return result;
	}
	
	
	private Map<String, String> getPdMapByBankAccess(BankAccess bankAccess, ParameterDataType typ) {
		
		final String sql = String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, typ.name());
		return executeSqlSelectStatementForMap(sql, bankAccess, "pdKey", String.class, "pdValue", String.class);
	}
	
	private boolean updatePD(ParameterDataType typ, BankAccess bankAccess) {
		
			ParameterDataBankAccess pda = typ == ParameterDataType.BPD ? new Bpd() : new Upd();
			pda.setBankAccessId(bankAccess.getId());

			int affectedRows = executeSqlDeleteStatement(DaoSqlStatements.SQL_DELETE_ALL_BANKACCESS_PARAMETERDATA_BY_BANKACCESS, pda);

			log.info("deleted {} {} entries for BankAccess: {}", affectedRows, typ.name(), bankAccess.getBankName());
			
			List<ParameterData> parameterDataListDb = getAll(ParameterData.class);
			List<ParameterData> parameterDataListBa = buildBpdUpdList(typ,
					typ == ParameterDataType.BPD ? bankAccess.getFints().getBpd() : bankAccess.getFints().getUpd());
			parameterDataListBa.removeAll(parameterDataListDb);
			
			if (!parameterDataListBa.isEmpty()) {
				String pdDataSql = String.format(SQL_INSERT_PARAMETERDATA, parameterDataListBa.stream().map(v -> "(?,?,?)").collect(Collectors.joining(", ")));
				
				executeSqlUpdateStatementForList(pdDataSql, StatementType.INSERT, null, parameterDataListBa);
			}
			
			parameterDataListBa = getAll(ParameterData.class);
			parameterDataListBa.retainAll(buildBpdUpdList(typ,
					typ == ParameterDataType.BPD ? bankAccess.getFints().getBpd() : bankAccess.getFints().getUpd()));

			if (!parameterDataListBa.isEmpty()) {
				String pdDataBankAccessSql = String.format(SQL_INSERT_BANKACCESS_PARAMETERDATA,
						parameterDataListBa.stream().map(v -> "(?,?,?,?)").collect(Collectors.joining(", ")));
				executeSqlUpdateStatementForList(pdDataBankAccessSql, parameterDataListBa, bankAccess, ParameterDataBankAccess.class);
			}
			
			affectedRows = executeSqlDeleteStatement(DaoSqlStatements.SQL_DELETE_UNUSED_PARAMETERDATA, null);
			
			log.info("deleted {} now unused entries for ParameterData:", affectedRows);

		return true;
	}
	
	private List<ParameterData> buildBpdUpdList(ParameterDataType typ, Properties bpdMapBa) {
		
		List<ParameterData> parameterDataList = new ArrayList<>();
		for (Map.Entry<Object, Object> property : bpdMapBa.entrySet()) {
			switch (typ) {
			case BPD:
				parameterDataList.add(new Bpd((String) property.getKey(), (String) property.getValue()));
				break;
			case UPD:
				parameterDataList.add(new Upd((String) property.getKey(), (String) property.getValue()));
				break;
			default:
				log.error("Unknown ParameterDate type: {}", typ);
				throw new GBankingException("Unknown ParameterDate type: {}", typ);

			}
		}
		return parameterDataList;
	}
	
	@Override
	public void addOneToManyRelations(BankAccess bankAccess, List<? extends Dao> childrenList) {
		
		bankAccess.setAccounts(convertToTypedList(childrenList, new ArrayList<BankAccount>(), BankAccount.class));
	}

}
