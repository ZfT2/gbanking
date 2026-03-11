package de.gbanking.gui.fx.panel.overview;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.bankaccess.BankAccessDetailPanel;
import de.gbanking.gui.fx.panel.bankaccess.BankAccessListPanel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class BankAccessOverviewPanel extends OverviewBasePanel {

	private BankAccessDetailPanel bankAccessDetailPanel;
	private BankAccessListPanel bankAccessListPanel;

	private BankAccess currentBankAccess;

	public BankAccessOverviewPanel() {
		bankAccessDetailPanel = new BankAccessDetailPanel(this);
		bankAccessListPanel = new BankAccessListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.BANKACCESS);

		Label title = new Label(getText("UI_PANEL_BANK_ACCESS"));
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		bankAccessDetailPanel.setPrefHeight(300);
		bankAccessDetailPanel.setMinHeight(260);

		BorderPane content = new BorderPane();
		content.setTop(bankAccessDetailPanel);
		content.setCenter(bankAccessListPanel);

		VBox root = new VBox(8, title, content);
		root.setPadding(new Insets(5));
		VBox.setVgrow(content, Priority.ALWAYS);

		getChildren().clear();
		getChildren().add(root);

		setDisable(!show);
	}

	public BankAccessDetailPanel getBankAccessDetailPanel() {
		return bankAccessDetailPanel;
	}

	public BankAccessListPanel getBankAccessListPanel() {
		return bankAccessListPanel;
	}

	public BankAccess getCurrentBankAccess() {
		return currentBankAccess;
	}

	public void setCurrentBankAccess(BankAccess currentBankAccess) {
		this.currentBankAccess = currentBankAccess;
	}
}