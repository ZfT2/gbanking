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
import de.gbanking.db.dao.mapper.SettingMapper;
import de.gbanking.db.dao.mapper.UpdMapper;
import de.gbanking.exception.GBankingException;

public final class StatementsConfig {

	private StatementsConfig() {
	}

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

		DELETE_BANKACCESS_BY_BLZ(SQLMode.DELETE, null, false),

		SELECT_ACCOUNT_LAST_BOOKING_DATE(SQLMode.SELECT, SINGLE_FIELD, true),
		UPDATE_ACCOUNT_SOURCE(SQLMode.UPDATE, null, true),

		UPDATE_BOOKING_SOURCE(SQLMode.UPDATE, null, true),

		SELECT_FIND_UNREFERENCED(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_SPECIFIC_EDITABLE(SQLMode.SELECT, SINGLE_FIELD, true),
		UPDATE_SPECIFIC_NOT_REFERENCED(SQLMode.UPDATE, null, true),
		UPDATE_SPECIFIC_REFERENCED(SQLMode.UPDATE, SINGLE_FIELD, true);

		private final SQLMode sqlMode;
		private final ResultType resultType;
		private final boolean simpleField;

		StatementType(SQLMode sqlMode, ResultType resultType, boolean simpleField) {
			this.sqlMode = sqlMode;
			this.resultType = resultType;
			this.simpleField = simpleField;
		}

		public SQLMode getSqlMode() {
			return sqlMode;
		}

		public ResultType getResultType() {
			return resultType;
		}

		public boolean isSimpleField() {
			return simpleField;
		}
	}

	public enum ResultType {
		SINGLE_FIELD(false, false),
		WITHOUT_RELATIONS(false, false),
		FULL_WITHOUT_RELATIONS(true, false),
		FULL(true, true);

		private final boolean withAllColumns;
		private final boolean withRelations;

		ResultType(boolean withAllColumns, boolean withRelations) {
			this.withAllColumns = withAllColumns;
			this.withRelations = withRelations;
		}

		public boolean isWithAllColumns() {
			return withAllColumns;
		}

		public boolean isWithRelations() {
			return withRelations;
		}
	}

	private record DaoMetadata<T extends Dao, V>(AbstractDaoMapper<T, V> mapper, StatementsLogic<T, V> logic, Class<? extends Dao> childType,
			String tableViewName, Map<StatementType, String> sqlStatements) {
	}

	private static final DaoMetadata<Dao, ?> DEFAULT_METADATA = new DaoMetadata<>(new DefaultMapper(), new StatementsLogicDefault<>(), null, null, Map.of());

	private static final Map<Class<? extends Dao>, DaoMetadata<?, ?>> DAO_METADATA = Map.ofEntries(
            Map.entry(Dao.class, DEFAULT_METADATA),

            Map.entry(BankAccess.class, new DaoMetadata<>(
                    new BankAccessMapper(),
                    new StatementsLogicBankAccess(),
                    BankAccount.class,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCESSES,
                            StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCESS_BY_BLZ,
                            StatementType.INSERT, SQL_INSERT_BANKACCESS,
                            StatementType.UPDATE, SQL_UPDATE_BANKACCESS,
                            StatementType.DELETE_BANKACCESS_BY_BLZ, SQL_DELETE_BANKACCESS_BY_BLZ
                    ))),

            Map.entry(BankAccount.class, new DaoMetadata<>(
                    new BankAccountMapper(),
                    new StatementsLogicBankAccount(),
                    Booking.class,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCOUNTS,
                            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS,
                            StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER,
                            StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_BANKACCOUNTS,
                            StatementType.INSERT, SQL_INSERT_BANKACCOUNT,
                            StatementType.UPDATE, SQL_UPDATE_BANKACCOUNT,
                            StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE,
                            StatementType.UPDATE_ACCOUNT_SOURCE, SqlStatements.SQL_UPDATE_BANKACCOUNT_SOURCE
                    ))),

            Map.entry(Booking.class, new DaoMetadata<>(
                    new BookingMapper(),
                    new StatementsLogicBooking(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_BOOKINGS,
                            StatementType.SELECT_FULL_DATA, SqlStatements.SQL_SELECT_ALL_BOOKINGS_FULL,
                            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
                            StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
                            StatementType.INSERT, SQL_INSERT_BOOKING,
                            StatementType.UPDATE, SQL_UPDATE_BOOKING,
                            StatementType.UPDATE_BOOKING_SOURCE, SqlStatements.SQL_UPDATE_BOOKINGS_SOURCE
                    ))),

            Map.entry(Category.class, new DaoMetadata<>(
                    new CategoryMapper(),
                    new StatementsLogicCategory(),
                    null,
                    "categoryFull",
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_CATEGORIES_FULL,
                            StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORY_BY_NAME,
                            StatementType.SELECT_FIND, SQL_FIND_CATEGORY,
                            StatementType.INSERT, SQL_INSERT_CATEGORY,
                            StatementType.UPDATE, SQL_UPDATE_CATEGORY
                    ))),

            Map.entry(CategoryRule.class, new DaoMetadata<>(
                    new CategoryRuleMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORY_RULE,
                            StatementType.INSERT, SQL_INSERT_CATEGORY_RULE,
                            StatementType.UPDATE, SQL_UPDATE_CATEGORY_RULE
                    ))),

            Map.entry(MoneyTransfer.class, new DaoMetadata<>(
                    new MoneytransferMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
                            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
                            StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE,
                            StatementType.SELECT_WITH_PARENT_AND_FILTER, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE,
                            StatementType.SELECT_ID, SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID,
                            StatementType.INSERT, SQL_INSERT_MONEYTRANSFER,
                            StatementType.UPDATE, SQL_UPDATE_MONEYTRANSFER
                    ))),

            Map.entry(Recipient.class, new DaoMetadata<>(
                    new RecipientMapper(),
                    new StatementsLogicRecipient(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_RECIPIENTS,
                            StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN,
                            StatementType.SELECT_FIND, SQL_FIND_RECIPIENT_BY_ARGS,
                            StatementType.SELECT_FIND_UNREFERENCED, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
                            StatementType.SELECT_SPECIFIC_EDITABLE, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
                            StatementType.INSERT, SqlStatements.SQL_INSERT_RECIPIENT,
                            StatementType.UPDATE, SqlStatements.SQL_UPDATE_RECIPIENT,
                            StatementType.UPDATE_SPECIFIC_NOT_REFERENCED, SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED,
                            StatementType.UPDATE_SPECIFIC_REFERENCED, SqlStatements.SQL_UPDATE_RECIPIENT_NOTE
                    ))),

            Map.entry(ParameterData.class, new DaoMetadata<>(
                    new ParameterDataMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_PARAMETERDATA,
                            StatementType.INSERT, SQL_INSERT_PARAMETERDATA
                    ))),

            Map.entry(Bpd.class, new DaoMetadata<>(
                    new BpdMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.BPD.name()),
                            StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.BPD.name())
                    ))),

            Map.entry(Upd.class, new DaoMetadata<>(
                    new UpdMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.UPD.name()),
                            StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.UPD.name())
                    ))),

            Map.entry(Institute.class, new DaoMetadata<>(
                    new InstituteMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_INSTITUTES,
                            StatementType.SELECT_ID, SQL_SELECT_ID_INSTIUTE_BY_ID,
                            StatementType.INSERT, SQL_INSERT_INSTIUTE,
                            StatementType.UPDATE, SQL_UPDATE_INSTIUTE
                    ))),

            Map.entry(Setting.class, new DaoMetadata<>(
                    new SettingMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_SETTINGS,
                            StatementType.SELECT_ID, SQL_SELECT_ID_SETTING_BY_ID,
                            StatementType.INSERT, SQL_INSERT_SETTING,
                            StatementType.UPDATE, SQL_UPDATE_SETTING
                    ))),

            Map.entry(BusinessCase.class, new DaoMetadata<>(
                    new BusinessCaseMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.SELECT_ALL, SQL_SELECT_ALL_BUSINESSCASES,
                            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT,
                            StatementType.INSERT, SqlStatements.SQL_INSERT_BUSINESSCASE
                    ))),

            Map.entry(ParameterDataBankAccess.class, new DaoMetadata<>(
                    new ParameterDataBankAccessMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of()
            )),

            Map.entry(MnDao.class, new DaoMetadata<>(
                    new MtoNTableMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    null,
                    Map.of(
                            StatementType.INSERT, SQL_INSERT_CATEGORY_RULE_BANKACCOUNT
                    )))
    );

	private static DaoMetadata<?, ?> getMetadata(Class<? extends Dao> type) {
		DaoMetadata<?, ?> metadata = DAO_METADATA.get(type);
		if (metadata == null) {
			throw new GBankingException("Unknown DAO type: " + type.getName());
		}
		return metadata;
	}

	public static String getSqlStatement(Class<? extends Dao> type, StatementType statementType) {
		DaoMetadata<?, ?> metadata = getMetadata(type);
		String sql = metadata.sqlStatements().get(statementType);

		if (sql != null) {
			return sql;
		} else if (statementType == StatementType.DELETE) {
			return "DELETE FROM " + getTableViewName(type) + " WHERE id = ?";
		}

		throw new GBankingException(String.format("Unknown statementType %s for DAO type %s", statementType, type.getName()));
	}

	public static String getTableViewName(Class<? extends Dao> type) {
		String tableViewName = getMetadata(type).tableViewName();
		return tableViewName != null ? tableViewName : type.getSimpleName();
	}

	public static Class<? extends Dao> getChildType(Class<? extends Dao> type) {
		return getMetadata(type).childType();
	}

	@SuppressWarnings("unchecked")
	public static <T extends Dao, V> AbstractDaoMapper<T, V> getMapperForDaoType(Class<? extends Dao> type) {
		DaoMetadata<?, ?> metadata = DAO_METADATA.get(type);
		if (metadata == null) {
			metadata = DEFAULT_METADATA;
		}
		return (AbstractDaoMapper<T, V>) metadata.mapper();
	}

	@SuppressWarnings("unchecked")
	public static <T extends Dao, V> StatementsLogic<T, V> getLogicForDaoType(Class<? extends Dao> type) {
		DaoMetadata<?, ?> metadata = DAO_METADATA.get(type);
		if (metadata == null) {
			metadata = DEFAULT_METADATA;
		}
		return (StatementsLogic<T, V>) metadata.logic();
	}

	public static <T extends Dao, V> StatementsLogic<T, V> getLogicForDaoType(Collection<? extends Dao> collection) {
		if (collection == null || collection.isEmpty()) {
			throw new GBankingException("Cannot determine DAO type from empty collection");
		}

		Class<? extends Dao> type = collection.iterator().next().getClass();
		return getLogicForDaoType(type);
	}
}