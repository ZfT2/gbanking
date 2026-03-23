package de.gbanking.gui.panel.bankaccess;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.gbanking.db.dao.enu.TanProcedure;
import de.gbanking.gui.enu.ButtonContext;
import de.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.gbanking.gui.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.util.FormFields;
import de.gbanking.gui.util.FormStyleUtils;
import de.gbanking.util.TypeConverter;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

public class BankAccessDetailPanel extends AbstractReadonlyDetailPanel {

	private final TextField blzText = FormFields.textS();
	private final TextField bankNameText = FormFields.textM();
	private final TextField userNameText = FormFields.textM();
	private final TextField customerIdText = FormFields.textM();

	private final TextField urlText = FormFields.textL();
	private final TextField portText = FormFields.textXs();
	private final TextField systemIdText = FormFields.textS();
	private final ComboBox<TanProcedure> tanProcedureBox = new ComboBox<>();

	private final TextField hbciVersionText = FormFields.textS();
	private final ComboBox<HbciEncodingFilterType> hbciFilterTypeBox = new ComboBox<>();
	private final TextField bpdVersionText = FormFields.textS();
	private final TextField updVersionText = FormFields.textS();
	private final CheckBox activeBox = FormFields.checkBox();
	private final TextField updatedAtText = FormFields.textS();

	private final Button buttonBankAccessNew = new Button(getText("UI_BUTTON_BANK_ACCESS_NEW"));
	private final Button buttonBankAccessUpdate = new Button(getText("UI_BUTTON_BANK_ACCESS_UPDATE"));
	private final Button buttonBankAccessEdit = new Button(getText("UI_BUTTON_BANK_ACCESS_EDIT"));
	private final Button buttonBankAccessSave = new Button(getText("UI_BUTTON_SAVE"));
	private final Button buttonBankAccessCancel = new Button(getText("UI_BUTTON_CANCEL"));
	private final Button buttonBankAccessDelete = new Button(getText("UI_BUTTON_BANK_ACCESS_DELETE"));

	private final BankAccessOverviewPanel parentPanel;

	private BankAccess currentBankAccess;

	public BankAccessDetailPanel(BankAccessOverviewPanel parentPanel) {
		super("UI_PANEL_BANK_ACCESS_DETAILS");
		this.parentPanel = parentPanel;
		configureGrid();
		createInnerBankAccessDetailPanel();
	}

	private void configureGrid() {
		formGrid.getColumnConstraints().setAll(createGrowColumn(), createGrowColumn(), createGrowColumn());
	}

	private ColumnConstraints createGrowColumn() {
		ColumnConstraints constraints = new ColumnConstraints();
		constraints.setHgrow(Priority.ALWAYS);
		constraints.setFillWidth(true);
		return constraints;
	}

	private void createInnerBankAccessDetailPanel() {
		configureComboBoxes();
		addFields();
		configureButtons();
		addContentNode(FormStyleUtils.createButtonBar(buttonBankAccessNew, buttonBankAccessUpdate, buttonBankAccessEdit, buttonBankAccessSave,
				buttonBankAccessCancel, buttonBankAccessDelete));
		setEditMode(false);
	}

	private void configureComboBoxes() {
		tanProcedureBox.setMaxWidth(Double.MAX_VALUE);
		tanProcedureBox.setConverter(new StringConverter<>() {
			@Override
			public String toString(TanProcedure value) {
				return value == null ? "" : value.toString();
			}

			@Override
			public TanProcedure fromString(String string) {
				return null;
			}
		});

		hbciFilterTypeBox.setItems(FXCollections.observableArrayList(HbciEncodingFilterType.values()));
		hbciFilterTypeBox.setMaxWidth(Double.MAX_VALUE);
		hbciFilterTypeBox.setConverter(new StringConverter<>() {
			@Override
			public String toString(HbciEncodingFilterType value) {
				return value == null ? "" : value.toString();
			}

			@Override
			public HbciEncodingFilterType fromString(String string) {
				return null;
			}
		});
	}

	private void addFields() {
		addFieldInline("UI_LABEL_BLZ", blzText, 0, 0);
		addFieldInline("UI_LABEL_BANK", bankNameText, 0, 1);
		addFieldInline("UI_LABEL_USER", userNameText, 0, 2);
		addFieldInline("UI_LABEL_CUSTOMER_ID", customerIdText, 0, 3);

		addFieldInline("UI_LABEL_FINTS_URL", urlText, 1, 0);
		addFieldInline("UI_LABEL_FINTS_PORT", portText, 1, 1);
		addFieldInline("UI_LABEL_SYSTEM_ID", systemIdText, 1, 2);
		addFieldInline("UI_LABEL_TAN_PROCEDURE_SELECTED", tanProcedureBox, 1, 3);

		addFieldInline("UI_LABEL_HBCI_VERSION", hbciVersionText, 2, 0);
		addFieldInline("UI_LABEL_HBCI_ENCRYPTION", hbciFilterTypeBox, 2, 1);
		addFieldInline("UI_LABEL_BPD_VERSION", bpdVersionText, 2, 2);
		addFieldInline("UI_LABEL_UPD_VERSION", updVersionText, 2, 3);
		addFieldInline("UI_LABEL_ACTIVE", activeBox, 2, 4);
		addFieldInline("UI_LABEL_UPDATED_AT", updatedAtText, 2, 5);
	}

	private void configureButtons() {
		buttonBankAccessNew.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_NEW));
		buttonBankAccessUpdate.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_EDIT));
		buttonBankAccessEdit.setOnAction(e -> enableManualEditWithConfirmation());
		buttonBankAccessSave.setOnAction(e -> saveManualChanges());
		buttonBankAccessCancel.setOnAction(e -> cancelManualChanges());
		buttonBankAccessDelete.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_DELETE));
	}

	public void updatePanelFieldValues(BankAccess selectedAccess) {
		currentBankAccess = selectedAccess;
		parentPanel.setCurrentBankAccess(selectedAccess);
		fillForm(selectedAccess);
		setEditMode(false);
	}

	private void fillForm(BankAccess access) {
		updateTitle(access.getBankName());

		blzText.setText(access.getBlz());
		bankNameText.setText(access.getBankName());
		userNameText.setText(access.getUserId());
		customerIdText.setText(access.getCustomerId());

		urlText.setText(access.getHbciURL());
		portText.setText(access.getPort() == null ? "" : String.valueOf(access.getPort()));
		systemIdText.setText(access.getSysId());

		refreshSupportedTanProcedures(access);
		tanProcedureBox.setValue(access.getTanProcedure());

		hbciVersionText.setText(access.getHbciVersion());
		hbciFilterTypeBox.setValue(access.getFilterType());
		bpdVersionText.setText(access.getBpdVersion());
		updVersionText.setText(access.getUpdVersion());
		activeBox.setSelected(access.isActive());
		updatedAtText.setText(TypeConverter.toDateStringLong(access.getUpdatedAt()));
	}

	private void applyFormTo(BankAccess access) {
		access.setBlz(trimToNull(blzText.getText()));
		access.setBankName(trimToNull(bankNameText.getText()));
		access.setUserId(trimToNull(userNameText.getText()));
		access.setCustomerId(trimToNull(customerIdText.getText()));

		access.setHbciURL(trimToNull(urlText.getText()));
		access.setPort(parseAndValidatePostiveInt(portText.getText()));
		access.setSysId(trimToNull(systemIdText.getText()));
		access.setTanProcedure(tanProcedureBox.getValue());

		access.setHbciVersion(trimToNull(hbciVersionText.getText()));
		access.setFilterType(hbciFilterTypeBox.getValue());
		access.setBpdVersion(trimToNull(bpdVersionText.getText()));
		access.setUpdVersion(trimToNull(updVersionText.getText()));
		access.setActive(activeBox.isSelected());
	}

	private void enableManualEditWithConfirmation() {
		if (currentBankAccess == null) {
			return;
		}

		Alert alert = new Alert(Alert.AlertType.WARNING, getText("UI_QUESTION_BANK_ACCESS_EDIT_MANUAL"), ButtonType.OK, ButtonType.CANCEL);
		alert.setTitle(getText("UI_BUTTON_BANK_ACCESS_EDIT"));
		alert.setHeaderText(getText("UI_WARNING_BANK_ACCESS_EDIT_MANUAL"));

		alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> setEditMode(true));
	}

	private void saveManualChanges() {
		if (currentBankAccess == null || !validateForm()) {
			return;
		}

		applyFormTo(currentBankAccess);

		BankAccess savedBankAccess = dbController.insertOrUpdate(currentBankAccess);
		if (savedBankAccess != null) {
			currentBankAccess = savedBankAccess;
			parentPanel.setCurrentBankAccess(savedBankAccess);
			parentPanel.getBankAccessListPanel().refreshModelBankAccess();
			fillForm(savedBankAccess);
			setEditMode(false);
		}
	}

	private void cancelManualChanges() {
		if (currentBankAccess != null) {
			fillForm(currentBankAccess);
		}
		setEditMode(false);
	}

	private boolean validateForm() {
		if (isBlank(blzText.getText()) || isBlank(bankNameText.getText()) || isBlank(urlText.getText()) || isBlank(userNameText.getText())) {
			showAlert("UI_WARNING_BANK_ACCESS_REQUIRED_FIELDS_TITLE", "UI_WARNING_BANK_ACCESS_REQUIRED_FIELDS_HEADER",
					"UI_WARNING_BANK_ACCESS_REQUIRED_FIELDS_TEXT");
			return false;
		}

		if (parseAndValidatePostiveInt(portText.getText()) == null && !isBlank(portText.getText())) {
			showAlert("UI_WARNING_BANK_ACCESS_INVALID_PORT_TITLE", "UI_WARNING_BANK_ACCESS_INVALID_PORT_HEADER", "UI_WARNING_BANK_ACCESS_INVALID_PORT_TEXT");
			return false;
		}

		return true;
	}

	private void refreshSupportedTanProcedures(BankAccess access) {
		List<TanProcedure> supportedProcedures = determineSupportedTanProcedures(access);
		tanProcedureBox.setItems(FXCollections.observableArrayList(supportedProcedures));

		TanProcedure selectedProcedure = access.getTanProcedure();
		if (selectedProcedure != null && !supportedProcedures.contains(selectedProcedure)) {
			tanProcedureBox.getItems().add(selectedProcedure);
			tanProcedureBox.getItems().sort(Comparator.comparingInt(this::tanCode));
		}
	}

	private List<TanProcedure> determineSupportedTanProcedures(BankAccess access) {
		List<String> mechanisms = access.getAllowedTwostepMechanisms();
		if (mechanisms == null || mechanisms.isEmpty()) {
			return List.of(TanProcedure.values());
		}

		List<TanProcedure> procedures = mechanisms.stream().map(this::toTanProcedure).filter(Objects::nonNull).distinct()
				.sorted(Comparator.comparingInt(this::tanCode)).toList();

		return procedures.isEmpty() ? List.of(TanProcedure.values()) : procedures;
	}

	private TanProcedure toTanProcedure(String value) {
		if (isBlank(value)) {
			return null;
		}

		String digits = value.replaceAll("\\D+", "");
		if (digits.isEmpty()) {
			return null;
		}

		try {
			return TanProcedure.forCode(Integer.parseInt(digits));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private int tanCode(TanProcedure procedure) {
		String digits = procedure.toString().replaceAll("\\D+", "");
		return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
	}

	private void setEditMode(boolean editMode) {
		FormStyleUtils.setEditable(editMode, editableControls().toArray(Control[]::new));

		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);

		boolean hasSelection = currentBankAccess != null;

		setButtonVisible(buttonBankAccessNew, !editMode);
		setButtonVisible(buttonBankAccessUpdate, !editMode && hasSelection);
		setButtonVisible(buttonBankAccessEdit, !editMode && hasSelection);
		setButtonVisible(buttonBankAccessDelete, !editMode && hasSelection);

		setButtonVisible(buttonBankAccessSave, editMode);
		setButtonVisible(buttonBankAccessCancel, editMode);
	}

	private List<Control> editableControls() {
		return List.of(blzText, bankNameText, userNameText, customerIdText, urlText, portText, systemIdText, tanProcedureBox, hbciVersionText,
				hbciFilterTypeBox, bpdVersionText, updVersionText, activeBox);
	}

	private void setButtonVisible(Button button, boolean visible) {
		button.setVisible(visible);
		button.setManaged(visible);
	}

	private void showAlert(String titleKey, String headerKey, String contentKey) {
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle(getText(titleKey));
		alert.setHeaderText(getText(headerKey));
		alert.setContentText(getText(contentKey));
		alert.showAndWait();
	}

	private void newBankAccessDialog(ButtonContext buttonContext) {
		BankAccessNewDialogHolder dialogHolder = new BankAccessNewDialogHolder(buttonContext, parentPanel);
		dialogHolder.showDialog();
	}
}