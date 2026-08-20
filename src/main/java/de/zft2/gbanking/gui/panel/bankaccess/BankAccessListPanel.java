package de.zft2.gbanking.gui.panel.bankaccess;

import java.time.LocalDate;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.overview.BankAccessOverviewPanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.paypal.PaypalSupport;
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
		configureTableLayout("bankAccess");
		onSelection(this::handleSelection);
		reload();
	}

	private List<TableColumn<BankAccess, ?>> createColumns() {
		TableColumn<BankAccess, Boolean> selectedCol = createSelectAllSelectionColumn(
				access -> access.isSelected(), (access, selected) -> access.setSelected(selected));
		TableColumn<BankAccess, String> bankCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BANK"), BankAccess::getBankName, 260);
		TableColumn<BankAccess, String> userCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_USER_ID"),
				access -> getUserId(access), 120);
		TableColumn<BankAccess, String> tanCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_TAN_PROCEDURE"),
				access -> access.getFints().getTanProcedure() != null ? access.getFints().getTanProcedure().toString() : "", 160);
		TableColumn<BankAccess, String> urlCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_FINTS_URL"),
				access -> access.getFints().getHbciURL(), 200, 260);
		TableColumn<BankAccess, String> activeCol = TableColumnFactory.createBooleanAsTextColumn(getText("UI_TABLE_ACTIVE"), BankAccess::isActive, 70);
		TableColumn<BankAccess, LocalDate> updatedCol = TableColumnFactory.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"),
				BankAccess::getUpdatedAt, 90);

		return List.of(selectedCol, bankCol, userCol, tanCol, urlCol, activeCol, updatedCol);
	}

	private void handleSelection(BankAccess selectedAccess) {
		log.log(Level.INFO, () -> getText("LOG_BANK_ACCESS_SELECTED", selectedAccess.getId()));
		List<BankAccount> bankAccessAccountList = dbController.getAllByParent(BankAccount.class, selectedAccess.getId());
		selectedAccess.setAccounts(bankAccessAccountList);
		parentPanel.getBankAccessDetailPanel().updatePanelFieldValues(selectedAccess);
	}

	@Override
	protected boolean matchesFilter(BankAccess access, String filter) {
		return matchesAny(filter, access.getBankName(), getUserId(access), access.getFints().getHbciURL());
	}

	private String getUserId(BankAccess access) {
		return PaypalSupport.isPaypal(access) ? access.getPaypal().getUserId() : access.getFints().getUserId();
	}

	public void reload() {
		replaceItemsFrom(() -> dbController.getAll(BankAccess.class));
	}

	public void refreshModelBankAccess() {
		reload();
	}
}
