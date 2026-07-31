package de.zft2.gbanking.gui.dialog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.stage.Window;

public class BankAccessParameterDataDialog implements BaseMessages {

	public void show(Window parentWindow, String parameterType, BankAccess bankAccess, List<ParameterDataBankAccess> parameterData) {
		Stage dialog = DialogWindowSupport.createModalStageWithTitle(parentWindow,
				getText("UI_DIALOG_BANK_ACCESS_PARAMETER_DATA_TITLE", parameterType));
		Label header = new Label(
				getText("UI_DIALOG_BANK_ACCESS_PARAMETER_DATA_HEADER", parameterType, bankAccess != null ? bankAccess.getBankName() : ""));
		TableView<ParameterDataBankAccess> parameterTable = createParameterTable(parameterData);

		Button closeButton = new Button(getText("UI_BUTTON_CLOSE"));
		closeButton.setOnAction(event -> dialog.close());

		DialogWindowSupport.setVgrowAlways(parameterTable);
		dialog.setScene(DialogWindowSupport.createScene(
				DialogWindowSupport.createDialogRoot(header, parameterTable, DialogWindowSupport.createButtonBar(closeButton)), 720, 420));
		dialog.showAndWait();
	}

	private TableView<ParameterDataBankAccess> createParameterTable(List<ParameterDataBankAccess> parameterData) {
		List<ParameterDataBankAccess> rows = new ArrayList<>(parameterData != null ? parameterData : List.of());
		rows.sort(Comparator.comparing(ParameterDataBankAccess::getPdKey, Comparator.nullsLast(String::compareToIgnoreCase)));

		TableView<ParameterDataBankAccess> table = new TableView<>(FXCollections.observableArrayList(rows));
		table.setPlaceholder(new Label(getText("UI_DIALOG_BANK_ACCESS_PARAMETER_DATA_EMPTY")));
		table.getColumns().setAll(List.<TableColumn<ParameterDataBankAccess, ?>>of(
				TableColumnFactory.createTextColumn(getText("UI_DIALOG_BANK_ACCESS_PARAMETER_DATA_KEY"),
						ParameterDataBankAccess::getPdKey, 220, 280),
				TableColumnFactory.createWrappedTextColumn(getText("UI_DIALOG_BANK_ACCESS_PARAMETER_DATA_VALUE"),
						ParameterDataBankAccess::getPdValue, 300, 420)));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
		GuiLayoutState.configureTable(table, "dialog.bankAccessParameterData");
		return table;
	}
}
