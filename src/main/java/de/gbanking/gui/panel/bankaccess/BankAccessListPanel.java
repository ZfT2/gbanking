package de.gbanking.gui.panel.bankaccess;

import java.time.LocalDate;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.util.FxTableUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class BankAccessListPanel extends AbstractFilterableTablePanel<BankAccess> {

	private static final Logger log = LogManager.getLogger(BankAccessListPanel.class);

	private final BankAccessOverviewPanel parentPanel;

	public BankAccessListPanel(BankAccessOverviewPanel parentPanel) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parentPanel;
		createBankAccessListPanel();
	}

	private void createBankAccessListPanel() {
		setPanelTitleByKey("UI_PANEL_BANK_ACCESS_LIST");
		setColumns(createColumns());
		onSelection(this::handleSelection);
		reload();
	}

	private List<TableColumn<BankAccess, ?>> createColumns() {
		TableColumn<BankAccess, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), BankAccess::isSelected,
				BankAccess::setSelected);
		TableColumn<BankAccess, String> bankCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BANK"), BankAccess::getBankName, 120);
		TableColumn<BankAccess, String> userCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_USER_ID"), BankAccess::getUserId, 120);
		TableColumn<BankAccess, String> tanCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_TAN_PROCEDURE"),
				access -> access.getTanProcedure() != null ? access.getTanProcedure().toString() : "", 120);
		TableColumn<BankAccess, String> urlCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_FINTS_URL"), BankAccess::getHbciURL, 220, 320);
		TableColumn<BankAccess, String> activeCol = TableColumnFactory.createBooleanAsTextColumn(getText("UI_TABLE_ACTIVE"), BankAccess::isActive, 70);
		TableColumn<BankAccess, LocalDate> updatedCol = TableColumnFactory.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"),
				BankAccess::getUpdatedAt, 90);

		return List.of(selectedCol, bankCol, userCol, tanCol, urlCol, activeCol, updatedCol);
	}

	private void handleSelection(BankAccess selectedAccess) {
		log.log(Level.INFO, () -> messages.getFormattedMessage("LOG_BANK_ACCESS_SELECTED", selectedAccess.getId()));
		List<BankAccount> bankAccessAccountList = dbController.getAllByParent(BankAccount.class, selectedAccess.getId());
		selectedAccess.setAccounts(bankAccessAccountList);
		parentPanel.getBankAccessDetailPanel().updatePanelFieldValues(selectedAccess);
	}

	@Override
	protected boolean matchesFilter(BankAccess access, String filter) {
		return filter.isBlank() || contains(access.getBankName(), filter) || contains(access.getUserId(), filter) || contains(access.getHbciURL(), filter);
	}

	public void reload() {
		replaceItems(dbController.getAll(BankAccess.class));
	}

	public void refreshModelBankAccess() {
		reload();
	}
}
