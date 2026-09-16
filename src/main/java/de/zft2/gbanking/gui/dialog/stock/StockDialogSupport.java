package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Window;

final class StockDialogSupport {

	private StockDialogSupport() {
	}

	static ComboBox<Currency> createCurrencyCombo(Currency initialValue) {
		ComboBox<Currency> result = new ComboBox<>(FXCollections.observableArrayList(Currency.values()));
		result.setValue(initialValue != null ? initialValue : Currency.EUR);
		return result;
	}

	static BigDecimal parseRequiredDecimal(TextField field, String fieldName) {
		BigDecimal value = parseOptionalDecimal(field, fieldName);
		if (value == null) {
			throw new GBankingException(fieldName + " muss angegeben werden");
		}
		return value;
	}

	static BigDecimal parseOptionalDecimal(TextField field, String fieldName) {
		String value = field.getText();
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value.trim().replace(',', '.'));
		} catch (NumberFormatException exception) {
			throw new GBankingException(fieldName + " ist keine gültige Zahl", exception);
		}
	}

	static String formatDecimal(BigDecimal value) {
		return value != null ? value.stripTrailingZeros().toPlainString().replace('.', ',') : "";
	}

	static void showError(Window owner, RuntimeException exception) {
		DialogWindowSupport.showAlert(owner, Alert.AlertType.ERROR, exception.getMessage());
	}
}
