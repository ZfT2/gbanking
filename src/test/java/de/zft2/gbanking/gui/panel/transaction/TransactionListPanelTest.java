package de.zft2.gbanking.gui.panel.transaction;

import static de.zft2.gbanking.service.ServiceStubbingUtil.createWithServices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AllTransactionsOverviewPanel;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.booking.BookingCategoryService;
import de.zft2.gbanking.service.booking.BookingService;
import de.zft2.gbanking.service.booking.BookingSplitService;
import de.zft2.gbanking.testdata.TestDataFactory;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Tests the transaction count of the transaction list panel.
 */
class TransactionListPanelTest {

	@AfterEach
	void clearDependencyServices() {
		ServiceRegistry.resetServices();
	}

	private static final double NARROW_WINDOW_WIDTH = 700;
	private static final double SCENE_HEIGHT = 500;

	@RegisterExtension
	static final TestDatabaseExtension database = TestDatabaseExtension.withoutClearing();

	@Test
	void depotCounterAccountLinkShouldNavigateToThePortfolioTransaction() {
		var depot = TestDataFactory.createSampleAccount(null);
		depot.setAccountType(AccountType.DEPOT);
		database.controller().insertOrUpdate(depot);
		JavaFxTestSupport.runFx(() -> {
			AccountsTransactionsOverviewPanel overview = new AccountsTransactionsOverviewPanel();
			TransactionListPanel panel = overview.getTransactionListPanel();
			Booking booking = createBooking("Wertpapierkauf");
			booking.setCrossAccountId(depot.getId());
			booking.setCrossBookingId(42);
			booking.setCrossAccountName("Depot");
			panel.updateModelBooking(List.of(booking));
			layoutInScene(overview, 1600);
			AtomicReference<Booking> navigated = new AtomicReference<>();
			GuiContext.setStockTransactionNavigationHandler(item -> navigated.set(item));
			try {
				Hyperlink link = panel.lookupAll(".hyperlink").stream().filter(Hyperlink.class::isInstance)
						.map(Hyperlink.class::cast).filter(item -> "Depot".equals(item.getText())).findFirst().orElseThrow();
				link.fire();
				assertEquals(booking, navigated.get());
			} finally {
				GuiContext.setStockTransactionNavigationHandler(null);
			}
		});
	}

	@Test
	void returningFromPortfolioShouldRevealTheAccountBookingDespiteFiltersAndStatementsTab() {
		var account = database.controller().insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Booking booking = database.controller().insertOrUpdate(TestDataFactory.createSampleBooking(account.getId()));
		JavaFxTestSupport.runFx(() -> {
			AccountsTransactionsOverviewPanel overview = new AccountsTransactionsOverviewPanel();
			layoutInScene(overview, 1200);
			TabPane tabs = (TabPane) overview.lookup(".tab-pane");
			tabs.getSelectionModel().select(1);
			TextField accountFilter = (TextField) overview.getAccountListPanel().lookup(".text-field");
			accountFilter.setText("No matching account");
			TransactionListPanel transactions = overview.getTransactionListPanel();
			filterText(transactions).setText("No matching booking");

			assertTrue(overview.selectBooking(account, booking.getId()));

			assertEquals(account.getId(), overview.getSelectedAccount().getId());
			assertEquals(booking.getId(), transactions.getSelectedItem().getId());
			assertEquals(0, tabs.getSelectionModel().getSelectedIndex());
			assertEquals("", accountFilter.getText());
			assertEquals("", filterText(transactions).getText());
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void clickingSelectedAccountShouldRestoreItsDetailsOnlyWhenChangesCanBeDiscarded(boolean discardAllowed) {
		var account = database.controller().insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Booking booking = database.controller().insertOrUpdate(TestDataFactory.createSampleBooking(account.getId()));
		JavaFxTestSupport.runFx(() -> {
			AtomicBoolean allowSelection = new AtomicBoolean(true);
			AccountsTransactionsOverviewPanel overview = new AccountsTransactionsOverviewPanel() {
				@Override
				public boolean canChangeAccountSelection() {
					return allowSelection.get() && super.canChangeAccountSelection();
				}
			};
			layoutInScene(overview, 1200);
			assertTrue(overview.selectBooking(account, booking.getId()));
			overview.applyCss();
			overview.layout();
			TransactionListPanel transactions = overview.getTransactionListPanel();
			DetailListPane content = (DetailListPane) transactions.getParent();
			List<Booking> rows = List.copyOf(transactions.getMasterData());
			Booking selected = transactions.getSelectedItem();
			TableRow<?> accountRow = overview.getAccountListPanel().lookupAll(".table-row-cell").stream()
					.filter(node -> node instanceof TableRow<?> row && row.getItem() instanceof BankAccount item
							&& item.getId() == account.getId())
					.map(node -> (TableRow<?>) node).findFirst().orElseThrow();
			allowSelection.set(discardAllowed);
			accountRow.fireEvent(accountPress(MouseButton.SECONDARY));
			assertSame(overview.getTransactionDetailPanel(), content.getTop());
			accountRow.fireEvent(accountPress(MouseButton.PRIMARY));
			assertEquals(rows, transactions.getMasterData());
			assertEquals(account.getId(), overview.getSelectedAccount().getId());
			if (discardAllowed) {
				assertSame(overview.getAccountDetailPanel(), content.getTop());
				assertNull(transactions.getSelectedItem());
				assertTrue(transactions.selectBookingById(booking.getId()));
				assertSame(overview.getTransactionDetailPanel(), content.getTop());
			} else {
				assertSame(overview.getTransactionDetailPanel(), content.getTop());
				assertSame(selected, transactions.getSelectedItem());
			}
		});
	}

	private static MouseEvent accountPress(MouseButton button) {
		return new MouseEvent(MouseEvent.MOUSE_PRESSED, 10, 25, 10, 25, button,
				1, false, false, false, false, button == MouseButton.PRIMARY, false,
				button == MouseButton.SECONDARY, false, false, true, null);
	}

	@Test
	void shouldShowTransactionCountOfAllTransactions() {
		JavaFxTestSupport.runFx(() -> {
			TransactionListPanel listPanel = createListPanel();
			listPanel.updateModelBooking(List.of(createBooking("Miete"), createBooking("Tafel")));

			assertEquals(expectedCount("UI_PANEL_TRANSACTIONS_COUNT", 2), transactionCountLabel(listPanel).getText());
		});
	}

	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false" })
	void deletionFailureShouldShowWarningWithoutReloading(boolean singleDelete, boolean deleteFromDate) {
		JavaFxTestSupport.runFx(() -> {
			TransactionListPanel panel = mock(TransactionListPanel.class);
			Booking booking = createBooking("Depot");
			booking.setSource(singleDelete ? Source.MANUELL : Source.IMPORT);
			when(panel.getSelectedItem()).thenReturn(booking);
			when(panel.getMasterData()).thenReturn(FXCollections.observableArrayList(booking));
			BookingService bookings = mock(BookingService.class);
			BookingSplitService splits = mock(BookingSplitService.class);
			String message = BaseMessages.getTextStatic("ALERT_BOOKING_DELETE_CORPORATE_ACTION");
			when(bookings.deleteBookingsInBlock(booking, deleteFromDate)).thenThrow(new GBankingException(message));
			when(splits.deleteBookingWithSplits(booking)).thenThrow(new GBankingException(message));
			TransactionListPanelContextMenu menu = createWithServices(() -> new TransactionListPanelContextMenu(panel),
					mock(BankingCapabilityService.class), bookings, mock(BookingCategoryService.class), splits,
					mock(AccountTransactionService.class));
			try (var dialogs = mockStatic(DialogWindowSupport.class)) {
				dialogs.when(() -> DialogWindowSupport.showConfirmation(any(), eq(Alert.AlertType.CONFIRMATION),
						anyString(), anyString(), anyString(), eq(ButtonType.OK), eq(ButtonType.CANCEL))).thenReturn(true);
				if (singleDelete) {
					menu.deleteBookingItem.fire();
				} else {
					(deleteFromDate ? menu.deleteFromDateItem : menu.deleteUntilDateItem).fire();
				}
				dialogs.verify(() -> DialogWindowSupport.showAlert(null, Alert.AlertType.WARNING, message));
				verify(panel, never()).reload();
			}
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

			filterText(listPanel).setText("miete 15.01.26");

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
		booking.setDateBooking(LocalDate.of(2026, Month.JANUARY, 15));
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
