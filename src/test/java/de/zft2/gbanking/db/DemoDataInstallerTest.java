package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.demo.DemoDataInstaller;
import de.zft2.gbanking.service.BankingCapabilityService;

class DemoDataInstallerTest extends DBControllerIntegrationBaseTest {

	@Test
	void shouldInstallConsistentAndRepresentativeDemoData() throws SQLException {
		new DemoDataInstaller().install();

		assertEquals(4, countRows("bankAccount"));
		assertEquals(222, countRows("booking"));
		assertEquals(18, countRows("category"));
		assertEquals(7, countRows("moneytransfer"));
		assertEquals(2, db.getAll(CategoryRule.class).size());
		assertFalse(hasForeignKeyViolation());
		assertAdditionalCategoriesAreUsed();

		Booking salary = db.getByIdFull(Booking.class, 910001);
		assertEquals(LocalDate.of(2026, Month.JANUARY, 3), salary.getDateBooking());

		Booking pendingBooking = db.getByIdFull(Booking.class, 910025);
		assertEquals(Source.ONLINE_PRENO, pendingBooking.getSource());

		Booking reviewBooking = db.getByIdFull(Booking.class, 910014);
		assertNotNull(reviewBooking.getNoteDetails());
		assertTrue(reviewBooking.getNoteDetails().isReviewRequired());

		Booking outgoingRebooking = db.getByIdFull(Booking.class, 910010);
		Booking incomingRebooking = db.getByIdFull(Booking.class, 910011);
		assertEquals(incomingRebooking.getId(), outgoingRebooking.getCrossBookingId());
		assertEquals(outgoingRebooking.getId(), incomingRebooking.getCrossBookingId());
		assertEquals(2, db.getSplitBookings(910020).size());

		List<MoneyTransfer> transfers = db.getAllByParent(MoneyTransfer.class, 900001);
		assertEquals(7, transfers.size());
		MoneyTransfer scheduledTransfer = transfers.stream().filter(transfer -> transfer.getId() == 940004).findFirst().orElseThrow();
		assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 15), scheduledTransfer.getExecutionDate());
		assertEquals("DEMO-SCHEDULED-001", scheduledTransfer.getBankOrderId());
		MoneyTransferProtocol instantPaymentProtocol = db.getAllByParent(MoneyTransferProtocol.class, 940002).get(0);
		assertEquals(SepaOrderStatus.COMPLETED, instantPaymentProtocol.getSepaOrderStatus());
		assertEquals(1, countRows("moneytransferForeign"));
		assertEquals(6, countRows("moneytransferProtocol"));
	}

	private void assertAdditionalCategoriesAreUsed() throws SQLException {
		for (int categoryId = 920050; categoryId <= 920054; categoryId++) {
			assertEquals(40, countBookingsForCategory(categoryId));
		}

		assertEquals(LocalDate.of(2024, Month.MARCH, 1), db.getByIdFull(Booking.class, 950001).getDateBooking());
		assertEquals(LocalDate.of(2026, Month.MAY, 6), db.getByIdFull(Booking.class, 950200).getDateBooking());
	}

	@Test
	void shouldEnableSupportedBankingCapabilitiesWithCompactDemoParameters() {
		new DemoDataInstaller().install();
		BankAccount account = db.getByIdFull(BankAccount.class, 900001);
		BankingCapabilityService capabilityService = new BankingCapabilityService();

		for (OrderType orderType : OrderType.values()) {
			assertTrue(capabilityService.supportsTransferOrderType(account, orderType), orderType.name());
		}
		assertTrue(capabilityService.supportsAccountTransactions(account));
		assertTrue(capabilityService.supportsAccountStatements(account));
	}

	private int countRows(String tableName) throws SQLException {
		String sql = switch (tableName) {
		case "bankAccount" -> "SELECT COUNT(*) FROM bankAccount";
		case "booking" -> "SELECT COUNT(*) FROM booking";
		case "category" -> "SELECT COUNT(*) FROM category";
		case "moneytransfer" -> "SELECT COUNT(*) FROM moneytransfer";
		case "moneytransferForeign" -> "SELECT COUNT(*) FROM moneytransferForeign";
		case "moneytransferProtocol" -> "SELECT COUNT(*) FROM moneytransferProtocol";
		default -> throw new IllegalArgumentException("Unsupported demo table: " + tableName);
		};
		try (Statement statement = DBController.getConnection().createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			return resultSet.next() ? resultSet.getInt(1) : 0;
		}
	}

	private int countBookingsForCategory(int categoryId) throws SQLException {
		try (PreparedStatement statement = DBController.getConnection()
				.prepareStatement("SELECT COUNT(*) FROM booking WHERE category_id = ?")) {
			statement.setInt(1, categoryId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getInt(1) : 0;
			}
		}
	}

	private boolean hasForeignKeyViolation() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				ResultSet resultSet = statement.executeQuery("PRAGMA foreign_key_check")) {
			return resultSet.next();
		}
	}
}
