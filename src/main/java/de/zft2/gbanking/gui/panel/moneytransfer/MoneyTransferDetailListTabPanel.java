package de.zft2.gbanking.gui.panel.moneytransfer;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.zft2.gbanking.gui.panel.recipient.RecipientListPanel;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class MoneyTransferDetailListTabPanel extends BorderPane implements BaseMessages {

	private final MoneyTransferOverviewPanel parentPanel;
	private MoneyTransferInputBasePanel moneyTransferInputPanel;
	private MoneyTransferListPanel moneyTransferListPanel;
	private MoneyTransferListPanel activeMoneyTransferListPanel;
	private MoneyTransferListPanel archiveMoneyTransferListPanel;
	private final OrderType orderType;

	public MoneyTransferDetailListTabPanel(OrderType orderType, MoneyTransferOverviewPanel parentPanel) {
		this.parentPanel = parentPanel;
		this.orderType = orderType;
		getTabbedPanel();
	}

	private void getTabbedPanel() {
		switch (orderType) {
		case TRANSFER, REALTIME_TRANSFER, URGENT_TRANSFER -> moneyTransferInputPanel = new MoneyTransferInputPanel(this);
		case SCHEDULED_TRANSFER -> moneyTransferInputPanel = new MoneyTransferInputScheduledPanel(this);
		case STANDING_ORDER -> moneyTransferInputPanel = new MoneyTransferInputStandingOrderPanel(this);
		case FOREIGN_TRANSFER -> moneyTransferInputPanel = new MoneyTransferInputForeignPanel(this);
		default -> throw new IllegalStateException("Unsupported order type: " + orderType);
		}

		RecipientListPanel recipientListPanel = new RecipientListPanel(this);
		recipientListPanel.setPrefWidth(650);
		recipientListPanel.setMinWidth(500);

		moneyTransferInputPanel.setPrefWidth(540);
		moneyTransferInputPanel.setMinWidth(520);
		moneyTransferInputPanel.setMaxWidth(540);

		activeMoneyTransferListPanel = new MoneyTransferListPanel(orderType, this, false);
		archiveMoneyTransferListPanel = new MoneyTransferListPanel(orderType, this, true);
		moneyTransferListPanel = activeMoneyTransferListPanel;

		TabPane listTabPane = createListTabPane();

		HBox topRow = new HBox(24, moneyTransferInputPanel, recipientListPanel);
		topRow.setPadding(new Insets(5));
		topRow.setFillHeight(true);

		HBox.setHgrow(moneyTransferInputPanel, Priority.NEVER);
		HBox.setHgrow(recipientListPanel, Priority.ALWAYS);

		setTop(topRow);
		setCenter(listTabPane);
	}

	private TabPane createListTabPane() {
		Tab activeTab = createListTab(orderType.getPlural(), activeMoneyTransferListPanel);
		Tab archiveTab = createListTab(getText("UI_TAB_ARCHIVE"), archiveMoneyTransferListPanel);

		TabPane listTabPane = new TabPane(activeTab, archiveTab);
		listTabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		listTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
			moneyTransferListPanel = (MoneyTransferListPanel) newTab.getContent();
			parentPanel.handleMoneyTransferListTabSelection(this);
		});
		listTabPane.getSelectionModel().select(activeTab);
		GuiLayoutState.configureTabPane(listTabPane, "moneyTransfers.list." + orderType.name());
		return listTabPane;
	}

	private Tab createListTab(String title, MoneyTransferListPanel listPanel) {
		listPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		Tab tab = new Tab(title, listPanel);
		tab.setClosable(false);
		return tab;
	}

	public MoneyTransferInputBasePanel getMoneyTransferInputPanel() {
		return moneyTransferInputPanel;
	}

	public MoneyTransferListPanel getMoneyTransferListPanel() {
		return moneyTransferListPanel;
	}

	public void reloadListPanels() {
		activeMoneyTransferListPanel.reload();
		archiveMoneyTransferListPanel.reload();
	}

	public void refreshCapabilityState() {
		moneyTransferInputPanel.refreshCapabilityState(getSelectedAccount());
	}

	public boolean hasAvailableOrderType(BankAccount bankAccount) {
		if (MoneyTransferInputPanel.isTransferOrderType(orderType)) {
			return MoneyTransferInputPanel.getTransferOrderTypes().stream().anyMatch(type -> parentPanel.supportsTransferOrderType(bankAccount, type));
		}
		if (orderType == OrderType.SCHEDULED_TRANSFER || orderType == OrderType.STANDING_ORDER) {
			return parentPanel.supportsBankOrderManagement(bankAccount, orderType);
		}
		return parentPanel.supportsTransferOrderType(bankAccount, orderType);
	}

	public BankAccount getSelectedAccount() {
		return parentPanel.getSelectedAccount();
	}

	public OrderType getOrderType() {
		return orderType;
	}
}
