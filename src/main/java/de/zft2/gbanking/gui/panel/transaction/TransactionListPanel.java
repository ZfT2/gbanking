package de.zft2.gbanking.gui.panel.transaction;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.TransactionsOverviewBasePanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.geometry.Pos;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class TransactionListPanel extends AbstractFilterableTablePanel<Booking> {

	private static final Logger log = LogManager.getLogger(TransactionListPanel.class);

	private final TransactionsOverviewBasePanel parentPanel;
	private final Label saldoValueLabel = new Label();
	private final DecimalFormat amountFormat = FxTableUtils.createGermanDecimalFormat();
	private TableColumn<Booking, Boolean> selectedCol;
	private TableColumn<Booking, Booking> dateCol;
	private TableColumn<Booking, String> purposeCol;
	private TableColumn<Booking, BigDecimal> amountCol;

	public TransactionListPanel(TransactionsOverviewBasePanel parent) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parent;
		createInnerTransactionsPanel();
	}

	private void createInnerTransactionsPanel() {
		setPanelTitleByKey("UI_PANEL_TRANSACTIONS");
		setColumns(createColumns());
		configureFooter();
		configureContextMenu();
		tableView.setFixedCellSize(70);
		onSelection(this::handleBookingSelection);
		filteredData.addListener((ListChangeListener<Booking>) change -> updateSaldoLabel());
		filterText.textProperty().addListener((obs, oldValue, newValue) -> tableView.refresh());
		updateSaldoLabel();

		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			replaceItems(bean.getAllBookings());
		}
	}

	private void configureFooter() {
		filterText.setPrefWidth(360);
		filterText.setMinWidth(320);
		filterText.setMaxWidth(420);

		Label searchLabel = new Label(getText("UI_LABEL_SEARCH"));
		HBox searchBox = new HBox(10, searchLabel, filterText);
		searchBox.setAlignment(Pos.CENTER_LEFT);

		GridPane footer = new GridPane();
		bindFooterColumnWidth(footer, selectedCol);
		bindFooterColumnWidth(footer, dateCol);
		bindFooterColumnWidth(footer, purposeCol);
		bindFooterColumnWidth(footer, amountCol);
		footer.add(searchBox, 0, 0, 3, 1);
		footer.add(saldoValueLabel, 3, 0);
		saldoValueLabel.setMinWidth(100);
		saldoValueLabel.setPrefWidth(110);
		saldoValueLabel.setMaxWidth(110);
		saldoValueLabel.setAlignment(Pos.CENTER_RIGHT);
		setBottom(footer);
	}

	private void configureContextMenu() {
		ContextMenu contextMenu = createContextMenu();
		tableView.setRowFactory(tv -> createContextMenuRow(contextMenu));
	}

	private List<TableColumn<Booking, ?>> createColumns() {
		selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), Booking::isSelected, Booking::setSelected);
		dateCol = new TableColumn<>(getText("UI_TABLE_DATE"));
		dateCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		dateCol.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
			@Override
			protected void updateItem(Booking item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : DateFormatUtils.formatBookingAndValue(item.getDateBooking(), item.getDateValue()));
			}
		});
		dateCol.setComparator((left, right) -> {
			int bookingDateCompare = java.util.Comparator.nullsLast(LocalDate::compareTo).compare(left != null ? left.getDateBooking() : null,
					right != null ? right.getDateBooking() : null);
			if (bookingDateCompare != 0) {
				return bookingDateCompare;
			}
			return java.util.Comparator.nullsLast(LocalDate::compareTo).compare(left != null ? left.getDateValue() : null,
					right != null ? right.getDateValue() : null);
		});
		FxTableUtils.setFixedWidth(dateCol, 115);
		purposeCol = TableColumnFactory.createWrappedTextColumn(getText("UI_TABLE_PURPOSE"), Booking::getPurpose, 320, 500);
		amountCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_AMOUNT"), Booking::getAmount, 110);
		TableColumn<Booking, Booking> balanceCol = new TableColumn<>(getText("UI_TABLE_BALANCE"));
		balanceCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		balanceCol.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
			private final Text textNode = createAmountText();
			private final HBox graphic = createAmountGraphic(textNode);

			@Override
			protected void updateItem(Booking item, boolean empty) {
				super.updateItem(item, empty);
				getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
				textNode.getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
				setAlignment(Pos.CENTER_RIGHT);

				BigDecimal balance = empty || item == null || !normalize(filterText.getText()).isBlank() ? null : item.getBalance();
				if (balance == null) {
					setText(null);
					setGraphic(null);
					setStyle(null);
					return;
				}

				textNode.setText(amountFormat.format(balance));
				applyAmountTextStyle(textNode, balance);
				setText(null);
				setGraphic(graphic);
				if (balance.signum() > 0) {
					getStyleClass().add("amount-positive");
					textNode.getStyleClass().add("amount-positive");
				} else if (balance.signum() < 0) {
					getStyleClass().add("amount-negative");
					textNode.getStyleClass().add("amount-negative");
				} else {
					getStyleClass().add("amount-neutral");
					textNode.getStyleClass().add("amount-neutral");
				}
			}

			@Override
			public void updateSelected(boolean selected) {
				super.updateSelected(selected);
				Booking booking = getItem();
				BigDecimal balance = booking == null || !normalize(filterText.getText()).isBlank() ? null : booking.getBalance();
				if (balance != null) {
					applyAmountTextStyle(textNode, balance);
				}
			}
		});
		FxTableUtils.setFixedWidth(balanceCol, 110);
		TableColumn<Booking, String> typeCol = TableColumnFactory.createSymbolColumn(getText("UI_TABLE_BOOKING_TYPE"),
				booking -> booking.getSource() != null ? booking.getSource().getSymbol() : "", 70);

		if (parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
			TableColumn<Booking, String> crossAccountCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_COUNTER_ACCOUNT"),
					Booking::getCrossAccountName, 160, 200);
			return List.of(selectedCol, dateCol, purposeCol, amountCol, balanceCol, typeCol, crossAccountCol);
		}

		TableColumn<Booking, String> accountCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT"), Booking::getAccountName, 160, 200);
		return List.of(selectedCol, dateCol, purposeCol, amountCol, balanceCol, typeCol, accountCol);
	}

	@Override
	protected boolean matchesFilter(Booking booking, String filter) {
		if (filter.isBlank()) {
			return true;
		}

		return contains(booking.getPurpose(), filter) || contains(booking.getCurrency(), filter) || matchesNumbers(booking, filter)
				|| matchesSymbols(booking, filter) || contains(booking.getCrossAccountName(), filter) || contains(booking.getAccountName(), filter);
	}

	private boolean matchesNumbers(Booking booking, String filter) {
		return (booking.getAmount() != null && booking.getAmount().toString().toLowerCase().contains(filter))
				|| (booking.getBalance() != null && booking.getBalance().toString().toLowerCase().contains(filter));
	}

	private boolean matchesSymbols(Booking booking, String filter) {
		return booking.getSource() != null && booking.getSource().getSymbol() != null && booking.getSource().getSymbol().toLowerCase().contains(filter);
	}

	private void handleBookingSelection(Booking booking) {
		log.log(Level.INFO, () -> getText("LOG_BOOKING_SELECTED", booking.getId()));

		Recipient recipient = dbController.getByIdFull(Recipient.class, booking.getRecipientId());
		booking.setRecipient(recipient);
		parentPanel.getTransactionDetailPanel().updatePanelFieldValues(booking);

		if (parentPanel instanceof AccountsTransactionsOverviewPanel parent) {
			parent.enableTransactionDetailPanel();
		}
	}

	private ContextMenu createContextMenu() {
		MenuItem newManualItem = new MenuItem(getText("UI_MENU_BOOKING_NEW_MANUAL"));
		MenuItem editManualItem = new MenuItem(getText("UI_MENU_BOOKING_EDIT_MANUAL"));
		MenuItem deleteFromDateItem = new MenuItem(getText("UI_MENU_BOOKING_DELETE_FROM_DATE"));
		MenuItem deleteUntilDateItem = new MenuItem(getText("UI_MENU_BOOKING_DELETE_UNTIL_DATE"));

		newManualItem.setOnAction(event -> handleNewManualBooking());
		editManualItem.setOnAction(event -> handleEditManualBooking());
		deleteFromDateItem.setOnAction(event -> handleDeleteBookingBlock(true));
		deleteUntilDateItem.setOnAction(event -> handleDeleteBookingBlock(false));

		ContextMenu contextMenu = new ContextMenu(newManualItem, editManualItem, deleteFromDateItem, deleteUntilDateItem);
		contextMenu.setOnShowing(event -> updateContextMenuState(newManualItem, editManualItem, deleteFromDateItem, deleteUntilDateItem));
		return contextMenu;
	}

	private TableRow<Booking> createContextMenuRow(ContextMenu contextMenu) {
		TableRow<Booking> row = new TableRow<>();
		row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> row.setContextMenu(isEmpty ? null : contextMenu));
		row.setOnMousePressed(event -> {
			if (event.isSecondaryButtonDown() && !row.isEmpty()) {
				tableView.getSelectionModel().select(row.getIndex());
				handleBookingSelection(row.getItem());
			}
		});
		return row;
	}

	private void updateContextMenuState(MenuItem newManualItem, MenuItem editManualItem, MenuItem deleteFromDateItem, MenuItem deleteUntilDateItem) {
		Booking selectedBooking = getSelectedBooking();
		boolean hasSelectedBooking = selectedBooking != null;
		boolean canCreateManual = resolveContextAccount(selectedBooking) != null;
		boolean canEditManual = hasSelectedBooking && selectedBooking.getSource() == Source.MANUELL;
		boolean canDeleteBlock = hasSelectedBooking && isBlockDeleteSource(selectedBooking.getSource());

		newManualItem.setDisable(!canCreateManual);
		editManualItem.setDisable(!canEditManual);
		deleteFromDateItem.setDisable(!canDeleteBlock);
		deleteUntilDateItem.setDisable(!canDeleteBlock);
	}

	private void handleNewManualBooking() {
		Booking selectedBooking = getSelectedBooking();
		if (resolveContextAccount(selectedBooking) == null) {
			return;
		}
		parentPanel.getTransactionDetailPanel().startNewManualBooking();
	}

	private void handleEditManualBooking() {
		Booking selectedBooking = getSelectedBooking();
		if (selectedBooking == null || selectedBooking.getSource() != Source.MANUELL) {
			return;
		}
		handleBookingSelection(selectedBooking);
		parentPanel.getTransactionDetailPanel().startEditDisplayedBooking();
	}

	private void handleDeleteBookingBlock(boolean deleteFromDate) {
		Booking selectedBooking = getSelectedBooking();
		if (selectedBooking == null || !isBlockDeleteSource(selectedBooking.getSource())) {
			return;
		}

		String rangeText = deleteFromDate ? getText("UI_MENU_BOOKING_DELETE_FROM_DATE") : getText("UI_MENU_BOOKING_DELETE_UNTIL_DATE");
		String confirmTextKey = deleteFromDate ? "ALERT_BOOKING_BLOCK_DELETE_TEXT" : "ALERT_BOOKING_BLOCK_DELETE_TEXT_UNTIL";
		Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION,
				getText(confirmTextKey, rangeText, DateFormatUtils.formatShort(getRelevantBookingDate(selectedBooking))),
				ButtonType.OK, ButtonType.CANCEL);
		confirmAlert.setTitle(getText("ALERT_BOOKING_BLOCK_DELETE_TITLE"));
		confirmAlert.setHeaderText(getText("ALERT_BOOKING_BLOCK_DELETE_HEADER"));

		if (!ButtonType.OK.equals(confirmAlert.showAndWait().orElse(ButtonType.CANCEL))) {
			return;
		}

		bean.deleteBookingsInBlock(selectedBooking, deleteFromDate);
		parentPanel.getTransactionDetailPanel().clearDisplayedBooking();
		reload();
		if (currentContextAccount() != null && parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
			updateModelBooking(bean.getBookingsForAccount(currentContextAccount().getId()));
		}
	}

	public void updateModelBooking(List<Booking> bookingList) {
		replaceItems(bookingList);
	}

	public void updatePanelBorder(String title) {
		setPanelTitle(title);
	}

	public void reload() {
		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			replaceItems(bean.getAllBookings());
		}
	}

	private void updateSaldoLabel() {
		BigDecimal saldo = filteredData.stream()
				.map(Booking::getAmount)
				.filter(amount -> amount != null)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		saldoValueLabel.setText(amountFormat.format(saldo));
		saldoValueLabel.getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
		if (saldo.signum() > 0) {
			saldoValueLabel.getStyleClass().add("amount-positive");
			saldoValueLabel.setStyle("-fx-text-fill: rgb(0, 100, 0);");
		} else if (saldo.signum() < 0) {
			saldoValueLabel.getStyleClass().add("amount-negative");
			saldoValueLabel.setStyle("-fx-text-fill: red;");
		} else {
			saldoValueLabel.getStyleClass().add("amount-neutral");
			saldoValueLabel.setStyle("");
		}
	}

	private <T> void bindFooterColumnWidth(GridPane footer, TableColumn<Booking, T> column) {
		ColumnConstraints constraints = new ColumnConstraints();
		constraints.prefWidthProperty().bind(column.widthProperty());
		constraints.minWidthProperty().bind(column.widthProperty());
		constraints.maxWidthProperty().bind(column.widthProperty());
		footer.getColumnConstraints().add(constraints);
	}

	private Text createAmountText() {
		Text text = new Text();
		text.getStyleClass().addAll("amount", "amount-text");
		text.setStyle("-fx-font-weight: bold; -fx-fill: rgb(0, 0, 0);");
		return text;
	}

	private Color resolveAmountColor(BigDecimal value) {
		if (value == null || value.signum() == 0) {
			return Color.BLACK;
		}
		return value.signum() > 0 ? Color.rgb(0, 100, 0) : Color.RED;
	}

	private void applyAmountTextStyle(Text text, BigDecimal value) {
		Color color = resolveAmountColor(value);
		text.setFill(color);
		text.setStyle("-fx-font-weight: bold; -fx-fill: " + toCssColor(color) + ";");
	}

	private String toCssColor(Color color) {
		return String.format(Locale.ROOT, "rgb(%d, %d, %d)",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}

	private HBox createAmountGraphic(Text text) {
		HBox box = new HBox(text);
		box.setAlignment(Pos.CENTER_RIGHT);
		box.setMaxWidth(Double.MAX_VALUE);
		return box;
	}

	private Booking getSelectedBooking() {
		return tableView.getSelectionModel().getSelectedItem();
	}

	private boolean isBlockDeleteSource(Source source) {
		return source == Source.ONLINE || source == Source.ONLINE_NEW || source == Source.ONLINE_PRENO || source == Source.ONLINE_PRENO_NEW
				|| source == Source.IMPORT || source == Source.IMPORT_NEW || source == Source.IMPORT_INITIAL || source == Source.IMPORT_INITIAL_NEW;
	}

	private LocalDate getRelevantBookingDate(Booking booking) {
		return booking.getDateBooking() != null ? booking.getDateBooking() : booking.getDateValue();
	}

	private de.zft2.gbanking.db.dao.BankAccount resolveContextAccount(Booking selectedBooking) {
		de.zft2.gbanking.db.dao.BankAccount account = currentContextAccount();
		if (account != null) {
			parentPanel.getTransactionDetailPanel().setCurrentAccount(account);
			return account;
		}
		if (selectedBooking == null) {
			return null;
		}
		account = dbController.getById(de.zft2.gbanking.db.dao.BankAccount.class, selectedBooking.getAccountId());
		if (account != null) {
			parentPanel.getTransactionDetailPanel().setCurrentAccount(account);
		}
		return account;
	}

	private de.zft2.gbanking.db.dao.BankAccount currentContextAccount() {
		if (parentPanel instanceof AccountsTransactionsOverviewPanel accountsPanel) {
			return Optional.ofNullable(accountsPanel.getSelectedAccount()).orElse(null);
		}
		return null;
	}
}
