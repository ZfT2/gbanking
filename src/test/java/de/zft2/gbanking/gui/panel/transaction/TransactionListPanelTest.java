package de.zft2.gbanking.gui.panel.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.panel.overview.AllTransactionsOverviewPanel;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * Tests the transaction count of the transaction list panel.
 */
class TransactionListPanelTest {

	private static Path tempDir;

	@BeforeAll
	static void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
	}

	@AfterAll
	static void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void shouldShowTransactionCountOfAllTransactions() {
		JavaFxTestSupport.runFx(() -> {
			TransactionListPanel listPanel = createListPanel();
			listPanel.updateModelBooking(List.of(createBooking("Miete"), createBooking("Tafel")));

			assertEquals(expectedCount("UI_PANEL_TRANSACTIONS_COUNT", 2), transactionCountLabel(listPanel).getText());
		});
	}

	@Test
	void shouldMarkTransactionCountAsFilteredWhenSearchIsUsed() {
		JavaFxTestSupport.runFx(() -> {
			TransactionListPanel listPanel = createListPanel();
			listPanel.updateModelBooking(List.of(createBooking("Miete"), createBooking("Tafel")));
			Label countLabel = transactionCountLabel(listPanel);

			filterText(listPanel).setText("miete");

			assertEquals(expectedCount("UI_PANEL_TRANSACTIONS_COUNT_FILTERED", 1), countLabel.getText());
		});
	}

	private static TransactionListPanel createListPanel() {
		AllTransactionsOverviewPanel overviewPanel = new AllTransactionsOverviewPanel();
		layoutInScene(overviewPanel, 1200);
		return overviewPanel.getTransactionListPanel();
	}

	private static void layoutInScene(javafx.scene.layout.Region root, double width) {
		Scene scene = new Scene(root, width, 500);
		scene.getRoot().applyCss();
		scene.getRoot().layout();
	}

	private static Booking createBooking(String purpose) {
		Booking booking = new Booking();
		booking.setId(1);
		booking.setAccountId(1);
		booking.setDateBooking(LocalDate.of(2026, 1, 15));
		booking.setPurpose(purpose);
		booking.setAmount(new BigDecimal("100.00"));
		return booking;
	}

	private static String expectedCount(String messageKey, int count) {
		return BaseMessages.messages.getFormattedMessage(messageKey, count);
	}

	private static Label transactionCountLabel(TransactionListPanel listPanel) {
		HBox titleRow = (HBox) listPanel.getTop();
		return (Label) titleRow.getChildren().get(1);
	}

	private static TextField filterText(TransactionListPanel listPanel) {
		// The search field is the first text field of the panel, the further ones belong to the
		// advanced filter section.
		return (TextField) listPanel.lookupAll(".text-field").iterator().next();
	}
}
