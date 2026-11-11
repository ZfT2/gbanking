package de.zft2.gbanking.gui.panel.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.cache.InstituteLookupCache.InstituteLookupEntry;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.util.StandingOrderSchedule;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;

class MoneyTransferListPanelTest {

	@ParameterizedTest
	@EnumSource(value = OrderType.class, names = { "TRANSFER", "SCHEDULED_TRANSFER", "STANDING_ORDER", "FOREIGN_TRANSFER" })
	void allOrderTablesShouldCombineIbanAndBankOnTwoLines(OrderType orderType) {
		JavaFxTestSupport.runFx(() -> {
			for (boolean archive : new boolean[] { false, true }) {
				MoneyTransferListPanel panel = new MoneyTransferListPanel(orderType, mock(MoneyTransferDetailListTabPanel.class), archive);
				TableView<?> table = (TableView<?>) panel.getCenter();
				assertTrue(table.getColumns().stream().noneMatch(column -> panel.getText("UI_TABLE_BANK").equals(column.getText())));
				TableColumn<?, ?> column = table.getColumns().stream()
						.filter(candidate -> panel.getText("UI_TABLE_IBAN_BANK").equals(candidate.getText())).findFirst().orElseThrow();
				Recipient recipient = new Recipient();
				recipient.setIban("DE89370400440532013000");
				recipient.setBank("Test Bank");
				MoneyTransfer transfer = transfer(orderType, archive ? MoneyTransferStatus.SENT : MoneyTransferStatus.NEW);
				transfer.setRecipient(recipient);
				panel.updateModelMoneytransfer(List.of(transfer));
				Scene scene = new Scene(panel);
				scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
				panel.resize(1800, 300);
				panel.applyCss();
				panel.layout();
				assertEquals(recipient.getIban() + "\nTest Bank", column.getCellData(0));
				TableCell<?, ?> cell = table.lookupAll(".table-cell").stream().filter(node -> node instanceof TableCell<?, ?> candidate
						&& candidate.getTableColumn() == column && candidate.getIndex() == 0).map(node -> (TableCell<?, ?>) node)
						.findFirst().orElseThrow();
				assertEquals(column.getCellData(0), cell.getText());
				assertTrue(cell.prefHeight(-1) <= table.getFixedCellSize());
				assertTrue(panel.matchesFilter(transfer, "test bank"));
				try (MockedStatic<InstituteLookupCache> lookup = mockStatic(InstituteLookupCache.class)) {
					recipient.setBank(null);
					assertEquals(recipient.getIban(), column.getCellData(0));
					recipient.setIban(null);
					assertEquals("", column.getCellData(0));
				}
				recipient.setBank("Test Bank");
				assertEquals("\nTest Bank", column.getCellData(0));
				transfer.setRecipient(null);
				assertEquals("", column.getCellData(0));
				panel.updateModelMoneytransfer(List.of());
				panel.layout();
				assertNull(cell.getText());
			}
		});
	}

	@ParameterizedTest
	@CsvSource(value = {
			"NULL, NULL, DE89370400440532013000, NULL, Lookup Bank",
			"NULL, 37040044, NULL, TESTFRPPXXX, Lookup Bank",
			"'  ', NULL, FR7630006000011234567890189, TESTFRPPXXX, BIC Bank",
			"Saved Bank, NULL, DE89370400440532013000, TESTFRPPXXX, Saved Bank",
			"Saved Bank, NULL, DE89370400440532013000, NULL, Saved Bank",
			"NULL, NULL, DE12, NULL, NULL",
			"NULL, 12345678, DE89370400440532013000, TESTFRPPXXX, BLZ Bank",
			"NULL, NULL, DE89370400440532013000, TESTFRPPXXX, Lookup Bank",
			"NULL, 99999999, DE89370400440532013000, TESTFRPPXXX, BIC Bank"
	}, nullValues = "NULL")
	void formAndTableShouldResolveTheSameBankNameWithoutChangingRecipient(String bank, String blz,
			String iban, String bic, String expectedBank) {
		JavaFxTestSupport.runFx(() -> {
			try (MockedStatic<InstituteLookupCache> lookup = mockStatic(InstituteLookupCache.class)) {
				lookup.when(() -> InstituteLookupCache.extractGermanBlzFromIban(any())).thenCallRealMethod();
				lookup.when(() -> InstituteLookupCache.getEntriesForBankData(any(), any(), any())).thenCallRealMethod();
				lookup.when(() -> InstituteLookupCache.findBankNameForBankData(any(), any(), any())).thenCallRealMethod();
				lookup.when(() -> InstituteLookupCache.getEntriesForBankCode("37040044"))
						.thenReturn(List.of(new InstituteLookupEntry("Lookup Bank", "LOOKDEFFXXX")));
				lookup.when(() -> InstituteLookupCache.getEntriesForBankCode("12345678"))
						.thenReturn(List.of(new InstituteLookupEntry("BLZ Bank", "BLZSDEFFXXX")));
				lookup.when(() -> InstituteLookupCache.getEntriesForBankCode("TESTFRPPXXX"))
						.thenReturn(List.of(new InstituteLookupEntry("BIC Bank", "TESTFRPPXXX"),
								new InstituteLookupEntry("Alternative Bank", "TESTFRPPXXX")));
				MoneyTransferListPanel panel = new MoneyTransferListPanel(OrderType.TRANSFER, mock(MoneyTransferDetailListTabPanel.class), false);
				TableView<?> table = (TableView<?>) panel.getCenter();
				TableColumn<?, ?> column = table.getColumns().stream()
						.filter(candidate -> panel.getText("UI_TABLE_IBAN_BANK").equals(candidate.getText())).findFirst().orElseThrow();
				MoneyTransfer transfer = transfer(OrderType.TRANSFER, MoneyTransferStatus.NEW);
				Recipient recipient = new Recipient("Test Recipient", iban, bic, null, blz, bank, null);
				transfer.setRecipient(recipient);
				panel.updateModelMoneytransfer(List.of(transfer));
				assertEquals((iban == null ? "" : iban) + (expectedBank == null ? "" : "\n" + expectedBank), column.getCellData(0));
				assertEquals(expectedBank != null, panel.matchesFilter(transfer, "bank"));
				MoneyTransferInputPanel input = new MoneyTransferInputPanel(mock(MoneyTransferDetailListTabPanel.class));
				input.updatePanelFieldValues(recipient);
				assertEquals(expectedBank == null ? "" : expectedBank, input.bankNameLookupField.getSelectedBankName());
				if (bic != null) {
					assertEquals(bic, input.tfBIC.getText());
				}
				if ("BIC Bank".equals(expectedBank)) {
					ComboBox<?> choices = (ComboBox<?>) input.bankNameLookupField.lookup(".combo-box");
					assertTrue(choices.isVisible());
					assertEquals(2, choices.getItems().size());
					choices.getSelectionModel().selectLast();
					assertEquals("Alternative Bank", input.bankNameLookupField.getSelectedBankName());
					assertEquals(bic, input.tfBIC.getText());
				}
				assertEquals(bank, recipient.getBank());
				assertEquals(bic, recipient.getBic());
				assertEquals(blz, recipient.getBlz());
				assertEquals(iban, recipient.getIban());
			}
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void standingOrderDatesShouldBeInChronologicalOrderAndFitTheirHeaders(boolean archive) {
		JavaFxTestSupport.runFx(() -> {
			Locale previous = Messages.getLocale();
			try {
				for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
					Messages.setLocale(locale);
					MoneyTransferListPanel panel = new MoneyTransferListPanel(OrderType.STANDING_ORDER,
							mock(MoneyTransferDetailListTabPanel.class), archive);
					TableView<?> table = (TableView<?>) panel.getCenter();
					MoneyTransfer transfer = transfer(OrderType.STANDING_ORDER, archive ? MoneyTransferStatus.SENT : MoneyTransferStatus.NEW);
					transfer.setExecutionDate(LocalDate.of(2024, 1, 15));
					panel.updateModelMoneytransfer(List.of(transfer));
					Scene scene = new Scene(panel);
					scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
					panel.resize(1800, 300);
					assertEquals(panel.getText("UI_TABLE_FIRST_EXECUTION_DATE"), table.getColumns().get(1).getText());
					assertEquals(panel.getText("UI_TABLE_NEXT_EXECUTION_DATE"), table.getColumns().get(2).getText());
					assertEquals(transfer.getExecutionDate(), table.getColumns().get(1).getCellData(0));
					for (int fontSize : new int[] { 12, 16, 12 }) {
						panel.setStyle("-fx-font-size: " + fontSize + "px;");
						for (boolean sorted : new boolean[] { false, true }) {
							if (sorted) {
								sortDateColumns(table);
							} else {
								table.getSortOrder().clear();
							}
							panel.applyCss();
							panel.layout();
							for (int index : new int[] { 1, 2 }) {
								TableColumn<?, ?> column = table.getColumns().get(index);
								Label header = table.lookupAll(".column-header .label").stream()
										.filter(node -> node instanceof Label label && column.getText().equals(label.getText()))
										.map(node -> (Label) node).findFirst().orElseThrow();
								Text text = new Text(header.getText());
								text.setFont(header.getFont());
								double textWidth = text.getLayoutBounds().getWidth();
								assertTrue(header.getWidth() >= textWidth, header.getText() + ", font=" + fontSize
										+ ", column=" + column.getWidth() + ", label=" + header.getWidth() + ", text=" + textWidth);
								assertTrue(column.getWidth() <= Math.ceil(textWidth) + (sorted ? 24 : 8));
							}
						}
						panel.updateModelMoneytransfer(List.of());
					}
				}
			} finally {
				Messages.setLocale(previous);
			}
		});
	}

	private <T> void sortDateColumns(TableView<T> table) {
		table.getSortOrder().setAll(table.getColumns().subList(1, 3));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void nextExecutionShouldPreferBankDateAndMarkOnlyCalculatedOrPastDates(boolean archive) {
		JavaFxTestSupport.runFx(() -> {
			MoneyTransferListPanel panel = new MoneyTransferListPanel(OrderType.STANDING_ORDER,
					mock(MoneyTransferDetailListTabPanel.class), archive);
			TableView<?> table = (TableView<?>) panel.getCenter();
			TableColumn<?, ?> column = table.getColumns().get(2);
			Scene scene = new Scene(panel);
			scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
			panel.resize(1800, 300);
			LocalDate today = LocalDate.now(ZoneId.systemDefault());
			LocalDate calculatedDate = StandingOrderSchedule.nextExecutionDate(today.minusMonths(1), 15, StandingorderMode.MONTHLY, today);
			TableCell<?, ?> cell = null;
			for (LocalDate bankDate : Arrays.asList(null, calculatedDate, null, today.minusDays(1), today, today.plusDays(2), null)) {
				MoneyTransfer transfer = transfer(OrderType.STANDING_ORDER, archive ? MoneyTransferStatus.SENT : MoneyTransferStatus.NEW);
				transfer.setExecutionDate(today.minusMonths(1));
				transfer.setExecutionDay(15);
				transfer.setStandingorderMode(StandingorderMode.MONTHLY);
				transfer.setExecutionDateNext(bankDate);
				panel.updateModelMoneytransfer(List.of(transfer));
				panel.applyCss();
				panel.layout();
				LocalDate expected = bankDate != null ? bankDate
						: StandingOrderSchedule.nextExecutionDate(transfer.getExecutionDate(), 15, StandingorderMode.MONTHLY, today);
				boolean marked = bankDate == null || bankDate.isBefore(today);
				assertEquals(expected, column.getCellData(0));
				cell = table.lookupAll(".table-cell").stream().filter(node -> node instanceof TableCell<?, ?> candidate
						&& candidate.getTableColumn() == column && candidate.getIndex() == 0).map(node -> (TableCell<?, ?>) node)
						.findFirst().orElseThrow();
				assertEquals(DateFormatUtils.formatShort(expected) + (marked ? "*" : ""), cell.getText());
				assertEquals(marked, cell.getFont().getStyle().contains("Italic"));
				if (marked) {
					assertEquals(panel.getText(bankDate == null ? "UI_TOOLTIP_STANDING_ORDER_DATE_CALCULATED"
							: "UI_TOOLTIP_STANDING_ORDER_DATE_STALE"), cell.getTooltip().getText());
				} else {
					assertNull(cell.getTooltip());
				}
			}
			panel.updateModelMoneytransfer(List.of());
			panel.applyCss();
			panel.layout();
			assertNull(cell.getText());
			assertNull(cell.getTooltip());
			assertFalse(cell.getFont().getStyle().contains("Italic"));
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void transferListShouldShowAllTransferTypesRegardlessOfFormSelection(boolean archive) {
		JavaFxTestSupport.runFx(() -> {
			MoneyTransferDetailListTabPanel parent = mock(MoneyTransferDetailListTabPanel.class);
			MoneyTransferInputBasePanel input = mock(MoneyTransferInputBasePanel.class);
			when(parent.getMoneyTransferInputPanel()).thenReturn(input);
			MoneyTransferListPanel panel = new MoneyTransferListPanel(OrderType.TRANSFER, parent, archive);
			List<MoneyTransfer> transfers = new ArrayList<>();
			for (OrderType type : OrderType.values()) {
				transfers.add(transfer(type, MoneyTransferStatus.NEW));
				transfers.add(transfer(type, MoneyTransferStatus.SENT));
			}
			transfers.add(transfer(null, MoneyTransferStatus.NEW));
			List<MoneyTransfer> expected = transfers.stream()
					.filter(transfer -> transfer.getOrderType() == OrderType.TRANSFER || transfer.getOrderType() == OrderType.REALTIME_TRANSFER
							|| transfer.getOrderType() == OrderType.URGENT_TRANSFER)
					.filter(transfer -> transfer.getMoneytransferStatus().isArchiveStatus() == archive).toList();
			TableView<?> table = (TableView<?>) panel.getCenter();
			for (OrderType selectedType : MoneyTransferInputPanel.getTransferOrderTypes()) {
				when(input.getOrderType()).thenReturn(selectedType);
				panel.updateModelMoneytransfer(transfers);
				assertEquals(expected, table.getItems());
			}
			assertEquals(panel.getText("UI_TABLE_DATE"), table.getColumns().get(1).getText());
			assertEquals(panel.getText("UI_TABLE_TRANSFER_TYPE"), table.getColumns().get(2).getText());
			assertEquals(panel.getText("UI_TABLE_RECIPIENT"), table.getColumns().get(3).getText());
			for (int index = 0; index < expected.size(); index++) {
				assertEquals(expected.get(index).getOrderType().toString(), table.getColumns().get(2).getCellData(index));
			}
			MoneyTransfer instant = transfer(OrderType.REALTIME_TRANSFER, MoneyTransferStatus.NEW);
			assertTrue(panel.matchesFilter(instant, OrderType.REALTIME_TRANSFER.toString().toLowerCase(Locale.ROOT)));
			verify(parent, never()).getMoneyTransferInputPanel();
		});
	}

	@ParameterizedTest
	@EnumSource(value = OrderType.class, names = { "SCHEDULED_TRANSFER", "STANDING_ORDER", "FOREIGN_TRANSFER" })
	void otherTabsShouldKeepTheirOwnOrdersAndHidePendingChangePredecessors(OrderType orderType) {
		JavaFxTestSupport.runFx(() -> {
			MoneyTransferListPanel panel = new MoneyTransferListPanel(orderType, mock(MoneyTransferDetailListTabPanel.class), false);
			MoneyTransfer predecessor = transfer(orderType, MoneyTransferStatus.INVENTORY);
			predecessor.setId(1);
			MoneyTransfer changed = transfer(orderType, MoneyTransferStatus.CHANGED);
			changed.setId(2);
			changed.setHistoryorderId(predecessor.getId());

			panel.updateModelMoneytransfer(List.of(predecessor, changed, transfer(OrderType.TRANSFER, MoneyTransferStatus.NEW),
					transfer(orderType, MoneyTransferStatus.SENT)));

			TableView<?> table = (TableView<?>) panel.getCenter();
			assertEquals(List.of(changed), table.getItems());
			assertTrue(table.getColumns().stream().noneMatch(column -> panel.getText("UI_TABLE_TRANSFER_TYPE").equals(column.getText())));
		});
	}

	@Test
	void changingFormTypeShouldNotReloadOrAccessTheTransferList() {
		JavaFxTestSupport.runFx(() -> {
			MoneyTransferDetailListTabPanel parent = mock(MoneyTransferDetailListTabPanel.class);
			MoneyTransferInputPanel input = new MoneyTransferInputPanel(parent);
			for (OrderType type : MoneyTransferInputPanel.getTransferOrderTypes()) {
				input.updateSpecificFieldValues(transfer(type, MoneyTransferStatus.NEW));
				assertEquals(type, input.getOrderType());
			}
			verify(parent, never()).getMoneyTransferListPanel();
		});
	}

	@Test
	void archiveStatusShouldContainCompletedAndHistoricalOrders() {
		assertTrue(MoneyTransferStatus.SENT.isArchiveStatus());
		assertTrue(MoneyTransferStatus.DELETED.isArchiveStatus());
		assertTrue(MoneyTransferStatus.SUPERSEDED.isArchiveStatus());
		assertTrue(MoneyTransferStatus.NOT_IN_BANK_INVENTORY.isArchiveStatus());

		assertFalse(MoneyTransferStatus.NEW.isArchiveStatus());
		assertFalse(MoneyTransferStatus.CHANGED.isArchiveStatus());
		assertFalse(MoneyTransferStatus.ERROR.isArchiveStatus());
		assertFalse(MoneyTransferStatus.INVENTORY.isArchiveStatus());
		assertFalse(MoneyTransferStatus.DELETE_PENDING.isArchiveStatus());
		assertFalse(MoneyTransferStatus.UNCERTAIN.isArchiveStatus());
	}

	@Test
	void instantPaymentStatusQueryShouldRequireOnlyInstantTransfers() {
		MoneyTransfer instantTransfer = new MoneyTransfer();
		instantTransfer.setOrderType(OrderType.REALTIME_TRANSFER);
		MoneyTransfer regularTransfer = new MoneyTransfer();
		regularTransfer.setOrderType(OrderType.TRANSFER);

		assertTrue(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of(instantTransfer)));
		assertTrue(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of(instantTransfer, instantTransfer)));
		assertFalse(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of(instantTransfer, regularTransfer)));
		assertFalse(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of()));
	}

	private MoneyTransfer transfer(OrderType type, MoneyTransferStatus status) {
		MoneyTransfer transfer = new MoneyTransfer();
		transfer.setOrderType(type);
		transfer.setMoneytransferStatus(status);
		return transfer;
	}
}
