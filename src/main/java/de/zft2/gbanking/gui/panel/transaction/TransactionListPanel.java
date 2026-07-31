package de.zft2.gbanking.gui.panel.transaction;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingNoteDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.TransactionsOverviewBasePanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Callback;
import javafx.util.StringConverter;

public class TransactionListPanel extends AbstractFilterableTablePanel<Booking> {

	private static final String AMOUNT_NEUTRAL = "amount-neutral";

	private static final String AMOUNT_NEGATIVE = "amount-negative";

	private static final String AMOUNT_POSITIVE = "amount-positive";

	private static final String BOOKING_PRENOTIFICATION = "booking-prenotification";

	private static final double PURPOSE_COLUMN_MIN_WIDTH = 180;

	private static final double PURPOSE_COLUMN_PREF_WIDTH = 530;

	private static final double DATE_COLUMN_WIDTH = 88;

	private static final double AMOUNT_COLUMN_MIN_WIDTH = 92;
	private static final double AMOUNT_COLUMN_PREF_WIDTH = 100;
	private static final double REVIEW_REQUIRED_COLUMN_WIDTH = 40;
	private static final double TYPE_COLUMN_WIDTH = 40;
	private static final String REVIEW_REQUIRED_SYMBOL = "\u2605";
	private static final double FILTER_DATE_WIDTH = 115;
	private static final double FILTER_AMOUNT_WIDTH = 105;
	private static final double FILTER_CHOICE_WIDTH = 170;
	private static final double FILTER_TOGGLE_WIDTH = 32;
	private static final String FILTER_EXPAND_SYMBOL = "\u25bc";
	private static final String FILTER_COLLAPSE_SYMBOL = "\u25b2";
	private static final DateTimeFormatter GERMAN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

	private static final Logger log = LogManager.getLogger(TransactionListPanel.class);

	final TransactionsOverviewBasePanel parentPanel;
	private final Label saldoValueLabel = new Label();
	private final DecimalFormat amountFormat = FxTableUtils.createGermanDecimalFormat();
	private final DatePicker dateFromFilter = new DatePicker();
	private final DatePicker dateToFilter = new DatePicker();
	private final TextField amountFromFilter = new TextField();
	private final TextField amountToFilter = new TextField();
	private final ComboBox<FilterChoice<Integer>> categoryFilter = new ComboBox<>();
	private final ComboBox<FilterChoice<TransactionFilter.BookingState>> bookingStateFilter = new ComboBox<>();
	private TableColumn<Booking, Boolean> selectedCol;
	private TableColumn<Booking, Boolean> reviewRequiredCol;
	private TableColumn<Booking, Booking> dateCol;
	private TableColumn<Booking, Booking> purposeCol;
	private TableColumn<Booking, String> categoryCol;
	private TableColumn<Booking, BigDecimal> amountCol;
	private TableColumn<Booking, Booking> balanceCol;
	private boolean restoringSelection;
	private boolean updatingFilters;
	private BigDecimal amountFromFilterValue;
	private BigDecimal amountToFilterValue;

	public TransactionListPanel(TransactionsOverviewBasePanel parent) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parent;
		createInnerTransactionsPanel();
	}

	ObservableList<Booking> getMasterData() {
		return masterData;
	}

	private void createInnerTransactionsPanel() {
		setPanelTitleByKey("UI_PANEL_TRANSACTIONS");
		setColumns(createColumns());
		configureDefaultSorting();
		configureTableLayout("transactions." + parentPanel.getPageContext().name());
		applyCompactColumnWidths();
		configureFooter();
		configureContextMenu();
		tableView.setFixedCellSize(70);
		configureBookingSelection();
		filteredData.addListener((ListChangeListener<Booking>) change -> updateSaldoLabel());
		filterText.textProperty().addListener((obs, oldValue, newValue) -> tableView.refresh());
		updateSaldoLabel();

		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			replaceBookingItems(bean.getAllBookings());
		}
	}

	private void configureFooter() {
		configureAdvancedFilters();
		filterText.setPrefWidth(360);
		filterText.setMinWidth(320);
		filterText.setMaxWidth(420);

		FlowPane advancedFilters = createAdvancedFilterPane();
		ToggleButton detailFilterToggle = createDetailFilterToggle(advancedFilters);
		Label searchLabel = new Label(getText("UI_LABEL_SEARCH"));
		HBox searchBox = new HBox(10, searchLabel, filterText, detailFilterToggle);
		searchBox.setAlignment(Pos.CENTER_LEFT);

		GridPane footer = new GridPane();
		bindFooterColumnWidth(footer, selectedCol);
		bindFooterColumnWidth(footer, reviewRequiredCol);
		bindFooterColumnWidth(footer, dateCol);
		bindFooterColumnWidth(footer, purposeCol);
		bindFooterColumnWidth(footer, categoryCol);
		bindFooterColumnWidth(footer, amountCol);
		footer.add(searchBox, 0, 0, 5, 1);
		footer.add(saldoValueLabel, 5, 0);
		footer.add(advancedFilters, 0, 1, 6, 1);
		GridPane.setMargin(advancedFilters, new Insets(8, 0, 0, 0));
		saldoValueLabel.setMinWidth(100);
		saldoValueLabel.setPrefWidth(110);
		saldoValueLabel.setMaxWidth(110);
		saldoValueLabel.setAlignment(Pos.CENTER_RIGHT);
		setBottom(footer);
	}

	private void configureAdvancedFilters() {
		configureDateFilter(dateFromFilter);
		configureDateFilter(dateToFilter);
		configureAmountFilter(amountFromFilter, true);
		configureAmountFilter(amountToFilter, false);
		configureFilterWidth(dateFromFilter, FILTER_DATE_WIDTH);
		configureFilterWidth(dateToFilter, FILTER_DATE_WIDTH);
		configureFilterWidth(amountFromFilter, FILTER_AMOUNT_WIDTH);
		configureFilterWidth(amountToFilter, FILTER_AMOUNT_WIDTH);
		configureFilterWidth(categoryFilter, FILTER_CHOICE_WIDTH);
		configureFilterWidth(bookingStateFilter, FILTER_CHOICE_WIDTH);

		bookingStateFilter.getItems().setAll(List.of(
				new FilterChoice<>(TransactionFilter.BookingState.ALL, getText("UI_FILTER_ALL")),
				new FilterChoice<>(TransactionFilter.BookingState.BOOKED, getText("UI_FILTER_BOOKED")),
				new FilterChoice<>(TransactionFilter.BookingState.PRENOTIFICATION, getText("UI_FILTER_PRENOTIFICATION"))));
		bookingStateFilter.getSelectionModel().selectFirst();
		refreshFilterChoices();

		dateFromFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
		dateToFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
		categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
		bookingStateFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
	}

	private FlowPane createAdvancedFilterPane() {
		Button resetButton = new Button(getText("UI_BUTTON_RESET_FILTER"));
		resetButton.setOnAction(event -> resetFilters());
		VBox resetBox = createFilterField("", resetButton);
		resetBox.setAlignment(Pos.BOTTOM_LEFT);

		FlowPane pane = new FlowPane(10, 6);
		pane.setAlignment(Pos.CENTER_LEFT);
		pane.getChildren().addAll(
				createFilterField(getText("UI_LABEL_DATE_FROM"), dateFromFilter),
				createFilterField(getText("UI_LABEL_DATE_TO"), dateToFilter),
				createFilterField(getText("UI_LABEL_AMOUNT_FROM"), amountFromFilter),
				createFilterField(getText("UI_LABEL_AMOUNT_TO"), amountToFilter),
				createFilterField(getText("UI_LABEL_CATEGORY"), categoryFilter),
				createFilterField(getText("UI_FILTER_BOOKING_STATE"), bookingStateFilter),
				resetBox);
		pane.setManaged(false);
		pane.setVisible(false);
		return pane;
	}

	private ToggleButton createDetailFilterToggle(FlowPane advancedFilters) {
		ToggleButton toggle = new ToggleButton(FILTER_EXPAND_SYMBOL);
		Tooltip tooltip = new Tooltip(getText("UI_FILTER_DETAILS_SHOW"));
		toggle.setTooltip(tooltip);
		toggle.setAccessibleText(tooltip.getText());
		configureFilterWidth(toggle, FILTER_TOGGLE_WIDTH);
		toggle.selectedProperty().addListener((obs, wasExpanded, isExpanded) -> {
			boolean expanded = Boolean.TRUE.equals(isExpanded);
			advancedFilters.setManaged(expanded);
			advancedFilters.setVisible(expanded);
			toggle.setText(expanded ? FILTER_COLLAPSE_SYMBOL : FILTER_EXPAND_SYMBOL);
			String tooltipText = getText(expanded ? "UI_FILTER_DETAILS_HIDE" : "UI_FILTER_DETAILS_SHOW");
			tooltip.setText(tooltipText);
			toggle.setAccessibleText(tooltipText);
		});
		return toggle;
	}

	private VBox createFilterField(String labelText, Control control) {
		Label label = new Label(labelText);
		VBox field = new VBox(2, label, control);
		field.setAlignment(Pos.TOP_LEFT);
		return field;
	}

	private void configureDateFilter(DatePicker datePicker) {
		datePicker.setEditable(true);
		datePicker.setConverter(new StringConverter<>() {
			@Override
			public String toString(LocalDate date) {
				return DateFormatUtils.formatLong(date);
			}

			@Override
			public LocalDate fromString(String text) {
				if (text == null || text.isBlank()) {
					return null;
				}
				try {
					return LocalDate.parse(text.trim(), GERMAN_DATE_FORMAT);
				} catch (DateTimeParseException ex) {
					return datePicker.getValue();
				}
			}
		});
	}

	private void configureAmountFilter(TextField field, boolean fromField) {
		field.setPromptText("0,00");
		field.textProperty().addListener((obs, oldValue, newValue) -> {
			BigDecimal parsedValue = parseFilterAmount(newValue);
			if (fromField) {
				amountFromFilterValue = parsedValue;
			} else {
				amountToFilterValue = parsedValue;
			}
			applyFilters();
		});
		field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
			if (!Boolean.TRUE.equals(isFocused)) {
				formatAmountFilter(field);
			}
		});
	}

	private BigDecimal parseFilterAmount(String value) {
		try {
			return TransactionFilter.parseGermanAmount(value);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private void formatAmountFilter(TextField field) {
		BigDecimal amount = parseFilterAmount(field.getText());
		if (amount != null) {
			field.setText(TransactionFilter.formatGermanAmount(amount));
		}
	}

	private void configureFilterWidth(Control control, double width) {
		control.setMinWidth(width);
		control.setPrefWidth(width);
		control.setMaxWidth(width);
	}

	private void refreshFilterChoices() {
		Integer selectedCategoryId = getSelectedFilterValue(categoryFilter);
		Map<Integer, String> categories = new LinkedHashMap<>();
		boolean hasUncategorizedBookings = false;

		for (Booking booking : masterData) {
			if (booking.getCategory() == null) {
				hasUncategorizedBookings = true;
			} else {
				categories.putIfAbsent(booking.getCategory().getId(), getCategoryFilterLabel(booking));
			}
		}

		List<FilterChoice<Integer>> categoryChoices = new ArrayList<>();
		categoryChoices.add(new FilterChoice<>(null, getText("UI_FILTER_ALL")));
		if (hasUncategorizedBookings) {
			categoryChoices.add(new FilterChoice<>(TransactionFilter.UNCATEGORIZED_ID, getText("UI_FILTER_WITHOUT_CATEGORY")));
		}
		categories.entrySet().stream()
				.sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
				.map(entry -> new FilterChoice<>(entry.getKey(), entry.getValue()))
				.forEach(categoryChoices::add);

		updatingFilters = true;
		try {
			categoryFilter.getItems().setAll(categoryChoices);
			selectFilterValue(categoryFilter, selectedCategoryId);
		} finally {
			updatingFilters = false;
		}
		applyFilters();
	}

	private String getCategoryFilterLabel(Booking booking) {
		String categoryName = trimToNull(booking.getCategory().getFullName());
		return categoryName != null ? categoryName : Integer.toString(booking.getCategory().getId());
	}

	private <T> T getSelectedFilterValue(ComboBox<FilterChoice<T>> comboBox) {
		FilterChoice<T> choice = comboBox.getValue();
		return choice != null ? choice.value() : null;
	}

	private <T> void selectFilterValue(ComboBox<FilterChoice<T>> comboBox, T value) {
		comboBox.getItems().stream()
				.filter(choice -> Objects.equals(choice.value(), value))
				.findFirst()
				.ifPresentOrElse(comboBox.getSelectionModel()::select, comboBox.getSelectionModel()::selectFirst);
	}

	private void applyFilters() {
		if (updatingFilters) {
			return;
		}
		String searchText = normalize(filterText.getText());
		filteredData.setPredicate(booking -> matchesFilter(booking, searchText));
		tableView.refresh();
	}

	private void resetFilters() {
		updatingFilters = true;
		try {
			filterText.clear();
			dateFromFilter.setValue(null);
			dateToFilter.setValue(null);
			amountFromFilter.clear();
			amountToFilter.clear();
			categoryFilter.getSelectionModel().selectFirst();
			bookingStateFilter.getSelectionModel().selectFirst();
		} finally {
			updatingFilters = false;
		}
		applyFilters();
	}

	private boolean hasActiveFilter() {
		return !normalize(filterText.getText()).isBlank()
				|| dateFromFilter.getValue() != null
				|| dateToFilter.getValue() != null
				|| !amountFromFilter.getText().isBlank()
				|| !amountToFilter.getText().isBlank()
				|| getSelectedFilterValue(categoryFilter) != null
				|| getSelectedFilterValue(bookingStateFilter) != TransactionFilter.BookingState.ALL;
	}

	private void configureContextMenu() {
		installRowContextMenu(new TransactionListPanelContextMenu(this));
	}

	private void configureBookingSelection() {
		tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldBooking, selectedBooking) -> {
			if (restoringSelection || selectedBooking == null) {
				return;
			}
			if (!parentPanel.getTransactionDetailPanel().confirmDiscardUnsavedSplitBookings()) {
				restoreSelection(oldBooking);
				return;
			}
			handleBookingSelection(selectedBooking);
		});
	}

	private void restoreSelection(Booking booking) {
		restoringSelection = true;
		try {
			if (booking != null) {
				tableView.getSelectionModel().select(booking);
			} else {
				tableView.getSelectionModel().clearSelection();
			}
		} finally {
			restoringSelection = false;
		}
	}

	private List<TableColumn<Booking, ?>> createColumns() {
		selectedCol = createSelectAllSelectionColumn(
				booking -> booking.isSelected(), (booking, selected) -> booking.setSelected(selected));
		reviewRequiredCol = createReviewRequiredColumn();
		dateCol = new TableColumn<>(getText("UI_TABLE_DATE"));
		dateCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		dateCol.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
			@Override
			protected void updateItem(Booking item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : DateFormatUtils.formatBookingAndValue(item.getDateBooking(), item.getDateValue()));
			}
		});
		dateCol.setComparator(Comparator.comparing(Booking::getDateBooking, Comparator.nullsFirst(LocalDate::compareTo)).thenComparingInt(Booking::getId));
		FxTableUtils.setFixedWidth(dateCol, DATE_COLUMN_WIDTH);
		purposeCol = createPurposeColumn();
		categoryCol = createCategoryColumn();
		amountCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_AMOUNT"), Booking::getAmount, this::isPrenotification);
		amountCol.setCellFactory(FxTableUtils.createAutoFitBigDecimalAmountCellFactory(this::isPrenotification));
		FxTableUtils.setPreferredWidth(amountCol, AMOUNT_COLUMN_MIN_WIDTH, AMOUNT_COLUMN_PREF_WIDTH);
		balanceCol = new TableColumn<>(getText("UI_TABLE_BALANCE"));
		balanceCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		balanceCol.setCellFactory(createBalanceCol());
		FxTableUtils.setPreferredWidth(balanceCol, AMOUNT_COLUMN_MIN_WIDTH, AMOUNT_COLUMN_PREF_WIDTH);
		TableColumn<Booking, Source> typeCol = createTypeColumn();

		if (parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
			TableColumn<Booking, Booking> crossAccountCol = createCrossAccountColumn();
			return List.of(selectedCol, reviewRequiredCol, dateCol, purposeCol, categoryCol, amountCol, balanceCol, typeCol, crossAccountCol);
		}

		TableColumn<Booking, String> accountCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT"), Booking::getAccountName, 140, 180);
		return List.of(selectedCol, reviewRequiredCol, dateCol, purposeCol, categoryCol, amountCol, balanceCol, typeCol, accountCol);
	}

	private TableColumn<Booking, Boolean> createReviewRequiredColumn() {
		String reviewRequiredText = getText("UI_TABLE_REVIEW_REQUIRED");
		Label headerSymbol = new Label(REVIEW_REQUIRED_SYMBOL);
		headerSymbol.setAccessibleText(reviewRequiredText);
		headerSymbol.setTooltip(new Tooltip(reviewRequiredText));
		TableColumn<Booking, Boolean> column = new TableColumn<>();
		column.setGraphic(headerSymbol);
		column.setCellValueFactory(data -> {
			BookingNoteDetails details = data.getValue().getNoteDetails();
			return new ReadOnlyObjectWrapper<>(details != null && details.isReviewRequired());
		});
		column.setCellFactory(tableColumn -> new TableCell<>() {
			@Override
			protected void updateItem(Boolean reviewRequired, boolean empty) {
				super.updateItem(reviewRequired, empty);
				setText(!empty && Boolean.TRUE.equals(reviewRequired) ? REVIEW_REQUIRED_SYMBOL : null);
				setAlignment(Pos.CENTER);
			}
		});
		FxTableUtils.setFixedWidth(column, REVIEW_REQUIRED_COLUMN_WIDTH);
		return column;
	}

	private TableColumn<Booking, Source> createTypeColumn() {
		TableColumn<Booking, Source> column = new TableColumn<>(getText("UI_TABLE_BOOKING_TYPE"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getSource()));
		column.setCellFactory(tableColumn -> new TableCell<>() {
			private final Tooltip sourceTooltip = new Tooltip();

			@Override
			protected void updateItem(Source source, boolean empty) {
				super.updateItem(source, empty);
				Source value = empty ? null : source;
				String sourceName = value != null ? value.toString() : null;
				setText(value != null ? value.getSymbol() : null);
				sourceTooltip.setText(sourceName);
				setTooltip(value != null ? sourceTooltip : null);
				setAccessibleText(sourceName);
				setAlignment(Pos.CENTER);
			}
		});
		FxTableUtils.setFixedWidth(column, TYPE_COLUMN_WIDTH);
		return column;
	}

	private void applyCompactColumnWidths() {
		purposeCol.setMinWidth(PURPOSE_COLUMN_MIN_WIDTH);
		purposeCol.setPrefWidth(PURPOSE_COLUMN_PREF_WIDTH);
		FxTableUtils.setPreferredWidth(amountCol, AMOUNT_COLUMN_MIN_WIDTH, AMOUNT_COLUMN_PREF_WIDTH);
		FxTableUtils.setPreferredWidth(balanceCol, AMOUNT_COLUMN_MIN_WIDTH, AMOUNT_COLUMN_PREF_WIDTH);
	}

	private TableColumn<Booking, Booking> createCrossAccountColumn() {
		TableColumn<Booking, Booking> column = new TableColumn<>(getText("UI_TABLE_COUNTER_ACCOUNT"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		column.setCellFactory(this::createCrossAccountCell);
		FxTableUtils.setPreferredWidth(column, 140, 180);
		return column;
	}

	private TableCell<Booking, Booking> createCrossAccountCell(TableColumn<Booking, Booking> column) {
		return new TableCell<>() {
			private final Hyperlink accountLink = new Hyperlink();

			{
				accountLink.maxWidthProperty().bind(column.widthProperty().subtract(16));
				accountLink.setOnAction(event -> {
					Booking booking = getItem();
					if (booking != null) {
						navigateToCrossBooking(booking);
					}
				});
			}

			@Override
			protected void updateItem(Booking booking, boolean empty) {
				super.updateItem(booking, empty);
				if (empty || booking == null || trimToNull(booking.getCrossAccountName()) == null) {
					setText(null);
					setGraphic(null);
					return;
				}

				if (hasLinkedCrossBooking(booking)) {
					accountLink.setText(booking.getCrossAccountName());
					setText(null);
					setGraphic(accountLink);
				} else {
					setText(booking.getCrossAccountName());
					setGraphic(null);
				}
			}
		};
	}

	private TableColumn<Booking, String> createCategoryColumn() {
		TableColumn<Booking, String> column = TableColumnFactory.createTextColumn(getText("UI_LABEL_CATEGORY"), this::getCategoryFullName, 140, 180);
		column.setCellFactory(tableColumn -> new TableCell<>() {
			@Override
			protected void updateItem(String categoryName, boolean empty) {
				super.updateItem(categoryName, empty);
				setText(empty ? null : categoryName);
				setTooltip(empty ? null : createCategoryTooltip(getBookingFromRow()));
			}

			private Booking getBookingFromRow() {
				return getTableRow() != null ? getTableRow().getItem() : null;
			}
		});
		return column;
	}

	private Tooltip createCategoryTooltip(Booking booking) {
		if (booking == null || trimToNull(getCategoryFullName(booking)) == null) {
			return null;
		}
		String tooltipText = getCategoryRuleTooltipText(booking);
		return tooltipText != null ? new Tooltip(tooltipText) : null;
	}

	private String getCategoryRuleTooltipText(Booking booking) {
		if (booking.getCategoryRuleId() != null && booking.getCategoryRuleId() > 0) {
			return trimToNull(booking.getCategoryRuleName());
		}
		return getText("UI_BOOKING_CATEGORY_MANUAL_ASSIGNMENT");
	}

	private boolean hasLinkedCrossBooking(Booking booking) {
		return booking.getCrossAccountId() != null && booking.getCrossAccountId() > 0
				&& booking.getCrossBookingId() != null && booking.getCrossBookingId() > 0;
	}

	private void navigateToCrossBooking(Booking booking) {
		if (!hasLinkedCrossBooking(booking) || !(parentPanel instanceof AccountsTransactionsOverviewPanel accountsPanel)) {
			return;
		}

		BankAccount crossAccount = dbController.getById(BankAccount.class, booking.getCrossAccountId());
		if (crossAccount == null) {
			return;
		}

		accountsPanel.getAccountListPanel().selectAccount(crossAccount);
		selectBookingById(booking.getCrossBookingId());
	}

	private void configureDefaultSorting() {
		dateCol.setSortType(TableColumn.SortType.DESCENDING);
		tableView.getSortOrder().setAll(Arrays.asList(dateCol));
		tableView.sort();
	}

	private Callback<TableColumn<Booking, Booking>, TableCell<Booking, Booking>> createBalanceCol() {
		return column -> new javafx.scene.control.TableCell<>() {
			private final Text textNode = createAmountText();

			@Override
			protected void updateItem(Booking item, boolean empty) {
				super.updateItem(item, empty);
				getStyleClass().removeAll(AMOUNT_POSITIVE, AMOUNT_NEGATIVE, AMOUNT_NEUTRAL);
				textNode.getStyleClass().removeAll(AMOUNT_POSITIVE, AMOUNT_NEGATIVE, AMOUNT_NEUTRAL);
				setAlignment(Pos.CENTER_RIGHT);

				BigDecimal balance = empty || item == null || isPrenotification(item) || hasActiveFilter() ? null : item.getBalance();
				if (balance == null) {
					setText(null);
					setGraphic(null);
					setStyle(null);
					return;
				}
				formatBalanceText(this, textNode, balance);
			}

			@Override
			public void updateSelected(boolean selected) {
				super.updateSelected(selected);
				Booking booking = getItem();
				BigDecimal balance = booking == null || isPrenotification(booking) || hasActiveFilter() ? null : booking.getBalance();
				if (balance != null) {
					applyAmountTextStyle(textNode, balance);
				}
			}
		};
	}

	private void formatBalanceText(TableCell<Booking, Booking> tableCell, Text textNode, BigDecimal balance) {
		final HBox graphic = createAmountGraphic(textNode);
		textNode.setText(amountFormat.format(balance));
		applyAmountTextStyle(textNode, balance);
		FxTableUtils.ensureColumnFitsText(tableCell.getTableColumn(), textNode);
		tableCell.setText(null);
		tableCell.setGraphic(graphic);
		if (balance.signum() > 0) {
			getStyleClass().add(AMOUNT_POSITIVE);
			textNode.getStyleClass().add(AMOUNT_POSITIVE);
		} else if (balance.signum() < 0) {
			getStyleClass().add(AMOUNT_NEGATIVE);
			textNode.getStyleClass().add(AMOUNT_NEGATIVE);
		} else {
			getStyleClass().add(AMOUNT_NEUTRAL);
			textNode.getStyleClass().add(AMOUNT_NEUTRAL);
		}
	}

	@Override
	protected void updateRowStyle(TableRow<Booking> row, Booking booking) {
		row.getStyleClass().removeAll(BOOKING_PRENOTIFICATION);
		if (isPrenotification(booking)) {
			row.getStyleClass().add(BOOKING_PRENOTIFICATION);
		}
	}

	@Override
	protected boolean matchesFilter(Booking booking, String filter) {
		FilterChoice<TransactionFilter.BookingState> bookingState = bookingStateFilter.getValue();
		TransactionFilter.Criteria criteria = new TransactionFilter.Criteria(filter, dateFromFilter.getValue(), dateToFilter.getValue(),
				amountFromFilterValue, amountToFilterValue, getSelectedFilterValue(categoryFilter),
				bookingState != null ? bookingState.value() : TransactionFilter.BookingState.ALL);
		return TransactionFilter.matches(booking, criteria);
	}

	private TableColumn<Booking, Booking> createPurposeColumn() {
		TableColumn<Booking, Booking> column = new TableColumn<>(getText("UI_TABLE_PURPOSE"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		column.setCellFactory(this::createPurposeCell);
		FxTableUtils.setPreferredWidth(column, PURPOSE_COLUMN_MIN_WIDTH, PURPOSE_COLUMN_PREF_WIDTH);
		return column;
	}

	private TableCell<Booking, Booking> createPurposeCell(TableColumn<Booking, Booking> column) {
		return new TableCell<>() {
			private final Label recipientLabel = new Label();
			private final Label purposeLabel = new Label();
			private final VBox graphic = new VBox(2, recipientLabel, purposeLabel);

			{
				recipientLabel.setStyle("-fx-font-weight: bold;");
				recipientLabel.setWrapText(true);
				purposeLabel.setWrapText(true);
				recipientLabel.maxWidthProperty().bind(column.widthProperty().subtract(16));
				purposeLabel.maxWidthProperty().bind(column.widthProperty().subtract(16));
				setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
				setAlignment(Pos.CENTER_LEFT);
			}

			@Override
			protected void updateItem(Booking booking, boolean empty) {
				super.updateItem(booking, empty);
				String recipientName = empty || booking == null ? null : trimToNull(getRecipientName(booking));
				String purpose = empty || booking == null ? null : trimToNull(booking.getPurpose());

				if (recipientName == null && purpose == null) {
					setGraphic(null);
					return;
				}

				recipientLabel.setText(recipientName);
				recipientLabel.setVisible(recipientName != null);
				recipientLabel.setManaged(recipientName != null);
				purposeLabel.setText(purpose);
				purposeLabel.setVisible(purpose != null);
				purposeLabel.setManaged(purpose != null);
				setGraphic(graphic);
			}
		};
	}

	private String getRecipientName(Booking booking) {
		return booking.getRecipient() != null ? booking.getRecipient().getName() : null;
	}

	private String getCategoryFullName(Booking booking) {
		return booking.getCategory() != null ? booking.getCategory().getFullName() : null;
	}

	void handleBookingSelection(Booking booking) {
		log.log(Level.INFO, () -> getText("LOG_BOOKING_SELECTED", booking.getId()));

		Recipient recipient = dbController.getByIdFull(Recipient.class, booking.getRecipientId());
		booking.setRecipient(recipient);
		parentPanel.getTransactionDetailPanel().updatePanelFieldValues(booking);

		if (parentPanel instanceof AccountsTransactionsOverviewPanel parent) {
			parent.enableTransactionDetailPanel();
		}
	}

	public void updateModelBooking(List<Booking> bookingList) {
		replaceBookingItems(bookingList);
	}

	public void updatePanelBorder(String title) {
		setPanelTitle(title);
	}

	public void reload() {
		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			replaceBookingItems(bean.getAllBookings());
		}
	}

	void refreshAfterAdditionalNoteChange() {
		applyFilters();
		tableView.refresh();
	}

	public boolean selectBookingById(Integer bookingId) {
		if (bookingId == null || bookingId <= 0) {
			return false;
		}

		Booking bookingToSelect = masterData.stream()
				.filter(candidate -> candidate.getId() == bookingId)
				.findFirst()
				.orElse(null);
		if (bookingToSelect == null) {
			return false;
		}

		if (!filteredData.contains(bookingToSelect)) {
			resetFilters();
		}
		tableView.getSelectionModel().select(bookingToSelect);
		tableView.scrollTo(bookingToSelect);
		return true;
	}

	private void replaceBookingItems(List<Booking> bookingList) {
		BookingRunningBalanceCalculator.applyTo(bookingList);
		applyCompactColumnWidths();
		replaceItems(bookingList);
		refreshFilterChoices();
	}

	private void updateSaldoLabel() {
		BigDecimal saldo = filteredData.stream()
				.filter(booking -> !isPrenotification(booking))
				.map(Booking::getAmount)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		saldoValueLabel.setText(amountFormat.format(saldo));
		saldoValueLabel.getStyleClass().removeAll(AMOUNT_POSITIVE, AMOUNT_NEGATIVE, AMOUNT_NEUTRAL);
		if (saldo.signum() > 0) {
			saldoValueLabel.getStyleClass().add(AMOUNT_POSITIVE);
			saldoValueLabel.setStyle("-fx-text-fill: rgb(0, 100, 0);");
		} else if (saldo.signum() < 0) {
			saldoValueLabel.getStyleClass().add(AMOUNT_NEGATIVE);
			saldoValueLabel.setStyle("-fx-text-fill: red;");
		} else {
			saldoValueLabel.getStyleClass().add(AMOUNT_NEUTRAL);
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

	private boolean isPrenotification(Booking booking) {
		return booking != null && booking.getSource() != null && booking.getSource().isPrenotification();
	}

	private record FilterChoice<T>(T value, String label) {

		@Override
		public String toString() {
			return label;
		}
	}
}
