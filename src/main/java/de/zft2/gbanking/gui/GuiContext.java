package de.zft2.gbanking.gui;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.OrderType;

public final class GuiContext {

	private static BiConsumer<Booking, OrderType> moneyTransferTemplateHandler;
	private static Consumer<Booking> categoryRuleTemplateHandler;
	private static Consumer<Booking> stockTransactionNavigationHandler;
	private static Consumer<Booking> accountBookingNavigationHandler;
	private static Consumer<Integer> stockPortfolioNavigationHandler;
	private static Integer selectedAccountId;
	private static boolean onlyOnlineAccountsVisible;
	private static boolean onlySecuritiesWithHoldingsVisible;

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

	public static synchronized void setCategoryRuleTemplateHandler(Consumer<Booking> handler) {
		categoryRuleTemplateHandler = handler;
	}

	public static void useBookingAsCategoryRuleTemplate(Booking booking) {
		Consumer<Booking> handler;
		synchronized (GuiContext.class) {
			handler = categoryRuleTemplateHandler;
		}
		if (handler != null) {
			handler.accept(booking);
		}
	}

	public static synchronized void setStockTransactionNavigationHandler(Consumer<Booking> handler) {
		stockTransactionNavigationHandler = handler;
	}

	public static void showLinkedStockTransaction(Booking booking) {
		Consumer<Booking> handler;
		synchronized (GuiContext.class) {
			handler = stockTransactionNavigationHandler;
		}
		if (handler != null) {
			handler.accept(booking);
		}
	}

	public static synchronized void setAccountBookingNavigationHandler(Consumer<Booking> handler) {
		accountBookingNavigationHandler = handler;
	}

	public static void showAccountBooking(Booking booking) {
		Consumer<Booking> handler;
		synchronized (GuiContext.class) {
			handler = accountBookingNavigationHandler;
		}
		if (handler != null) {
			handler.accept(booking);
		}
	}

	public static synchronized void setStockPortfolioNavigationHandler(Consumer<Integer> handler) {
		stockPortfolioNavigationHandler = handler;
	}

	public static void showStockPortfolio(int portfolioId) {
		Consumer<Integer> handler;
		synchronized (GuiContext.class) {
			handler = stockPortfolioNavigationHandler;
		}
		if (handler != null) {
			handler.accept(portfolioId);
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

	public static synchronized boolean isOnlySecuritiesWithHoldingsVisible() {
		return onlySecuritiesWithHoldingsVisible;
	}

	public static synchronized void setOnlySecuritiesWithHoldingsVisible(boolean visible) {
		onlySecuritiesWithHoldingsVisible = visible;
	}

	public static synchronized void resetTenantState() {
		selectedAccountId = null;
	}
}
