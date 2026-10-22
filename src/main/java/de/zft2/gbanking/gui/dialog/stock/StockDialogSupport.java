package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.util.Arrays;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.util.StringConverter;

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

	/**
	 * Enables or disables the exchange rate field. A disabled field shows the rate one, an enabled
	 * field is reset if it still shows the rate one of the settlement currency.
	 *
	 * @param field    the exchange rate field
	 * @param required true if the exchange rate has to be entered
	 */
	static void updateExchangeRateField(TextField field, boolean required) {
		field.setDisable(!required);
		if (!required) {
			field.setText("1");
		} else if ("1".equals(field.getText())) {
			field.clear();
		}
	}

	/**
	 * Returns the exchange rate of the field. The rate of the settlement currency is one.
	 *
	 * @param field     the exchange rate field
	 * @param fieldName the name of the field, it is used for the error message
	 * @return the exchange rate
	 */
	static BigDecimal parseExchangeRate(TextField field, String fieldName) {
		return field.isDisabled() ? BigDecimal.ONE : parseRequiredDecimal(field, fieldName);
	}

	/**
	 * Creates a converter for the transaction types. The label of a type is the localized text of
	 * the type, an unknown label is mapped to the given default type.
	 *
	 * @param defaultType the type used for an unknown label
	 * @return the converter
	 */
	static StringConverter<StockTransactionType> createTransactionTypeConverter(StockTransactionType defaultType) {
		return new StringConverter<>() {
			@Override
			public String toString(StockTransactionType type) {
				return type == null ? "" : transactionTypeLabel(type);
			}

			@Override
			public StockTransactionType fromString(String value) {
				return Arrays.stream(StockTransactionType.values())
						.filter(type -> transactionTypeLabel(type).equals(value)).findFirst().orElse(defaultType);
			}
		};
	}

	private static String transactionTypeLabel(StockTransactionType type) {
		return BaseMessages.getTextStatic("UI_STOCK_TRANSACTION_" + type.name());
	}

	static void showError(Window owner, RuntimeException exception) {
		DialogWindowSupport.showAlert(owner, Alert.AlertType.ERROR, exception.getMessage());
	}
}
