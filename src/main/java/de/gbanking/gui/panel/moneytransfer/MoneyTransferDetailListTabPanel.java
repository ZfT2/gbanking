package de.gbanking.gui.panel.moneytransfer;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.panel.recipient.RecipientListPanel;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class MoneyTransferDetailListTabPanel extends BorderPane {

	private final MoneyTransferOverviewPanel parentPanel;
	private MoneyTransferInputBasePanel moneyTransferInputPanel;
	private MoneyTransferListPanel moneyTransferListPanel;
	private final OrderType orderType;

	public MoneyTransferDetailListTabPanel(OrderType orderType, MoneyTransferOverviewPanel parentPanel) {
		this.parentPanel = parentPanel;
		this.orderType = orderType;
		getTabbedPanel();
	}

	private void getTabbedPanel() {
		switch (orderType) {
		case TRANSFER, REALTIME_TRANSFER -> moneyTransferInputPanel = new MoneyTransferInputPanel(this);
		case SCHEDULED_TRANSFER -> moneyTransferInputPanel = new MoneyTransferInputScheduledPanel(this);
		case STANDING_ORDER -> moneyTransferInputPanel = new MoneyTransferInputStandingOrderPanel(this);
		default -> throw new IllegalStateException("Unsupported order type: " + orderType);
		}

		RecipientListPanel recipientListPanel = new RecipientListPanel(this);
		recipientListPanel.setPrefWidth(520);
		recipientListPanel.setMinWidth(420);

		moneyTransferInputPanel.setPrefWidth(560);
		moneyTransferInputPanel.setMinWidth(480);
		moneyTransferInputPanel.setMaxWidth(Double.MAX_VALUE);

		moneyTransferListPanel = new MoneyTransferListPanel(orderType, this);
		moneyTransferListPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		HBox topRow = new HBox(10, moneyTransferInputPanel, recipientListPanel);
		topRow.setPadding(new Insets(5));
		topRow.setFillHeight(true);

		HBox.setHgrow(moneyTransferInputPanel, Priority.ALWAYS);
		HBox.setHgrow(recipientListPanel, Priority.ALWAYS);

		setTop(topRow);
		setCenter(moneyTransferListPanel);
	}

	public MoneyTransferInputBasePanel getMoneyTransferInputPanel() {
		return moneyTransferInputPanel;
	}

	public MoneyTransferListPanel getMoneyTransferListPanel() {
		return moneyTransferListPanel;
	}

	public BankAccount getSelectedAccount() {
		return parentPanel.getSelectedAccount();
	}

	public OrderType getOrderType() {
		return orderType;
	}
}
