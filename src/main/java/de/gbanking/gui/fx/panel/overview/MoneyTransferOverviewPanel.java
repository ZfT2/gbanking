package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
import de.gbanking.gui.fx.panel.moneytransfer.MoneyTransferDetailListTabPanel;
import de.gbanking.gui.fx.panel.moneytransfer.MoneyTransferInputBasePanel;
import de.gbanking.gui.fx.panel.moneytransfer.MoneyTransferListPanel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MoneyTransferOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(MoneyTransferOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private MoneyTransferInputBasePanel moneyTransferInputPanel;
	private MoneyTransferListPanel moneyTransferListPanel;
	private BankAccount selectedAccount;
	private TabPane tabPane;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ACCOUNTS_MONEYTRANSFERS);

		VBox root = new VBox(8);
		root.setPadding(new Insets(5));
		root.setFillWidth(true);
		root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		Label title = new Label(getText("UI_PANEL_MONEYTRANSFERS"));
		title.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");

		SplitPane splitPane = new SplitPane();
		splitPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		accountListPanel = new AccountListPanel(this);
		accountListPanel.setMinWidth(280);
		accountListPanel.setPrefWidth(335);

		tabPane = new TabPane();
		tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		tabPane.getTabs().addAll(createTab(OrderType.TRANSFER), createTab(OrderType.SCHEDULED_TRANSFER), createTab(OrderType.STANDING_ORDER));

		tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> switchTab(newTab));

		splitPane.getItems().addAll(accountListPanel, tabPane);
		splitPane.setDividerPositions(0.22);

		root.getChildren().addAll(title, splitPane);
		VBox.setVgrow(splitPane, Priority.ALWAYS);

		getChildren().clear();
		getChildren().add(root);
		VBox.setVgrow(root, Priority.ALWAYS);

		tabPane.getSelectionModel().select(0);
		switchTab(tabPane.getTabs().get(0));

		setDisable(!show);
	}

	private Tab createTab(OrderType type) {
		MoneyTransferDetailListTabPanel panel = new MoneyTransferDetailListTabPanel(type, this);
		Tab tab = new Tab(type.getPlural(), panel);
		tab.setClosable(false);
		return tab;
	}

	private void switchTab(Tab tab) {
		log.info("selected Tab: {}", tab.getText());
		MoneyTransferDetailListTabPanel selectedTab = (MoneyTransferDetailListTabPanel) tab.getContent();
		setActivePanels(selectedTab);
	}

	private void setActivePanels(MoneyTransferDetailListTabPanel selectedTab) {
		this.moneyTransferInputPanel = selectedTab.getMoneyTransferInputPanel();
		this.moneyTransferListPanel = selectedTab.getMoneyTransferListPanel();
		selectedTab.getMoneyTransferListPanel()
				.updatePanelBorder(selectedTab.getOrderType().getPlural() + " " + (selectedAccount != null ? selectedAccount.getAccountName() : ""));
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
}