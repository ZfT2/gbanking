package de.zft2.gbanking.gui.panel.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

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
import de.zft2.gbanking.db.dao.BookingSepaDetails;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AllTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.TransactionsOverviewBasePanel;
import de.zft2.gbanking.mapper.BookingCurrencyMapper;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.testdata.TestDataFactory;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

class TransactionDetailPanelTest {

	private static final List<String> SECTION_KEYS = List.of("UI_PANEL_BOOKING_CURRENCY_DETAILS",
			"UI_PANEL_BOOKING_FEE_DETAILS", "UI_PANEL_SEPA_INFO");
	private static final List<String> SEPA_TEXT_KEYS = List.of("UI_LABEL_SEPA_CUSTOMER_REF", "UI_LABEL_SEPA_CREDITOR_ID",
			"UI_LABEL_SEPA_END_TO_END", "UI_LABEL_SEPA_MANDATE", "UI_LABEL_SEPA_PERSON_ID", "UI_LABEL_SEPA_PURPOSE");

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	@AfterEach
	void resetServices() {
		ServiceRegistry.resetServices();
	}

	@ParameterizedTest
	@CsvSource({ "false,,false", "true,,false", "false,0,false", "false,1.25,false", "false,,true",
			"true,1.25,true", "true,,true", "true,-1.25,false", "false,1.25,true" })
	void readonlySectionsShouldFollowActualDetails(boolean foreign, BigDecimal fee, boolean sepa) {
		JavaFxTestSupport.runFx(() -> {
			Booking booking = createBooking();
			if (foreign) {
				booking.setForeignCurrencyDetails(BookingCurrencyMapper.createForeignCurrencyDetails(
						booking.getAmount(), Currency.EUR, new BigDecimal("-10"), "USD", null));
			}
			booking.setFee(BookingCurrencyMapper.createFee(fee, null, Currency.EUR));
			BookingSepaDetails sepaDetails = new BookingSepaDetails();
			sepaDetails.setType(sepa ? SepaType.BANK_TRANSFER : null);
			booking.setSepaDetails(sepaDetails);
			TransactionDetailPanel panel = createPanel(booking);
			assertSections(panel, foreign, fee != null && fee.signum() != 0, sepa);
			VBox detailSections = (VBox) section(panel, SECTION_KEYS.get(0)).getParent();
			double heightWithDetails = detailSections.getHeight();
			panel.updatePanelFieldValues(createBooking());
			panel.layout();
			assertSections(panel, false, false, false);
			if (foreign || fee != null && fee.signum() != 0 || sepa) {
				assertTrue(heightWithDetails > detailSections.getHeight());
			}
			panel.clearDisplayedBooking();
			assertSections(panel, false, false, false);
		});
	}

	@Test
	void editingShouldRevealSectionsWithoutOpeningThemAndCancelShouldRestoreVisibility() {
		JavaFxTestSupport.runFx(() -> {
			Booking booking = createBooking();
			BookingSepaDetails details = new BookingSepaDetails();
			details.setCustomerRef("Reference");
			booking.setSepaDetails(details);
			database.controller().insertOrUpdate(booking);
			TransactionDetailPanel panel = createPanel(booking);
			TitledPane sepa = section(panel, SECTION_KEYS.get(2));
			TitledPane fees = section(panel, SECTION_KEYS.get(1));
			sepa.setExpanded(true);
			panel.startEditDisplayedBooking();
			assertSections(panel, true, true, true);
			assertTrue(sepa.isExpanded());
			assertFalse(fees.isExpanded());
			assertFalse(section(panel, SECTION_KEYS.get(0)).isExpanded());
			fees.setExpanded(true);
			actionButton(panel, 1).fire();
			assertSections(panel, false, false, true);
			panel.startEditDisplayedBooking();
			assertFalse(fees.isExpanded());
			actionButton(panel, 1).fire();
			panel.startNewManualBooking();
			assertSections(panel, true, true, true);
			actionButton(panel, 0).fire();
			assertSections(panel, false, false, false);
			booking.setSource(Source.ONLINE);
			panel.updatePanelFieldValues(booking);
			panel.startEditDisplayedBooking();
			assertSections(panel, false, false, true);
		});
	}

	@Test
	void savingShouldAllowEmptyFeesAndPersistOrRemoveOptionalDetails() {
		JavaFxTestSupport.runFx(() -> {
			try (var alerts = mockStatic(DialogWindowSupport.class)) {
				Booking booking = createBooking();
				TransactionDetailPanel panel = createPanel(booking);
				TitledPane fees = section(panel, SECTION_KEYS.get(1));
				TitledPane sepa = section(panel, SECTION_KEYS.get(2));
				TextField feeAmount = field(fees, "UI_LABEL_BOOKING_FEE", TextField.class);
				ComboBox<?> sepaType = field(sepa, "UI_LABEL_SEPA_TYPE", ComboBox.class);
				panel.startEditDisplayedBooking();
				assertEquals(Currency.EUR, field(fees, "UI_LABEL_FEE_CURRENCY", ComboBox.class).getValue());
				actionButton(panel, 0).fire();
				assertNull(database.controller().getByIdFull(Booking.class, booking.getId()).getFee());
				assertSections(panel, false, false, false);

				panel.startEditDisplayedBooking();
				feeAmount.setText("1,25");
				for (String key : SEPA_TEXT_KEYS) {
					field(sepa, key, TextField.class).setText("  " + key + "  ");
				}
				assertNull(sepaType.getItems().get(0));
				sepaType.getSelectionModel().select(sepaType.getItems().indexOf(SepaType.DIRECT_DEBIT));
				actionButton(panel, 0).fire();
				Booking saved = database.controller().getByIdFull(Booking.class, booking.getId());
				assertEquals(new BigDecimal("1.25"), saved.getFee().getAmount());
				assertEquals(Currency.EUR, saved.getFee().getCurrency());
				assertEquals(SepaType.DIRECT_DEBIT, saved.getSepaDetails().getType());
				assertEquals(SEPA_TEXT_KEYS, List.of(saved.getSepaCustomerRef(), saved.getSepaCreditorId(),
						saved.getSepaEndToEnd(), saved.getSepaMandate(), saved.getSepaPersonId(), saved.getSepaPurpose()));
				assertSections(panel, false, true, true);

				panel.startEditDisplayedBooking();
				feeAmount.clear();
				actionButton(panel, 0).fire();
				saved = database.controller().getByIdFull(Booking.class, booking.getId());
				assertNull(saved.getFee());
				assertEquals(SepaType.DIRECT_DEBIT, saved.getSepaDetails().getType());
				assertSections(panel, false, false, true);

				panel.startEditDisplayedBooking();
				for (String key : SEPA_TEXT_KEYS) {
					field(sepa, key, TextField.class).setText(" ");
				}
				sepaType.getSelectionModel().select(0);
				actionButton(panel, 0).fire();
				assertNull(database.controller().getByIdFull(Booking.class, booking.getId()).getSepaDetails());
				assertSections(panel, false, false, false);
				alerts.verifyNoInteractions();
			}
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void realOverviewShouldGrowDetailsAndScrollOnlyWhenSpaceRunsOut(boolean accountOverview) {
		JavaFxTestSupport.runFx(() -> {
			Booking booking = createBooking();
			TransactionsOverviewBasePanel overview = accountOverview
					? new AccountsTransactionsOverviewPanel() : new AllTransactionsOverviewPanel();
			TransactionDetailPanel panel = overview.getTransactionDetailPanel();
			panel.updatePanelFieldValues(booking);
			if (overview instanceof AccountsTransactionsOverviewPanel accounts) {
				accounts.enableTransactionDetailPanel();
			}
			new Scene(overview, 1400, 950);
			for (String key : SECTION_KEYS) {
				section(panel, key).setAnimated(false);
			}
			layoutOverview(overview, 950);
			double compactHeight = panel.getHeight();
			panel.startEditDisplayedBooking();
			layoutOverview(overview, 950);
			double editingHeight = panel.getHeight();
			assertTrue(editingHeight > compactHeight);
			for (String key : SECTION_KEYS) {
				section(panel, key).setExpanded(true);
			}
			layoutOverview(overview, 950);
			double expandedHeight = panel.getHeight();
			assertTrue(expandedHeight > editingHeight);
			ScrollPane scroll = detailScroll(panel);
			assertTrue(scroll.getViewportBounds().getHeight() >= scroll.getContent().getLayoutBounds().getHeight() - 1,
					"The expanded form must fit without scrolling in a tall overview");
			assertListFits(overview);

			layoutOverview(overview, 550);
			assertTrue(panel.getHeight() < expandedHeight, "Details must shrink when the overview becomes shorter");
			assertTrue(scroll.getViewportBounds().getHeight() < scroll.getContent().getLayoutBounds().getHeight());
			scroll.setVvalue(1);
			layoutOverview(overview, 550);
			assertPurposeFieldFitsViewport(panel, scroll);
			assertListFits(overview);

			layoutOverview(overview, 950);
			assertEquals(expandedHeight, panel.getHeight(), 1);
			assertTrue(scroll.getViewportBounds().getHeight() >= scroll.getContent().getLayoutBounds().getHeight() - 1);
			for (String key : SECTION_KEYS) {
				section(panel, key).setExpanded(false);
			}
			layoutOverview(overview, 950);
			assertEquals(editingHeight, panel.getHeight(), 1);
			try (var dialogs = mockStatic(DialogWindowSupport.class)) {
				actionButton(panel, accountOverview ? 0 : 1).fire();
				dialogs.verifyNoInteractions();
			}
			layoutOverview(overview, 950);
			assertSections(panel, false, false, false);
			assertEquals(compactHeight, panel.getHeight(), 1);

			TabPane tabs = (TabPane) ((VBox) panel.getCenter()).getChildren().get(0);
			tabs.getSelectionModel().select(1);
			layoutOverview(overview, 550);
			assertTrue(tabs.getTabs().get(1).getContent().isVisible());
			var splitChildren = ((VBox) tabs.getTabs().get(1).getContent()).getChildren();
			Node splitActions = splitChildren.get(splitChildren.size() - 1);
			assertTrue(splitActions.localToScene(splitActions.getLayoutBounds()).getMaxY()
					<= tabs.localToScene(tabs.getLayoutBounds()).getMaxY() + 1);
			assertListFits(overview);
			tabs.getSelectionModel().select(0);
			if (overview instanceof AccountsTransactionsOverviewPanel accounts) {
				accounts.enableAccountDetailPanel();
				layoutOverview(overview, 950);
				assertEquals(accounts.getAccountDetailPanel().prefHeight(accounts.getAccountDetailPanel().getWidth()),
						accounts.getAccountDetailPanel().getHeight(), 1);
				accounts.enableTransactionDetailPanel();
			}
			layoutOverview(overview, 950);
			assertEquals(compactHeight, panel.getHeight(), 1);
		});
	}

	private void assertPurposeFieldFitsViewport(TransactionDetailPanel panel, ScrollPane scroll) {
		Node viewport = scroll.lookup(".viewport");
		Bounds visible = viewport.localToScene(viewport.getLayoutBounds());
		VBox left = (VBox) detailColumns(panel).getChildren().get(0);
		Node purposeField = ((TitledPane) left.getChildren().get(1)).getContent();
		Bounds fieldBounds = purposeField.localToScene(purposeField.getBoundsInLocal());
		assertTrue(fieldBounds.getMinY() >= visible.getMinY() - 1);
		assertTrue(fieldBounds.getMaxY() <= visible.getMaxY() + 1);
	}

	private void layoutOverview(Region root, double height) {
		root.resize(1400, height);
		for (int pass = 0; pass < 3; pass++) {
			root.applyCss();
			root.layout();
		}
	}

	private void assertListFits(TransactionsOverviewBasePanel overview) {
		TransactionListPanel list = overview.getTransactionListPanel();
		assertTrue(list.getHeight() >= list.minHeight(list.getWidth()) - 1);
		assertTrue(list.localToScene(list.getLayoutBounds()).getMaxY()
				<= overview.localToScene(overview.getLayoutBounds()).getMaxY() + 1);
	}

	private Booking createBooking() {
		BankAccount account = database.controller().insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Booking booking = TestDataFactory.createSampleBooking(account.getId());
		booking.setSource(Source.MANUELL);
		booking.setAmount(new BigDecimal("-12.50"));
		return database.controller().insertOrUpdate(booking);
	}

	private TransactionDetailPanel createPanel(Booking booking) {
		TransactionsOverviewBasePanel parent = mock(TransactionsOverviewBasePanel.class);
		when(parent.getPageContext()).thenReturn(PageContext.ACCOUNTS_TRANSACTIONS);
		when(parent.getTransactionListPanel()).thenReturn(mock(TransactionListPanel.class));
		TransactionDetailPanel panel = new TransactionDetailPanel(parent);
		panel.updatePanelFieldValues(booking);
		for (String key : SECTION_KEYS) {
			section(panel, key).setAnimated(false);
		}
		new Scene(panel, 1400, 800);
		panel.applyCss();
		panel.layout();
		return panel;
	}

	private HBox detailColumns(TransactionDetailPanel panel) {
		return (HBox) detailScroll(panel).getContent();
	}

	private ScrollPane detailScroll(TransactionDetailPanel panel) {
		TabPane tabs = (TabPane) ((VBox) panel.getCenter()).getChildren().get(0);
		return (ScrollPane) tabs.getTabs().get(0).getContent();
	}

	private TitledPane section(TransactionDetailPanel panel, String key) {
		VBox left = (VBox) detailColumns(panel).getChildren().get(0);
		return ((VBox) left.getChildren().get(0)).getChildren().stream().filter(node -> node instanceof TitledPane)
				.map(node -> (TitledPane) node).filter(pane -> pane.getText().equals(BaseMessages.getTextStatic(key)))
				.findFirst().orElseThrow();
	}

	private <T extends Control> T field(TitledPane pane, String key, Class<T> type) {
		return ((GridPane) pane.getContent()).getChildren().stream().map(node -> (VBox) node)
				.filter(box -> ((Label) box.getChildren().get(0)).getText().equals(BaseMessages.getTextStatic(key)))
				.map(box -> type.cast(box.getChildren().get(1))).findFirst().orElseThrow();
	}

	private Button actionButton(TransactionDetailPanel panel, int index) {
		VBox right = (VBox) detailColumns(panel).getChildren().get(1);
		return (Button) ((HBox) right.getChildren().get(1)).getChildren().get(index);
	}

	private void assertSections(TransactionDetailPanel panel, boolean... visible) {
		for (int index = 0; index < visible.length; index++) {
			TitledPane pane = section(panel, SECTION_KEYS.get(index));
			assertTrue(pane.isCollapsible());
			assertEquals(visible[index], pane.isVisible());
			assertEquals(visible[index], pane.isManaged());
		}
	}
}
