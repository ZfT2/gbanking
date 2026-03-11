package de.gbanking.gui.fx.panel.recipient;

import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.fx.panel.BasePanelHolder;
import de.gbanking.gui.fx.panel.overview.RecipientOverviewPanel;
import de.gbanking.gui.fx.util.FormGridHelper;
import de.gbanking.util.TypeConverter;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class RecipientDetailPanel extends BasePanelHolder {

	private final TextField nameText = new TextField();
	private final TextField ibanText = new TextField();
	private final TextField bicText = new TextField();
	private final TextField accountNumberText = new TextField();
	private final TextField blzText = new TextField();
	private final TextField bankText = new TextField();
	private final TextArea noteText = new TextArea();
	private final TextField updatedAtText = new TextField();

	private final Button buttonRecipientNew = new Button();
	private final Button buttonRecipientSave = new Button();
	private final Button buttonRecipientDelete = new Button();

	private final RecipientOverviewPanel parentPanel;
	private Recipient selectedRecipient;

	public RecipientDetailPanel(RecipientOverviewPanel parentPanel) {
		this.parentPanel = parentPanel;
		createInnerRecipientDetailPanel();
	}

	private void createInnerRecipientDetailPanel() {
		buttonRecipientNew.setText(getText("UI_BUTTON_NEW"));
		buttonRecipientSave.setText(getText("UI_BUTTON_SAVE"));
		buttonRecipientDelete.setText(getText("UI_BUTTON_DELETE"));

		buttonRecipientNew.setOnAction(e -> resetTextFields());
		buttonRecipientSave.setOnAction(e -> saveRecipient());
		buttonRecipientDelete.setOnAction(e -> deleteRecipient());

		updatedAtText.setEditable(false);
		noteText.setPrefRowCount(3);
		noteText.setWrapText(true);

		GridPane grid = FormGridHelper.createDefaultGrid();
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_NAME"), nameText, 0, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_BANK"), bankText, 1, 0);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_IBAN"), ibanText, 0, 1);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_BIC"), bicText, 1, 1);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_ACCOUNT_NUMBER"), accountNumberText, 0, 2);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_BLZ"), blzText, 1, 2);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_NOTE"), noteText, 0, 3);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_UPDATED_AT"), updatedAtText, 1, 3);

		HBox buttonBar = new HBox(10, buttonRecipientNew, buttonRecipientSave, buttonRecipientDelete);
		VBox content = new VBox(8, grid, buttonBar);
		content.setPadding(new Insets(6));

		TitledPane titledPane = new TitledPane(getText("UI_PANEL_RECIPIENT_DETAILS"), content);
		titledPane.setCollapsible(false);

		getChildren().clear();
		getChildren().add(titledPane);
	}

	private void saveRecipient() {
		if (nameText.getText().isBlank() || ibanText.getText().isBlank()) {
			new Alert(Alert.AlertType.WARNING, getText("ALERT_RECIPIENT_REQUIRED_FIELD_MISSING")).showAndWait();
			return;
		}

		Recipient recipient = new Recipient(nameText.getText(), ibanText.getText());
		recipient.setSource(Source.MANUELL);

		if (!bicText.getText().isBlank()) {
			recipient.setBic(bicText.getText());
		}
		if (!accountNumberText.getText().isBlank()) {
			recipient.setAccountNumber(accountNumberText.getText());
		}
		if (!blzText.getText().isBlank()) {
			recipient.setBlz(blzText.getText());
		}
		if (!bankText.getText().isBlank()) {
			recipient.setBank(bankText.getText());
		}
		if (!noteText.getText().isBlank()) {
			recipient.setNote(noteText.getText());
		}

		bean.saveRecipientToDB(recipient);
		parentPanel.getRecipientListPanel().refresh();
	}

	private void deleteRecipient() {
		if (selectedRecipient != null) {
			bean.deleteRecipientFromDB(selectedRecipient);
			parentPanel.getRecipientListPanel().refresh();
			resetTextFields();
		}
	}

	private void resetTextFields() {
		nameText.clear();
		ibanText.clear();
		bicText.clear();
		accountNumberText.clear();
		blzText.clear();
		bankText.clear();
		noteText.clear();
		updatedAtText.clear();
		enableInputFields(true);
		selectedRecipient = null;
	}

	private void enableInputFields(boolean enable) {
		nameText.setDisable(!enable);
		ibanText.setDisable(!enable);
		bicText.setDisable(!enable);
		accountNumberText.setDisable(!enable);
		blzText.setDisable(!enable);
		bankText.setDisable(!enable);
		noteText.setDisable(!enable);
	}

	public void updatePanelFieldValues(Recipient selectedRecipient) {
		nameText.setText(selectedRecipient.getName());
		ibanText.setText(selectedRecipient.getIban());
		bicText.setText(selectedRecipient.getBic());
		accountNumberText.setText(selectedRecipient.getAccountNumber());
		blzText.setText(selectedRecipient.getBlz());
		bankText.setText(selectedRecipient.getBank());
		noteText.setText(selectedRecipient.getNote());
		updatedAtText.setText(TypeConverter.toDateStringLong(selectedRecipient.getUpdatedAt()));

		parentPanel.setCurrentRecipient(selectedRecipient);

		boolean isRecipientEditable = bean.isRecipientEditable(selectedRecipient);
		buttonRecipientDelete.setDisable(!isRecipientEditable);
		enableInputFields(isRecipientEditable);

		this.selectedRecipient = selectedRecipient;
	}
}