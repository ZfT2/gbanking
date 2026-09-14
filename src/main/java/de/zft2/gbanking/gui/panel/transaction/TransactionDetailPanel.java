package de.zft2.gbanking.gui.panel.transaction;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingFee;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.BookingNoteDetails;
import de.zft2.gbanking.db.dao.BookingSepaDetails;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.file.BookingCsvFormat;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher.Action;
import de.zft2.gbanking.gui.panel.overview.TransactionsOverviewBasePanel;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import de.zft2.gbanking.gui.util.FxNodeSupport;
import de.zft2.gbanking.mapper.BookingCurrencyMapper;
import de.zft2.gbanking.service.booking.BookingSplitService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.util.TypeConverter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

public class TransactionDetailPanel extends BorderPane implements BaseMessagesDb {

	private static final String UI_BUTTON_NEW_SHORT = "UI_BUTTON_NEW_SHORT";
	private static final String UI_BUTTON_EDIT_SHORT = "UI_BUTTON_EDIT_SHORT";
	private static final String UI_BUTTON_CANCEL_SHORT = "UI_BUTTON_CANCEL_SHORT";
	private static final String UI_BUTTON_SAVE = "UI_BUTTON_SAVE";

	private static final double AMOUNT_FIELD_WIDTH = 104.0;
	private static final double CURRENCY_FIELD_WIDTH = 90.0;
	private static final double DETAIL_FIELD_WIDTH = 200.0;
	private static final double UPDATED_AT_FIELD_WIDTH = 100.0;
	private static final double ADDITIONAL_NOTE_TOGGLE_WIDTH = 32.0;
	private static final String EXPAND_SYMBOL = "\u25bc";
	private static final String COLLAPSE_SYMBOL = "\u25b2";

	private enum EditContext {
		NEW, EDIT, READONLY
	}

	private final TransactionsOverviewBasePanel parentPanel;
	private final SplitBookingEditor splitBookingEditor;
	private EditContext context = EditContext.READONLY;

	private final DatePicker dateBookingPicker = FormStyleUtils.applyWidth(new DatePicker(), FieldWidth.S);
	private final DatePicker dateValuePicker = FormStyleUtils.applyWidth(new DatePicker(), FieldWidth.S);
	private final TextArea purposeText = FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);
	private final TextField amountText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final ComboBox<Currency> currencyCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.XS);
	private final TextField foreignAmountText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final ComboBox<Currency> foreignCurrencyCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.XS);
	private final TextField exchangeRateText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField feeAmountText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final ComboBox<Currency> feeCurrencyCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.XS);
	private final ComboBox<BookingType> bookingTypeCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<Source> bookingSourceCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<BankAccount> crossAccountCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<Category> categoryCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final TextArea additionalNoteText = FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);
	private final CheckBox reviewRequiredCheckBox = new CheckBox(getText("UI_LABEL_REVIEW_REQUIRED"));

	private final TextField sepaCustomerRefText = new TextField();
	private final TextField sepaCreditorIdText = new TextField();
	private final TextField sepaEndToEndText = new TextField();
	private final TextField sepaMandateText = new TextField();
	private final TextField sepaPersonIdText = new TextField();
	private final TextField sepaPurposeText = new TextField();
	private final TextField sepaTypText = new TextField();

	private final TextField recipientNameText = new TextField();
	private final TextField recipientIbanText = new TextField();
	private final TextField recipientAccountNumberText = new TextField();
	private final TextField recipientBicText = new TextField();
	private final TextField recipientBlzText = new TextField();
	private final TextField recipientBankText = new TextField();

	private final TextField updatedAtText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);

	private final Button newButton = new Button(getText(UI_BUTTON_NEW_SHORT));
	private final Button editButton = new Button(getText(UI_BUTTON_EDIT_SHORT));
	private final Button additionalNoteEditButton = new Button(getText(UI_BUTTON_EDIT_SHORT));
	private final Button additionalNoteCancelButton = new Button(getText(UI_BUTTON_CANCEL_SHORT));

	private final ObservableList<Category> categoryChoices = FXCollections.observableArrayList();

	private Booking displayedBooking;
	private BankAccount currentAccount;
	private boolean additionalNoteEditing;
	private TitledPane currencyDetailsPane;

	public TransactionDetailPanel(TransactionsOverviewBasePanel parentPanel) {
		this(parentPanel, ServiceRegistry.getService(BookingSplitService.class));
	}

	TransactionDetailPanel(TransactionsOverviewBasePanel parentPanel, BookingSplitService bookingSplitService) {
		this.parentPanel = parentPanel;
		this.splitBookingEditor = new SplitBookingEditor(bookingSplitService, this::reloadParentData, createCategoryConverter());
		createUI();
		loadComboValues();
		enableFields(false);

		newButton.setOnAction(e -> handlePrimaryButton());
		editButton.setOnAction(e -> handleSecondaryButton());
		additionalNoteEditButton.setOnAction(e -> handleAdditionalNoteEditButton());
		additionalNoteCancelButton.setOnAction(e -> cancelAdditionalNoteEdit());
		KeyboardShortcutDispatcher.register(this, Action.SAVE, this::saveFromShortcut);
		KeyboardShortcutDispatcher.register(this, Action.CANCEL, this::cancelFromShortcut);
		KeyboardShortcutDispatcher.blockRefreshWhile(this, this::hasActiveEdit);
		bookingTypeCombo.valueProperty().addListener((observable, oldValue, newValue) -> updateCrossAccountState());
		updateActionButtons();
	}

	private void createUI() {
		VBox root = new VBox(8, createTabPane());
		root.setPadding(new Insets(6));
		setCenter(root);
	}

	private Node createTabPane() {
		TabPane tabPane = new TabPane();
		tabPane.setMaxHeight(Region.USE_PREF_SIZE);
		Tab transactionDetailsTab = new Tab(getText("UI_PANEL_TRANSACTION_DETAILS"), createContentArea());
		Tab splitBookingsTab = new Tab(getText("UI_PANEL_SPLIT_BOOKINGS"), splitBookingEditor);
		transactionDetailsTab.setClosable(false);
		splitBookingsTab.setClosable(false);
		tabPane.getTabs().addAll(transactionDetailsTab, splitBookingsTab);
		GuiLayoutState.configureTabPane(tabPane, "transactionDetails.main");
		return tabPane;
	}

	private Node createContentArea() {
		VBox leftColumn = new VBox(8, createMainDetailsPane(), createPurposePane());
		leftColumn.setMinWidth(0);
		HBox.setHgrow(leftColumn, Priority.ALWAYS);

		VBox rightColumn = new VBox(8, createRecipientPane(), createButtonsPane());
		rightColumn.setPrefWidth(280);
		rightColumn.setMinWidth(260);
		rightColumn.setMaxWidth(320);

		return new HBox(10, leftColumn, rightColumn);
	}

	private Node createMainDetailsPane() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);
		grid.setPadding(new Insets(6));

		addFieldAbove(grid, "UI_LABEL_BOOKING_DATE", dateBookingPicker, 0, 0);
		addFieldAbove(grid, "UI_LABEL_VALUE_DATE", dateValuePicker, 1, 0);
		addAmountCurrencyFields(grid, 2, 0);
		addFieldAbove(grid, "UI_LABEL_CATEGORY", categoryCombo, 3, 0);

		addFieldAbove(grid, "UI_LABEL_BOOKING_TYPE", bookingTypeCombo, 0, 1);
		addFieldAbove(grid, "UI_LABEL_CROSS_ACCOUNT", crossAccountCombo, 1, 1);
		addFieldAbove(grid, "UI_LABEL_SOURCE", bookingSourceCombo, 2, 1);
		TitledPane additionalNotePane = createAdditionalNotePane();
		addAdditionalNoteToggleAndUpdatedAtField(grid, createAdditionalNoteToggle(additionalNotePane), 3, 1);
		applyFixedWidth(amountText, AMOUNT_FIELD_WIDTH);
		applyFixedWidth(currencyCombo, CURRENCY_FIELD_WIDTH);

		TitledPane detailsPane = titled(getText("UI_PANEL_TRANSACTION_DETAILS"), grid);
		currencyDetailsPane = titled(getText("UI_PANEL_BOOKING_CURRENCY_DETAILS"), createCurrencyDetailsGrid());
		currencyDetailsPane.setCollapsible(true);
		currencyDetailsPane.setExpanded(false);
		TitledPane sepaPane = titled(getText("UI_PANEL_SEPA_INFO"), createSepaGrid());
		sepaPane.setCollapsible(true);
		sepaPane.setExpanded(false);
		applyDefaultFieldWidth(dateBookingPicker, dateValuePicker, bookingTypeCombo, bookingSourceCombo, crossAccountCombo, categoryCombo, updatedAtText);

		VBox content = new VBox(8, detailsPane, additionalNotePane, currencyDetailsPane, sepaPane);
		content.setFillWidth(true);
		return content;
	}

	private Node createCurrencyDetailsGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);
		grid.setPadding(new Insets(6));
		addFieldAbove(grid, "UI_LABEL_FOREIGN_AMOUNT", foreignAmountText, 0, 0);
		addFieldAbove(grid, "UI_LABEL_FOREIGN_CURRENCY", foreignCurrencyCombo, 1, 0);
		addFieldAbove(grid, "UI_LABEL_EXCHANGE_RATE_TO_BASE", exchangeRateText, 2, 0);
		addFieldAbove(grid, "UI_LABEL_BOOKING_FEE", feeAmountText, 0, 1);
		addFieldAbove(grid, "UI_LABEL_FEE_CURRENCY", feeCurrencyCombo, 1, 1);
		return grid;
	}

	private Node createSepaGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);
		grid.setPadding(new Insets(6));

		addFieldAbove(grid, "UI_LABEL_SEPA_CUSTOMER_REF", sepaCustomerRefText, 0, 0);
		addFieldAbove(grid, "UI_LABEL_SEPA_CREDITOR_ID", sepaCreditorIdText, 1, 0);
		addFieldAbove(grid, "UI_LABEL_SEPA_END_TO_END", sepaEndToEndText, 2, 0);
		addFieldAbove(grid, "UI_LABEL_SEPA_MANDATE", sepaMandateText, 3, 0);

		addFieldAbove(grid, "UI_LABEL_SEPA_PERSON_ID", sepaPersonIdText, 0, 1);
		addFieldAbove(grid, "UI_LABEL_SEPA_PURPOSE", sepaPurposeText, 1, 1);
		addFieldAbove(grid, "UI_LABEL_SEPA_TYPE", sepaTypText, 2, 1);
		applyDefaultFieldWidth(sepaCustomerRefText, sepaCreditorIdText, sepaEndToEndText, sepaMandateText, sepaPersonIdText, sepaPurposeText, sepaTypText);

		return grid;
	}

	private Node createRecipientPane() {
		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(6);
		grid.setPadding(new Insets(6));

		addFieldAbove(grid, "UI_LABEL_NAME", recipientNameText, 0, 0, 2);
		addFieldAbove(grid, "UI_LABEL_IBAN_OR_ACCOUNT_NUMBER", recipientIbanText, 0, 1);
		addFieldAbove(grid, "UI_LABEL_ACCOUNT_NUMBER_EMPTY", recipientAccountNumberText, 1, 1);
		addFieldAbove(grid, "UI_LABEL_BIC_OR_BLZ", recipientBicText, 0, 2);
		addFieldAbove(grid, "UI_LABEL_BLZ_EMPTY", recipientBlzText, 1, 2);
		addFieldAbove(grid, "UI_LABEL_BANK", recipientBankText, 0, 3, 2);

		return titled(getText("UI_PANEL_RECIPIENT_PAYER"), grid);
	}

	private TitledPane createAdditionalNotePane() {
		additionalNoteText.setMinWidth(0);
		HBox buttons = new HBox(8, additionalNoteEditButton, additionalNoteCancelButton);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		FormStyleUtils.styleButtons(additionalNoteEditButton, additionalNoteCancelButton);
		VBox content = new VBox(6, additionalNoteText, reviewRequiredCheckBox, buttons);
		content.setPadding(new Insets(6));
		TitledPane pane = titled(getText("UI_PANEL_BOOKING_ADDITIONAL_NOTE"), content);
		pane.setMinWidth(0);
		pane.setMaxWidth(Double.MAX_VALUE);
		FxNodeSupport.setVisibleManaged(pane, false);
		return pane;
	}

	private ToggleButton createAdditionalNoteToggle(Node additionalNotePane) {
		ToggleButton toggle = new ToggleButton(EXPAND_SYMBOL);
		Tooltip tooltip = new Tooltip(getText("UI_BOOKING_NOTE_SHOW"));
		toggle.setTooltip(tooltip);
		toggle.setAccessibleText(tooltip.getText());
		applyFixedWidth(toggle, ADDITIONAL_NOTE_TOGGLE_WIDTH);
		toggle.selectedProperty().addListener((obs, wasExpanded, isExpanded) -> {
			boolean expanded = Boolean.TRUE.equals(isExpanded);
			FxNodeSupport.setVisibleManaged(additionalNotePane, expanded);
			toggle.setText(expanded ? COLLAPSE_SYMBOL : EXPAND_SYMBOL);
			String tooltipText = getText(expanded ? "UI_BOOKING_NOTE_HIDE" : "UI_BOOKING_NOTE_SHOW");
			tooltip.setText(tooltipText);
			toggle.setAccessibleText(tooltipText);
		});
		return toggle;
	}

	private void addAdditionalNoteToggleAndUpdatedAtField(GridPane grid, ToggleButton toggle, int col, int rowGroup) {
		int row = rowGroup * 2;
		Label noteLabel = new Label(getText("UI_LABEL_NOTE"));
		noteLabel.setLabelFor(toggle);
		noteLabel.setOnMouseClicked(event -> toggle.setSelected(!toggle.isSelected()));
		HBox noteToggle = new HBox(4, toggle, noteLabel);
		noteToggle.setAlignment(Pos.CENTER_LEFT);
		VBox toggleBox = new VBox(2, new Label(" "), noteToggle);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		Label updatedAtLabel = new Label(getText("UI_LABEL_UPDATED_AT"));
		updatedAtLabel.setMaxWidth(Double.MAX_VALUE);
		updatedAtLabel.setAlignment(Pos.CENTER_RIGHT);
		VBox updatedAtBox = new VBox(2, updatedAtLabel, updatedAtText);
		updatedAtBox.setMinWidth(UPDATED_AT_FIELD_WIDTH);
		updatedAtBox.setPrefWidth(UPDATED_AT_FIELD_WIDTH);
		updatedAtBox.setMaxWidth(UPDATED_AT_FIELD_WIDTH);
		updatedAtBox.setAlignment(Pos.TOP_RIGHT);
		updatedAtText.setAlignment(Pos.CENTER_RIGHT);

		HBox box = new HBox(8, toggleBox, spacer, updatedAtBox);
		box.setMinWidth(DETAIL_FIELD_WIDTH);
		box.setPrefWidth(DETAIL_FIELD_WIDTH);
		box.setMaxWidth(DETAIL_FIELD_WIDTH);
		box.setAlignment(Pos.TOP_LEFT);
		grid.add(box, col, row, 1, 2);
	}

	private Node createPurposePane() {
		purposeText.setPrefRowCount(3);
		purposeText.setWrapText(true);
		purposeText.setMinWidth(0);
		TitledPane pane = titled(getText("UI_PANEL_PURPOSE"), purposeText);
		pane.setMinWidth(0);
		pane.setMaxWidth(Double.MAX_VALUE);
		return pane;
	}

	private Node createButtonsPane() {
		HBox buttons = new HBox(10, newButton, editButton);
		buttons.setPadding(new Insets(6));
		FormStyleUtils.styleButtons(newButton, editButton);
		return buttons;
	}

	private TitledPane titled(String title, Node content) {
		TitledPane pane = new TitledPane(title, content);
		pane.setCollapsible(false);
		return pane;
	}

	private void addFieldAbove(GridPane grid, String labelKey, Control field, int col, int rowGroup) {
		addFieldAbove(grid, labelKey, field, col, rowGroup, 1);
	}

	private void addFieldAbove(GridPane grid, String labelKey, Control field, int col, int rowGroup, int colspan) {
		int row = rowGroup * 2;
		VBox box = new VBox(2, new Label(getText(labelKey)), field);
		grid.add(box, col, row, colspan, 2);
		field.setMaxWidth(Double.MAX_VALUE);
	}

	private void addAmountCurrencyFields(GridPane grid, int col, int rowGroup) {
		int row = rowGroup * 2;
		VBox amountBox = new VBox(2, new Label(getText("UI_LABEL_AMOUNT")), amountText);
		VBox currencyBox = new VBox(2, new Label(getText("UI_LABEL_CURRENCY")), currencyCombo);
		HBox box = new HBox(6, amountBox, currencyBox);
		grid.add(box, col, row, 1, 2);
	}

	private void applyFixedWidth(Control field, double width) {
		field.setMinWidth(width);
		field.setPrefWidth(width);
		field.setMaxWidth(width);
	}

	private void applyDefaultFieldWidth(Control... fields) {
		for (Control field : fields) {
			field.setMinWidth(0);
			field.setPrefWidth(DETAIL_FIELD_WIDTH);
			field.setMaxWidth(DETAIL_FIELD_WIDTH);
		}
		applyFixedWidth(updatedAtText, UPDATED_AT_FIELD_WIDTH);
	}

	private void loadComboValues() {
		configureDatePicker(dateBookingPicker);
		configureDatePicker(dateValuePicker);
		configureCategoryCombo();
		bookingTypeCombo.setItems(FXCollections.observableArrayList(BookingType.values()));
		currencyCombo.setItems(FXCollections.observableArrayList(Currency.values()));
		foreignCurrencyCombo.setItems(FXCollections.observableArrayList(Currency.values()));
		feeCurrencyCombo.setItems(FXCollections.observableArrayList(Currency.values()));
		refreshSourceChoices(false);
		refreshReferenceChoices();
	}

	private void configureDatePicker(DatePicker datePicker) {
		datePicker.setEditable(true);
		datePicker.setConverter(new StringConverter<LocalDate>() {
			@Override
			public String toString(LocalDate value) {
				return TypeConverter.toDateStringLong(value);
			}

			@Override
			public LocalDate fromString(String value) {
				return parseLocalDate(value);
			}
		});
	}

	private void configureCategoryCombo() {
		categoryCombo.setConverter(createCategoryConverter());
		categoryCombo.setItems(categoryChoices);
	}

	private StringConverter<Category> createCategoryConverter() {
		return new StringConverter<>() {
			@Override
			public String toString(Category category) {
				return category != null ? category.getFullName() : "";
			}

			@Override
			public Category fromString(String string) {
				return null;
			}
		};
	}

	private void refreshSourceChoices(boolean manualOnly) {
		bookingSourceCombo.setItems(FXCollections.observableArrayList(manualOnly ? List.of(Source.MANUELL) : List.of(Source.values())));
	}

	private void refreshReferenceChoices() {
		List<BankAccount> accounts = dbController.getAll(BankAccount.class).stream()
				.filter(account -> currentAccount == null || account.getId() != currentAccount.getId()).toList();
		crossAccountCombo.setItems(FXCollections.observableArrayList(accounts));
		categoryChoices.setAll(dbController.getAll(Category.class));
		List<BankAccount> splitCrossAccounts = accounts.stream()
				.filter(account -> account.isOfflineAccount() && account.getAccountState() == AccountState.ACTIVE).toList();
		splitBookingEditor.setReferenceChoices(categoryChoices, splitCrossAccounts);
	}

	private void enableFields(boolean enable) {
		boolean editable = enable && FormStyleUtils.isUserEditable(displayedBooking != null ? displayedBooking.getSource() : null);

		FormStyleUtils.setEditable(editable, dateBookingPicker, dateValuePicker, purposeText, amountText, foreignAmountText,
				foreignCurrencyCombo, feeAmountText, feeCurrencyCombo, bookingTypeCombo, crossAccountCombo,
				categoryCombo, sepaCustomerRefText, sepaCreditorIdText, sepaEndToEndText, sepaMandateText, sepaPersonIdText, sepaPurposeText, sepaTypText,
				recipientNameText, recipientIbanText, recipientAccountNumberText, recipientBicText, recipientBlzText, recipientBankText);
		dateBookingPicker.setEditable(editable);
		dateValuePicker.setEditable(editable);
		bookingSourceCombo.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, bookingSourceCombo);
		currencyCombo.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, currencyCombo);
		exchangeRateText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, exchangeRateText);

		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);
		updateCrossAccountState();
		updateAdditionalNoteControls();
	}

	private void handlePrimaryButton() {
		if (context == EditContext.READONLY) {
			performNew();
		} else if (context == EditContext.EDIT) {
			performSave();
		} else {
			cancelEdit();
		}
	}

	private void handleSecondaryButton() {
		if (context == EditContext.READONLY) {
			performEdit();
		} else if (context == EditContext.EDIT) {
			cancelEdit();
		} else {
			performSave();
		}
	}

	private void performNew() {
		if (currentAccount == null) {
			showWarning("ALERT_ACCOUNT_NO_SELECTION");
			return;
		}
		if (!confirmDiscardUnsavedSplitBookings()) {
			return;
		}

		context = EditContext.NEW;
		displayedBooking = new Booking();
		displayedBooking.setAccountId(currentAccount.getId());
		displayedBooking.setSource(Source.MANUELL);
		refreshReferenceChoices();
		refreshSourceChoices(true);
		clearFields();
		splitBookingEditor.clear();
		bookingSourceCombo.setValue(Source.MANUELL);
		dateBookingPicker.setValue(LocalDate.now(ZoneId.systemDefault()));
		currencyCombo.setValue(currentAccount.getBaseCurrency());
		feeCurrencyCombo.setValue(currentAccount.getBaseCurrency());
		bookingSourceCombo.setValue(Source.MANUELL);
		enableFields(true);
		updateActionButtons();
	}

	private void performEdit() {
		if (displayedBooking == null || !FormStyleUtils.isUserEditable(displayedBooking.getSource())) {
			return;
		}

		context = EditContext.EDIT;
		enableFields(true);
		updateActionButtons();
	}

	private void performSave() {
		try {
			updateBookingFromUI();
			dbController.insertOrUpdate(displayedBooking);
			displayedBooking = dbController.getByIdFull(Booking.class, displayedBooking.getId());
			context = EditContext.READONLY;
			refreshSourceChoices(false);
			updatePanelFieldValues(displayedBooking);
			reloadParentData();
		} catch (Exception ex) {
			DialogWindowSupport.showAlert(getOwnerWindow(), javafx.scene.control.Alert.AlertType.WARNING, ex.getMessage());
		}
	}

	private void cancelEdit() {
		if (context == EditContext.NEW) {
			displayedBooking = null;
			clearFields();
		} else if (displayedBooking != null && displayedBooking.getId() > 0) {
			updatePanelFieldValues(dbController.getByIdFull(Booking.class, displayedBooking.getId()));
			return;
		}

		context = EditContext.READONLY;
		refreshSourceChoices(false);
		enableFields(false);
		updateActionButtons();
	}

	private boolean saveFromShortcut() {
		return fireShortcutButton(resolveSaveShortcutButton());
	}

	private boolean cancelFromShortcut() {
		Button button = resolveCancelShortcutButton();
		if (button != null) {
			return fireShortcutButton(button);
		}
		if (splitBookingEditor.hasUnsavedChanges() && displayedBooking != null) {
			splitBookingEditor.discardChanges();
			return true;
		}
		return false;
	}

	private Button resolveSaveShortcutButton() {
		if (additionalNoteEditing) {
			return additionalNoteEditButton;
		}
		if (context == EditContext.EDIT) {
			return newButton;
		}
		if (context == EditContext.NEW) {
			return editButton;
		}
		return splitBookingEditor.hasUnsavedChanges() ? splitBookingEditor.getSaveButton() : null;
	}

	private Button resolveCancelShortcutButton() {
		if (additionalNoteEditing) {
			return additionalNoteCancelButton;
		}
		if (context == EditContext.EDIT) {
			return editButton;
		}
		return context == EditContext.NEW ? newButton : null;
	}

	private boolean fireShortcutButton(Button button) {
		if (button == null || button.isDisabled()) {
			return false;
		}
		button.fire();
		return true;
	}

	private boolean hasActiveEdit() {
		return context != EditContext.READONLY || additionalNoteEditing || splitBookingEditor.hasUnsavedChanges();
	}

	private void updateBookingFromUI() {
		if (displayedBooking == null) {
			displayedBooking = new Booking();
		}
		BankAccount account = Objects.requireNonNull(currentAccount, "currentAccount");

		LocalDate bookingDate = readDate(dateBookingPicker);
		LocalDate valueDate = readDate(dateValuePicker);
		BigDecimal amount = parseAmount(amountText.getText());
		BookingType bookingType = bookingTypeCombo.getValue();
		Source source = context == EditContext.NEW ? Source.MANUELL : bookingSourceCombo.getValue();
		BookingForeignCurrencyDetails foreignDetails = BookingCurrencyMapper.createForeignCurrencyDetails(amount,
				account.getBaseCurrency(), parseAmount(foreignAmountText.getText()),
				foreignCurrencyCombo.getValue() != null ? foreignCurrencyCombo.getValue().name() : null, null);

		validateBookingInput(bookingDate, amount, bookingType, source, foreignDetails != null);

		displayedBooking.setAccountId(account.getId());
		displayedBooking.setDateBooking(bookingDate);
		displayedBooking.setDateValue(valueDate);
		displayedBooking.setDate(valueDate != null ? valueDate : bookingDate);
		displayedBooking.setPurpose(trimToNull(purposeText.getText()));
		displayedBooking.setAmount(amount);
		displayedBooking.setForeignCurrencyDetails(foreignDetails);
		displayedBooking.setFee(BookingCurrencyMapper.createFee(parseAmount(feeAmountText.getText()),
				feeCurrencyCombo.getValue() != null ? feeCurrencyCombo.getValue().name() : null,
				account.getBaseCurrency()));
		displayedBooking.setBookingType(bookingType);
		displayedBooking.setSource(source);
		int previousCategoryId = getCategoryId(displayedBooking);
		Category selectedCategory = categoryCombo.getValue();
		displayedBooking.setCategory(selectedCategory);
		if (previousCategoryId != getCategoryId(selectedCategory)) {
			clearCategoryRuleAssignment(displayedBooking);
		}

		BankAccount cross = crossAccountCombo.getValue();
		displayedBooking.setCrossAccountId(BookingType.isRebooking(bookingType) && cross != null ? cross.getId() : null);
		displayedBooking.setRecipient(saveRecipientFromUI());
		displayedBooking.setRecipientId(displayedBooking.getRecipient() != null ? displayedBooking.getRecipient().getId() : 0);

		displayedBooking.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
	}

	public void updatePanelFieldValues(Booking booking) {
		context = EditContext.READONLY;
		this.displayedBooking = booking;
		additionalNoteEditing = false;
		if (booking == null) {
			clearDisplayedBooking();
			return;
		}
		if (currentAccount == null || currentAccount.getId() != booking.getAccountId()) {
			currentAccount = dbController.getById(BankAccount.class, booking.getAccountId());
		}

		refreshReferenceChoices();
		refreshSourceChoices(false);

		dateBookingPicker.setValue(booking.getDateBooking());
		dateValuePicker.setValue(booking.getDateValue());
		purposeText.setText(booking.getPurpose());
		amountText.setText(booking.getAmountStr());
		currencyCombo.setValue(currentAccount != null ? currentAccount.getBaseCurrency() : null);
		updateCurrencyDetails(booking);
		bookingTypeCombo.setValue(booking.getBookingType());
		bookingSourceCombo.setValue(booking.getSource());
		selectCategory(booking.getCategory());

		Integer crossAccountId = booking.getCrossAccountId();
		if (crossAccountId != null && crossAccountId > 0) {
			for (BankAccount account : crossAccountCombo.getItems()) {
				if (account.getId() == crossAccountId) {
					crossAccountCombo.setValue(account);
					break;
				}
			}
		} else {
			crossAccountCombo.setValue(null);
		}

		Recipient recipient = booking.getRecipient();
		if (recipient != null) {
			recipientNameText.setText(recipient.getName());
			recipientIbanText.setText(recipient.getIban());
			recipientAccountNumberText.setText(recipient.getAccountNumber());
			recipientBicText.setText(recipient.getBic());
			recipientBlzText.setText(recipient.getBlz());
			recipientBankText.setText(recipient.getBank());
		} else {
			recipientNameText.clear();
			recipientIbanText.clear();
			recipientAccountNumberText.clear();
			recipientBicText.clear();
			recipientBlzText.clear();
			recipientBankText.clear();
		}

		updateSepaFields(booking.getSepaDetails());
		updateAdditionalNoteFields(booking.getNoteDetails());

		updatedAtText.setText(TypeConverter.toDateStringLong(booking.getUpdatedAt()));

		boolean editable = FormStyleUtils.isUserEditable(booking.getSource());
		editButton.setDisable(!editable);
		enableFields(false);
		updateActionButtons();
		splitBookingEditor.load(booking);
	}

	private void updateSepaFields(BookingSepaDetails details) {
		if (details == null) {
			clearSepaFields();
			return;
		}
		sepaCustomerRefText.setText(details.getCustomerRef());
		sepaCreditorIdText.setText(details.getCreditorId());
		sepaEndToEndText.setText(details.getEndToEnd());
		sepaMandateText.setText(details.getMandate());
		sepaPersonIdText.setText(details.getPersonId());
		sepaPurposeText.setText(details.getPurpose());
		sepaTypText.setText(details.getType() != null ? details.getType().toString() : null);
	}

	private void updateAdditionalNoteFields(BookingNoteDetails details) {
		additionalNoteText.setText(details != null ? details.getNote() : null);
		reviewRequiredCheckBox.setSelected(details != null && details.isReviewRequired());
	}

	private void updateCurrencyDetails(Booking booking) {
		BookingForeignCurrencyDetails foreign = booking.getForeignCurrencyDetails();
		foreignAmountText.setText(foreign != null ? formatDecimal(foreign.getForeignAmount()) : null);
		foreignCurrencyCombo.setValue(foreign != null ? foreign.getForeignCurrency() : null);
		exchangeRateText.setText(foreign != null ? formatDecimal(foreign.getExchangeRateToBaseCurrency()) : null);
		BookingFee fee = booking.getFee();
		feeAmountText.setText(fee != null ? formatDecimal(fee.getAmount()) : null);
		Currency defaultCurrency = currentAccount != null ? currentAccount.getBaseCurrency() : Currency.EUR;
		feeCurrencyCombo.setValue(fee != null ? fee.getCurrency() : defaultCurrency);
		currencyDetailsPane.setExpanded(foreign != null || fee != null);
	}

	private void clearSepaFields() {
		sepaCustomerRefText.clear();
		sepaCreditorIdText.clear();
		sepaEndToEndText.clear();
		sepaMandateText.clear();
		sepaPersonIdText.clear();
		sepaPurposeText.clear();
		sepaTypText.clear();
	}

	private void clearFields() {
		dateBookingPicker.setValue(null);
		dateBookingPicker.getEditor().clear();
		dateValuePicker.setValue(null);
		dateValuePicker.getEditor().clear();
		purposeText.clear();
		amountText.clear();
		currencyCombo.setValue(null);
		foreignAmountText.clear();
		foreignCurrencyCombo.setValue(null);
		exchangeRateText.clear();
		feeAmountText.clear();
		feeCurrencyCombo.setValue(null);
		categoryCombo.setValue(null);

		clearSepaFields();
		additionalNoteText.clear();
		reviewRequiredCheckBox.setSelected(false);
		additionalNoteEditing = false;

		recipientNameText.clear();
		recipientIbanText.clear();
		recipientAccountNumberText.clear();
		recipientBicText.clear();
		recipientBlzText.clear();
		recipientBankText.clear();

		crossAccountCombo.setValue(null);
		bookingTypeCombo.setValue(null);
	}

	public void setCurrentAccount(BankAccount currentAccount) {
		this.currentAccount = currentAccount;
		refreshReferenceChoices();
	}

	public void startNewManualBooking() {
		performNew();
	}

	public void startEditDisplayedBooking() {
		if (displayedBooking != null) {
			performEdit();
		}
	}

	public void clearDisplayedBooking() {
		displayedBooking = null;
		context = EditContext.READONLY;
		clearFields();
		splitBookingEditor.clear();
		refreshSourceChoices(false);
		enableFields(false);
		updateActionButtons();
	}

	public boolean confirmDiscardUnsavedSplitBookings() {
		boolean unsavedSplitBookings = splitBookingEditor.hasUnsavedChanges();
		boolean unsavedAdditionalNote = hasUnsavedAdditionalNote();
		if (!unsavedSplitBookings && !unsavedAdditionalNote) {
			if (additionalNoteEditing) {
				cancelAdditionalNoteEdit();
			}
			return true;
		}

		ButtonType discardButton = new ButtonType(getText("UI_BUTTON_DISCARD"), ButtonBar.ButtonData.OK_DONE);
		String keyPrefix = unsavedSplitBookings && !unsavedAdditionalNote ? "ALERT_SPLIT_BOOKING_UNSAVED" : "ALERT_BOOKING_CHANGES_UNSAVED";
		boolean discard = DialogWindowSupport.showConfirmation(getOwnerWindow(), Alert.AlertType.CONFIRMATION,
				getText(keyPrefix + "_TITLE"), getText(keyPrefix + "_HEADER"), getText(keyPrefix + "_TEXT"), discardButton, ButtonType.CANCEL);
		if (discard && additionalNoteEditing) {
			cancelAdditionalNoteEdit();
		}
		return discard;
	}

	private boolean hasUnsavedAdditionalNote() {
		if (!additionalNoteEditing || displayedBooking == null) {
			return false;
		}
		BookingNoteDetails details = displayedBooking.getNoteDetails();
		String persistedNote = details != null ? details.getNote() : null;
		boolean persistedReviewRequired = details != null && details.isReviewRequired();
		return !Objects.equals(trimToNull(additionalNoteText.getText()), trimToNull(persistedNote))
				|| reviewRequiredCheckBox.isSelected() != persistedReviewRequired;
	}

	private void handleAdditionalNoteEditButton() {
		if (!additionalNoteEditing) {
			additionalNoteEditing = displayedBooking != null && displayedBooking.getId() > 0 && context == EditContext.READONLY;
			updateActionButtons();
			return;
		}
		saveAdditionalNote();
	}

	private void saveAdditionalNote() {
		if (displayedBooking == null || displayedBooking.getId() <= 0) {
			return;
		}

		try {
			Booking noteUpdate = new Booking();
			noteUpdate.setId(displayedBooking.getId());
			BookingNoteDetails noteDetails = new BookingNoteDetails();
			noteDetails.setNote(trimToNull(additionalNoteText.getText()));
			noteDetails.setReviewRequired(reviewRequiredCheckBox.isSelected());
			noteUpdate.setNoteDetails(noteDetails);
			dbController.updateBookingAdditionalNote(noteUpdate);

			Booking reloaded = dbController.getByIdFull(Booking.class, displayedBooking.getId());
			BookingNoteDetails reloadedNoteDetails = reloaded.getNoteDetails();
			displayedBooking.setNoteDetails(reloadedNoteDetails);
			displayedBooking.setUpdatedAt(reloaded.getUpdatedAt());
			updateAdditionalNoteFields(reloadedNoteDetails);
			updatedAtText.setText(TypeConverter.toDateStringLong(reloaded.getUpdatedAt()));
			additionalNoteEditing = false;
			updateActionButtons();
			parentPanel.getTransactionListPanel().refreshAfterAdditionalNoteChange();
		} catch (Exception ex) {
			DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, ex.getMessage());
		}
	}

	private void cancelAdditionalNoteEdit() {
		additionalNoteEditing = false;
		updateAdditionalNoteFields(displayedBooking != null ? displayedBooking.getNoteDetails() : null);
		updateActionButtons();
	}

	private void updateAdditionalNoteControls() {
		boolean persistedBookingSelected = displayedBooking != null && displayedBooking.getId() > 0;
		boolean editable = persistedBookingSelected && context == EditContext.READONLY && additionalNoteEditing;
		FormStyleUtils.setEditable(editable, additionalNoteText, reviewRequiredCheckBox);
		additionalNoteEditButton.setText(additionalNoteEditing ? getText(UI_BUTTON_SAVE) : getText(UI_BUTTON_EDIT_SHORT));
		additionalNoteEditButton.setDisable(!persistedBookingSelected || context != EditContext.READONLY);
		FxNodeSupport.setVisibleManaged(additionalNoteCancelButton, additionalNoteEditing);
	}

	private void updateActionButtons() {
		if (context == EditContext.NEW) {
			newButton.setText(getText(UI_BUTTON_CANCEL_SHORT));
			editButton.setText(getText(UI_BUTTON_SAVE));
			newButton.setDisable(false);
			editButton.setDisable(false);
		} else if (context == EditContext.EDIT) {
			newButton.setText(getText(UI_BUTTON_SAVE));
			editButton.setText(getText("UI_BUTTON_CANCEL"));
			newButton.setDisable(false);
			editButton.setDisable(false);
		} else {
			newButton.setText(getText(UI_BUTTON_NEW_SHORT));
			editButton.setText(getText(UI_BUTTON_EDIT_SHORT));
			newButton.setDisable(false);
			editButton.setDisable(displayedBooking == null || !FormStyleUtils.isUserEditable(displayedBooking.getSource()));
		}
		if (additionalNoteEditing) {
			newButton.setDisable(true);
			editButton.setDisable(true);
		}
		updateAdditionalNoteControls();
	}

	private void validateBookingInput(LocalDate bookingDate, BigDecimal amount, BookingType bookingType,
			Source source, boolean hasForeignCurrency) {
		if (bookingDate == null || amount == null || bookingType == null || currencyCombo.getValue() == null || trimToNull(purposeText.getText()) == null) {
			throw new IllegalArgumentException(getText("ALERT_BOOKING_REQUIRED_FIELD_MISSING"));
		}

		if (amount.signum() < 0 && !isNegativeAmountType(bookingType) || amount.signum() > 0 && !isPositiveAmountType(bookingType)) {
			throw new IllegalArgumentException(getText("ALERT_BOOKING_TYPE_AMOUNT_MISMATCH"));
		}

		if (context == EditContext.NEW && source != Source.MANUELL) {
			throw new IllegalArgumentException(getText("ALERT_BOOKING_SOURCE_MANUAL_ONLY"));
		}

		if (BookingType.isRebooking(bookingType) && crossAccountCombo.getValue() == null) {
			throw new IllegalArgumentException(getText("ALERT_REBOOKING_CROSS_ACCOUNT_MISSING"));
		}
		if (BookingType.isRebooking(bookingType) && hasForeignCurrency) {
			throw new IllegalArgumentException(getText("ALERT_REBOOKING_FOREIGN_CURRENCY"));
		}
		BankAccount crossAccount = crossAccountCombo.getValue();
		if (BookingType.isRebooking(bookingType) && currentAccount != null && crossAccount != null
				&& currentAccount.getBaseCurrency() != crossAccount.getBaseCurrency()) {
			throw new IllegalArgumentException(getText("ALERT_REBOOKING_CURRENCY_MISMATCH"));
		}
	}

	private Recipient saveRecipientFromUI() {
		if (trimToNull(recipientNameText.getText()) == null && trimToNull(recipientIbanText.getText()) == null
				&& trimToNull(recipientAccountNumberText.getText()) == null && trimToNull(recipientBicText.getText()) == null
				&& trimToNull(recipientBlzText.getText()) == null && trimToNull(recipientBankText.getText()) == null) {
			return null;
		}

		Recipient recipient = new Recipient();
		recipient.setName(trimToNull(recipientNameText.getText()));
		recipient.setIban(trimToNull(recipientIbanText.getText()));
		recipient.setAccountNumber(trimToNull(recipientAccountNumberText.getText()));
		recipient.setBic(trimToNull(recipientBicText.getText()));
		recipient.setBlz(trimToNull(recipientBlzText.getText()));
		recipient.setBank(trimToNull(recipientBankText.getText()));
		recipient.setSource(Source.MANUELL);

		return dbController.resolveRecipientForManualBooking(displayedBooking, recipient);
	}

	private void reloadParentData() {
		if (currentAccount != null && parentPanel.getPageContext() == de.zft2.gbanking.gui.enu.PageContext.ACCOUNTS_TRANSACTIONS) {
			parentPanel.getTransactionListPanel().updateModelBooking(dbController.getAllByParentFull(Booking.class, currentAccount.getId()));
			parentPanel.getTransactionListPanel().updatePanelBorder(getText("UI_PANEL_TRANSACTIONS") + " - " + currentAccount.getAccountName());
			return;
		}

		parentPanel.getTransactionListPanel().reload();
	}

	private void updateCrossAccountState() {
		boolean enabled = context != EditContext.READONLY && BookingType.isRebooking(bookingTypeCombo.getValue());
		if (!BookingType.isRebooking(bookingTypeCombo.getValue())) {
			crossAccountCombo.setValue(null);
		}
		crossAccountCombo.setDisable(!enabled);
		FormStyleUtils.setReadOnlyStyle(!enabled, crossAccountCombo);
	}

	private boolean isNegativeAmountType(BookingType bookingType) {
		return bookingType == BookingType.REMOVAL || bookingType == BookingType.INTEREST_CHARGE || bookingType == BookingType.REBOOKING_OUT;
	}

	private boolean isPositiveAmountType(BookingType bookingType) {
		return bookingType == BookingType.DEPOSIT || bookingType == BookingType.INTEREST || bookingType == BookingType.REBOOKING_IN;
	}

	private BigDecimal parseAmount(String value) {
		String trimmedValue = trimToNull(value);
		if (trimmedValue == null) {
			return null;
		}
		return BookingCsvFormat.parseAmount(trimmedValue);
	}

	private String formatDecimal(BigDecimal value) {
		return value != null ? value.toPlainString().replace('.', ',') : null;
	}

	private LocalDate readDate(DatePicker picker) {
		if (picker.getValue() != null) {
			return picker.getValue();
		}
		return parseLocalDate(picker.getEditor().getText());
	}

	private LocalDate parseLocalDate(String value) {
		String trimmedValue = trimToNull(value);
		if (trimmedValue == null) {
			return null;
		}

		LocalDate parsedDate = TypeConverter.toLocalDateFromDateStr(trimmedValue);
		if (parsedDate == null) {
			parsedDate = TypeConverter.toLocalDateFromDateStrShort(trimmedValue);
		}
		return parsedDate;
	}

	private void selectCategory(Category category) {
		if (category == null) {
			categoryCombo.setValue(null);
			return;
		}

		for (Category availableCategory : categoryCombo.getItems()) {
			if (availableCategory.getId() == category.getId()) {
				categoryCombo.setValue(availableCategory);
				return;
			}
		}
		categoryCombo.setValue(category);
	}

	private int getCategoryId(Booking booking) {
		if (booking == null) {
			return 0;
		}
		int categoryId = getCategoryId(booking.getCategory());
		return categoryId > 0 ? categoryId : booking.getCategoryId();
	}

	private int getCategoryId(Category category) {
		return category != null && category.getId() > 0 ? category.getId() : 0;
	}

	private void clearCategoryRuleAssignment(Booking booking) {
		if (booking != null) {
			booking.setCategoryRuleId(null);
			booking.setCategoryRuleName(null);
		}
	}

	private void showWarning(String key) {
		DialogWindowSupport.showAlert(getOwnerWindow(), javafx.scene.control.Alert.AlertType.WARNING, getText(key));
	}

	private Window getOwnerWindow() {
		return getScene() != null ? getScene().getWindow() : null;
	}
}
