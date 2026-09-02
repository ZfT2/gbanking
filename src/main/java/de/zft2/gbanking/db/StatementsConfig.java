package de.zft2.gbanking.db;

import static de.zft2.gbanking.db.DaoSqlStatements.BANK_ACCESS_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.BANK_ACCOUNT_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.BANK_ACCOUNT_STATEMENT_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.BANK_MESSAGE_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.BOOKING_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.BPD_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.BUSINESS_CASE_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.CATEGORY_RULE_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.CATEGORY_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.IMPORT_HISTORY_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.INSTITUTE_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.MN_DAO_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.MONEY_TRANSFER_FOREIGN_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.MONEY_TRANSFER_PROTOCOL_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.MONEY_TRANSFER_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.PARAMETER_DATA_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.PSD2_CLIENT_CONFIGURATION_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.RECIPIENT_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.SETTING_SQL;
import static de.zft2.gbanking.db.DaoSqlStatements.UPD_SQL;
import static de.zft2.gbanking.db.StatementsConfig.ResultType.FULL;
import static de.zft2.gbanking.db.StatementsConfig.ResultType.SINGLE_FIELD;
import static de.zft2.gbanking.db.StatementsConfig.ResultType.WITHOUT_RELATIONS;

import java.util.Collection;
import java.util.Map;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.dao.logic.MnDao;
import de.zft2.gbanking.db.dao.logic.StatementsLogic;
import de.zft2.gbanking.db.dao.logic.StatementsLogicBankAccess;
import de.zft2.gbanking.db.dao.logic.StatementsLogicBankAccount;
import de.zft2.gbanking.db.dao.logic.StatementsLogicBooking;
import de.zft2.gbanking.db.dao.logic.StatementsLogicCategory;
import de.zft2.gbanking.db.dao.logic.StatementsLogicCategoryRule;
import de.zft2.gbanking.db.dao.logic.StatementsLogicDefault;
import de.zft2.gbanking.db.dao.logic.StatementsLogicImportHistory;
import de.zft2.gbanking.db.dao.logic.StatementsLogicInstitute;
import de.zft2.gbanking.db.dao.logic.StatementsLogicMoneyTransfer;
import de.zft2.gbanking.db.dao.logic.StatementsLogicMoneyTransferProtocol;
import de.zft2.gbanking.db.dao.logic.StatementsLogicRecipient;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.BankAccessMapper;
import de.zft2.gbanking.db.dao.mapper.BankAccountMapper;
import de.zft2.gbanking.db.dao.mapper.BankAccountStatementMapper;
import de.zft2.gbanking.db.dao.mapper.BankMessageMapper;
import de.zft2.gbanking.db.dao.mapper.BookingMapper;
import de.zft2.gbanking.db.dao.mapper.BpdMapper;
import de.zft2.gbanking.db.dao.mapper.BusinessCaseMapper;
import de.zft2.gbanking.db.dao.mapper.CategoryMapper;
import de.zft2.gbanking.db.dao.mapper.CategoryRuleMapper;
import de.zft2.gbanking.db.dao.mapper.DefaultMapper;
import de.zft2.gbanking.db.dao.mapper.ImportHistoryMapper;
import de.zft2.gbanking.db.dao.mapper.InstituteMapper;
import de.zft2.gbanking.db.dao.mapper.MoneyTransferForeignMapper;
import de.zft2.gbanking.db.dao.mapper.MoneyTransferProtocolMapper;
import de.zft2.gbanking.db.dao.mapper.MoneytransferMapper;
import de.zft2.gbanking.db.dao.mapper.MtoNTableMapper;
import de.zft2.gbanking.db.dao.mapper.ParameterDataBankAccessMapper;
import de.zft2.gbanking.db.dao.mapper.ParameterDataMapper;
import de.zft2.gbanking.db.dao.mapper.Psd2ClientConfigurationMapper;
import de.zft2.gbanking.db.dao.mapper.RecipientMapper;
import de.zft2.gbanking.db.dao.mapper.SettingMapper;
import de.zft2.gbanking.db.dao.mapper.UpdMapper;
import de.zft2.gbanking.exception.GBankingException;

public final class StatementsConfig {

	private StatementsConfig() {
	}

	public enum StatementType {
		SELECT_ALL(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_FULL_DATA(SQLMode.SELECT, FULL, false),
		SELECT_WITH_PARENT(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_WITH_FILTER(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_WITH_PARENT_AND_FILTER(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_WITH_PARENT_AND_FULL_DATA(SQLMode.SELECT, FULL, false),
		SELECT_WITH_PARENT_AND_DATE_RANGE(SQLMode.SELECT, FULL, false),
		SELECT_ID(SQLMode.SELECT, WITHOUT_RELATIONS, false),
		SELECT_FIND(SQLMode.SELECT, WITHOUT_RELATIONS, false),

		INSERT(SQLMode.INSERT, FULL, false),
		UPDATE(SQLMode.UPDATE, FULL, false),
		DELETE(SQLMode.DELETE, null, false),

		DELETE_BANKACCESS_BY_ID(SQLMode.DELETE, null, false),

		SELECT_ACCOUNT_LAST_BOOKING_DATE(SQLMode.SELECT, SINGLE_FIELD, true),
		UPDATE_ACCOUNT_SOURCE(SQLMode.UPDATE, null, true),

		UPDATE_BOOKING_SOURCE(SQLMode.UPDATE, null, true),

		SELECT_SPECIFIC_EDITABLE(SQLMode.SELECT, SINGLE_FIELD, true),
		SELECT_SPECIFIC_DELETABLE(SQLMode.SELECT, SINGLE_FIELD, true);

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

	private record DaoMetadata<T extends Dao, V>(AbstractDaoMapper<T, V> mapper, StatementsLogic<T> logic,
			String tableViewName, Map<StatementType, String> sqlStatements) {
	}

	private static final DaoMetadata<Dao, ?> DEFAULT_METADATA =
			new DaoMetadata<>(new DefaultMapper(), new StatementsLogicDefault<>(), null, Map.of());

	private static final Map<Class<? extends Dao>, DaoMetadata<?, ?>> DAO_METADATA = Map.ofEntries(
            Map.entry(Dao.class, DEFAULT_METADATA),

            Map.entry(BankAccess.class, new DaoMetadata<>(
                    new BankAccessMapper(),
                    new StatementsLogicBankAccess(),
                    null,
					BANK_ACCESS_SQL)),

            Map.entry(BankAccount.class, new DaoMetadata<>(
                    new BankAccountMapper(),
                    new StatementsLogicBankAccount(),
                    null,
					BANK_ACCOUNT_SQL)),

			Map.entry(Psd2ClientConfiguration.class, new DaoMetadata<>(
					new Psd2ClientConfigurationMapper(),
					new StatementsLogicDefault<>(),
					null,
					PSD2_CLIENT_CONFIGURATION_SQL)),

            Map.entry(BankAccountStatement.class, new DaoMetadata<>(
                    new BankAccountStatementMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					BANK_ACCOUNT_STATEMENT_SQL)),

            Map.entry(BankMessage.class, new DaoMetadata<>(
                    new BankMessageMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					BANK_MESSAGE_SQL)),

            Map.entry(Booking.class, new DaoMetadata<>(
                    new BookingMapper(),
                    new StatementsLogicBooking(),
                    "bookingFull",
					BOOKING_SQL)),

            Map.entry(Category.class, new DaoMetadata<>(
                    new CategoryMapper(),
                    new StatementsLogicCategory(),
                    "categoryFull",
					CATEGORY_SQL)),

            Map.entry(CategoryRule.class, new DaoMetadata<>(
                    new CategoryRuleMapper(),
                    new StatementsLogicCategoryRule(),
                    null,
					CATEGORY_RULE_SQL)),

            Map.entry(MoneyTransfer.class, new DaoMetadata<>(
                    new MoneytransferMapper(),
                    new StatementsLogicMoneyTransfer(),
                    null,
					MONEY_TRANSFER_SQL)),

            Map.entry(MoneyTransferForeign.class, new DaoMetadata<>(
                    new MoneyTransferForeignMapper(),
                    new StatementsLogicDefault<>(),
                    "moneytransferForeign",
					MONEY_TRANSFER_FOREIGN_SQL)),

            Map.entry(MoneyTransferProtocol.class, new DaoMetadata<>(
                    new MoneyTransferProtocolMapper(),
                    new StatementsLogicMoneyTransferProtocol(),
                    "moneytransferProtocol",
					MONEY_TRANSFER_PROTOCOL_SQL)),

            Map.entry(Recipient.class, new DaoMetadata<>(
                    new RecipientMapper(),
                    new StatementsLogicRecipient(),
                    null,
					RECIPIENT_SQL)),

            Map.entry(ParameterData.class, new DaoMetadata<>(
                    new ParameterDataMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					PARAMETER_DATA_SQL)),

            Map.entry(Bpd.class, new DaoMetadata<>(
                    new BpdMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					BPD_SQL)),

            Map.entry(Upd.class, new DaoMetadata<>(
                    new UpdMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					UPD_SQL)),

            Map.entry(Institute.class, new DaoMetadata<>(
                    new InstituteMapper(),
					new StatementsLogicInstitute(),
                    "institute_db.institute",
					INSTITUTE_SQL)),

            Map.entry(ImportHistory.class, new DaoMetadata<>(
                    new ImportHistoryMapper(),
                    new StatementsLogicImportHistory(),
                    "institute_db.importHistory",
					IMPORT_HISTORY_SQL)),

            Map.entry(Setting.class, new DaoMetadata<>(
                    new SettingMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					SETTING_SQL)),

            Map.entry(BusinessCase.class, new DaoMetadata<>(
                    new BusinessCaseMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					BUSINESS_CASE_SQL)),

            Map.entry(ParameterDataBankAccess.class, new DaoMetadata<>(
                    new ParameterDataBankAccessMapper(),
                    new StatementsLogicDefault<>(),
                    null,
                    Map.of()
            )),

            Map.entry(MnDao.class, new DaoMetadata<>(
                    new MtoNTableMapper(),
                    new StatementsLogicDefault<>(),
                    null,
					MN_DAO_SQL))
    );

	private static DaoMetadata<?, ?> getMetadata(Class<? extends Dao> type) {
		DaoMetadata<?, ?> metadata = DAO_METADATA.get(type);
		if (metadata == null) {
			throw new GBankingException("Unknown DAO type: " + type.getName());
		}
		return metadata;
	}

	public static String getSelectByIdSqlStatement(Class<? extends Dao> type) {
		String sql = DaoSqlStatements.selectByPrimaryId(type);
		if (sql == null) {
			throw new GBankingException("No SELECT by id statement configured for DAO type: " + type.getName());
		}
		return sql;
	}

	public static String getSqlStatement(Class<? extends Dao> type, StatementType statementType) {
		DaoMetadata<?, ?> metadata = getMetadata(type);
		String sql = metadata.sqlStatements().get(statementType);

		if (sql != null) {
			return sql;
		} else if (statementType == StatementType.DELETE) {
			sql = DaoSqlStatements.deleteByPrimaryId(type);
			if (sql != null) {
				return sql;
			}
		}

		throw new GBankingException(String.format("Unknown statementType %s for DAO type %s", statementType, type.getName()));
	}

	public static String getTableViewName(Class<? extends Dao> type) {
		String tableViewName = getMetadata(type).tableViewName();
		return tableViewName != null ? tableViewName : type.getSimpleName();
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
	public static <T extends Dao> StatementsLogic<T> getLogicForDaoType(Class<? extends Dao> type) {
		DaoMetadata<?, ?> metadata = DAO_METADATA.get(type);
		if (metadata == null) {
			metadata = DEFAULT_METADATA;
		}
		return (StatementsLogic<T>) metadata.logic();
	}

	public static <T extends Dao> StatementsLogic<T> getLogicForDaoType(Collection<? extends Dao> collection) {
		if (collection == null || collection.isEmpty()) {
			throw new GBankingException("Cannot determine DAO type from empty collection");
		}

		Class<? extends Dao> type = collection.iterator().next().getClass();
		return getLogicForDaoType(type);
	}
}
