package de.zft2.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferDetailListTabPanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferInputBasePanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferInputPanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferListPanel;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class MoneyTransferOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(MoneyTransferOverviewPanel.class);

	private static final double ACCOUNT_DIVIDER = 0.22;
	private final BankingCapabilityService bankingCapabilityService;

	private AccountListPanel accountListPanel;
	private MoneyTransferInputBasePanel moneyTransferInputPanel;
	private MoneyTransferListPanel moneyTransferListPanel;
	private BankAccount selectedAccount;
	private TabPane tabPane;

	public MoneyTransferOverviewPanel() {
		this(ServiceRegistry.getService(BankingCapabilityService.class));
	}

	MoneyTransferOverviewPanel(BankingCapabilityService bankingCapabilityService) {
		this.bankingCapabilityService = bankingCapabilityService;
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ACCOUNTS_MONEYTRANSFERS);

		accountListPanel = new AccountListPanel(this);

		tabPane = new TabPane();
		tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		tabPane.getTabs().setAll(createTab(OrderType.TRANSFER), createTab(OrderType.SCHEDULED_TRANSFER), createTab(OrderType.STANDING_ORDER),
				createTab(OrderType.FOREIGN_TRANSFER));
		tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> switchTab(newTab));

		MasterContentPane mainPane = new MasterContentPane(accountListPanel, tabPane, "moneyTransfers.main", ACCOUNT_DIVIDER);
		setOverviewContent("UI_PANEL_MONEYTRANSFERS", mainPane, show);

		tabPane.getSelectionModel().selectFirst();
		switchTab(tabPane.getTabs().get(0));
		GuiLayoutState.configureTabPane(tabPane, "moneyTransfers.orderType");
	}

	private Tab createTab(OrderType type) {
		Tab tab = new Tab(type.getPlural(), new MoneyTransferDetailListTabPanel(type, this));
		tab.setClosable(false);
		return tab;
	}

	private void switchTab(Tab tab) {
		if (tab == null) {
			return;
		}
		log.info("selected Tab: {}", tab.getText());
		setActivePanels((MoneyTransferDetailListTabPanel) tab.getContent());
		if (selectedAccount != null) {
			moneyTransferInputPanel.updatePanelFieldValues(selectedAccount);
			moneyTransferListPanel.reload();
		}
		refreshCapabilityState();
	}

	private void setActivePanels(MoneyTransferDetailListTabPanel selectedTab) {
		moneyTransferInputPanel = selectedTab.getMoneyTransferInputPanel();
		moneyTransferListPanel = selectedTab.getMoneyTransferListPanel();
		moneyTransferListPanel.updatePanelBorder(selectedAccount != null ? selectedAccount.getAccountName() : "");
	}

	public void handleMoneyTransferListTabSelection(MoneyTransferDetailListTabPanel selectedTab) {
		setActivePanels(selectedTab);
		if (selectedAccount != null) {
			moneyTransferListPanel.reload();
		}
		selectedTab.refreshCapabilityState();
	}

	public void handleAccountSelection(BankAccount selectedAccount) {
		setSelectedAccount(selectedAccount);
		refreshCapabilityState();
		moneyTransferListPanel.reload();
		moneyTransferListPanel.updatePanelBorder(selectedAccount.getAccountName());
		moneyTransferInputPanel.updatePanelFieldValues(selectedAccount);
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	public MoneyTransferInputBasePanel getMoneyTransferInputPanel() {
		return moneyTransferInputPanel;
	}

	public MoneyTransferListPanel getMoneyTransferListPanel() {
		return moneyTransferListPanel;
	}

	public BankAccount getSelectedAccount() {
		return selectedAccount;
	}

	public void setSelectedAccount(BankAccount selectedAccount) {
		this.selectedAccount = selectedAccount;
	}

	public boolean supportsTransferOrderType(BankAccount account, OrderType orderType) {
		return bankingCapabilityService.supportsTransferOrderType(account, orderType);
	}

	public boolean supportsBankOrderManagement(BankAccount account, OrderType orderType) {
		return bankingCapabilityService.supportsBankOrderManagement(account, orderType);
	}

	public void useBookingAsTemplate(BankAccount account, Booking booking, OrderType orderType) {
		showOrderType(orderType);
		selectAccount(account);
		moneyTransferInputPanel.prefillFromBookingTemplate(booking);
	}

	public void selectAccount(BankAccount account) {
		if (accountListPanel != null) {
			accountListPanel.selectAccount(account);
		}
	}

	public void showOrderType(OrderType orderType) {
		if (tabPane != null) {
			OrderType tabOrderType = MoneyTransferInputPanel.isTransferOrderType(orderType) ? OrderType.TRANSFER : orderType;
			tabPane.getTabs().stream().filter(tab -> ((MoneyTransferDetailListTabPanel) tab.getContent()).getOrderType() == tabOrderType).findFirst()
					.ifPresent(tab -> tabPane.getSelectionModel().select(tab));
		}
		refreshOnShow();
	}

	@Override
	public void refreshOnShow() {
		if (accountListPanel != null) {
			accountListPanel.refreshModelAccount();
			if (accountListPanel.getSelectedAccount() == null && selectedAccount != null) {
				setSelectedAccount(null);
				if (moneyTransferInputPanel != null) {
					moneyTransferInputPanel.updatePanelFieldValues((BankAccount) null);
				}
			}
		}
		refreshCapabilityState();
		if (moneyTransferListPanel != null) {
			((MoneyTransferDetailListTabPanel) tabPane.getSelectionModel().getSelectedItem().getContent()).reloadListPanels();
		}
	}

	private void refreshCapabilityState() {
		if (tabPane == null) {
			return;
		}
		for (Tab tab : tabPane.getTabs()) {
			MoneyTransferDetailListTabPanel panel = (MoneyTransferDetailListTabPanel) tab.getContent();
			boolean available = panel.hasAvailableOrderType(selectedAccount);
			tab.setStyle(available ? "" : "-fx-opacity: 0.55;");
			panel.refreshCapabilityState();
		}
	}
}
