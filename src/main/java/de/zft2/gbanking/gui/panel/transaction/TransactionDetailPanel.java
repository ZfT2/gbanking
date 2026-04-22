package de.zft2.gbanking.gui.panel.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.panel.BaseBorderPanePanel;
import de.zft2.gbanking.gui.panel.overview.TransactionsOverviewBasePanel;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import de.zft2.gbanking.util.TypeConverter;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class TransactionDetailPanel extends BaseBorderPanePanel {

	private static final List<String> SUPPORTED_CURRENCIES = List.of("EUR", "USD", "CHF", "GBP", "PLN", "CZK", "NOK");

	private enum EditContext {
		NEW, EDIT, READONLY
	}

	private final TransactionsOverviewBasePanel parentPanel;
	private EditContext context = EditContext.READONLY;

	private final DatePicker dateBookingPicker = FormStyleUtils.applyWidth(new DatePicker(), FieldWidth.S);
	private final DatePicker dateValuePicker = FormStyleUtils.applyWidth(new DatePicker(), FieldWidth.S);
	private final TextArea purposeText = FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);
	private final TextField amountText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final ComboBox<String> currencyCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.XS);
	private final ComboBox<BookingType> bookingTypeCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<Source> bookingSourceCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<BankAccount> crossAccountCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<Category> categoryCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);

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

	private final TextField updatedAtText = new TextField();

	private final Button newButton = new Button(getText("UI_BUTTON_NEW_SHORT"));
	private final Button deleteButton = new Button(getText("UI_BUTTON_DELETE"));
	private final Button editButton = new Button(getText("UI_BUTTON_EDIT"));
	private final Button saveButton = new Button(getText("UI_BUTTON_SAVE"));

	private Booking displayedBooking;
	private BankAccount currentAccount;

	public TransactionDetailPanel(TransactionsOverviewBasePanel parentPanel) {
		this.parentPanel = parentPanel;
		createUI();
		loadComboValues();
		enableFields(false);

		newButton.setOnAction(e -> performNew());
		deleteButton.setOnAction(e -> performDelete());
		editButton.setOnAction(e -> performEdit());
		saveButton.setOnAction(e -> performSave());
		bookingTypeCombo.valueProperty().addListener((observable, oldValue, newValue) -> updateCrossAccountState());
		updateActionButtons();
	}

	private void createUI() {
		VBox root = new VBox(8, createTopArea(), createBottomArea());
		root.setPadding(new Insets(6));
		setCenter(root);
	}

	private Node createTopArea() {
		HBox top = new HBox(10, createMainDetailsPane(), createRecipientPane());

		Node main = top.getChildren().get(0);
		if (main instanceof Region mainRegion) {
			mainRegion.setPrefWidth(920);
			HBox.setHgrow(mainRegion, Priority.ALWAYS);
		}

		Node recipient = top.getChildren().get(1);
		if (recipient instanceof Region recipientRegion) {
			recipientRegion.setPrefWidth(280);
			recipientRegion.setMinWidth(260);
			recipientRegion.setMaxWidth(320);
		}

		return top;
	}

	private Node createBottomArea() {
		HBox bottom = new HBox(10, createPurposePane(), createButtonsPane());

		Node purpose = bottom.getChildren().get(0);
		if (purpose instanceof Region purposeRegion) {
			HBox.setHgrow(purposeRegion, Priority.ALWAYS);
		}

		return bottom;
	}

	private Node createMainDetailsPane() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);
		grid.setPadding(new Insets(6));

		addFieldAbove(grid, "UI_LABEL_BOOKING_DATE", dateBookingPicker, 0, 0);
		addFieldAbove(grid, "UI_LABEL_VALUE_DATE", dateValuePicker, 1, 0);
		addFieldAbove(grid, "UI_LABEL_AMOUNT", amountText, 2, 0);
		addFieldAbove(grid, "UI_LABEL_CURRENCY", currencyCombo, 3, 0);

		addFieldAbove(grid, "UI_LABEL_BOOKING_TYPE", bookingTypeCombo, 0, 1);
		addFieldAbove(grid, "UI_LABEL_SOURCE", bookingSourceCombo, 1, 1);
		addFieldAbove(grid, "UI_LABEL_COUNTER_ACCOUNT", crossAccountCombo, 2, 1);
		addFieldAbove(grid, "UI_LABEL_CATEGORY", categoryCombo, 3, 1);

		TitledPane detailsPane = titled(getText("UI_PANEL_TRANSACTION_DETAILS"), grid);
		TitledPane sepaPane = titled(getText("UI_PANEL_SEPA_INFO"), createSepaGrid());

		return new VBox(8, detailsPane, sepaPane);
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
		addFieldAbove(grid, "UI_LABEL_UPDATED_AT", updatedAtText, 3, 1);

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

	private Node createPurposePane() {
		purposeText.setPrefRowCount(3);
		purposeText.setWrapText(true);
		return titled(getText("UI_PANEL_PURPOSE"), purposeText);
	}

	private Node createButtonsPane() {
		VBox buttons = new VBox(8, newButton, deleteButton, editButton, saveButton);
		buttons.setPadding(new Insets(6));
		FormStyleUtils.styleButtons(newButton, deleteButton, editButton, saveButton);
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

		if (field instanceof Region) {
			Region region = field;
			region.setMaxWidth(Double.MAX_VALUE);
		}
	}

	private void loadComboValues() {
		configureDatePicker(dateBookingPicker);
		configureDatePicker(dateValuePicker);
		configureCategoryCombo();
		bookingTypeCombo.setItems(FXCollections.observableArrayList(BookingType.values()));
		currencyCombo.setItems(FXCollections.observableArrayList(SUPPORTED_CURRENCIES));
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
		categoryCombo.setConverter(new StringConverter<Category>() {
			@Override
			public String toString(Category category) {
				return category != null ? category.getFullName() : "";
			}

			@Override
			public Category fromString(String string) {
				return null;
			}
		});
	}

	private void refreshSourceChoices(boolean manualOnly) {
		bookingSourceCombo.setItems(FXCollections.observableArrayList(manualOnly ? List.of(Source.MANUELL) : List.of(Source.values())));
	}

	private void refreshReferenceChoices() {
		List<BankAccount> accounts = dbController.getAll(BankAccount.class).stream()
				.filter(account -> currentAccount == null || account.getId() != currentAccount.getId()).toList();
		crossAccountCombo.setItems(FXCollections.observableArrayList(accounts));
		categoryCombo.setItems(FXCollections.observableArrayList(dbController.getAll(Category.class)));
	}

	private void enableFields(boolean enable) {
		boolean editable = enable && FormStyleUtils.isUserEditable(displayedBooking != null ? displayedBooking.getSource() : null);

		FormStyleUtils.setEditable(editable, dateBookingPicker, dateValuePicker, purposeText, amountText, currencyCombo, bookingTypeCombo, crossAccountCombo,
				categoryCombo, sepaCustomerRefText, sepaCreditorIdText, sepaEndToEndText, sepaMandateText, sepaPersonIdText, sepaPurposeText, sepaTypText,
				recipientNameText, recipientIbanText, recipientAccountNumberText, recipientBicText, recipientBlzText, recipientBankText);
		dateBookingPicker.setEditable(editable);
		dateValuePicker.setEditable(editable);
		bookingSourceCombo.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, bookingSourceCombo);

		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);
		saveButton.setDisable(!editable);
		updateCrossAccountState();
	}

	private void performNew() {
		if (currentAccount == null) {
			showWarning("ALERT_ACCOUNT_NO_SELECTION");
			return;
		}

		context = EditContext.NEW;
		displayedBooking = new Booking();
		displayedBooking.setAccountId(currentAccount.getId());
		displayedBooking.setSource(Source.MANUELL);
		refreshReferenceChoices();
		refreshSourceChoices(true);
		clearFields();
		bookingSourceCombo.setValue(Source.MANUELL);
		dateBookingPicker.setValue(LocalDate.now());
		currencyCombo.setValue("EUR");
		bookingSourceCombo.setValue(Source.MANUELL);
		enableFields(true);
		updateActionButtons();
	}

	private void performEdit() {
		if (displayedBooking != null && !FormStyleUtils.isUserEditable(displayedBooking.getSource())) {
			return;
		}
		if (context == EditContext.EDIT || context == EditContext.NEW) {
			context = EditContext.READONLY;
			editButton.setText(getText("UI_BUTTON_EDIT"));
			newButton.setDisable(false);
			enableFields(false);
		} else {
			context = EditContext.EDIT;
			editButton.setText(getText("UI_BUTTON_CANCEL"));
			newButton.setDisable(true);
			enableFields(true);
		}
		updateActionButtons();
	}

	private void performSave() {
		try {
			updateBookingFromUI();
			dbController.insertOrUpdate(displayedBooking);
			displayedBooking = dbController.getByIdFull(Booking.class, displayedBooking.getId());
			context = EditContext.READONLY;
			editButton.setText(getText("UI_BUTTON_EDIT"));
			newButton.setDisable(false);
			refreshSourceChoices(false);
			updatePanelFieldValues(displayedBooking);
			reloadParentData();
		} catch (Exception ex) {
			new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
		}
	}

	private void performDelete() {
		if (!canDeleteDisplayedBooking()) {
			return;
		}

		Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, getText("ALERT_BOOKING_DELETE_CONFIRM_TEXT"), ButtonType.OK, ButtonType.CANCEL);
		confirmAlert.setTitle(getText("ALERT_BOOKING_DELETE_CONFIRM_TITLE"));
		confirmAlert.setHeaderText(getText("ALERT_BOOKING_DELETE_CONFIRM_HEADER"));
		if (!ButtonType.OK.equals(confirmAlert.showAndWait().orElse(ButtonType.CANCEL))) {
			return;
		}

		dbController.delete(displayedBooking, null);
		displayedBooking = null;
		context = EditContext.READONLY;
		editButton.setText(getText("UI_BUTTON_EDIT"));
		editButton.setDisable(true);
		newButton.setDisable(false);
		clearFields();
		refreshSourceChoices(false);
		enableFields(false);
		updateActionButtons();
		reloadParentData();
	}

	private void updateBookingFromUI() {
		if (displayedBooking == null) {
			displayedBooking = new Booking();
		}

		LocalDate bookingDate = readDate(dateBookingPicker);
		LocalDate valueDate = readDate(dateValuePicker);
		BigDecimal amount = parseAmount(amountText.getText());
		BookingType bookingType = bookingTypeCombo.getValue();
		Source source = context == EditContext.NEW ? Source.MANUELL : bookingSourceCombo.getValue();

		validateBookingInput(bookingDate, amount, bookingType, source);

		displayedBooking.setAccountId(currentAccount != null ? currentAccount.getId() : displayedBooking.getAccountId());
		displayedBooking.setDateBooking(bookingDate);
		displayedBooking.setDateValue(valueDate);
		displayedBooking.setDate(valueDate != null ? valueDate : bookingDate);
		displayedBooking.setPurpose(trimToNull(purposeText.getText()));
		displayedBooking.setAmount(amount);
		displayedBooking.setCurrency(currencyCombo.getValue());
		displayedBooking.setBookingType(bookingType);
		displayedBooking.setSource(source);
		displayedBooking.setCategory(categoryCombo.getValue());

		BankAccount cross = crossAccountCombo.getValue();
		displayedBooking.setCrossAccountId(isRebookingType(bookingType) && cross != null ? cross.getId() : null);
		displayedBooking.setRecipient(saveRecipientFromUI());
		displayedBooking.setRecipientId(displayedBooking.getRecipient() != null ? displayedBooking.getRecipient().getId() : 0);

		displayedBooking.setUpdatedAt(LocalDate.now());
	}

	public void updatePanelFieldValues(Booking booking) {
		this.displayedBooking = booking;
		if (booking != null && (currentAccount == null || currentAccount.getId() != booking.getAccountId())) {
			currentAccount = dbController.getById(BankAccount.class, booking.getAccountId());
		}

		refreshReferenceChoices();
		refreshSourceChoices(false);

		dateBookingPicker.setValue(booking.getDateBooking());
		dateValuePicker.setValue(booking.getDateValue());
		purposeText.setText(booking.getPurpose());
		amountText.setText(booking.getAmountStr());
		currencyCombo.setValue(booking.getCurrency());
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

		sepaCustomerRefText.setText(booking.getSepaCustomerRef());
		sepaCreditorIdText.setText(booking.getSepaCreditorId());
		sepaEndToEndText.setText(booking.getSepaEndToEnd());
		sepaMandateText.setText(booking.getSepaMandate());
		sepaPersonIdText.setText(booking.getSepaPersonId());
		sepaPurposeText.setText(booking.getSepaPurpose());
		sepaTypText.setText(booking.getSepaTyp() != null ? booking.getSepaTyp().toString() : null);

		updatedAtText.setText(TypeConverter.toDateStringLong(booking.getUpdatedAt()));

		boolean editable = FormStyleUtils.isUserEditable(booking.getSource());
		editButton.setDisable(!editable);
		enableFields(false);
		updateActionButtons();
	}

	private void clearFields() {
		dateBookingPicker.setValue(null);
		dateBookingPicker.getEditor().clear();
		dateValuePicker.setValue(null);
		dateValuePicker.getEditor().clear();
		purposeText.clear();
		amountText.clear();
		currencyCombo.setValue(null);
		categoryCombo.setValue(null);

		sepaCustomerRefText.clear();
		sepaCreditorIdText.clear();
		sepaEndToEndText.clear();
		sepaMandateText.clear();
		sepaPersonIdText.clear();
		sepaPurposeText.clear();
		sepaTypText.clear();

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
		editButton.setText(getText("UI_BUTTON_EDIT"));
		editButton.setDisable(true);
		newButton.setDisable(false);
		clearFields();
		refreshSourceChoices(false);
		enableFields(false);
		updateActionButtons();
	}

	private void updateActionButtons() {
		boolean showDelete = canDeleteDisplayedBooking() && context == EditContext.EDIT;
		newButton.setManaged(!showDelete);
		newButton.setVisible(!showDelete);
		deleteButton.setManaged(showDelete);
		deleteButton.setVisible(showDelete);
		deleteButton.setDisable(!showDelete);
	}

	private boolean canDeleteDisplayedBooking() {
		return displayedBooking != null && displayedBooking.getId() > 0 && displayedBooking.getSource() == Source.MANUELL;
	}

	private void validateBookingInput(LocalDate bookingDate, BigDecimal amount, BookingType bookingType, Source source) {
		if (bookingDate == null || amount == null || bookingType == null || currencyCombo.getValue() == null || trimToNull(purposeText.getText()) == null) {
			throw new IllegalArgumentException(getText("ALERT_BOOKING_REQUIRED_FIELD_MISSING"));
		}

		if (amount.signum() < 0 && !isNegativeAmountType(bookingType) || amount.signum() > 0 && !isPositiveAmountType(bookingType)) {
			throw new IllegalArgumentException(getText("ALERT_BOOKING_TYPE_AMOUNT_MISMATCH"));
		}

		if (context == EditContext.NEW && source != Source.MANUELL) {
			throw new IllegalArgumentException(getText("ALERT_BOOKING_SOURCE_MANUAL_ONLY"));
		}

		if (isRebookingType(bookingType) && crossAccountCombo.getValue() == null) {
			throw new IllegalArgumentException(getText("ALERT_REBOOKING_CROSS_ACCOUNT_MISSING"));
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

		Recipient recipientDb = dbController.find(Recipient.class, recipient);
		return recipientDb != null ? recipientDb : dbController.insertOrUpdate(recipient);
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
		boolean enabled = context != EditContext.READONLY && isRebookingType(bookingTypeCombo.getValue());
		if (!isRebookingType(bookingTypeCombo.getValue())) {
			crossAccountCombo.setValue(null);
		}
		crossAccountCombo.setDisable(!enabled);
		FormStyleUtils.setReadOnlyStyle(!enabled, crossAccountCombo);
	}

	private boolean isRebookingType(BookingType bookingType) {
		return bookingType == BookingType.REBOOKING_IN || bookingType == BookingType.REBOOKING_OUT;
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
		return new BigDecimal(trimmedValue.replace(',', '.'));
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

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void showWarning(String key) {
		new Alert(Alert.AlertType.WARNING, getText(key)).showAndWait();
	}
}
