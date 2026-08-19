package de.zft2.gbanking.gui;

import java.util.function.BiConsumer;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.OrderType;

public final class GuiContext {

	private static BiConsumer<Booking, OrderType> moneyTransferTemplateHandler;
	private static Integer selectedAccountId;
	private static boolean onlyOnlineAccountsVisible;

	private GuiContext() {
	}

	public static synchronized void setMoneyTransferTemplateHandler(BiConsumer<Booking, OrderType> handler) {
		moneyTransferTemplateHandler = handler;
	}

	public static void useBookingAsMoneyTransferTemplate(Booking booking, OrderType orderType) {
		BiConsumer<Booking, OrderType> handler;
		synchronized (GuiContext.class) {
			handler = moneyTransferTemplateHandler;
		}
		if (handler != null) {
			handler.accept(booking, orderType);
		}
	}

	public static synchronized void setSelectedAccount(BankAccount account) {
		selectedAccountId = account != null ? account.getId() : null;
	}

	public static synchronized Integer getSelectedAccountId() {
		return selectedAccountId;
	}

	public static synchronized void clearSelectedAccount() {
		selectedAccountId = null;
	}

	public static synchronized boolean isOnlyOnlineAccountsVisible() {
		return onlyOnlineAccountsVisible;
	}

	public static synchronized void setOnlyOnlineAccountsVisible(boolean onlyOnlineAccountsVisible) {
		GuiContext.onlyOnlineAccountsVisible = onlyOnlineAccountsVisible;
	}

	public static synchronized void resetTenantState() {
		selectedAccountId = null;
	}
}
