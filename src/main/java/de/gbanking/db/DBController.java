package de.gbanking.db;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.Dao;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.logic.StatementsLogic;
import de.gbanking.db.dao.mapper.StatementsResultMapper;

public class DBController extends DbExecutor {

	private static Logger log = LogManager.getLogger(DBController.class);

	private static final DBController dbcontroller = new DBController();

	public static DBController getInstance(String dbFilePath) {
		String resolvedDbFilePath = DbRuntimeContext.resolveDbDirectory(dbFilePath);
		DbConnectionHandler.getInstance(resolvedDbFilePath);

		return dbcontroller;
	}

	public static void resetConnection() {
		DbConnectionHandler.resetConnection();
	}
	
	public boolean updateBookingsWithRecipients(Map<Recipient, Set<Integer>> recipientBookingMap) {
		
		return updateDaoListWithDetailIdList(recipientBookingMap, DaoSqlStatements.SQL_UPDATE_BOOKINGS_RECIPIENT);
	}
	
	public boolean updateBookingsWithCategories(Map<Category, Set<Integer>> categoryBookingMap) {
		
		return updateDaoListWithDetailIdList(categoryBookingMap, DaoSqlStatements.SQL_UPDATE_BOOKINGS_CATEGORY);
	}
	
	public boolean insertOrUpdatePD(BankAccess bankAccess) {

		boolean result = true;
		
		StatementsLogic<BankAccess, ?> logic = StatementsConfig.getLogicForDaoType(BankAccess.class);
		result = logic.updateSpecific(bankAccess);

		return result;
	}
	
	public boolean insertBusinessCases(BankAccount bankAccount) {
		StatementsLogic<BankAccount, ?> logic = StatementsConfig.getLogicForDaoType(bankAccount.getClass());
		return logic.insertSpecific(bankAccount);
	}
	
	public boolean insertAccountBookings(Collection<Booking> bookingList) {
		if (bookingList == null || bookingList.isEmpty()) {
			return false;
		}

		Set<Booking> bookingListDb = insertAll(new HashSet<>(bookingList));
		return bookingListDb.stream().allMatch(booking -> booking.getId() > 0);
	}
	
	public Booking findCrossBooking(Booking booking) {
		if (booking == null || booking.getRecipient() == null) {
			return null;
		}

		Booking crossBooking = null;

		try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_FIND_CROSS_BOOKINGS_FULL)) {
			ps.setString(1, booking.getRecipient().getIban());
			ps.setString(2, booking.getRecipient().getAccountNumber());
			ps.setBigDecimal(3, booking.getAmount().negate());
			ps.setDate(4, java.sql.Date.valueOf(booking.getDateBooking()));

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.isBeforeFirst()) {
					return null;
				}

				while (rs.next()) {
					crossBooking = (Booking) StatementsResultMapper.toDao(Booking.class, rs, ResultType.FULL);
				}
			}
		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_FIND), e);
		}

		return crossBooking;
	}

	public Map<String, Integer> getAccountsIdsByAccountName() {

		StatementsLogic<BankAccount, ?> logic = StatementsConfig.getLogicForDaoType(BankAccount.class);
		return logic.getTableIds(BankAccount.class, SqlFields.ACCOUNT_ACCOUNTNAME, null);
	}
	
	public Map<String, Integer> getCrossAccountsIdsByIbanOrNumber() {

		StatementsLogic<BankAccount, ?> logic = StatementsConfig.getLogicForDaoType(BankAccount.class);
		return logic.getTableIds(BankAccount.class, "iban", "number");
	}
	
	public BankAccess getBankAccessById(int id) {
		return getBankAccessByField(DaoSqlStatements.SQL_SELECT_BANKACCESS_BY_ID, id);

	}
	
	public BankAccess getBankAccessByBlz(String blz) {
		return getBankAccessByField(DaoSqlStatements.SQL_SELECT_BANKACCESS_BY_BLZ, blz);
	}
	
	public <T extends Dao> Set<T> insertAll(Set<T> entityList) {

		if (entityList.isEmpty())
			return Collections.emptySet();

		StatementsLogic<T,?> logic = StatementsConfig.getLogicForDaoType(detectListType(entityList));
		return logic.insertAll(entityList);
	}
	
	public void printAccountsInDB() {

		try (Statement stmt = connection.createStatement()) {

			ResultSet rs = stmt.executeQuery(DaoSqlStatements.SQL_SELECT_ALL_BANKACCOUNTS);
			while (rs.next()) {
				if (log.isInfoEnabled())
					log.info("id = {}, accountName = {}, accountType = {}, iban = {}, bic = {}, number = {}, bankName = {}",
						rs.getInt("id"), rs.getString(SqlFields.ACCOUNT_ACCOUNTNAME), rs.getString("accountType"), rs.getString("iban"),
						rs.getString("bic"), rs.getString("number"), rs.getInt(SqlFields.BANKNAME));
			}
			rs.close();
		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), e);
		}
	}

	public void printBookingsInDB() {

		try (Statement stmt = connection.createStatement()) {

			ResultSet rs = stmt.executeQuery(DaoSqlStatements.SQL_SELECT_ALL_BOOKINGS);
			while (rs.next()) {
				if (log.isInfoEnabled())
					log.info("id = {}, account_id = {}, dateBooking = {}, dateValue = {}, purpose = {}, amount = {}, typ = {}, crossAccount_id = {}",
						rs.getInt("id"), rs.getInt(SqlFields.ACCOUNT_ACCOUNTID), rs.getString(SqlFields.BOOKING_DATEBOOKING),
						rs.getString("dateValue"), rs.getString(SqlFields.BOOKING_PURPOSE), rs.getDouble(SqlFields.BOOKING_AMOUNT), rs.getString(SqlFields.BOOKING_BOOKINGTYPE),
						rs.getInt("crossAccount_id"));
			}
			rs.close();
		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), e);
		}
	}
	
	private BankAccess getBankAccessByField(String sql, Object value) {
		BankAccess bankAccess = null;

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			ps.setObject(1, value);

			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				bankAccess = (BankAccess) StatementsResultMapper.toDao(BankAccess.class, rs, ResultType.WITHOUT_RELATIONS);
				break;
			}
			rs.close();
		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), e);
		}
		return bankAccess;
	}
}
