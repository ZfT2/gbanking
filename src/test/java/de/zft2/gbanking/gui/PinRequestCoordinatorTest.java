package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.testdata.TestDataFactory;

class PinRequestCoordinatorTest {

	@Test
	void requestPinsByBankAccessShouldRequestOnePinPerBankAccess() {
		List<Integer> requestedAccountIds = new ArrayList<>();
		PinRequestCoordinator coordinator = new PinRequestCoordinator(account -> {
			requestedAccountIds.add(account.getId());
			return new char[] { (char) ('0' + account.getId()) };
		});

		Map<Integer, char[]> pins = coordinator.requestPinsByBankAccess(
				List.of(account(1, 10), account(2, 10), account(3, 20)));

		assertEquals(List.of(1, 3), requestedAccountIds);
		assertEquals(List.of(10, 20), new ArrayList<>(pins.keySet()));
		assertArrayEquals(new char[] { '1' }, pins.get(10));
		assertArrayEquals(new char[] { '3' }, pins.get(20));
		PinRequestCoordinator.clearPins(pins);
	}

	@Test
	void requestPinsShouldClearPreviouslyEnteredPinsWhenCancelled() {
		char[] enteredPin = { '1', '2', '3', '4' };
		AtomicInteger requestCount = new AtomicInteger();
		PinRequestCoordinator coordinator = new PinRequestCoordinator(
				account -> requestCount.getAndIncrement() == 0 ? enteredPin : null);

		Map<Integer, BankAccount> accountsById = new LinkedHashMap<>();
		accountsById.put(1, account(1, 10));
		accountsById.put(2, account(2, 20));
		Map<Integer, char[]> pins = coordinator.requestPinsByAccountId(accountsById);

		assertTrue(pins.isEmpty());
		assertArrayEquals(new char[enteredPin.length], enteredPin);
	}

	@Test
	void requestPinsShouldClearPreviouslyEnteredPinsWhenPromptFails() {
		char[] enteredPin = { '1', '2', '3', '4' };
		AtomicInteger requestCount = new AtomicInteger();
		PinRequestCoordinator coordinator = new PinRequestCoordinator(account -> {
			if (requestCount.getAndIncrement() == 0) {
				return enteredPin;
			}
			throw new IllegalStateException();
		});

		assertThrows(IllegalStateException.class,
				() -> coordinator.requestPinsByBankAccess(List.of(account(1, 10), account(2, 20))));
		assertArrayEquals(new char[enteredPin.length], enteredPin);
	}

	@Test
	void requestPinsByAccountShouldUseAccountsAsKeys() {
		BankAccount firstAccount = account(1, 10);
		BankAccount secondAccount = account(2, 10);
		PinRequestCoordinator coordinator = new PinRequestCoordinator(account -> new char[] { '1' });

		Map<BankAccount, char[]> pins = coordinator.requestPinsByAccount(List.of(firstAccount, secondAccount));

		assertEquals(List.of(firstAccount, secondAccount), new ArrayList<>(pins.keySet()));
		PinRequestCoordinator.clearPins(pins);
	}

	@Test
	void pinUtilitiesShouldCopyAndClearSecrets() {
		char[] pin = { '1', '2', '3', '4' };
		char[] copy = PinRequestCoordinator.copyPin(pin);
		PinRequestCoordinator coordinator = new PinRequestCoordinator(account -> pin);
		Map<Integer, char[]> pins = coordinator.requestPinsByAccountId(Map.of(99, account(1, 10)));

		assertNotSame(pin, copy);
		assertArrayEquals(pin, copy);
		assertNull(PinRequestCoordinator.copyPin(null));
		assertTrue(pins.containsKey(99));

		PinRequestCoordinator.clearPins(pins);
		assertTrue(pins.isEmpty());
		assertArrayEquals(new char[pin.length], pin);
	}

	private static BankAccount account(int id, int bankAccessId) {
		return TestDataFactory.createWithIdAndBankAccess(id, bankAccessId);
	}
}
