package de.gbanking.gui.fx.panel.moneytransfer;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.fx.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.fx.panel.recipient.RecipientListPanel;
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
		case TRANSFER -> moneyTransferInputPanel = new MoneyTransferInputPanel(this);
		case SCHEDULED_TRANSFER -> moneyTransferInputPanel = new MoneyTransferInputScheduledPanel(this);
		case STANDING_ORDER -> moneyTransferInputPanel = new MoneyTransferInputStandingOrderPanel(this);
		default -> throw new IllegalStateException("Unsupported order type: " + orderType);
		}

		RecipientListPanel recipientListPanel = new RecipientListPanel(this);
		recipientListPanel.setPrefWidth(360);
		recipientListPanel.setMinWidth(300);

		moneyTransferInputPanel.setPrefWidth(620);
		moneyTransferInputPanel.setMinWidth(520);

		moneyTransferListPanel = new MoneyTransferListPanel(orderType, this);

		HBox topRow = new HBox(10, moneyTransferInputPanel, recipientListPanel);
		topRow.setPadding(new Insets(5));
		topRow.setPrefHeight(280);
		HBox.setHgrow(moneyTransferInputPanel, Priority.ALWAYS);
		HBox.setHgrow(recipientListPanel, Priority.SOMETIMES);

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