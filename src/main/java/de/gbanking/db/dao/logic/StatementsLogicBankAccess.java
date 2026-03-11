package de.gbanking.db.dao.logic;

import static de.gbanking.db.SqlStatements.SQL_INSERT_BANKACCESS_PARAMETERDATA;
import static de.gbanking.db.SqlStatements.SQL_INSERT_PARAMETERDATA;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.SqlStatements;
import de.gbanking.db.StatementsConfig.StatementType;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Bpd;
import de.gbanking.db.dao.Dao;
import de.gbanking.db.dao.ParameterData;
import de.gbanking.db.dao.ParameterDataBankAccess;
import de.gbanking.db.dao.Upd;
import de.gbanking.db.dao.enu.ParameterDataType;
import de.gbanking.exception.GBankingException;

public class StatementsLogicBankAccess extends StatementsLogicDefault<BankAccess, Void> implements StatementsLogic<BankAccess, Void> {
	
	private static Logger log = LogManager.getLogger(StatementsLogicBankAccess.class);
	
	@Override
	public SqlParameter getSqlParameter(BankAccess bA) {
		return new SqlParameter(bA.getBlz(), null);
	}

	@Override
	public boolean updateSpecific(BankAccess bankAccess) {

		boolean result = true;

		for (ParameterDataType typ : ParameterDataType.values()) {
			
			Properties bpdUpdMapBa = (typ == ParameterDataType.BPD ? bankAccess.getBpd() : bankAccess.getUpd());
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

			int affectedRows = executeSqlDeleteStatement(SqlStatements.SQL_DELETE_ALL_BANKACCESS_PARAMETERDATA_BY_BANKACCESS, pda);

			log.info("deleted {} {} entries for BankAccess: {}", affectedRows, typ.name(), bankAccess.getBankName());
			
			List<ParameterData> parameterDataListDb = getAll(ParameterData.class);
			List<ParameterData> parameterDataListBa = buildBpdUpdList(typ, (typ == ParameterDataType.BPD ? bankAccess.getBpd() : bankAccess.getUpd()));
			parameterDataListBa.removeAll(parameterDataListDb);
			
			if (!parameterDataListBa.isEmpty()) {
				String pdDataSql = String.format(SQL_INSERT_PARAMETERDATA, parameterDataListBa.stream().map(v -> "(?,?,?)").collect(Collectors.joining(", ")));
				
				executeSqlUpdateStatementForList(pdDataSql, StatementType.INSERT, null, parameterDataListBa);
			}
			
			parameterDataListBa = getAll(ParameterData.class);
			parameterDataListBa.retainAll(buildBpdUpdList(typ, (typ == ParameterDataType.BPD ? bankAccess.getBpd() : bankAccess.getUpd())));

			String pdDataBankAccessSql = String.format(SQL_INSERT_BANKACCESS_PARAMETERDATA, parameterDataListBa.stream().map(v -> "(?,?,?,?)").collect(Collectors.joining(", ")));
			
			executeSqlUpdateStatementForList(pdDataBankAccessSql, parameterDataListBa, bankAccess, ParameterDataBankAccess.class);

			affectedRows = executeSqlDeleteStatement(SqlStatements.SQL_DELETE_UNUSED_PARAMETERDATA, null);
			
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
