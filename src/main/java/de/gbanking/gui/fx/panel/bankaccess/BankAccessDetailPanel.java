package de.gbanking.gui.fx.panel.bankaccess;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.fx.enu.ButtonContext;
import de.gbanking.gui.fx.panel.BasePanelHolder;
import de.gbanking.gui.fx.panel.overview.BankAccessOverviewPanel;
import de.gbanking.util.TypeConverter;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class BankAccessDetailPanel extends BasePanelHolder {

	private final TextField blzText = new TextField();
	private final TextField bankNameText = new TextField();
	private final TextField urlText = new TextField();
	private final TextField portText = new TextField();
	private final TextField userNameText = new TextField();
	private final TextField customerIdText = new TextField();
	private final TextField systemIdText = new TextField();
	private final TextField tanProcedureText = new TextField();
	private final TextField hbciVersionText = new TextField();
	private final TextField bpdVersionText = new TextField();
	private final TextField updVersionText = new TextField();
	private final TextField hbciFilterTypeText = new TextField();
	private final TextField activeText = new TextField();
	private final TextField updatedAtText = new TextField();

	private final Button buttonBankAccessEdit = new Button(getText("BANKACCESS_BUTTON_EDIT"));
	private final Button buttonBankAccessDelete = new Button(getText("BANKACCESS_BUTTON_DELETE"));

	private final BankAccessOverviewPanel parentPanel;

	public BankAccessDetailPanel(BankAccessOverviewPanel parentPanel) {
		this.parentPanel = parentPanel;
		createInnerBankAccessDetailPanel();
	}

	private void createInnerBankAccessDetailPanel() {
		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(8);
		grid.setPadding(new Insets(8));

		addFieldSide(grid, "BLZ", blzText, 0, 0);
		addFieldSide(grid, "Bank", bankNameText, 1, 0);

		addFieldSide(grid, "FinTS-URL", urlText, 0, 1);
		addFieldSide(grid, "FinTS-Port", portText, 1, 1);

		addFieldSide(grid, "Benutzer", userNameText, 0, 2);
		addFieldSide(grid, "Customer-ID", customerIdText, 1, 2);

		addFieldSide(grid, "System-ID", systemIdText, 0, 3);
		addFieldSide(grid, "ausgew. TAN Verfahren", tanProcedureText, 1, 3);

		addFieldSide(grid, "HBCI-Version", hbciVersionText, 0, 4);
		addFieldSide(grid, "HBCI-Verschlüsselung", hbciFilterTypeText, 1, 4);

		addFieldSide(grid, "BPD-Version", bpdVersionText, 0, 5);
		addFieldSide(grid, "UPD-Version", updVersionText, 1, 5);

		addFieldSide(grid, "aktiviert", activeText, 0, 6);
		addFieldSide(grid, "Stand", updatedAtText, 1, 6);

		Button buttonBankAccessNew = new Button(getText("BANKACCESS_BUTTON_NEW"));
		buttonBankAccessNew.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_NEW));

		buttonBankAccessEdit.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_EDIT));
		buttonBankAccessDelete.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_DELETE));

		buttonBankAccessEdit.setDisable(true);
		buttonBankAccessDelete.setDisable(true);

		HBox buttons = new HBox(10, buttonBankAccessNew, buttonBankAccessEdit, buttonBankAccessDelete);

		TitledPane pane = new TitledPane("Bankzugang Details", new VBox(8, grid, buttons));
		pane.setCollapsible(false);

		setReadOnly();
		getChildren().add(pane);
	}

	private void addFieldSide(GridPane grid, String labelText, TextField field, int colGroup, int row) {
		int col = colGroup * 2;
		grid.add(new Label(labelText), col, row);
		grid.add(field, col + 1, row);
	}

	private void setReadOnly() {
		for (TextField field : new TextField[] { blzText, bankNameText, urlText, portText, userNameText, customerIdText, systemIdText, tanProcedureText,
				hbciVersionText, bpdVersionText, updVersionText, hbciFilterTypeText, activeText, updatedAtText }) {
			field.setEditable(false);
		}
	}

	public void updatePanelFieldValues(BankAccess selectedAccess) {
		blzText.setText(selectedAccess.getBlz());
		bankNameText.setText(selectedAccess.getBankName());
		urlText.setText(selectedAccess.getHbciURL());
		portText.setText(String.valueOf(selectedAccess.getPort()));
		userNameText.setText(selectedAccess.getUserId());
		customerIdText.setText(selectedAccess.getCustomerId());
		systemIdText.setText(selectedAccess.getSysId());
		tanProcedureText.setText(selectedAccess.getTanProcedure() != null ? selectedAccess.getTanProcedure().toString() : "");
		hbciVersionText.setText(selectedAccess.getHbciVersion());
		bpdVersionText.setText(selectedAccess.getBpdVersion());
		updVersionText.setText(selectedAccess.getUpdVersion());
		hbciFilterTypeText.setText(selectedAccess.getFilterType() != null ? selectedAccess.getFilterType().toString() : "");
		activeText.setText(String.valueOf(selectedAccess.isActive()));
		updatedAtText.setText(TypeConverter.toDateStringLong(selectedAccess.getUpdatedAt()));

		parentPanel.setCurrentBankAccess(selectedAccess);
		buttonBankAccessEdit.setDisable(false);
		buttonBankAccessDelete.setDisable(false);
	}

	private void newBankAccessDialog(ButtonContext buttonContext) {
		BankAccessNewDialogHolder dialogHolder = new BankAccessNewDialogHolder(buttonContext, parentPanel);
		dialogHolder.showDialog();
	}
}