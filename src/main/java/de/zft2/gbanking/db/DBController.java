package de.zft2.gbanking.db;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountIdentifier;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.AccountIdentifierType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.SourceGroup;
import de.zft2.gbanking.db.dao.logic.StatementsLogic;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.BookingMapper;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.util.TypeConverter;

public class DBController extends DbExecutor {

	private static final Logger log = LogManager.getLogger(DBController.class);

	private DBController() {
	}

	private static final class Holder {

		private static final DBController INSTANCE = new DBController();

		private Holder() {
		}
	}

	public static DBController getInstance(String dbFilePath) {
		return getInstance(dbFilePath, null);
	}

	public static DBController getInstance(String dbFilePath, DbMigrationProgressListener migrationProgressListener) {
		DbConnectionHandler.initialize(dbFilePath, migrationProgressListener);

		return Holder.INSTANCE;
	}

	public static DBController getInstance(String dbFilePath, DbMigrationProgressListener migrationProgressListener,
			boolean allowMissingInstituteDatabase) {
		DbConnectionHandler.initialize(dbFilePath, migrationProgressListener, allowMissingInstituteDatabase);
		return Holder.INSTANCE;
	}

	public static boolean prepareInstituteDatabase(Path dataDirectory) {
		return DbConnectionHandler.prepareInstituteDatabaseFile(dataDirectory);
	}

	public static void validateDatabaseIntegrity(Path databaseFile, boolean fullIntegrityCheck) {
		DbConnectionHandler.validateDatabaseFileIntegrity(databaseFile, fullIntegrityCheck);
	}

	public static boolean hasOpenConnection() {
		return DbTransactionManager.withLifecycleLock(() -> {
			DbSession session = getSession();
			try {
				return session != null && session.isOpen();
			} catch (SQLException | RuntimeException exception) {
				log.warn("Could not inspect database connection state", exception);
				return false;
			}
		});
	}

	public <T> T executeInTransaction(Supplier<T> operation) {
		return withDbTransaction(operation);
	}

	public void executeInTransaction(Runnable operation) {
		withDbTransaction(operation);
	}

	public BankAccountRetrievalStatus getBankAccountRetrievalStatus(int bankAccountId) {
		return withDbAccess(() -> {
			if (bankAccountId <= 0) {
				return null;
			}

			try {
				return jdbc().query(DaoSqlStatements.SQL_SELECT_BANKACCOUNT_RETRIEVAL_STATUS,
						statement -> statement.setInt(1, bankAccountId),
						resultSet -> resultSet.next() ? mapBankAccountRetrievalStatus(resultSet) : null);
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure("Error reading bank account retrieval status", exception);
			}
		});
	}

	public void upsertBankAccountRetrievalStatus(BankAccountRetrievalStatus retrievalStatus) {
		withDbTransaction(() -> {
			try {
				int affectedRows = jdbc().update(DaoSqlStatements.SQL_UPSERT_BANKACCOUNT_RETRIEVAL_STATUS,
						statement -> {
							statement.setInt(1, retrievalStatus.bankAccountId());
							statement.setTimestamp(2, Timestamp.valueOf(retrievalStatus.retrievedAt()));
							statement.setInt(3, retrievalStatus.result().getDbStateId());
							statement.setInt(4, retrievalStatus.newBookingCount());
							statement.setInt(5, retrievalStatus.pendingBookingCount());
							statement.setString(6, retrievalStatus.lastError());
						});
				if (affectedRows != 1) {
					throw new GBankingException("Bank account retrieval status was not saved");
				}
			} catch (SQLException exception) {
				throw new GBankingException("Error saving bank account retrieval status", exception);
			}
		});
	}

	public List<BankAccountIdentifier> getBankAccountIdentifiers(int bankAccountId) {
		return readBankAccountIdentifiers(DaoSqlStatements.SQL_SELECT_BANKACCOUNT_IDENTIFIERS_BY_ACCOUNT, bankAccountId);
	}

	public List<BankAccountIdentifier> getAllBankAccountIdentifiers() {
		return readBankAccountIdentifiers(DaoSqlStatements.SQL_SELECT_ALL_BANKACCOUNT_IDENTIFIERS, null);
	}

	private List<BankAccountIdentifier> readBankAccountIdentifiers(String sql, Integer bankAccountId) {
		return withDbAccess(() -> {
			try {
				return jdbc().query(sql,
						bankAccountId == null ? null : statement -> statement.setInt(1, bankAccountId),
						resultSet -> {
							List<BankAccountIdentifier> identifiers = new ArrayList<>();
							while (resultSet.next()) {
								identifiers.add(new BankAccountIdentifier(
										resultSet.getInt("id"),
										resultSet.getInt("account_id"),
										AccountIdentifierType.forInt(resultSet.getInt("propertyType")),
										resultSet.getString("value")));
							}
							return identifiers;
						}
				);
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure("Error reading bank account identifiers", exception);
			}
		});
	}

	public void replaceBankAccountIdentifiers(int bankAccountId, Collection<BankAccountIdentifier> identifiers) {
		if (bankAccountId <= 0) {
			throw new IllegalArgumentException("A persisted bank account is required");
		}
		List<BankAccountIdentifier> normalizedIdentifiers = normalizeBankAccountIdentifiers(bankAccountId, identifiers);
		withDbTransaction(() -> {
			try {
				jdbc().update(DaoSqlStatements.SQL_DELETE_BANKACCOUNT_IDENTIFIERS_BY_ACCOUNT,
						statement -> statement.setInt(1, bankAccountId));
				if (!normalizedIdentifiers.isEmpty()) {
					jdbc().batch(DaoSqlStatements.SQL_INSERT_BANKACCOUNT_IDENTIFIER,
							normalizedIdentifiers,
							(statement, identifier) -> {
								statement.setInt(1, bankAccountId);
								statement.setInt(2, identifier.propertyType().getDbStateId());
								statement.setString(3, identifier.value());
							});
				}
			} catch (SQLException exception) {
				throw new GBankingException("Error saving bank account identifiers", exception);
			}
		});
	}

	private List<BankAccountIdentifier> normalizeBankAccountIdentifiers(int bankAccountId, Collection<BankAccountIdentifier> identifiers) {
		if (identifiers == null || identifiers.isEmpty()) {
			return List.of();
		}
		Map<String, BankAccountIdentifier> uniqueIdentifiers = new LinkedHashMap<>();
		for (BankAccountIdentifier identifier : identifiers) {
			if (identifier == null || identifier.propertyType() == null || identifier.value() == null || identifier.value().isBlank()) {
				continue;
			}
			String value = identifier.value().trim();
			String key = identifier.propertyType().getDbStateId() + ":" + value.toLowerCase(Locale.ROOT);
			uniqueIdentifiers.putIfAbsent(key, new BankAccountIdentifier(0, bankAccountId, identifier.propertyType(), value));
		}
		return List.copyOf(uniqueIdentifiers.values());
	}

	private static BankAccountRetrievalStatus mapBankAccountRetrievalStatus(ResultSet rs) throws SQLException {
		Timestamp retrievedAt = rs.getTimestamp("retrievedAt");
		LocalDateTime retrievedAtValue = retrievedAt != null ? retrievedAt.toLocalDateTime() : null;
		return new BankAccountRetrievalStatus(rs.getInt("bankAccount_id"), retrievedAtValue,
				AccountRetrievalStatus.forInt(rs.getInt("result")), rs.getInt("newBookingCount"), rs.getInt("pendingBookingCount"),
				rs.getString("lastError"));
	}
	
	public boolean updateBookingsWithRecipients(Map<Recipient, Set<Integer>> recipientBookingMap) {
		return withDbTransaction(() -> updateDaoListWithDetailIdList(recipientBookingMap, DaoSqlStatements.SQL_UPDATE_BOOKINGS_RECIPIENT));
	}

	public Recipient resolveRecipient(Recipient recipient) {
		return recipient != null ? insertOrUpdate(recipient) : null;
	}

	public Recipient resolveRecipientForManualBooking(Booking booking, Recipient recipient) {
		Recipient currentRecipient = booking != null ? booking.getRecipient() : null;
		boolean manualBooking = booking != null && booking.getSource() != null
				&& booking.getSource().getGroup() == SourceGroup.GROUP_MANUELL;
		if (recipient != null && manualBooking && isRecipientEditable(currentRecipient)) {
			recipient.setId(currentRecipient.getId());
		}
		return resolveRecipient(recipient);
	}

	public boolean isRecipientEditable(Recipient recipient) {
		return matchesRecipientState(recipient, StatementsConfig.StatementType.SELECT_SPECIFIC_EDITABLE);
	}

	public boolean isRecipientDeletable(Recipient recipient) {
		return matchesRecipientState(recipient, StatementsConfig.StatementType.SELECT_SPECIFIC_DELETABLE);
	}

	private boolean matchesRecipientState(Recipient recipient, StatementsConfig.StatementType statementType) {
		return recipient != null && recipient.getId() > 0
				&& Boolean.TRUE.equals(getSingleResultField(recipient, statementType, Boolean.class));
	}

	public Recipient findPreferredRecipientByIban(String iban) {
		return withDbAccess(() -> {
			if (iban == null || iban.isBlank()) {
				return null;
			}

			AbstractDaoMapper<Recipient, ?> mapper = StatementsConfig.getMapperForDaoType(Recipient.class);
			try {
				return jdbc().query(DaoSqlStatements.SQL_SELECT_PREFERRED_RECIPIENT_BY_IBAN,
						statement -> {
							statement.setString(1, iban);
							statement.setString(2, iban);
						},
						resultSet -> resultSet.next() ? mapper.map(resultSet, ResultType.FULL) : null);
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
			}
		});
	}

	public int updateRecipientDefault(int recipientId, boolean defaultRecipient) {
		return withDbTransaction(() -> {
			if (recipientId <= 0) {
				return 0;
			}

			try {
				return jdbc().update(DaoSqlStatements.SQL_UPDATE_RECIPIENT_DEFAULT,
						statement -> {
							statement.setBoolean(1, defaultRecipient);
							statement.setTimestamp(2, TypeConverter.toSqlTimestampNow());
							statement.setInt(3, recipientId);
						});
			} catch (SQLException exception) {
				log.error(getText(SqlErrors.ERROR_DB_UPDATE), exception);
				throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), exception);
			}
		});
	}

	public void updateBookingAdditionalNote(Booking booking) {
		if (booking == null || booking.getId() <= 0) {
			throw new IllegalArgumentException("A persisted booking is required");
		}

		withDbTransaction(() -> {
			AbstractDaoMapper<Booking, Void> mapperBase = StatementsConfig.getMapperForDaoType(Booking.class);
			BookingMapper mapper = (BookingMapper) mapperBase;
			try {
				if (mapper.hasAdditionalNoteData(booking)) {
					jdbc().update(DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_NOTE,
							statement -> mapper.setParamsAdditionalNote(booking, statement));
				} else {
					jdbc().update(DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_NOTE,
							statement -> statement.setInt(1, booking.getId()));
				}
			} catch (SQLException exception) {
				log.error(getText(SqlErrors.ERROR_DB_UPDATE), exception);
				throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), exception);
			}
		});
	}
	
	public boolean updateBookingsWithCategories(Map<Category, Set<Integer>> categoryBookingMap) {
		return withDbTransaction(() -> updateDaoListWithDetailIdList(categoryBookingMap, DaoSqlStatements.SQL_UPDATE_BOOKINGS_CATEGORY));
	}

	public boolean updateBookingsWithCategoryRule(CategoryRule categoryRule, Set<Integer> bookingIds) {
		return withDbTransaction(() -> {
			if (categoryRule == null || categoryRule.getCategory() == null || categoryRule.getCategory().getId() <= 0 || categoryRule.getId() <= 0) {
				return false;
			}

			Set<Integer> normalizedBookingIds = positiveIds(bookingIds);
			if (normalizedBookingIds.isEmpty()) {
				return false;
			}

			try {
				int[] updateCounts = jdbc().batch(DaoSqlStatements.SQL_UPDATE_BOOKINGS_CATEGORY_RULE,
						normalizedBookingIds,
						(statement, bookingId) -> {
							statement.setInt(1, categoryRule.getCategory().getId());
							statement.setInt(2, categoryRule.getId());
							statement.setInt(3, bookingId);
						});
				return sumUpdatedRows(updateCounts) > 0;
			} catch (SQLException exception) {
				log.error(getText(SqlErrors.ERROR_DB_UPDATE), exception);
				throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), exception);
			}
		});
	}

	public int clearBookingCategories(Collection<Integer> selectedBookingIds) {
		return withDbTransaction(() -> {
			List<Integer> normalizedBookingIds = positiveIds(selectedBookingIds).stream().sorted().toList();
			if (normalizedBookingIds.isEmpty()) {
				return 0;
			}

			try {
				Timestamp updatedAt = TypeConverter.toSqlTimestampNow();
				return sumUpdatedRows(jdbc().batch(DaoSqlStatements.SQL_CLEAR_BOOKING_CATEGORY,
						normalizedBookingIds,
						(statement, bookingId) -> {
							statement.setTimestamp(1, updatedAt);
							statement.setInt(2, bookingId);
						}));
			} catch (SQLException exception) {
				log.error(getText(SqlErrors.ERROR_DB_UPDATE), exception);
				throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), exception);
			}
		});
	}

	public int clearBookingCrossBookingIds(Collection<Integer> selectedBookingIds) {
		return withDbTransaction(() -> {
			List<Integer> normalizedBookingIds = positiveIds(selectedBookingIds).stream().sorted().toList();
			if (normalizedBookingIds.isEmpty()) {
				return 0;
			}

			try {
				Timestamp updatedAt = TypeConverter.toSqlTimestampNow();
				return sumUpdatedRows(jdbc().batch(DaoSqlStatements.SQL_CLEAR_BOOKING_CROSS_BOOKING_ID,
						normalizedBookingIds,
						(statement, bookingId) -> {
							statement.setTimestamp(1, updatedAt);
							statement.setInt(2, bookingId);
							statement.setInt(3, bookingId);
							statement.setInt(4, bookingId);
						}));
			} catch (SQLException exception) {
				log.error(getText(SqlErrors.ERROR_DB_UPDATE), exception);
				throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), exception);
			}
		});
	}

	private static Set<Integer> positiveIds(Collection<Integer> ids) {
		Set<Integer> normalizedIds = new HashSet<>();
		if (ids == null) {
			return normalizedIds;
		}
		for (Integer id : ids) {
			if (id != null && id > 0) {
				normalizedIds.add(id);
			}
		}
		return normalizedIds;
	}

	private static int sumUpdatedRows(int[] updateCounts) {
		int total = 0;
		for (int updateCount : updateCounts) {
			if (updateCount == Statement.EXECUTE_FAILED) {
				throw new GBankingException("Database batch update failed");
			}
			if (updateCount > 0) {
				total += updateCount;
			}
		}
		return total;
	}
	
	public boolean insertOrUpdatePD(BankAccess bankAccess) {
		return withDbTransaction(() -> {
			StatementsLogic<BankAccess> logic = StatementsConfig.getLogicForDaoType(BankAccess.class);
			return logic.updateSpecific(bankAccess);
		});
	}
	
	public boolean insertBusinessCases(BankAccount bankAccount) {
		return withDbTransaction(() -> {
			StatementsLogic<BankAccount> logic = StatementsConfig.getLogicForDaoType(bankAccount.getClass());
			return logic.insertSpecific(bankAccount);
		});
	}
	
	public boolean insertAccountBookings(Collection<Booking> bookingList) {
		return withDbTransaction(() -> {
			if (bookingList == null || bookingList.isEmpty()) {
				return false;
			}

			Set<Booking> bookingListDb = insertAll(new HashSet<>(bookingList));
			return bookingListDb.stream().allMatch(booking -> booking.getId() > 0);
		});
	}
	
	public Booking findCrossBooking(Booking booking) {
		return withDbAccess(() -> {
			if (booking == null || booking.getRecipient() == null) {
				return null;
			}

			AbstractDaoMapper<Booking, ?> mapper = StatementsConfig.getMapperForDaoType(Booking.class);
			try {
				return jdbc().query(DaoSqlStatements.SQL_FIND_CROSS_BOOKINGS_FULL,
						statement -> {
							statement.setString(1, booking.getRecipient().getIban());
							statement.setString(2, booking.getRecipient().getAccountNumber());
							statement.setBigDecimal(3, booking.getAmount().negate());
							statement.setDate(4, java.sql.Date.valueOf(booking.getDateBooking()));
							statement.setInt(5, BookingType.REBOOKING_OUT.getDbStateId());
							statement.setInt(6, BookingType.REBOOKING_IN.getDbStateId());
						},
						resultSet -> resultSet.next() ? mapper.map(resultSet, ResultType.FULL) : null);
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure(getText(SqlErrors.ERROR_DB_FIND), exception);
			}
		});
	}

	public List<Booking> getSplitBookings(int parentBookingId) {
		return withDbAccess(() -> {
			AbstractDaoMapper<Booking, ?> mapper = StatementsConfig.getMapperForDaoType(Booking.class);
			try {
				return jdbc().query(DaoSqlStatements.SQL_SELECT_SPLIT_BOOKINGS_FULL_BY_PARENT,
						statement -> statement.setInt(1, parentBookingId),
						resultSet -> {
							List<Booking> bookings = new ArrayList<>();
							while (resultSet.next()) {
								bookings.add(mapper.map(resultSet, ResultType.FULL));
							}
							return bookings;
						});
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
			}
		});
	}

	public Map<String, Integer> getAccountsIdsByAccountName() {
		return withDbAccess(() -> {
			StatementsLogic<BankAccount> logic = StatementsConfig.getLogicForDaoType(BankAccount.class);
			return logic.getTableIds(BankAccount.class, SqlFields.ACCOUNT_ACCOUNTNAME, null);
		});
	}
	
	public Map<String, Integer> getCrossAccountsIdsByIbanOrNumber() {
		return withDbAccess(() -> {
			StatementsLogic<BankAccount> logic = StatementsConfig.getLogicForDaoType(BankAccount.class);
			return logic.getTableIds(BankAccount.class, "iban", "number");
		});
	}
	
	public BankAccess getBankAccessById(int id) {
		return getBankAccessByField(DaoSqlStatements.SQL_SELECT_BANKACCESS_BY_ID, id);

	}
	
	public BankAccess getBankAccessByBlz(String blz) {
		return getBankAccessByField(DaoSqlStatements.SQL_SELECT_BANKACCESS_BY_BLZ, blz);
	}

	public BankAccess getBankAccessByBlzAndUserId(String blz, String userId) {
		return withDbAccess(() -> {
			boolean paypal = "PAYPAL".equalsIgnoreCase(blz);
			String sql = paypal ? DaoSqlStatements.SQL_SELECT_BANKACCESS_PAYPAL_BY_USER_ID
					: DaoSqlStatements.SQL_SELECT_BANKACCESS_BY_BLZ_AND_USER_ID;
			AbstractDaoMapper<BankAccess, ?> mapper = StatementsConfig.getMapperForDaoType(BankAccess.class);
			try {
				return jdbc().query(sql,
						statement -> {
							int index = 1;
							if (!paypal) {
								statement.setString(index++, blz);
							}
							statement.setString(index, userId);
						},
						resultSet -> resultSet.next()
								? mapper.map(resultSet, ResultType.WITHOUT_RELATIONS)
								: null);
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure("Error reading bank access", exception);
			}
		});
	}
	
	public <T extends Dao> Set<T> insertAll(Set<T> entityList) {
		if (entityList.isEmpty())
			return Collections.emptySet();

		Map<T, Integer> originalIds = new IdentityHashMap<>();
		entityList.forEach(entity -> originalIds.put(entity, entity.getId()));
		return withDbTransaction(() -> {
			DbTransactionManager.onRollback(() -> originalIds.forEach(Dao::setId));
			StatementsLogic<T> logic = StatementsConfig.getLogicForDaoType(detectListType(entityList));
			return logic.insertAll(entityList);
		});
	}
	
	public void printAccountsInDB() {
		if (!log.isDebugEnabled()) {
			return;
		}
		withDbAccess(() -> {
			try {
				jdbc().query(DaoSqlStatements.SQL_SELECT_ALL_BANKACCOUNTS, null, resultSet -> {
					while (resultSet.next()) {
						log.debug("id = {}, accountName = {}, accountType = {}, iban = {}, bic = {}, number = {}, bankName = {}",
								resultSet.getInt("id"),
								resultSet.getString(SqlFields.ACCOUNT_ACCOUNTNAME),
								resultSet.getString("accountType"),
								SensitiveDataMasker.maskIban(resultSet.getString("iban")),
								resultSet.getString("bic"),
								SensitiveDataMasker.maskAccountNumber(resultSet.getString("number")),
								resultSet.getString(SqlFields.BANKNAME));
					}
					return null;
				});
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
			}
		});
	}

	public void printBookingsInDB() {
		if (!log.isDebugEnabled()) {
			return;
		}
		withDbAccess(() -> {
			try {
				jdbc().query(DaoSqlStatements.SQL_SELECT_ALL_BOOKINGS, null, resultSet -> {
					while (resultSet.next()) {
						log.debug("id = {}, account_id = {}, dateBooking = {}, dateValue = {}, purpose = {}, amount = {}, typ = {}, crossAccount_id = {}",
								resultSet.getInt("id"),
								resultSet.getInt(SqlFields.ACCOUNT_ACCOUNTID),
								resultSet.getString(SqlFields.BOOKING_DATEBOOKING),
								resultSet.getString("dateValue"),
								SensitiveDataMasker.describeText(resultSet.getString(SqlFields.BOOKING_PURPOSE)),
								SensitiveDataMasker.describeAmount(resultSet.getBigDecimal(SqlFields.BOOKING_AMOUNT)),
								resultSet.getString(SqlFields.BOOKING_BOOKINGTYPE),
								resultSet.getInt("crossAccount_id"));
					}
					return null;
				});
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
			}
		});
	}
	
	private BankAccess getBankAccessByField(String sql, Object value) {
		return withDbAccess(() -> {
			AbstractDaoMapper<BankAccess, ?> mapper = StatementsConfig.getMapperForDaoType(BankAccess.class);
			try {
				return jdbc().query(sql,
						statement -> statement.setObject(1, value),
						resultSet -> resultSet.next()
								? mapper.map(resultSet, ResultType.WITHOUT_RELATIONS)
								: null);
			} catch (SQLException | RuntimeException exception) {
				throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
			}
		});
	}
}
