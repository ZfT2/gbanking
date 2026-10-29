package de.zft2.gbanking.gui.panel.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AllTransactionsOverviewPanel;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Tests the transaction count of the transaction list panel.
 */
class TransactionListPanelTest {

	private static final double NARROW_WINDOW_WIDTH = 700;
	private static final double SCENE_HEIGHT = 500;

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
	void shouldMarkTransactionCountAsFilteredAfterSearchDelay() throws InterruptedException {
		String filteredCount = expectedCount("UI_PANEL_TRANSACTIONS_COUNT_FILTERED", 1);
		CountDownLatch filtered = new CountDownLatch(1);
		JavaFxTestSupport.runFx(() -> {
			TransactionListPanel listPanel = createListPanel();
			listPanel.updateModelBooking(List.of(createBooking("Miete"), createBooking("Tafel")));
			Label countLabel = transactionCountLabel(listPanel);
			countLabel.textProperty().addListener((obs, oldText, newText) -> {
				if (filteredCount.equals(newText)) {
					filtered.countDown();
				}
			});

			filterText(listPanel).setText("miete");

			// The search is debounced, so the count only changes after the typing pause.
			assertEquals(expectedCount("UI_PANEL_TRANSACTIONS_COUNT", 2), countLabel.getText());
		});

		assertTrue(filtered.await(5, TimeUnit.SECONDS));
	}

	@Test
	void shouldKeepTransactionCountVisibleInNarrowWindow() {
		JavaFxTestSupport.runFx(() -> {
			AllTransactionsOverviewPanel overviewPanel = new AllTransactionsOverviewPanel();
			overviewPanel.getTransactionListPanel().updateModelBooking(List.of(createBooking("Miete")));

			Scene scene = layoutInScene(overviewPanel, NARROW_WINDOW_WIDTH);

			assertCountIsVisible(overviewPanel.getTransactionListPanel(), scene);
		});
	}

	@Test
	void shouldKeepTransactionCountVisibleInNarrowWindowWithSelectedBooking() {
		JavaFxTestSupport.runFx(() -> {
			AccountsTransactionsOverviewPanel overviewPanel = new AccountsTransactionsOverviewPanel();
			overviewPanel.getTransactionListPanel().updateModelBooking(List.of(createBooking("Miete")));
			overviewPanel.enableTransactionDetailPanel();

			Scene scene = layoutInScene(overviewPanel, NARROW_WINDOW_WIDTH);

			assertCountIsVisible(overviewPanel.getTransactionListPanel(), scene);
		});
	}

	/**
	 * Asserts that the transaction count is still inside the visible area of the window. The
	 * count is aligned to the right edge of the panel, so it leaves the visible area as soon as
	 * the panel needs more space than the window offers.
	 */
	private static void assertCountIsVisible(TransactionListPanel listPanel, Scene scene) {
		Label countLabel = transactionCountLabel(listPanel);
		Bounds countBounds = countLabel.localToScene(countLabel.getBoundsInLocal());
		assertTrue(countBounds.getMaxX() <= scene.getWidth(),
				"Transaction count ends at " + countBounds.getMaxX() + " but the window is only " + scene.getWidth() + " wide");
	}

	private static TransactionListPanel createListPanel() {
		AllTransactionsOverviewPanel overviewPanel = new AllTransactionsOverviewPanel();
		layoutInScene(overviewPanel, 1200);
		return overviewPanel.getTransactionListPanel();
	}

	private static Scene layoutInScene(Region root, double width) {
		Scene scene = new Scene(root, width, SCENE_HEIGHT);
		scene.getRoot().applyCss();
		scene.getRoot().layout();
		return scene;
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
