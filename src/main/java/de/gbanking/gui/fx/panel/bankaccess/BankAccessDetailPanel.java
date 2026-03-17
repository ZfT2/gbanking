package de.gbanking.gui.fx.panel.bankaccess;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.fx.enu.ButtonContext;
import de.gbanking.gui.fx.panel.AbstractReadonlyDetailPanel;
import de.gbanking.gui.fx.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.fx.util.FormFields;
import de.gbanking.gui.fx.util.FormStyleUtils;
import de.gbanking.util.TypeConverter;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class BankAccessDetailPanel extends AbstractReadonlyDetailPanel {

	// Spalte 1 - Bank / Zugang
	private final TextField blzText = FormFields.textS();
	private final TextField bankNameText = FormFields.textM();
	private final TextField userNameText = FormFields.textM();
	private final TextField customerIdText = FormFields.textM();

	// Spalte 2 - FinTS / HBCI
	private final TextField urlText = FormFields.textL();
	private final TextField portText = FormFields.textXs();
	private final TextField systemIdText = FormFields.textS();
	private final TextField tanProcedureText = FormFields.textM();

	// Spalte 3 - Version / Status
	private final TextField hbciVersionText = FormFields.textS();
	private final TextField hbciFilterTypeText = FormFields.textM();
	private final TextField bpdVersionText = FormFields.textS();
	private final TextField updVersionText = FormFields.textS();
	private final CheckBox activeBox = FormFields.checkBox();
	private final TextField updatedAtText = FormFields.textS();

	private final Button buttonBankAccessEdit = new Button(getText("UI_BUTTON_BANK_ACCESS_EDIT"));
	private final Button buttonBankAccessDelete = new Button(getText("UI_BUTTON_BANK_ACCESS_DELETE"));

	private final BankAccessOverviewPanel parentPanel;

	public BankAccessDetailPanel(BankAccessOverviewPanel parentPanel) {
		super("UI_PANEL_BANK_ACCESS_DETAILS");
		this.parentPanel = parentPanel;
		configureGrid();
		createInnerBankAccessDetailPanel();
	}

	private void configureGrid() {
		formGrid.getColumnConstraints().clear();
		formGrid.getColumnConstraints().addAll(createGrowColumn(), createGrowColumn(), createGrowColumn());
	}

	private ColumnConstraints createGrowColumn() {
		ColumnConstraints constraints = new ColumnConstraints();
		constraints.setHgrow(Priority.ALWAYS);
		constraints.setFillWidth(true);
		return constraints;
	}

	private void createInnerBankAccessDetailPanel() {
		// Spalte 1 - Bank / Zugang
		addFieldInline("UI_LABEL_BLZ", blzText, 0, 0);
		addFieldInline("UI_LABEL_BANK", bankNameText, 0, 1);
		addFieldInline("UI_LABEL_USER", userNameText, 0, 2);
		addFieldInline("UI_LABEL_CUSTOMER_ID", customerIdText, 0, 3);

		// Spalte 2 - FinTS / HBCI
		addFieldInline("UI_LABEL_FINTS_URL", urlText, 1, 0);
		addFieldInline("UI_LABEL_FINTS_PORT", portText, 1, 1);
		addFieldInline("UI_LABEL_SYSTEM_ID", systemIdText, 1, 2);
		addFieldInline("UI_LABEL_TAN_PROCEDURE_SELECTED", tanProcedureText, 1, 3);

		// Spalte 3 - Version / Status
		addFieldInline("UI_LABEL_HBCI_VERSION", hbciVersionText, 2, 0);
		addFieldInline("UI_LABEL_HBCI_ENCRYPTION", hbciFilterTypeText, 2, 1);
		addFieldInline("UI_LABEL_BPD_VERSION", bpdVersionText, 2, 2);
		addFieldInline("UI_LABEL_UPD_VERSION", updVersionText, 2, 3);
		addFieldInline("UI_LABEL_ACTIVE", activeBox, 2, 4);
		addFieldInline("UI_LABEL_UPDATED_AT", updatedAtText, 2, 5);

		Button buttonBankAccessNew = new Button(getText("UI_BUTTON_BANK_ACCESS_NEW"));
		buttonBankAccessNew.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_NEW));
		buttonBankAccessEdit.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_EDIT));
		buttonBankAccessDelete.setOnAction(e -> newBankAccessDialog(ButtonContext.BUTTON_DELETE));
		buttonBankAccessEdit.setDisable(true);
		buttonBankAccessDelete.setDisable(true);

		HBox buttons = FormStyleUtils.createButtonBar(buttonBankAccessNew, buttonBankAccessEdit, buttonBankAccessDelete);
		addContentNode(buttons);

		makeReadOnly(blzText, bankNameText, userNameText, customerIdText, urlText, portText, systemIdText, tanProcedureText, hbciVersionText,
				hbciFilterTypeText, bpdVersionText, updVersionText, updatedAtText);

		FormStyleUtils.setReadOnlyStyle(true, blzText, bankNameText, userNameText, customerIdText, urlText, portText, systemIdText, tanProcedureText,
				hbciVersionText, hbciFilterTypeText, bpdVersionText, updVersionText, updatedAtText);

		disable(activeBox);
	}

	public void updatePanelFieldValues(BankAccess selectedAccess) {
		updateTitle(selectedAccess.getBankName());

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