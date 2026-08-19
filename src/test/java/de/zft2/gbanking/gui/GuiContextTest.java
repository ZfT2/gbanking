package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.OrderType;

class GuiContextTest {

	@BeforeEach
	void setUp() {
		resetContext();
	}

	@AfterEach
	void tearDown() {
		resetContext();
	}

	@Test
	void selectedAccountShouldStoreAccountId() {
		BankAccount account = new BankAccount();
		account.setId(42);

		GuiContext.setSelectedAccount(account);

		assertEquals(42, GuiContext.getSelectedAccountId());
	}

	@Test
	void resetTenantStateShouldClearSelectedAccount() {
		BankAccount account = new BankAccount();
		account.setId(42);
		GuiContext.setSelectedAccount(account);

		GuiContext.resetTenantState();

		assertNull(GuiContext.getSelectedAccountId());
	}

	@Test
	void onlyOnlineAccountsVisibilityShouldBeStoredInContext() {
		assertFalse(GuiContext.isOnlyOnlineAccountsVisible());

		GuiContext.setOnlyOnlineAccountsVisible(true);

		assertTrue(GuiContext.isOnlyOnlineAccountsVisible());
	}

	@Test
	void moneyTransferTemplateShouldBeForwardedToRegisteredHandler() {
		AtomicReference<Booking> forwardedBooking = new AtomicReference<>();
		AtomicReference<OrderType> forwardedOrderType = new AtomicReference<>();
		Booking booking = new Booking();
		GuiContext.setMoneyTransferTemplateHandler((template, orderType) -> {
			forwardedBooking.set(template);
			forwardedOrderType.set(orderType);
		});

		GuiContext.useBookingAsMoneyTransferTemplate(booking, OrderType.TRANSFER);

		assertSame(booking, forwardedBooking.get());
		assertEquals(OrderType.TRANSFER, forwardedOrderType.get());
	}

	private static void resetContext() {
		GuiContext.resetTenantState();
		GuiContext.setOnlyOnlineAccountsVisible(false);
		GuiContext.setMoneyTransferTemplateHandler(null);
	}
}
