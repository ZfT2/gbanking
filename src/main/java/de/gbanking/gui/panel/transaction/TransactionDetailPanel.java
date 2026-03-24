package de.gbanking.gui.panel.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.panel.BaseBorderPanePanel;
import de.gbanking.gui.util.FormStyleUtils;
import de.gbanking.gui.util.FormStyleUtils.FieldWidth;
import de.gbanking.util.TypeConverter;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class TransactionDetailPanel extends BaseBorderPanePanel {

	private enum EditContext {
		NEW, EDIT, READONLY
	}

	private EditContext context = EditContext.READONLY;

	private final TextField dateBookingText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField dateValueText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextArea purposeText = FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);
	private final TextField amountText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField currencyText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.XS);
	private final ComboBox<BookingType> bookingTypeCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<Source> bookingSourceCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ComboBox<BankAccount> crossAccountCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final TextField categoryText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);

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
	private final Button editButton = new Button(getText("UI_BUTTON_EDIT"));
	private final Button saveButton = new Button(getText("UI_BUTTON_SAVE"));

	private Booking displayedBooking;

	public TransactionDetailPanel() {
		createUI();
		loadComboValues();
		enableFields(false);

		newButton.setOnAction(e -> performNew());
		editButton.setOnAction(e -> performEdit());
		saveButton.setOnAction(e -> performSave());
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

		addFieldAbove(grid, "UI_LABEL_BOOKING_DATE", dateBookingText, 0, 0);
		addFieldAbove(grid, "UI_LABEL_VALUE_DATE", dateValueText, 1, 0);
		addFieldAbove(grid, "UI_LABEL_AMOUNT", amountText, 2, 0);
		addFieldAbove(grid, "UI_LABEL_CURRENCY", currencyText, 3, 0);

		addFieldAbove(grid, "UI_LABEL_BOOKING_TYPE", bookingTypeCombo, 0, 1);
		addFieldAbove(grid, "UI_LABEL_SOURCE", bookingSourceCombo, 1, 1);
		addFieldAbove(grid, "UI_LABEL_COUNTER_ACCOUNT", crossAccountCombo, 2, 1);
		addFieldAbove(grid, "UI_LABEL_CATEGORY", categoryText, 3, 1);

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

		addFieldAbove(grid, "UI_LABEL_NAME", recipientNameText, 0, 0);
		addFieldAbove(grid, "UI_LABEL_IBAN_OR_ACCOUNT_NUMBER", recipientIbanText, 0, 1);
		addFieldAbove(grid, "UI_LABEL_ACCOUNT_NUMBER_EMPTY", recipientAccountNumberText, 1, 1);
		addFieldAbove(grid, "UI_LABEL_BIC_OR_BLZ", recipientBicText, 0, 2);
		addFieldAbove(grid, "UI_LABEL_BLZ_EMPTY", recipientBlzText, 1, 2);
		addFieldAbove(grid, "UI_LABEL_BANK", recipientBankText, 0, 3);

		return titled(getText("UI_PANEL_RECIPIENT_PAYER"), grid);
	}

	private Node createPurposePane() {
		purposeText.setPrefRowCount(3);
		purposeText.setWrapText(true);
		return titled(getText("UI_PANEL_PURPOSE"), purposeText);
	}

	private Node createButtonsPane() {
		VBox buttons = new VBox(8, newButton, editButton, saveButton);
		buttons.setPadding(new Insets(6));
		FormStyleUtils.styleButtons(newButton, editButton, saveButton);
		return buttons;
	}

	private TitledPane titled(String title, Node content) {
		TitledPane pane = new TitledPane(title, content);
		pane.setCollapsible(false);
		return pane;
	}

	private void addFieldAbove(GridPane grid, String labelKey, Control field, int col, int rowGroup) {
		int row = rowGroup * 2;
		VBox box = new VBox(2, new Label(getText(labelKey)), field);
		grid.add(box, col, row, 1, 2);

		if (field instanceof Region) {
			Region region = field;
			region.setMaxWidth(Double.MAX_VALUE);
		}
	}

	private void loadComboValues() {
		bookingTypeCombo.setItems(FXCollections.observableArrayList(BookingType.values()));
		bookingSourceCombo.setItems(FXCollections.observableArrayList(Source.values()));
		crossAccountCombo.setItems(FXCollections.observableArrayList(dbController.getAll(BankAccount.class)));
	}

	private void enableFields(boolean enable) {
		boolean editable = enable && FormStyleUtils.isUserEditable(displayedBooking != null ? displayedBooking.getSource() : null);

		FormStyleUtils.setEditable(editable, dateBookingText, dateValueText, purposeText, amountText, currencyText, bookingTypeCombo, bookingSourceCombo,
				crossAccountCombo, categoryText, sepaCustomerRefText, sepaCreditorIdText, sepaEndToEndText, sepaMandateText, sepaPersonIdText, sepaPurposeText,
				sepaTypText, recipientNameText, recipientIbanText, recipientAccountNumberText, recipientBicText, recipientBlzText, recipientBankText);

		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);
	}

	private void performNew() {
		context = EditContext.NEW;
		displayedBooking = new Booking();
		enableFields(true);
		bookingSourceCombo.setValue(Source.MANUELL);
		clearFields();
		enableFields(true);
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
	}

	private void performSave() {
		try {
			updateBookingFromUI();
			dbController.insertOrUpdate(displayedBooking);
			enableFields(false);
			context = EditContext.READONLY;
			editButton.setText(getText("UI_BUTTON_EDIT"));
			newButton.setDisable(false);
		} catch (Exception ex) {
			new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
		}
	}

	private void updateBookingFromUI() {
		if (displayedBooking == null) {
			displayedBooking = new Booking();
		}

		displayedBooking.setDateBooking(TypeConverter.toLocalDateFromDateStrShort(dateBookingText.getText()));
		displayedBooking.setDateValue(TypeConverter.toLocalDateFromDateStrShort(dateValueText.getText()));
		displayedBooking.setPurpose(purposeText.getText());
		displayedBooking.setAmount(amountText.getText() == null || amountText.getText().isBlank() ? null : new BigDecimal(amountText.getText()));
		displayedBooking.setCurrency(currencyText.getText());
		displayedBooking.setBookingType(bookingTypeCombo.getValue());
		displayedBooking.setSource(bookingSourceCombo.getValue());

		BankAccount cross = crossAccountCombo.getValue();
		if (cross != null) {
			displayedBooking.setCrossAccountId(cross.getId());
		}

		displayedBooking.setUpdatedAt(LocalDate.now());
	}

	public void updatePanelFieldValues(Booking booking) {
		this.displayedBooking = booking;

		dateBookingText.setText(TypeConverter.toDateStringShort(booking.getDateBooking()));
		dateValueText.setText(TypeConverter.toDateStringShort(booking.getDateValue()));
		purposeText.setText(booking.getPurpose());
		amountText.setText(booking.getAmountStr());
		currencyText.setText(booking.getCurrency());
		bookingTypeCombo.setValue(booking.getBookingType());
		bookingSourceCombo.setValue(booking.getSource());
		categoryText.setText(booking.getCategory() != null ? booking.getCategory().toString() : null);

		if (booking.getCrossAccountId() > 0) {
			for (BankAccount account : crossAccountCombo.getItems()) {
				if (account.getId() == booking.getCrossAccountId()) {
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
	}

	private void clearFields() {
		dateBookingText.clear();
		dateValueText.clear();
		purposeText.clear();
		amountText.clear();
		currencyText.clear();
		categoryText.clear();

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
}