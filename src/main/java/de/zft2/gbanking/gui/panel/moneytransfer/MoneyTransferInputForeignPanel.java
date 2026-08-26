package de.zft2.gbanking.gui.panel.moneytransfer;

import java.util.List;
import java.util.Locale;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class MoneyTransferInputForeignPanel extends MoneyTransferInputBasePanel {

	private final ComboBox<Currency> currencyCombo = FormStyleUtils.applyWidth(
			new ComboBox<>(FXCollections.observableArrayList(Currency.values())), FieldWidth.XS);
	private final TextField tfRecipientCountry = FormStyleUtils.applyWidth(new TextField(), FieldWidth.XS);
	private final TextField tfRecipientAccountNumber = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextField tfRecipientBankCode = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField tfRecipientSubAccount = FormStyleUtils.applyWidth(new TextField(), FieldWidth.XS);
	private final ComboBox<ForeignChargeBearer> chargeBearerCombo = FormStyleUtils
			.applyWidth(new ComboBox<>(FXCollections.observableArrayList(ForeignChargeBearer.values())), FieldWidth.M);
	private final TextField tfRecipientAddressLine1 = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	private final TextField tfRecipientAddressLine2 = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	private final TextField tfRecipientBankCountry = FormStyleUtils.applyWidth(new TextField(), FieldWidth.XS);
	private final TextField tfRecipientBankAddressLine1 = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	private final TextField tfRecipientBankAddressLine2 = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	private final TextField tfEndToEndReference = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextArea tfRegulatoryReporting = FormStyleUtils.prepareLargeTextArea(new TextArea(), 2);

	public MoneyTransferInputForeignPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		currencyCombo.setValue(Currency.EUR);
		chargeBearerCombo.setValue(ForeignChargeBearer.SHARED);
		setCurrencyField(currencyCombo);
		bankNameLookupField.setManualEntryEditable(true);
		tfIBAN.textProperty().addListener((observable, oldValue, newValue) -> prefillCountryFromIban());
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_FOREIGN_TRANSFER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_FOREIGN_RECIPIENT_COUNTRY", tfRecipientCountry, 0, 5);
		addFieldAbove("UI_LABEL_FOREIGN_RECIPIENT_ACCOUNT_NUMBER", tfRecipientAccountNumber, 1, 5, 2);
		addFieldAbove("UI_LABEL_FOREIGN_RECIPIENT_BANK_CODE", tfRecipientBankCode, 0, 6);
		addFieldAbove("UI_LABEL_FOREIGN_RECIPIENT_SUB_ACCOUNT", tfRecipientSubAccount, 1, 6);
		addFieldAbove("UI_LABEL_FOREIGN_CHARGE_BEARER", chargeBearerCombo, 2, 6);
		addFieldAbove("UI_LABEL_FOREIGN_RECIPIENT_ADDRESS_1", tfRecipientAddressLine1, 0, 7, 2);
		addFieldAbove("UI_LABEL_FOREIGN_RECIPIENT_ADDRESS_2", tfRecipientAddressLine2, 2, 7);
		addFieldAbove("UI_LABEL_FOREIGN_BANK_COUNTRY", tfRecipientBankCountry, 0, 8);
		addFieldAbove("UI_LABEL_FOREIGN_BANK_ADDRESS_1", tfRecipientBankAddressLine1, 1, 8, 2);
		addFieldAbove("UI_LABEL_FOREIGN_BANK_ADDRESS_2", tfRecipientBankAddressLine2, 0, 9, 2);
		addFieldAbove("UI_LABEL_FOREIGN_END_TO_END", tfEndToEndReference, 2, 9);
		addFieldAbove("UI_LABEL_FOREIGN_REGULATORY_REPORTING", tfRegulatoryReporting, 0, 10, 3);
	}

	@Override
	public OrderType getOrderType() {
		return OrderType.FOREIGN_TRANSFER;
	}

	@Override
	protected String getCurrency() {
		return currencyCombo.getValue() != null ? currencyCombo.getValue().name() : null;
	}

	@Override
	protected String validateSpecificInput() {
		if (currencyCombo.getValue() == null) {
			return requiredFieldMissingMessage("UI_LABEL_CURRENCY");
		}
		if (trimToNull(bankNameLookupField.getSelectedBankName()) == null) {
			return requiredFieldMissingMessage("UI_LABEL_BANK");
		}
		if (chargeBearerCombo.getValue() == null) {
			return requiredFieldMissingMessage("UI_LABEL_FOREIGN_CHARGE_BEARER");
		}
		return null;
	}

	@Override
	protected boolean hasRequiredRecipientAccountIdentifier() {
		return trimToNull(tfIBAN.getText()) != null || trimToNull(tfRecipientAccountNumber.getText()) != null;
	}

	@Override
	protected void resetSpecificFields() {
		currencyCombo.setValue(Currency.EUR);
		chargeBearerCombo.setValue(ForeignChargeBearer.SHARED);
		tfRecipientCountry.clear();
		tfRecipientAccountNumber.clear();
		tfRecipientBankCode.clear();
		tfRecipientSubAccount.clear();
		tfRecipientAddressLine1.clear();
		tfRecipientAddressLine2.clear();
		tfRecipientBankCountry.clear();
		tfRecipientBankAddressLine1.clear();
		tfRecipientBankAddressLine2.clear();
		tfEndToEndReference.clear();
		tfRegulatoryReporting.clear();
	}

	@Override
	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		currencyCombo.setValue(Currency.forCodeOrDefault(selectedMoneytransfer.getCurrency(), Currency.EUR));
		MoneyTransferForeign foreignTransfer = selectedMoneytransfer.getForeignTransfer();
		if (foreignTransfer == null) {
			return;
		}
		tfRecipientCountry.setText(defaultText(foreignTransfer.getRecipientCountry()));
		tfRecipientAccountNumber.setText(defaultText(foreignTransfer.getRecipientAccountNumber()));
		tfRecipientBankCode.setText(defaultText(foreignTransfer.getRecipientBankCode()));
		tfRecipientSubAccount.setText(defaultText(foreignTransfer.getRecipientSubAccount()));
		tfRecipientAddressLine1.setText(defaultText(foreignTransfer.getRecipientAddressLine1()));
		tfRecipientAddressLine2.setText(defaultText(foreignTransfer.getRecipientAddressLine2()));
		tfRecipientBankCountry.setText(defaultText(foreignTransfer.getRecipientBankCountry()));
		tfRecipientBankAddressLine1.setText(defaultText(foreignTransfer.getRecipientBankAddressLine1()));
		tfRecipientBankAddressLine2.setText(defaultText(foreignTransfer.getRecipientBankAddressLine2()));
		tfEndToEndReference.setText(defaultText(foreignTransfer.getEndToEndReference()));
		tfRegulatoryReporting.setText(defaultText(foreignTransfer.getRegulatoryReporting()));
		chargeBearerCombo.setValue(foreignTransfer.getChargeBearer() != null ? foreignTransfer.getChargeBearer() : ForeignChargeBearer.SHARED);
	}

	@Override
	protected List<Node> getSpecificCapabilityNodes() {
		return List.of(currencyCombo, tfRecipientCountry, tfRecipientAccountNumber, tfRecipientBankCode, tfRecipientSubAccount, chargeBearerCombo,
				tfRecipientAddressLine1, tfRecipientAddressLine2, tfRecipientBankCountry, tfRecipientBankAddressLine1, tfRecipientBankAddressLine2,
				tfEndToEndReference, tfRegulatoryReporting);
	}

	@Override
	public void updatePanelFieldValues(Recipient selectedRecipient) {
		super.updatePanelFieldValues(selectedRecipient);
		tfRecipientAccountNumber.setText(defaultText(selectedRecipient.getAccountNumber()));
		tfRecipientBankCode.setText(defaultText(selectedRecipient.getBlz()));
		prefillCountryFromIban();
	}

	@Override
	protected MoneyTransferForeign buildForeignTransferDetails() {
		MoneyTransferForeign foreignTransfer = new MoneyTransferForeign();
		foreignTransfer.setCurrency(currencyCombo.getValue() != null ? currencyCombo.getValue().name() : Currency.EUR.name());
		foreignTransfer.setRecipientCountry(normalizeCountry(trimToNull(tfRecipientCountry.getText())));
		foreignTransfer.setRecipientAccountNumber(trimToNull(tfRecipientAccountNumber.getText()));
		foreignTransfer.setRecipientBankCode(trimToNull(tfRecipientBankCode.getText()));
		foreignTransfer.setRecipientSubAccount(trimToNull(tfRecipientSubAccount.getText()));
		foreignTransfer.setRecipientAddressLine1(trimToNull(tfRecipientAddressLine1.getText()));
		foreignTransfer.setRecipientAddressLine2(trimToNull(tfRecipientAddressLine2.getText()));
		foreignTransfer.setRecipientBankCountry(normalizeCountry(trimToNull(tfRecipientBankCountry.getText())));
		foreignTransfer.setRecipientBankAddressLine1(trimToNull(tfRecipientBankAddressLine1.getText()));
		foreignTransfer.setRecipientBankAddressLine2(trimToNull(tfRecipientBankAddressLine2.getText()));
		foreignTransfer.setChargeBearer(chargeBearerCombo.getValue());
		foreignTransfer.setRegulatoryReporting(trimToNull(tfRegulatoryReporting.getText()));
		foreignTransfer.setEndToEndReference(trimToNull(tfEndToEndReference.getText()));
		return foreignTransfer;
	}

	private void prefillCountryFromIban() {
		if (trimToNull(tfRecipientCountry.getText()) != null) {
			return;
		}
		String iban = trimToNull(tfIBAN.getText());
		if (iban != null && iban.length() >= 2) {
			tfRecipientCountry.setText(iban.substring(0, 2).toUpperCase(Locale.ROOT));
		}
	}

	private String normalizeCountry(String country) {
		return country != null ? country.toUpperCase(Locale.ROOT) : null;
	}

	private String defaultText(String value) {
		return value != null ? value : "";
	}
}
