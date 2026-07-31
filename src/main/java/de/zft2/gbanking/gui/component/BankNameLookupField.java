package de.zft2.gbanking.gui.component;

import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.cache.InstituteLookupCache.InstituteLookupEntry;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

public class BankNameLookupField extends StackPane {

	private final TextField bankNameField = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final ComboBox<InstituteLookupEntry> bankNameCombo = FormStyleUtils.applyWidth(new ComboBox<>(), FieldWidth.M);
	private final ObjectProperty<InstituteLookupEntry> selectedEntry = new SimpleObjectProperty<>();
	private boolean manualEntryEditable;

	public BankNameLookupField() {
		bankNameField.setEditable(false);
		bankNameField.setMaxWidth(Double.MAX_VALUE);
		bankNameCombo.setMaxWidth(Double.MAX_VALUE);
		setMaxWidth(Double.MAX_VALUE);
		FormStyleUtils.setReadOnlyStyle(true, bankNameField);

		bankNameCombo.setEditable(false);
		bankNameCombo.setVisible(false);
		bankNameCombo.setManaged(false);
		bankNameCombo.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(InstituteLookupEntry entry) {
				return entry == null ? "" : Objects.toString(entry.bankName(), "");
			}

			@Override
			public InstituteLookupEntry fromString(String string) {
				return null;
			}
		});
		bankNameCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> selectedEntry.set(newValue));

		getChildren().addAll(bankNameField, bankNameCombo);
	}

	public void setEntries(List<InstituteLookupEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			clear();
			return;
		}

		if (entries.size() == 1) {
			InstituteLookupEntry entry = entries.get(0);
			bankNameField.setText(Objects.toString(entry.bankName(), ""));
			selectedEntry.set(entry);
			bankNameCombo.getItems().clear();
			showTextField();
			return;
		}

		bankNameCombo.setItems(FXCollections.observableArrayList(entries));
		bankNameCombo.getSelectionModel().selectFirst();
		showComboBox();
		selectedEntry.set(bankNameCombo.getSelectionModel().getSelectedItem());
	}

	public void setManualBankName(String bankName) {
		bankNameCombo.getItems().clear();
		bankNameField.setText(Objects.toString(bankName, ""));
		selectedEntry.set(new InstituteLookupEntry(bankName, null, Integer.MAX_VALUE));
		showTextField();
	}

	public void clear() {
		bankNameCombo.getItems().clear();
		bankNameCombo.getSelectionModel().clearSelection();
		bankNameField.clear();
		selectedEntry.set(null);
		showTextField();
	}

	public String getSelectedBankName() {
		if (manualEntryEditable && bankNameField.isVisible()) {
			return bankNameField.getText();
		}
		InstituteLookupEntry entry = selectedEntry.get();
		return entry != null ? entry.bankName() : bankNameField.getText();
	}

	public String getSelectedBic() {
		InstituteLookupEntry entry = selectedEntry.get();
		return entry != null ? entry.bic() : null;
	}

	public ObservableValue<InstituteLookupEntry> selectedEntryProperty() {
		return selectedEntry;
	}

	public void setManualEntryEditable(boolean editable) {
		manualEntryEditable = editable;
		bankNameField.setEditable(editable);
		FormStyleUtils.setReadOnlyStyle(!editable, bankNameField);
	}

	private void showTextField() {
		bankNameField.setVisible(true);
		bankNameField.setManaged(true);
		bankNameField.setEditable(manualEntryEditable);
		bankNameCombo.setVisible(false);
		bankNameCombo.setManaged(false);
	}

	private void showComboBox() {
		bankNameField.setVisible(false);
		bankNameField.setManaged(false);
		bankNameCombo.setVisible(true);
		bankNameCombo.setManaged(true);
	}
}
