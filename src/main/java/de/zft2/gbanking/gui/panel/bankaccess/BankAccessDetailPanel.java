package de.zft2.gbanking.gui.panel.bankaccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessFints;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.dialog.BankAccessParameterDataDialog;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.ButtonContext;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.zft2.gbanking.gui.panel.action.PinAskDialog;
import de.zft2.gbanking.gui.panel.overview.BankAccessOverviewPanel;
import de.zft2.gbanking.gui.util.DetailFormEditMode;
import de.zft2.gbanking.gui.util.FormFields;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.hbci.ChipTanUsbSupport;
import de.zft2.gbanking.hbci.TanProcedureSupport;
import de.zft2.gbanking.hbci.TanProcedureSupport.SupportedTanProcedure;
import de.zft2.gbanking.paypal.PaypalSupport;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.util.TypeConverter;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
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
	private final Button buttonEnablebankingNew = new Button(getText("UI_BUTTON_ENABLEBANKING_NEW"));
	private final Button buttonBankAccessUpdate = new Button(getText("UI_BUTTON_BANK_ACCESS_UPDATE"));
	private final Button buttonBankAccessEdit = new Button(getText("UI_BUTTON_BANK_ACCESS_EDIT"));
	private final Button buttonBankAccessSave = new Button(getText("UI_BUTTON_SAVE"));
	private final Button buttonBankAccessCancel = new Button(getText("UI_BUTTON_CANCEL"));
	private final Button buttonBankAccessDelete = new Button(getText("UI_BUTTON_BANK_ACCESS_DELETE"));
	private final Button buttonBankAccessRefreshParameterData = new Button(getText("UI_BUTTON_BANK_ACCESS_REFRESH_PARAMETER_DATA"));
	private final Button buttonBankAccessShowBpd = new Button(getText("UI_BUTTON_BANK_ACCESS_SHOW_BPD"));
	private final Button buttonBankAccessShowUpd = new Button(getText("UI_BUTTON_BANK_ACCESS_SHOW_UPD"));

	private final List<Control> editableControls = List.of(blzText, bankNameText, userNameText, customerIdText, urlText, portText, systemIdText,
			tanProcedureBox, hbciVersionText, hbciFilterTypeBox, bpdVersionText, updVersionText, activeBox);

	private final BankAccessOverviewPanel parentPanel;

	private BankAccess currentBankAccess;
	private DetailFormEditMode editModeController;

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
		KeyboardShortcutDispatcher.registerForm(this, buttonBankAccessSave, buttonBankAccessCancel);
		KeyboardShortcutDispatcher.blockRefreshWhile(this, buttonBankAccessSave::isVisible);
		addContentNode(createButtonBar());
		editModeController = new DetailFormEditMode(editableControls, List.of(updatedAtText), List.of(buttonBankAccessNew, buttonEnablebankingNew),
				List.of(buttonBankAccessUpdate, buttonBankAccessEdit, buttonBankAccessDelete, buttonBankAccessRefreshParameterData,
						buttonBankAccessShowBpd, buttonBankAccessShowUpd),
				List.of(buttonBankAccessSave, buttonBankAccessCancel));
		setEditMode(false);
	}

	private HBox createButtonBar() {
		FormStyleUtils.styleButtons(buttonBankAccessNew, buttonEnablebankingNew, buttonBankAccessUpdate, buttonBankAccessEdit, buttonBankAccessSave, buttonBankAccessCancel,
				buttonBankAccessDelete, buttonBankAccessRefreshParameterData, buttonBankAccessShowBpd, buttonBankAccessShowUpd);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox buttonBar = new HBox(10, buttonBankAccessNew, buttonEnablebankingNew, buttonBankAccessUpdate, buttonBankAccessEdit, buttonBankAccessSave,
				buttonBankAccessCancel, buttonBankAccessDelete, buttonBankAccessRefreshParameterData, spacer, buttonBankAccessShowBpd, buttonBankAccessShowUpd);
		buttonBar.setAlignment(Pos.CENTER_LEFT);
		buttonBar.setMaxWidth(Double.MAX_VALUE);
		buttonBar.getStyleClass().add("gbanking-button-bar");
		return buttonBar;
	}

	private void configureComboBoxes() {
		tanProcedureBox.setMaxWidth(Double.MAX_VALUE);
		tanProcedureBox.setConverter(createDisplayConverter());
		tanProcedureBox.setCellFactory(comboBox -> createTanProcedureCell());
		tanProcedureBox.setButtonCell(createTanProcedureCell());

		hbciFilterTypeBox.setItems(FXCollections.observableArrayList(HbciEncodingFilterType.values()));
		hbciFilterTypeBox.setMaxWidth(Double.MAX_VALUE);
		hbciFilterTypeBox.setConverter(createDisplayConverter());
	}

	private <T> StringConverter<T> createDisplayConverter() {
		return new StringConverter<>() {
			@Override
			public String toString(T value) {
				return value == null ? "" : value.toString();
			}

			@Override
			public T fromString(String string) {
				return null;
			}
		};
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
		buttonEnablebankingNew.setOnAction(e -> new EnablebankingSetupDialog(getOwnerWindow(),
				parentPanel.getBankAccessListPanel()::refreshModelBankAccess).show());
		buttonBankAccessUpdate.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_EDIT));
		buttonBankAccessEdit.setOnAction(e -> enableManualEditWithConfirmation());
		buttonBankAccessSave.setOnAction(e -> saveManualChanges());
		buttonBankAccessCancel.setOnAction(e -> cancelManualChanges());
		buttonBankAccessDelete.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_DELETE));
		buttonBankAccessRefreshParameterData.setOnAction(e -> refreshParameterData());
		buttonBankAccessShowBpd.setOnAction(e -> showParameterData("BPD", Bpd.class));
		buttonBankAccessShowUpd.setOnAction(e -> showParameterData("UPD", Upd.class));
	}

	private void refreshParameterData() {
		if (currentBankAccess == null || currentBankAccess.getAccessType() != BankAccessType.HBCI) {
			return;
		}

		PinAskDialog pinWindow = new PinAskDialog(getOwnerWindow());
		pinWindow.setBankInfo(currentBankAccess.getFints().getBlz(), currentBankAccess.getBankName());
		Stage pinDialog = pinWindow.createNewPinAskDialog();
		pinDialog.showAndWait();

		char[] pin = pinWindow.getPin();
		if (pin == null || pin.length == 0) {
			return;
		}

		buttonBankAccessRefreshParameterData.setDisable(true);
		Task<Boolean> refreshTask = new Task<>() {
			@Override
			protected Boolean call() {
				try {
					BankAccessService bankAccessService = ServiceRegistry.getService(BankAccessService.class);
					return bankAccessService.refreshBankAccessParameterData(currentBankAccess, pin);
				} finally {
					Arrays.fill(pin, '\0');
				}
			}
		};

		refreshTask.setOnSucceeded(event -> handleParameterDataRefreshFinished(Boolean.TRUE.equals(refreshTask.getValue())));
		refreshTask.setOnFailed(event -> {
			buttonBankAccessRefreshParameterData.setDisable(false);
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ERROR_BANK_ACCESS_REFRESH"));
		});
		refreshTask.setOnCancelled(event -> {
			Arrays.fill(pin, '\0');
			buttonBankAccessRefreshParameterData.setDisable(false);
		});

		BackgroundActionCoordinator.getInstance().start(refreshTask, "gbanking-hbci-refresh-bank-access-parameter-data");
	}

	private void handleParameterDataRefreshFinished(boolean success) {
		buttonBankAccessRefreshParameterData.setDisable(false);
		if (!success) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ERROR_BANK_ACCESS_REFRESH"));
			return;
		}

		parentPanel.getBankAccessListPanel().refreshModelBankAccess();
		BankAccess refreshedBankAccess = dbController.getBankAccessById(currentBankAccess.getId());
		if (refreshedBankAccess != null) {
			refreshedBankAccess.setAccounts(dbController.getAllByParent(de.zft2.gbanking.db.dao.BankAccount.class, refreshedBankAccess.getId()));
			updatePanelFieldValues(refreshedBankAccess);
		}
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.INFORMATION, getText("UI_INFO_BANK_ACCESS_REFRESH_SUCCESS"));
	}

	private <T extends ParameterDataBankAccess> void showParameterData(String parameterType, Class<T> dataType) {
		if (currentBankAccess != null && currentBankAccess.getAccessType() == BankAccessType.HBCI) {
			List<ParameterDataBankAccess> parameterData = new ArrayList<>(dbController.getAllByParent(dataType, currentBankAccess.getId()));
			new BankAccessParameterDataDialog().show(getOwnerWindow(), parameterType, currentBankAccess, parameterData);
		}
	}

	public void updatePanelFieldValues(BankAccess selectedAccess) {
		currentBankAccess = selectedAccess;
		parentPanel.setCurrentBankAccess(selectedAccess);
		fillForm(selectedAccess);
		setEditMode(false);
		configureProviderActions(selectedAccess);
	}

	private void fillForm(BankAccess access) {
		updateTitle(access.getBankName());
		boolean paypal = PaypalSupport.isPaypal(access);
		boolean fints = access.getAccessType() == BankAccessType.HBCI;
		BankAccessFints fintsData = access.getFints();

		blzText.setText(paypal ? PaypalSupport.DISPLAY_NAME : fints ? fintsData.getBlz() : "Enablebanking");
		bankNameText.setText(access.getBankName());
		userNameText.setText(fints ? fintsData.getUserId() : paypal ? access.getPaypal().getUserId() : "");
		customerIdText.setText(fints ? fintsData.getCustomerId() : "");

		urlText.setText(fints ? fintsData.getHbciURL() : "");
		portText.setText(fints && fintsData.getPort() != null ? String.valueOf(fintsData.getPort()) : "");
		systemIdText.setText(fints ? fintsData.getSysId() : "");

		refreshSupportedTanProcedures(fints ? access : null);
		tanProcedureBox.setValue(fints ? fintsData.getTanProcedure() : null);

		hbciVersionText.setText(fints ? fintsData.getHbciVersion() : "");
		hbciFilterTypeBox.setValue(fints ? fintsData.getFilterType() : null);
		bpdVersionText.setText(fints ? fintsData.getBpdVersion() : "");
		updVersionText.setText(fints ? fintsData.getUpdVersion() : "");
		activeBox.setSelected(access.isActive());
		updatedAtText.setText(TypeConverter.toDateStringLong(access.getUpdatedAt()));
	}

	private void configureProviderActions(BankAccess access) {
		boolean fints = access.getAccessType() == BankAccessType.HBCI;
		buttonBankAccessUpdate.setDisable(access.getAccessType() == BankAccessType.ENABLEBANKING);
		buttonBankAccessEdit.setDisable(!fints);
		setVisibleAndManaged(buttonBankAccessRefreshParameterData, fints);
		setVisibleAndManaged(buttonBankAccessShowBpd, fints);
		setVisibleAndManaged(buttonBankAccessShowUpd, fints);
	}

	private void setVisibleAndManaged(Control control, boolean visible) {
		control.setVisible(visible);
		control.setManaged(visible);
	}

	private void applyFormTo(BankAccess access) {
		BankAccessFints fints = access.getFints();
		fints.setBlz(trimToNull(blzText.getText()));
		access.setBankName(trimToNull(bankNameText.getText()));
		fints.setUserId(trimToNull(userNameText.getText()));
		fints.setCustomerId(trimToNull(customerIdText.getText()));

		fints.setHbciURL(trimToNull(urlText.getText()));
		fints.setPort(parseAndValidatePostiveInt(portText.getText()));
		fints.setSysId(trimToNull(systemIdText.getText()));
		fints.setTanProcedure(tanProcedureBox.getValue());

		fints.setHbciVersion(trimToNull(hbciVersionText.getText()));
		fints.setFilterType(hbciFilterTypeBox.getValue());
		fints.setBpdVersion(trimToNull(bpdVersionText.getText()));
		fints.setUpdVersion(trimToNull(updVersionText.getText()));
		access.setActive(activeBox.isSelected());
	}

	private void enableManualEditWithConfirmation() {
		if (currentBankAccess == null) {
			return;
		}

		if (createDialogHolder(ButtonContext.BUTTON_EDIT).showManualEditConfirmationDialog()) {
			setEditMode(true);
		}
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
		BankAccessDialogHolder dialogHolder = createDialogHolder(ButtonContext.BUTTON_EDIT);

		if (isBlank(blzText.getText()) || isBlank(bankNameText.getText()) || isBlank(urlText.getText()) || isBlank(userNameText.getText())) {
			dialogHolder.showRequiredFieldsWarningDialog();
			return false;
		}

		if (parseAndValidatePostiveInt(portText.getText()) == null && !isBlank(portText.getText())) {
			dialogHolder.showInvalidPortWarningDialog();
			return false;
		}

		return true;
	}

	private void refreshSupportedTanProcedures(BankAccess access) {
		List<TanProcedure> procedures = new ArrayList<>(determineSupportedTanProcedures(access));
		TanProcedure selectedProcedure = access != null ? access.getFints().getTanProcedure() : null;

		if (selectedProcedure != null && !procedures.contains(selectedProcedure)) {
			procedures.add(selectedProcedure);
		}

		procedures.sort(Comparator.comparingInt(this::tanCode));
		tanProcedureBox.setItems(FXCollections.observableArrayList(procedures));
	}

	private List<TanProcedure> determineSupportedTanProcedures(BankAccess access) {
		if (access == null) {
			return List.of();
		}

		List<Bpd> bpd = access.getId() > 0 ? dbController.getAllByParent(Bpd.class, access.getId()) : List.of();
		List<Upd> upd = access.getId() > 0 ? dbController.getAllByParent(Upd.class, access.getId()) : List.of();
		return TanProcedureSupport.determineSupportedProcedures(access, bpd, upd).stream()
				.map(SupportedTanProcedure::procedure)
				.distinct()
				.sorted(Comparator.comparingInt(this::tanCode))
				.toList();
	}

	private int tanCode(TanProcedure procedure) {
		return procedure.getCode();
	}

	private ListCell<TanProcedure> createTanProcedureCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(TanProcedure item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? "" : item.toString());
				setDisable(!empty && isTanProcedureUnavailable(item));
			}
		};
	}

	private boolean isTanProcedureUnavailable(TanProcedure procedure) {
		return procedure != null && procedure.requiresConfiguredCardReader() && !ChipTanUsbSupport.isEnabled();
	}

	private void setEditMode(boolean editMode) {
		editModeController.apply(editMode, currentBankAccess != null);
	}

	private BankAccessDialogHolder createDialogHolder(ButtonContext buttonContext) {
		return new BankAccessDialogHolder(buttonContext, parentPanel);
	}

	private void newBankAccessDialog(ButtonContext buttonContext) {
		createDialogHolder(buttonContext).showDialog();
	}
}
