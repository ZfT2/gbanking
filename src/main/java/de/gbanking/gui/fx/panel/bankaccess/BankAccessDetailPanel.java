// src/main/java/de/gbanking/gui/fx/panel/bankaccess/BankAccessDetailPanel.java
package de.gbanking.gui.fx.panel.bankaccess;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.fx.enu.ButtonContext;
import de.gbanking.gui.fx.panel.AbstractReadonlyDetailPanel;
import de.gbanking.gui.fx.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.fx.util.FormStyleUtils;
import de.gbanking.gui.fx.util.FormStyleUtils.FieldWidth;
import de.gbanking.util.TypeConverter;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class BankAccessDetailPanel extends AbstractReadonlyDetailPanel {

	private final TextField blzText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField bankNameText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextField urlText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	private final TextField portText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.XS);
	private final TextField userNameText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextField customerIdText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextField systemIdText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextField tanProcedureText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextField hbciVersionText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField bpdVersionText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField updVersionText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField hbciFilterTypeText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final CheckBox activeBox = new CheckBox();
	private final TextField updatedAtText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);

	private final Button buttonBankAccessEdit = new Button(getText("UI_BUTTON_BANK_ACCESS_EDIT"));
	private final Button buttonBankAccessDelete = new Button(getText("UI_BUTTON_BANK_ACCESS_DELETE"));
	private final BankAccessOverviewPanel parentPanel;

	public BankAccessDetailPanel(BankAccessOverviewPanel parentPanel) {
		super("UI_PANEL_BANK_ACCESS_DETAILS");
		this.parentPanel = parentPanel;
		createInnerBankAccessDetailPanel();
	}

	private void createInnerBankAccessDetailPanel() {
		addFieldAbove("UI_LABEL_BLZ", blzText, 0, 0);
		addFieldAbove("UI_LABEL_BANK", bankNameText, 1, 0);
		addFieldAbove("UI_LABEL_FINTS_URL", urlText, 0, 1);
		addFieldAbove("UI_LABEL_FINTS_PORT", portText, 1, 1);
		addFieldAbove("UI_LABEL_USER", userNameText, 0, 2);
		addFieldAbove("UI_LABEL_CUSTOMER_ID", customerIdText, 1, 2);
		addFieldAbove("UI_LABEL_SYSTEM_ID", systemIdText, 0, 3);
		addFieldAbove("UI_LABEL_TAN_PROCEDURE_SELECTED", tanProcedureText, 1, 3);
		addFieldAbove("UI_LABEL_HBCI_VERSION", hbciVersionText, 0, 4);
		addFieldAbove("UI_LABEL_HBCI_ENCRYPTION", hbciFilterTypeText, 1, 4);
		addFieldAbove("UI_LABEL_BPD_VERSION", bpdVersionText, 0, 5);
		addFieldAbove("UI_LABEL_UPD_VERSION", updVersionText, 1, 5);
		addFieldAbove("UI_LABEL_ACTIVE", activeBox, 0, 6);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 1, 6);

		Button buttonBankAccessNew = new Button(getText("UI_BUTTON_BANK_ACCESS_NEW"));
		buttonBankAccessNew.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_NEW));
		buttonBankAccessEdit.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_EDIT));
		buttonBankAccessDelete.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_DELETE));
		buttonBankAccessEdit.setDisable(true);
		buttonBankAccessDelete.setDisable(true);

		HBox buttons = FormStyleUtils.createButtonBar(buttonBankAccessNew, buttonBankAccessEdit, buttonBankAccessDelete);
		addContentNode(buttons);

		makeReadOnly(blzText, bankNameText, urlText, portText, userNameText, customerIdText, systemIdText, tanProcedureText, hbciVersionText, bpdVersionText,
				updVersionText, hbciFilterTypeText, updatedAtText);
		disable(activeBox);
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
		activeBox.setSelected(selectedAccess.isActive());
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