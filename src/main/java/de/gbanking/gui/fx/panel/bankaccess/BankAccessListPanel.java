package de.gbanking.gui.fx.panel.bankaccess;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.gui.fx.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.fx.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.fx.util.DateFormatUtils;
import de.gbanking.gui.fx.util.FxTableUtils;
import de.gbanking.gui.fx.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class BankAccessListPanel extends AbstractFilterableTablePanel<BankAccess> {

	private static final Logger log = LogManager.getLogger(BankAccessListPanel.class);

	private final BankAccessOverviewPanel parentPanel;
	private final ObservableList<BankAccess> masterData;

	public BankAccessListPanel(BankAccessOverviewPanel parentPanel) {
		this(FXCollections.observableArrayList(), parentPanel);
	}

	private BankAccessListPanel(ObservableList<BankAccess> data, BankAccessOverviewPanel parentPanel) {
		super(data);
		this.masterData = data;
		this.parentPanel = parentPanel;
		createBankAccessListPanel();
	}

	private void createBankAccessListPanel() {
		setPanelTitle(getText("UI_PANEL_BANK_ACCESS_LIST"));

		TableColumn<BankAccess, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), BankAccess::isSelected,
				BankAccess::setSelected);

		TableColumn<BankAccess, String> bankCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BANK"), BankAccess::getBankName, 120);

		TableColumn<BankAccess, String> userCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_USER_ID"), BankAccess::getUserId, 120);

		TableColumn<BankAccess, String> tanCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_TAN_PROCEDURE"),
				access -> access.getTanProcedure() != null ? access.getTanProcedure().toString() : "", 120);

		TableColumn<BankAccess, String> urlCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_FINTS_URL"), BankAccess::getHbciURL, 220, 320);

		TableColumn<BankAccess, String> activeCol = TableColumnFactory.createBooleanAsTextColumn(getText("UI_TABLE_ACTIVE"), BankAccess::isActive, 70);

		TableColumn<BankAccess, String> updatedCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_UPDATED_AT"),
				access -> DateFormatUtils.formatShort(access.getUpdatedAt()), 90);

		tableView.getColumns().setAll(List.of(selectedCol, bankCol, userCol, tanCol, urlCol, activeCol, updatedCol));

		tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selectedAccess) -> {
			if (selectedAccess != null) {
				handleSelection(selectedAccess);
			}
		});

		reload();
	}

	private void handleSelection(BankAccess selectedAccess) {
		log.info(messages.getFormattedMessage("LOG_INFO_ACCESS_SELECTED", selectedAccess.getId()));

		List<BankAccount> bankAccessAccountList = dbController.getAllByParent(BankAccount.class, selectedAccess.getId());
		selectedAccess.setAccounts(bankAccessAccountList);

		parentPanel.getBankAccessDetailPanel().updatePanelFieldValues(selectedAccess);
	}

	@Override
	protected boolean matchesFilter(BankAccess access, String filter) {
		return filter.isBlank() || contains(access.getBankName(), filter) || contains(access.getUserId(), filter) || contains(access.getHbciURL(), filter);
	}

	public void reload() {
		masterData.setAll(dbController.getAll(BankAccess.class));
	}

	public void refreshModelBankAccess() {
		reload();
	}
}