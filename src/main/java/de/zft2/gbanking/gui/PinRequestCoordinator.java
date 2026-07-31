package de.zft2.gbanking.gui;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.panel.action.PinAskDialog;
import javafx.stage.Stage;

final class PinRequestCoordinator {

	private final Function<BankAccount, char[]> pinRequester;

	PinRequestCoordinator(PinAskDialog pinDialog) {
		Objects.requireNonNull(pinDialog, "pinDialog");
		pinRequester = account -> requestPin(pinDialog, account);
	}

	PinRequestCoordinator(Function<BankAccount, char[]> pinRequester) {
		this.pinRequester = Objects.requireNonNull(pinRequester, "pinRequester");
	}

	Map<Integer, char[]> requestPinsByBankAccess(Iterable<BankAccount> accounts) {
		return requestPins(accounts, BankAccount::getBankAccessId);
	}

	Map<Integer, char[]> requestPinsByAccountId(Map<Integer, BankAccount> accountsById) {
		return requestPins(accountsById);
	}

	Map<BankAccount, char[]> requestPinsByAccount(Iterable<BankAccount> accounts) {
		return requestPins(accounts, Function.identity());
	}

	private <K> Map<K, char[]> requestPins(Iterable<BankAccount> accounts, Function<BankAccount, K> keyProvider) {
		Map<K, BankAccount> accountsByKey = new LinkedHashMap<>();
		for (BankAccount account : accounts) {
			accountsByKey.putIfAbsent(keyProvider.apply(account), account);
		}
		return requestPins(accountsByKey);
	}

	private <K> Map<K, char[]> requestPins(Map<K, BankAccount> accountsByKey) {
		Map<K, char[]> pinsByKey = new LinkedHashMap<>();
		boolean allPinsRequested = false;
		try {
			for (Map.Entry<K, BankAccount> entry : accountsByKey.entrySet()) {
				char[] pin = pinRequester.apply(entry.getValue());
				if (pin == null || pin.length == 0) {
					return Map.of();
				}
				pinsByKey.put(entry.getKey(), pin);
			}
			allPinsRequested = true;
			return pinsByKey;
		} finally {
			if (!allPinsRequested) {
				clearPins(pinsByKey);
			}
		}
	}

	private static char[] requestPin(PinAskDialog pinDialog, BankAccount account) {
		pinDialog.setBankInfo(account.getBlz(), account.getBankName());
		Stage dialog = pinDialog.createNewPinAskDialog();
		dialog.showAndWait();
		return pinDialog.getPin();
	}

	static char[] copyPin(char[] pin) {
		return pin != null ? Arrays.copyOf(pin, pin.length) : null;
	}

	static void clearPins(Map<?, char[]> pinsByKey) {
		for (char[] pin : pinsByKey.values()) {
			if (pin != null) {
				Arrays.fill(pin, '\0');
			}
		}
		pinsByKey.clear();
	}
}
