package de.gbanking.gui.fx.panel.transaction;

import java.math.BigDecimal;
import java.util.Calendar;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.fx.panel.BaseBorderPanePanel;
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

	private final TextField dateBookingText = new TextField();
	private final TextField dateValueText = new TextField();
	private final TextArea purposeText = new TextArea();
	private final TextField amountText = new TextField();
	private final TextField currencyText = new TextField();
	private final ComboBox<BookingType> bookingTypeCombo = new ComboBox<>();
	private final ComboBox<Source> bookingSourceCombo = new ComboBox<>();
	private final ComboBox<BankAccount> crossAccountCombo = new ComboBox<>();
	private final TextField categoryText = new TextField();

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
		addFieldAbove(grid, "UI_LABEL_SEPA_ENDTOEND", sepaEndToEndText, 2, 0);
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
		addFieldAbove(grid, "UI_LABEL_IBAN_ACCOUNTNO", recipientIbanText, 0, 1);
		addFieldAbove(grid, "UI_LABEL_ACCOUNT_NUMBER_EMPTY", recipientAccountNumberText, 1, 1);
		addFieldAbove(grid, "UI_LABEL_BIC_BLZ", recipientBicText, 0, 2);
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

		if (field instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
		}
	}

	private void loadComboValues() {
		bookingTypeCombo.setItems(FXCollections.observableArrayList(BookingType.values()));
		bookingSourceCombo.setItems(FXCollections.observableArrayList(Source.values()));
		crossAccountCombo.setItems(FXCollections.observableArrayList(dbController.getAll(BankAccount.class)));
	}

	private void enableFields(boolean enable) {
		dateBookingText.setDisable(!enable);
		dateValueText.setDisable(!enable);
		purposeText.setDisable(!enable);
		amountText.setDisable(!enable);
		currencyText.setDisable(!enable);
		bookingTypeCombo.setDisable(!enable);
		bookingSourceCombo.setDisable(!enable);
		crossAccountCombo.setDisable(!enable);
		categoryText.setDisable(!enable);

		sepaCustomerRefText.setDisable(!enable);
		sepaCreditorIdText.setDisable(!enable);
		sepaEndToEndText.setDisable(!enable);
		sepaMandateText.setDisable(!enable);
		sepaPersonIdText.setDisable(!enable);
		sepaPurposeText.setDisable(!enable);
		sepaTypText.setDisable(!enable);

		recipientNameText.setDisable(!enable);
		recipientIbanText.setDisable(!enable);
		recipientAccountNumberText.setDisable(!enable);
		recipientBicText.setDisable(!enable);
		recipientBlzText.setDisable(!enable);
		recipientBankText.setDisable(!enable);

		updatedAtText.setEditable(false);
	}

	private void performNew() {
		context = EditContext.NEW;
		displayedBooking = new Booking();
		enableFields(true);
		bookingSourceCombo.setValue(Source.MANUELL);
		clearFields();
	}

	private void performEdit() {
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

		displayedBooking.setDateBooking(TypeConverter.toCalendarFromDateStrShort(dateBookingText.getText()));
		displayedBooking.setDateValue(TypeConverter.toCalendarFromDateStrShort(dateValueText.getText()));
		displayedBooking.setPurpose(purposeText.getText());
		displayedBooking.setAmount(amountText.getText() == null || amountText.getText().isBlank() ? null : new BigDecimal(amountText.getText()));
		displayedBooking.setCurrency(currencyText.getText());
		displayedBooking.setBookingType(bookingTypeCombo.getValue());
		displayedBooking.setSource(bookingSourceCombo.getValue());

		BankAccount cross = crossAccountCombo.getValue();
		if (cross != null) {
			displayedBooking.setCrossAccountId(cross.getId());
		}

		displayedBooking.setUpdatedAt(Calendar.getInstance());
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