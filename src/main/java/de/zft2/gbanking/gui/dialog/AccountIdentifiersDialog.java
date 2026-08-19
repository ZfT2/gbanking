package de.zft2.gbanking.gui.dialog;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountIdentifier;
import de.zft2.gbanking.db.dao.enu.AccountIdentifierType;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.service.importproperties.ImportPropertiesSynchronizationService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AccountIdentifiersDialog implements BaseMessages {

	private static final Logger log = LogManager.getLogger(AccountIdentifiersDialog.class);

	private final int bankAccountId;
	private final String accountName;
	private final DBController dbController;
	private final Map<AccountIdentifierType, TextArea> editors = new EnumMap<>(AccountIdentifierType.class);

	private AccountIdentifiersDialog(BankAccount bankAccount) {
		this.bankAccountId = bankAccount.getId();
		this.accountName = bankAccount.getAccountName();
		this.dbController = DBController.getInstance(".");
	}

	public static void showAndWait(Window parentWindow, BankAccount bankAccount) {
		new AccountIdentifiersDialog(bankAccount).showDialog(parentWindow);
	}

	private void showDialog(Window parentWindow) {
		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_DIALOG_ACCOUNT_IDENTIFIERS_TITLE");
		TabPane tabPane = new TabPane();
		for (AccountIdentifierType type : AccountIdentifierType.values()) {
			TextArea editor = new TextArea();
			editor.setWrapText(false);
			editors.put(type, editor);
			String helpKey = type == AccountIdentifierType.ACCOUNT ? "UI_ACCOUNT_IDENTIFIERS_ACCOUNT_HELP"
					: "UI_ACCOUNT_IDENTIFIERS_TRANSFER_HELP";
			Label help = new Label(getText(helpKey));
			help.setWrapText(true);
			VBox tabContent = new VBox(8, help, editor);
			tabContent.setPadding(new Insets(10));
			VBox.setVgrow(editor, Priority.ALWAYS);
			Tab tab = new Tab(type.getPropertyValue(), tabContent);
			tab.setClosable(false);
			tabPane.getTabs().add(tab);
		}
		loadIdentifiers();

		Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		saveButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);
		saveButton.setOnAction(event -> save(dialog));
		cancelButton.setOnAction(event -> dialog.close());

		Label accountLabel = new Label(accountName);
		accountLabel.setStyle("-fx-font-weight: bold;");
		VBox root = new VBox(10, accountLabel, tabPane, DialogWindowSupport.createButtonBar(saveButton, cancelButton));
		root.setPadding(new Insets(12));
		VBox.setVgrow(tabPane, Priority.ALWAYS);
		dialog.setScene(new Scene(root, 650, 480));
		dialog.showAndWait();
	}

	private void loadIdentifiers() {
		for (BankAccountIdentifier identifier : dbController.getBankAccountIdentifiers(bankAccountId)) {
			TextArea editor = editors.get(identifier.propertyType());
			if (!editor.getText().isEmpty()) {
				editor.appendText(System.lineSeparator());
			}
			editor.appendText(identifier.value());
		}
	}

	private void save(Stage dialog) {
		List<BankAccountIdentifier> identifiers = new ArrayList<>();
		for (Map.Entry<AccountIdentifierType, TextArea> entry : editors.entrySet()) {
			for (String value : values(entry.getValue())) {
				identifiers.add(new BankAccountIdentifier(0, bankAccountId, entry.getKey(), value));
			}
		}
		try {
			dbController.replaceBankAccountIdentifiers(bankAccountId, identifiers);
			ServiceRegistry.getService(ImportPropertiesSynchronizationService.class).synchronize();
			dialog.close();
		} catch (RuntimeException exception) {
			log.error("Could not save account identifiers for account id {}", bankAccountId, exception);
			DialogWindowSupport.showAlert(dialog, AlertType.ERROR, getText("ALERT_ACCOUNT_IDENTIFIERS_SAVE_FAILED"));
		}
	}

	private static List<String> values(TextArea editor) {
		return editor.getText().lines().map(value -> value.trim()).filter(value -> !value.isEmpty()).toList();
	}
}
