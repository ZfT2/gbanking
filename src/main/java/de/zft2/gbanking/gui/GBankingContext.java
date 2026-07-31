package de.zft2.gbanking.gui;

import java.util.function.BiConsumer;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.service.GBankingBean;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

public final class GBankingContext {

	private static GBankingBean bean;
	private static BankAccessService bankAccessService;
	private static BiConsumer<Booking, OrderType> moneyTransferTemplateHandler;
	private static Integer selectedAccountId;
	private static boolean onlyOnlineAccountsVisible;

	private GBankingContext() {
	}

	public static synchronized GBankingBean getBean() {
		if (bean == null) {
			bean = new GBankingBean();
		}
		return bean;
	}

	public static synchronized BankAccessService getBankAccessService() {
		if (bankAccessService == null) {
			bankAccessService = new BankAccessService();
		}
		return bankAccessService;
	}

	public static DBController getDbController() {
		return DBController.getInstance(".");
	}

	public static synchronized void setMoneyTransferTemplateHandler(BiConsumer<Booking, OrderType> handler) {
		moneyTransferTemplateHandler = handler;
	}

	public static void useBookingAsMoneyTransferTemplate(Booking booking, OrderType orderType) {
		BiConsumer<Booking, OrderType> handler;
		synchronized (GBankingContext.class) {
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
		GBankingContext.onlyOnlineAccountsVisible = onlyOnlineAccountsVisible;
	}

	public static synchronized void resetServices() {
		bean = null;
		selectedAccountId = null;
	}
}
