package de.zft2.gbanking.gui.panel.moneytransfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.cache.InstituteLookupCache.InstituteLookupEntry;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.component.BankNameLookupField;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dto.MoneyTransferForm;
import de.zft2.gbanking.gui.panel.AbstractTitledFormPanel;
import de.zft2.gbanking.gui.util.FormControlUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import de.zft2.gbanking.service.moneytransfer.BankOrderOperation;
import de.zft2.gbanking.util.IbanCalculator;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

public abstract class MoneyTransferInputBasePanel extends AbstractTitledFormPanel {

	private static final String UI_LABEL_AMOUNT = "UI_LABEL_AMOUNT";
	private static final String UI_BUTTON_YES = "UI_BUTTON_YES";
	private static final String UI_BUTTON_NO = "UI_BUTTON_NO";

	private static final double BIC_COLUMN_WIDTH = 110.0;
	private static final double CURRENCY_COLUMN_WIDTH = 90.0;
	private static final double BANK_COLUMN_WIDTH = 245.0;
	private static final Pattern PLAIN_AMOUNT_PATTERN = Pattern.compile("[+-]?\\d+(?:,\\d{0,2})?");
	private static final Pattern GERMAN_GROUPED_AMOUNT_PATTERN = Pattern.compile("[+-]?\\d{1,3}(?:\\.\\d{3})+(?:,\\d{0,2})?");
	private static final Pattern LEGACY_DECIMAL_AMOUNT_PATTERN = Pattern.compile("[+-]?\\d+\\.\\d{1,2}");

	protected final TextField tfRecipientName = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	protected final TextField tfIBAN = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	protected final TextField tfBIC = FormStyleUtils.applyWidth(new TextField(), FieldWidth.XS);
	protected final BankNameLookupField bankNameLookupField = new BankNameLookupField();
	protected final TextField tfAmount = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	protected final TextArea tfPurpose = FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);

	protected final Button buttonSubmit = new Button();
	private final Button buttonDelete = new Button();

	private final StackPane currencyFieldHolder = new StackPane(new Label(getText("UI_LABEL_CURRENCY_EUR")));
	private final MoneyTransferDetailListTabPanel parentPanel;
	private MoneyTransfer currentMoneytransfer;
	private boolean specificFieldsInitialized = false;
	private boolean updatingBicProgrammatically = false;
	private boolean bicProtectedFromLookup = false;
	private boolean bankNameLookupSuspendedUntilBankCodeFocus = false;
	private String lastIbanCalculationPromptKey;

	protected MoneyTransferInputBasePanel(MoneyTransferDetailListTabPanel parentPanel) {
		super("UI_LABEL_SENDER_ACCOUNT");
		this.parentPanel = parentPanel;
		createBasePanel();
	}

	private void createBasePanel() {
		configureFormGridColumns();
		FormControlUtils.prepareWrapping(tfPurpose, 3);

		addFieldAbove("UI_LABEL_TRANSFER_RECIPIENT", tfRecipientName, 0, 0, 3);
		addFieldAbove("UI_LABEL_TRANSFER_IBAN", tfIBAN, 0, 1, 3);
		addFieldAbove("UI_LABEL_BIC_OR_BLZ", tfBIC, 0, 2);
		addFieldAbove("UI_LABEL_BANK", bankNameLookupField, 1, 2, 2);
		addFieldAbove("UI_LABEL_CURRENCY", currencyFieldHolder, 1, 3);
		addFieldAbove(UI_LABEL_AMOUNT, tfAmount, 2, 3);
		addFieldAbove("UI_LABEL_PURPOSE", tfPurpose, 0, 4, 3);
		currencyFieldHolder.setAlignment(Pos.CENTER_LEFT);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonNew.setOnAction(e -> resetTextFields());

		buttonSubmit.setOnAction(e -> saveTransfer());

		tfIBAN.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (Boolean.FALSE.equals(newValue)) {
				updateBankDataFromIban(false);
				maybePromptForIbanCalculationAfterFocusChange();
			}
		});
		tfBIC.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (Boolean.TRUE.equals(newValue)) {
				bankNameLookupSuspendedUntilBankCodeFocus = false;
				updateBankDataFromBankCodeField();
			} else if (Boolean.FALSE.equals(newValue)) {
				maybePromptForIbanCalculationAfterFocusChange();
			}
		});
		bankNameLookupField.selectedEntryProperty().addListener((observable, oldValue, newValue) -> applyLookupBicIfAllowed(newValue));
		tfBIC.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!updatingBicProgrammatically) {
				bicProtectedFromLookup = !isBlank(newValue);
				if (!bankNameLookupSuspendedUntilBankCodeFocus) {
					updateBankDataFromBankCodeField();
				}
			}
		});
		tfAmount.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (Boolean.FALSE.equals(newValue)) {
				formatAmountField();
			}
		});

		buttonDelete.setText(getText("UI_BUTTON_DELETE"));
		buttonDelete.setOnAction(e -> deleteTransfer());
		buttonDelete.setDisable(true);

		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));
		buttonCancel.setOnAction(e -> resetTextFields());
		KeyboardShortcutDispatcher.registerForm(this, buttonSubmit, buttonCancel);

		HBox buttonBar = FormStyleUtils.createButtonBar(buttonNew, buttonSubmit, buttonDelete, buttonCancel);
		addContentNode(buttonBar);
	}

	protected final void initializeSpecificFields() {
		if (specificFieldsInitialized) {
			return;
		}
		addSpecificFields();
		specificFieldsInitialized = true;
	}

	protected abstract void addSpecificFields();

	protected void saveTransfer() {
		BankAccount account = parentPanel.getSelectedAccount();
		MoneyTransfer existingMoneyTransfer = currentMoneytransfer;
		if (existingMoneyTransfer != null && existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_MONEYTRANSFER_DELETE_PENDING_EDIT"));
			return;
		}
		if (isArchivedMoneyTransfer(existingMoneyTransfer)) {
			if (!confirmCreateNewTransferForArchivedOrder()) {
				return;
			}
			existingMoneyTransfer = null;
		}

		BankOrderOperation operation = bean.isBankManagedOrder(existingMoneyTransfer) ? BankOrderOperation.EDIT : BankOrderOperation.CREATE;
		if (!bean.supportsBankOrderOperation(account, getOrderType(), operation)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_MONEYTRANSFER_ORDER_TYPE_NOT_SUPPORTED"));
			return;
		}

		String validationError = validateTransferInput(account);
		if (validationError != null) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, validationError);
			return;
		}

		formatAmountField();
		MoneyTransferForm moneyTransfer = buildMoneyTransferForm(account);

		currentMoneytransfer = bean.saveMoneyTransferToDB(moneyTransfer, existingMoneyTransfer);
		parentPanel.reloadListPanels();
		updateDeleteButtonState();
	}

	private boolean confirmCreateNewTransferForArchivedOrder() {
		ButtonType yesButton = new ButtonType(getText(UI_BUTTON_YES), ButtonBar.ButtonData.YES);
		ButtonType noButton = new ButtonType(getText(UI_BUTTON_NO), ButtonBar.ButtonData.NO);
		return DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.WARNING, getText("ALERT_MONEYTRANSFER_ARCHIVED_CREATE_NEW_TITLE"),
				getText("ALERT_MONEYTRANSFER_ARCHIVED_CREATE_NEW_HEADER"), getText("ALERT_MONEYTRANSFER_ARCHIVED_CREATE_NEW_TEXT"), yesButton, noButton);
	}

	static boolean isArchivedMoneyTransfer(MoneyTransfer moneyTransfer) {
		return moneyTransfer != null && moneyTransfer.getMoneytransferStatus() != null && moneyTransfer.getMoneytransferStatus().isArchiveStatus();
	}

	private String validateTransferInput(BankAccount account) {
		if (account == null) {
			return requiredFieldMissingMessage("UI_LABEL_SENDER_ACCOUNT");
		}
		if (!validateInputElement(tfRecipientName)) {
			return requiredFieldMissingMessage("UI_LABEL_TRANSFER_RECIPIENT");
		}
		if (!hasRequiredRecipientAccountIdentifier()) {
			return requiredFieldMissingMessage(getRecipientAccountIdentifierLabel());
		}
		if (!validateInputElement(tfAmount)) {
			return requiredFieldMissingMessage(UI_LABEL_AMOUNT);
		}
		if (!validateInputElement(tfPurpose)) {
			return requiredFieldMissingMessage("UI_LABEL_PURPOSE");
		}

		try {
			parseAmountInput(tfAmount.getText());
		} catch (NumberFormatException ex) {
			return requiredFieldMissingMessage(UI_LABEL_AMOUNT);
		}

		return validateSpecificInput();
	}

	private boolean validateInputElement(TextInputControl textField) {
		return textField.getText() != null && !textField.getText().isBlank();
	}


	protected void deleteTransfer() {
		if (currentMoneytransfer == null || isArchivedMoneyTransfer(currentMoneytransfer)) {
			return;
		}
		if (currentMoneytransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			currentMoneytransfer = bean.cancelBankOrderDeletion(currentMoneytransfer);
			parentPanel.reloadListPanels();
			updatePanelFieldValues(currentMoneytransfer);
			return;
		}
		if (bean.isBankManagedOrder(currentMoneytransfer)) {
			BankAccount account = parentPanel.getSelectedAccount();
			if (!bean.supportsBankOrderOperation(account, currentMoneytransfer.getOrderType(), BankOrderOperation.DELETE)) {
				DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_MONEYTRANSFER_ORDER_TYPE_NOT_SUPPORTED"));
				return;
			}
			if (!confirmBankOrderDeletion()) {
				return;
			}
			currentMoneytransfer = bean.requestBankOrderDeletion(currentMoneytransfer);
			parentPanel.reloadListPanels();
			updatePanelFieldValues(currentMoneytransfer);
			return;
		}
		bean.deleteMoneyTransferFromDB(currentMoneytransfer);
		resetTextFields();
		parentPanel.reloadListPanels();
	}

	private boolean confirmBankOrderDeletion() {
		ButtonType yesButton = new ButtonType(getText(UI_BUTTON_YES), ButtonBar.ButtonData.YES);
		ButtonType noButton = new ButtonType(getText(UI_BUTTON_NO), ButtonBar.ButtonData.NO);
		return DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.WARNING, getText("ALERT_MONEYTRANSFER_BANK_DELETE_CONFIRM_TITLE"),
				getText("ALERT_MONEYTRANSFER_BANK_DELETE_CONFIRM_HEADER"), getText("ALERT_MONEYTRANSFER_BANK_DELETE_CONFIRM_TEXT"), yesButton,
				noButton);
	}

	protected void resetTextFields() {
		currentMoneytransfer = null;
		bankNameLookupSuspendedUntilBankCodeFocus = false;
		lastIbanCalculationPromptKey = null;
		FormControlUtils.clearTextInputs(List.of(tfRecipientName, tfIBAN, tfAmount, tfPurpose));
		setBicText("", false);
		bankNameLookupField.clear();
		resetSpecificFields();
		updateDeleteButtonState();
		refreshCapabilityState(parentPanel.getSelectedAccount());
	}

	void updatePanelFieldValues(MoneyTransfer selectedMoneytransfer) {
		currentMoneytransfer = selectedMoneytransfer;
		tfAmount.setText(formatAmountForDisplay(selectedMoneytransfer.getAmount()));
		tfPurpose.setText(selectedMoneytransfer.getPurpose());
		updateSpecificFieldValues(selectedMoneytransfer);

		if (selectedMoneytransfer.getRecipient() != null) {
			updatePanelFieldValues(selectedMoneytransfer.getRecipient());
		}
		refreshCapabilityState(parentPanel.getSelectedAccount());
		updateDeleteButtonState();
	}

	public void updatePanelFieldValues(Recipient selectedRecipient) {
		tfRecipientName.setText(selectedRecipient.getName());
		tfIBAN.setText(selectedRecipient.getIban());
		String bic = trimToNull(selectedRecipient.getBic());
		String bicOrBlz = bic != null ? bic : trimToNull(selectedRecipient.getBlz());
		setBicText(bicOrBlz, bic != null);

		String bankName = trimToNull(selectedRecipient.getBank());
		if (bankName != null) {
			bankNameLookupSuspendedUntilBankCodeFocus = true;
			bankNameLookupField.setManualBankName(bankName);
			return;
		}

		bankNameLookupSuspendedUntilBankCodeFocus = false;
		bankNameLookupField.clear();
		if (!updateBankDataFromBankCodeField()) {
			updateBankDataFromIban(true);
		}
	}

	public void updatePanelFieldValues(BankAccount selectedAccount) {
		if (selectedAccount == null) {
			resetTitle();
			resetTextFields();
			refreshCapabilityState(null);
			return;
		}
		if (currentMoneytransfer != null && currentMoneytransfer.getAccountId() != selectedAccount.getId()) {
			resetTextFields();
		}
		updateTitle(selectedAccount.getAccountName());
		refreshCapabilityState(selectedAccount);
	}

	public void refreshCapabilityState(BankAccount selectedAccount) {
		if (currentMoneytransfer != null && currentMoneytransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			setCapabilityEnabled(false);
			return;
		}
		BankOrderOperation operation = bean.isBankManagedOrder(currentMoneytransfer) ? BankOrderOperation.EDIT : BankOrderOperation.CREATE;
		setCapabilityEnabled(bean.supportsBankOrderOperation(selectedAccount, getOrderType(), operation));
	}

	private void updateDeleteButtonState() {
		boolean deletionPending = currentMoneytransfer != null
				&& currentMoneytransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING;
		buttonDelete.setText(getText(deletionPending ? "UI_BUTTON_CANCEL_BANK_DELETION" : "UI_BUTTON_DELETE"));
		buttonDelete.setDisable(currentMoneytransfer == null || isArchivedMoneyTransfer(currentMoneytransfer));
	}

	public void prefillFromBookingTemplate(Booking booking) {
		BankAccount account = parentPanel.getSelectedAccount();
		resetTextFields();
		if (account != null) {
			updatePanelFieldValues(account);
		}
		if (booking == null) {
			return;
		}

		tfAmount.setText(formatTemplateAmount(booking.getAmount()));
		tfPurpose.setText(booking.getPurpose() != null ? booking.getPurpose() : "");

		Recipient recipient = resolveTemplateRecipient(booking);
		if (recipient != null) {
			updatePanelFieldValues(recipient);
		}
	}

	private String formatTemplateAmount(BigDecimal amount) {
		return formatAmountForDisplay(amount != null ? amount.abs() : null);
	}

	private void formatAmountField() {
		String amountText = tfAmount.getText();
		if (amountText == null || amountText.isBlank()) {
			return;
		}
		try {
			tfAmount.setText(formatAmountForDisplay(parseAmountInput(amountText)));
		} catch (NumberFormatException ex) {
			// Keep invalid user input visible so validation can point to the amount field.
		}
	}

	static BigDecimal parseAmountInput(String amountText) {
		String normalized = normalizeAmountInput(amountText);
		if (normalized == null) {
			throw new NumberFormatException("Amount is blank");
		}

		String decimalAmount;
		if (GERMAN_GROUPED_AMOUNT_PATTERN.matcher(normalized).matches()) {
			decimalAmount = toDecimalAmount(normalized.replace(".", ""));
		} else if (PLAIN_AMOUNT_PATTERN.matcher(normalized).matches()) {
			decimalAmount = toDecimalAmount(normalized);
		} else if (LEGACY_DECIMAL_AMOUNT_PATTERN.matcher(normalized).matches()) {
			decimalAmount = normalized;
		} else {
			throw new NumberFormatException("Invalid amount: " + amountText);
		}
		return new BigDecimal(decimalAmount).setScale(2);
	}

	static String formatAmountForDisplay(BigDecimal amount) {
		if (amount == null) {
			return "";
		}
		return createAmountFormat().format(amount.setScale(2, RoundingMode.HALF_UP));
	}

	private static String normalizeAmountInput(String amountText) {
		if (amountText == null) {
			return null;
		}
		String normalized = amountText.trim().replace("\u00A0", "").replace(" ", "");
		return normalized.isBlank() ? null : normalized;
	}

	private static String toDecimalAmount(String amountText) {
		String decimalAmount = amountText.replace(',', '.');
		return decimalAmount.endsWith(".") ? decimalAmount + "00" : decimalAmount;
	}

	private static DecimalFormat createAmountFormat() {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
		symbols.setGroupingSeparator('.');
		return new DecimalFormat("#,##0.00;-#,##0.00", symbols);
	}

	private Recipient resolveTemplateRecipient(Booking booking) {
		if (booking.getRecipient() != null) {
			return booking.getRecipient();
		}
		return booking.getRecipientId() > 0 ? dbController.getByIdFull(Recipient.class, booking.getRecipientId()) : null;
	}

	private void updateBankDataFromIban(boolean ibanPrefilled) {
		updateBankDataFromIban(ibanPrefilled, false);
	}

	private void updateBankDataFromIban(boolean ibanPrefilled, boolean replaceBlzWithLookupBic) {
		if (bankNameLookupSuspendedUntilBankCodeFocus) {
			return;
		}

		String blz = InstituteLookupCache.extractGermanBlzFromIban(tfIBAN.getText());
		if (blz == null) {
			if (ibanPrefilled && isBlank(bankNameLookupField.getSelectedBankName())) {
				bankNameLookupField.clear();
			}
			return;
		}

		List<InstituteLookupEntry> entries = InstituteLookupCache.getEntriesForBlz(blz);
		if (entries.isEmpty()) {
			if (ibanPrefilled && isBlank(bankNameLookupField.getSelectedBankName())) {
				bankNameLookupField.clear();
			}
			return;
		}

		bankNameLookupField.setEntries(entries);
		applyLookupBicIfAllowed(entries.get(0), replaceBlzWithLookupBic);
	}

	private boolean updateBankDataFromBankCodeField() {
		List<InstituteLookupEntry> entries = InstituteLookupCache.getEntriesForBankCode(tfBIC.getText());
		if (entries.isEmpty()) {
			bankNameLookupField.clear();
			return false;
		}

		bankNameLookupField.setEntries(entries);
		return true;
	}

	private void applyLookupBicIfAllowed(InstituteLookupEntry selectedEntry) {
		applyLookupBicIfAllowed(selectedEntry, false);
	}

	private void applyLookupBicIfAllowed(InstituteLookupEntry selectedEntry, boolean replaceBlzWithLookupBic) {
		if (selectedEntry == null || isBlank(selectedEntry.bic())) {
			return;
		}
		if (bicProtectedFromLookup && !(replaceBlzWithLookupBic && InstituteLookupCache.isBlzCandidate(tfBIC.getText()))) {
			return;
		}
		setBicText(selectedEntry.bic(), false);
	}

	private void maybePromptForIbanCalculationAfterFocusChange() {
		Platform.runLater(() -> {
			if (!tfIBAN.isFocused() && !tfBIC.isFocused()) {
				promptForIbanCalculationIfApplicable();
			}
		});
	}

	private void promptForIbanCalculationIfApplicable() {
		String accountNumber = normalizeAccountNumberCandidate(tfIBAN.getText());
		String blz = InstituteLookupCache.normalizeBlzCandidate(tfBIC.getText());
		if (accountNumber == null || blz == null) {
			return;
		}

		String promptKey = accountNumber + "|" + blz;
		if (promptKey.equals(lastIbanCalculationPromptKey)) {
			return;
		}
		lastIbanCalculationPromptKey = promptKey;

		String calculatedIban = IbanCalculator.calculateIban(accountNumber, blz);
		if (calculatedIban == null) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_IBAN_CALCULATION_FAILED"));
			return;
		}

		ButtonType yesButton = new ButtonType(getText(UI_BUTTON_YES), ButtonBar.ButtonData.YES);
		ButtonType noButton = new ButtonType(getText(UI_BUTTON_NO), ButtonBar.ButtonData.NO);
		boolean shouldApplyIban = DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.INFORMATION, getText("ALERT_IBAN_CALCULATION_TITLE"),
				getText("ALERT_IBAN_CALCULATION_HEADER"), getText("ALERT_IBAN_CALCULATION_TEXT", accountNumber, blz, calculatedIban), yesButton, noButton);
		if (shouldApplyIban) {
			tfIBAN.setText(calculatedIban);
			updateBankDataFromIban(false, true);
		}
	}

	static boolean isAccountNumberCandidate(String value) {
		return normalizeAccountNumberCandidate(value) != null;
	}

	static boolean isBlzCandidate(String value) {
		return InstituteLookupCache.isBlzCandidate(value);
	}

	static boolean isBicCandidate(String value) {
		return InstituteLookupCache.isBicCandidate(value);
	}

	private static String normalizeAccountNumberCandidate(String value) {
		String trimmed = value != null ? value.trim() : null;
		return trimmed != null && trimmed.matches("\\d{1,10}") ? trimmed : null;
	}

	private void setBicText(String bic, boolean protectFromLookup) {
		updatingBicProgrammatically = true;
		try {
			tfBIC.setText(bic == null ? "" : bic);
			bicProtectedFromLookup = protectFromLookup;
		} finally {
			updatingBicProgrammatically = false;
		}
	}

	public OrderType getOrderType() {
		return OrderType.TRANSFER;
	}

	protected LocalDate getExecutionDate() {
		return LocalDate.now(ZoneId.systemDefault());
	}

	protected Integer getExecutionDay() {
		return null;
	}

	protected StandingorderMode getStandingorderMode() {
		return null;
	}

	protected String validateSpecificInput() {
		return null;
	}

	protected String requiredFieldMissingMessage(String fieldLabelKey) {
		return getText("ALERT_MONEYTRANSFER_REQUIRED_FIELD_MISSING_DETAIL", getText(fieldLabelKey));
	}

	protected String getRecipientAccountIdentifierLabel() {
		return "UI_LABEL_TRANSFER_IBAN";
	}

	protected boolean hasRequiredRecipientAccountIdentifier() {
		return validateInputElement(tfIBAN);
	}

	protected void resetSpecificFields() {
		// default: no-op
	}

	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		// default: no-op
	}

	protected void setCapabilityEnabled(boolean enabled) {
		for (Node node : getCapabilityNodes()) {
			node.setDisable(!enabled);
		}
		buttonSubmit.setDisable(!enabled);
	}

	protected List<Node> getSpecificCapabilityNodes() {
		return List.of();
	}

	private List<Node> getCapabilityNodes() {
		List<Node> nodes = new java.util.ArrayList<>(List.of(tfRecipientName, tfIBAN, tfBIC, bankNameLookupField, tfAmount, tfPurpose, currencyFieldHolder));
		nodes.addAll(getSpecificCapabilityNodes());
		return nodes;
	}

	protected final void setCurrencyField(javafx.scene.Node field) {
		currencyFieldHolder.getChildren().setAll(field);
	}

	protected String getCurrency() {
		return "EUR";
	}

	protected MoneyTransferForeign buildForeignTransferDetails() {
		return null;
	}

	private MoneyTransferForm buildMoneyTransferForm(BankAccount account) {
		String bicOrBlz = trimToNull(tfBIC.getText());
		String bic = InstituteLookupCache.isBlzCandidate(bicOrBlz) ? null : bicOrBlz;
		String blz = InstituteLookupCache.isBlzCandidate(bicOrBlz) ? bicOrBlz : null;
		Recipient recipient = new Recipient(tfRecipientName.getText().trim(), trimToNull(tfIBAN.getText()), bic, null, blz,
				trimToNull(bankNameLookupField.getSelectedBankName()), null);
		MoneyTransferForm form = new MoneyTransferForm(account, getOrderType(), recipient, parseAmountInput(tfAmount.getText()), tfPurpose.getText().trim(),
				getExecutionDate(), buildForeignTransferDetails());
		if (getStandingorderMode() != null)
			form.setStandingorderInfo(getExecutionDay(), getStandingorderMode());
		form.setRecipientBlz(blz);
		return form;
	}

	protected MoneyTransferDetailListTabPanel getParentPanel() {
		return parentPanel;
	}

	private void configureFormGridColumns() {
		formGrid.getColumnConstraints().setAll(createColumn(BIC_COLUMN_WIDTH, Priority.NEVER), createColumn(CURRENCY_COLUMN_WIDTH, Priority.NEVER),
				createColumn(BANK_COLUMN_WIDTH, Priority.ALWAYS));
	}

	private ColumnConstraints createColumn(double prefWidth, Priority growPriority) {
		ColumnConstraints constraints = new ColumnConstraints();
		constraints.setMinWidth(prefWidth);
		constraints.setPrefWidth(prefWidth);
		constraints.setHgrow(growPriority);
		return constraints;
	}
}
