package de.gbanking.db;

import static de.gbanking.db.SqlStatements.SQL_DELETE_BANKACCESS_BY_BLZ;
import static de.gbanking.db.SqlStatements.SQL_FIND_CATEGORY;
import static de.gbanking.db.SqlStatements.SQL_FIND_RECIPIENT_BY_ARGS;
import static de.gbanking.db.SqlStatements.SQL_INSERT_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_INSERT_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_INSERT_BOOKING;
import static de.gbanking.db.SqlStatements.SQL_INSERT_CATEGORY;
import static de.gbanking.db.SqlStatements.SQL_INSERT_CATEGORY_RULE;
import static de.gbanking.db.SqlStatements.SQL_INSERT_CATEGORY_RULE_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_INSERT_INSTIUTE;
import static de.gbanking.db.SqlStatements.SQL_INSERT_MONEYTRANSFER;
import static de.gbanking.db.SqlStatements.SQL_INSERT_PARAMETERDATA;
import static de.gbanking.db.SqlStatements.SQL_INSERT_SETTING;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BANKACCESSES;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BANKACCOUNTS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BOOKINGS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BPD_OR_UPD;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BUSINESSCASES;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_CATEGORIES_FULL;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_INSTITUTES;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_PARAMETERDATA;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_RECIPIENTS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_SETTINGS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_BANKACCESS_BY_BLZ;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_CATEGORY_BY_NAME;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_CATEGORY_RULE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_INSTIUTE_BY_ID;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_SETTING_BY_ID;
import static de.gbanking.db.SqlStatements.SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_BOOKING;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_CATEGORY;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_CATEGORY_RULE;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_INSTIUTE;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_MONEYTRANSFER;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_SETTING;
import static de.gbanking.db.StatementsConfig.ResultType.FULL;
import static de.gbanking.db.StatementsConfig.ResultType.SINGLE_FIELD;
import static de.gbanking.db.StatementsConfig.ResultType.WITHOUT_RELATIONS;

import java.util.Collection;
import java.util.Map;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Bpd;
import de.gbanking.db.dao.BusinessCase;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.CategoryRule;
import de.gbanking.db.dao.Dao;
import de.gbanking.db.dao.Institute;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.ParameterData;
import de.gbanking.db.dao.ParameterDataBankAccess;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.Setting;
import de.gbanking.db.dao.Upd;
import de.gbanking.db.dao.enu.ParameterDataType;
import de.gbanking.db.dao.logic.MnDao;
import de.gbanking.db.dao.logic.StatementsLogic;
import de.gbanking.db.dao.logic.StatementsLogicBankAccess;
import de.gbanking.db.dao.logic.StatementsLogicBankAccount;
import de.gbanking.db.dao.logic.StatementsLogicBooking;
import de.gbanking.db.dao.logic.StatementsLogicCategory;
import de.gbanking.db.dao.logic.StatementsLogicDefault;
import de.gbanking.db.dao.logic.StatementsLogicRecipient;
import de.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.gbanking.db.dao.mapper.BankAccessMapper;
import de.gbanking.db.dao.mapper.BankAccountMapper;
import de.gbanking.db.dao.mapper.BookingMapper;
import de.gbanking.db.dao.mapper.BpdMapper;
import de.gbanking.db.dao.mapper.BusinessCaseMapper;
import de.gbanking.db.dao.mapper.CategoryMapper;
import de.gbanking.db.dao.mapper.CategoryRuleMapper;
import de.gbanking.db.dao.mapper.DefaultMapper;
import de.gbanking.db.dao.mapper.InstituteMapper;
import de.gbanking.db.dao.mapper.MoneytransferMapper;
import de.gbanking.db.dao.mapper.MtoNTableMapper;
import de.gbanking.db.dao.mapper.ParameterDataBankAccessMapper;
import de.gbanking.db.dao.mapper.ParameterDataMapper;
import de.gbanking.db.dao.mapper.RecipientMapper;
import de.gbanking.db.dao.mapper.UpdMapper;
import de.gbanking.exception.GBankingException;

public class StatementsConfig {
	
//	static StatementsConfig getInstance() {
//		return new StatementsConfig();
//	}
	
	public enum StatementType {
		
		SELECT_ALL(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_FULL_DATA(SQLMode.SELECT, FULL, false),
		SELECT_WITH_PARENT(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_WITH_PARENT_AND_FILTER(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_WITH_PARENT_AND_FULL_DATA(SQLMode.SELECT, FULL, false),
		SELECT_ID(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_FIND(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		INSERT(SQLMode.INSERT, FULL, false),
		UPDATE(SQLMode.UPDATE, FULL, false),
		DELETE(SQLMode.DELETE, null, false),
		
		/** specific by entity type**/
		
		/** BankAccess **/
		DELETE_BANKACCESS_BY_BLZ(SQLMode.DELETE, null, false),
		
		/** BankAccount **/
		
		/** latest booking date on given account **/
		SELECT_ACCOUNT_LAST_BOOKING_DATE(SQLMode.SELECT, SINGLE_FIELD, true),
		UPDATE_ACCOUNT_SOURCE(SQLMode.UPDATE, null, true),
		
		/** Booking **/
		UPDATE_BOOKING_SOURCE(SQLMode.UPDATE, null, true),
		
		/** Recipient **/
		SELECT_FIND_UNREFERENCED(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_SPECIFIC_EDITABLE(SQLMode.SELECT, SINGLE_FIELD, true),
		UPDATE_SPECIFIC_NOT_REFERENCED(SQLMode.UPDATE, null, true),
		UPDATE_SPECIFIC_REFERENCED(SQLMode.UPDATE, SINGLE_FIELD, true);
		
		private StatementType(SQLMode sqlMode, ResultType resultType, boolean isSimpleField) {
			this.sqlMode = sqlMode;
			this.resultType = resultType;
			this.isSimpleField = isSimpleField;
		}

		private final SQLMode sqlMode;
		private final ResultType resultType;
		private final boolean isSimpleField;
		
		public SQLMode getSqlMode() {
			return sqlMode;
		}

		public ResultType getResultType() {
			return resultType;
		}

		public boolean isSimpleField() {
			return isSimpleField;
		}
		
	}
	
	public enum ResultType {
		
		SINGLE_FIELD(false, false),
		WITHOUT_RELATIONS(false, false),
		FULL_WITHOUT_RELATIONS(true, false),
		FULL(true, true);

		ResultType(boolean allColumns, boolean allRelations) {
			this.withAllColumns = allColumns;
			this.withRelations = allRelations;
		}

		private final boolean withAllColumns;
		private final boolean withRelations;
		
		public boolean iswithAllColumns() {
			return withAllColumns;
		}

		public boolean isWithRelations() {
			return withRelations;
		}
	}
	
	private static Map<Class<? extends Dao>, StatementsLogic> statementsLogicMap = 
			Map.of(
					Dao.class, new StatementsLogicDefault<>(),
					BankAccess.class, new StatementsLogicBankAccess(),
					BankAccount.class, new StatementsLogicBankAccount(),
					Booking.class, new StatementsLogicBooking(),
					Category.class, new StatementsLogicCategory(),
					Recipient.class, new StatementsLogicRecipient()
					);

	static Map<Class<?>, Map<StatementType, String>> statementsMap = 
			Map.ofEntries(
					Map.entry(BusinessCase.class, 
						Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_BUSINESSCASES,
						StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT,
						StatementType.INSERT, SqlStatements.SQL_INSERT_BUSINESSCASE)),
					
					Map.entry(BankAccount.class, 
						Map.of(StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS, 
							StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCOUNTS,
							StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER,
							StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_BANKACCOUNTS,
							StatementType.INSERT, SQL_INSERT_BANKACCOUNT,
							StatementType.UPDATE, SQL_UPDATE_BANKACCOUNT,
							StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE,
							StatementType.UPDATE_ACCOUNT_SOURCE, SqlStatements.SQL_UPDATE_BANKACCOUNT_SOURCE)), 
					
					Map.entry(BankAccess.class, 
						Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCESSES,
								StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCESS_BY_BLZ,
								StatementType.INSERT, SQL_INSERT_BANKACCESS,
								StatementType.UPDATE, SQL_UPDATE_BANKACCESS,
								//StatementType.DELETE_BY_PARAM, SQL_DELETE_BANKACCESS_BY_BLZ,
								StatementType.DELETE_BANKACCESS_BY_BLZ, SQL_DELETE_BANKACCESS_BY_BLZ)),
					
					Map.entry(Booking.class, 
						Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_BOOKINGS, 
							StatementType.SELECT_FULL_DATA, SqlStatements.SQL_SELECT_ALL_BOOKINGS_FULL, 
							StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
							StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
							//StatementType.SELECT_ID, ,
							StatementType.INSERT, SQL_INSERT_BOOKING,
							StatementType.UPDATE, SQL_UPDATE_BOOKING,
							StatementType.UPDATE_BOOKING_SOURCE, SqlStatements.SQL_UPDATE_BOOKINGS_SOURCE)),
					
					Map.entry(Category.class, 
						Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_CATEGORIES_FULL,
								StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORY_BY_NAME,
								StatementType.SELECT_FIND, SQL_FIND_CATEGORY,
								StatementType.INSERT, SQL_INSERT_CATEGORY,
								StatementType.UPDATE, SQL_UPDATE_CATEGORY)),
					
					Map.entry(CategoryRule.class, 
						Map.of(StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORY_RULE,
								StatementType.INSERT, SQL_INSERT_CATEGORY_RULE,
								StatementType.UPDATE, SQL_UPDATE_CATEGORY_RULE)),
					
					Map.entry(MoneyTransfer.class, 
						Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
							  StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
							  StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE, 
							  StatementType.SELECT_WITH_PARENT_AND_FILTER, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE,
							  StatementType.SELECT_ID, SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID,
							  StatementType.INSERT, SQL_INSERT_MONEYTRANSFER,
							  StatementType.UPDATE, SQL_UPDATE_MONEYTRANSFER)),
					
					Map.entry(Recipient.class, 
						Map.of(StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN, 
							  StatementType.SELECT_ALL, SQL_SELECT_ALL_RECIPIENTS,
							  StatementType.SELECT_FIND, SQL_FIND_RECIPIENT_BY_ARGS,
							  StatementType.SELECT_FIND_UNREFERENCED, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
							  StatementType.SELECT_SPECIFIC_EDITABLE, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
							  StatementType.INSERT, SqlStatements.SQL_INSERT_RECIPIENT,
							  StatementType.UPDATE, SqlStatements.SQL_UPDATE_RECIPIENT,
							  StatementType.UPDATE_SPECIFIC_NOT_REFERENCED, SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED,
							  StatementType.UPDATE_SPECIFIC_REFERENCED, SqlStatements.SQL_UPDATE_RECIPIENT_NOTE)),
					
					Map.entry(ParameterData.class, 
						Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_PARAMETERDATA,
								StatementType.INSERT, SQL_INSERT_PARAMETERDATA)),
					
					Map.entry(Bpd.class, 
						Map.of(StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.BPD.name()), 
							  StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.BPD.name()))),
					
					Map.entry(Upd.class, 
						Map.of(StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.UPD.name()), 
							  StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.UPD.name()))),
					
					Map.entry(Institute.class, 
							Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_INSTITUTES,
									StatementType.SELECT_ID, SQL_SELECT_ID_INSTIUTE_BY_ID,
									StatementType.INSERT, SQL_INSERT_INSTIUTE,
									StatementType.UPDATE, SQL_UPDATE_INSTIUTE)),
					
					Map.entry(Setting.class, 
							Map.of(StatementType.SELECT_ALL, SQL_SELECT_ALL_SETTINGS,
									StatementType.SELECT_ID, SQL_SELECT_ID_SETTING_BY_ID,
									StatementType.INSERT, SQL_INSERT_SETTING,
									StatementType.UPDATE, SQL_UPDATE_SETTING)),
					
					Map.entry(MnDao.class, 
							Map.of(StatementType.INSERT, SQL_INSERT_CATEGORY_RULE_BANKACCOUNT))
			);
	
	public static final Map<Class<? extends Dao>, Class<? extends Dao>> childrenMap = 
			Map.of(
					BankAccess.class, BankAccount.class, 
					BankAccount.class, Booking.class
					);
	
	static Map<Class<? extends Dao>, String> tableViewMap = 
			Map.of(
					Category.class, "categoryFull"
					);
	
	static Map<Class<? extends Dao>, AbstractDaoMapper<? extends Dao, ?>> daoMapperMap = 
			Map.ofEntries(
					Map.entry(Dao.class, new DefaultMapper()),
					Map.entry(BankAccess.class, new BankAccessMapper()), 
					Map.entry(BankAccount.class, new BankAccountMapper()),
					Map.entry(Booking.class, new BookingMapper()),
					Map.entry(Category.class, new CategoryMapper()),
					Map.entry(CategoryRule.class, new CategoryRuleMapper()),
					Map.entry(MoneyTransfer.class, new MoneytransferMapper()),
					Map.entry(MnDao.class, new MtoNTableMapper()),
					Map.entry(ParameterData.class, new ParameterDataMapper()),
					Map.entry(ParameterDataBankAccess.class, new ParameterDataBankAccessMapper()),
					Map.entry(BusinessCase.class, new BusinessCaseMapper()),
					Map.entry(Recipient.class, new RecipientMapper()),
					Map.entry(Institute.class, new InstituteMapper()),
					Map.entry(Bpd.class, new BpdMapper()),
					Map.entry(Upd.class, new UpdMapper())
					);
	
	public static String getSqlStatement(Class<? extends Dao> table, StatementType statementType) {
		Map<StatementType, String> tableMap = statementsMap.get(table);
		if (tableMap == null) {
			throw new GBankingException(String.format("Unknown DAO type: %s", table.getName()));
		}
		String sql = tableMap.get(statementType);
		if (sql == null) {
			if (statementType == StatementType.DELETE) {
				sql = "DELETE FROM " + table.getSimpleName() + " WHERE id = ?";
			} else {
				throw new GBankingException(String.format("Unknown statementType %s for DAO type %s", statementType, table.getName()));
			}
		}
		return sql;
	}
	
	public static String getTableViewName(Class<? extends Dao> table) {
		String viewName = tableViewMap.get(table);
		return viewName != null ? viewName : table.getSimpleName();
	}
	
/*	public static <T> AbstractDaoMapper<T> getMapperForDaoType(T dao) {
		AbstractDaoMapper<T> mapper = (AbstractDaoMapper<T>) daoMapperMap.get(dao.getClass());
		if (mapper == null) {
			throw new GBankingException("No Mapper found for dao type: " + dao.getClass().getName());
		}
		return mapper;
	} 
*/
	
	@SuppressWarnings("unchecked")
	public static <T, V> AbstractDaoMapper<T, V> getMapperForDaoType(Class<?> type) {
		AbstractDaoMapper<T, V> mapper = (AbstractDaoMapper<T, V>) (daoMapperMap.get(type) == null ?  daoMapperMap.get(Dao.class) : daoMapperMap.get(type));
		if (mapper == null) {
			throw new GBankingException("No Mapper found for dao type: " + type.getName());
		}
		return mapper;
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends Dao, V> StatementsLogic<T,V> getLogicForDaoType(Class<? extends Dao> type) {
		return statementsLogicMap.get(type) == null ? statementsLogicMap.get(Dao.class) : statementsLogicMap.get(type);
	}
	
	public static <T extends Dao, V> StatementsLogic<T,V> getLogicForDaoType(Collection<? extends Dao> collection) {
		Class<? extends Dao> type = collection.iterator().next().getClass();
		return getLogicForDaoType(type);
	}
}
