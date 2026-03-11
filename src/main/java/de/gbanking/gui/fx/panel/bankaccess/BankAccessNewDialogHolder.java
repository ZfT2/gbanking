package de.gbanking.gui.fx.panel.bankaccess;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.enu.TanProcedure;
import de.gbanking.gui.fx.enu.ButtonContext;
import de.gbanking.gui.fx.panel.BasePanelHolder;
import de.gbanking.gui.fx.panel.overview.BankAccessOverviewPanel;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class BankAccessNewDialogHolder extends BasePanelHolder {

	private final ButtonContext buttonContext;
	private final BankAccessOverviewPanel overviewPanel;
	private BankAccess currentBankAccess;

	public BankAccessNewDialogHolder(ButtonContext buttonContext, BankAccessOverviewPanel overviewPanel) {
		this.buttonContext = buttonContext;
		this.overviewPanel = overviewPanel;
		this.currentBankAccess = overviewPanel.getCurrentBankAccess();
	}

	public void showDialog() {
		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(buttonContext.getHeadline());

		switch (buttonContext) {
		case BUTTON_NEW, BUTTON_EDIT -> dialog.setScene(new Scene(createStep1(dialog), 420, 240));
		case BUTTON_DELETE -> dialog.setScene(new Scene(createDeletePanel(dialog), 420, 180));
		default -> throw new IllegalStateException("Unsupported buttonContext: " + buttonContext);
		}

		dialog.showAndWait();
	}

	private VBox createStep1(Stage dialog) {
		TextField blzText = new TextField("30530500");
		TextField userNameText = new TextField("");
		PasswordField pinText = new PasswordField();

		if (buttonContext == ButtonContext.BUTTON_EDIT && currentBankAccess != null) {
			blzText.setText(currentBankAccess.getBlz());
			userNameText.setText(currentBankAccess.getUserId());
		}

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(6);
		grid.setPadding(new Insets(10));
		grid.add(new Label("Bank"), 0, 0);
		grid.add(blzText, 1, 0);
		grid.add(new Label("Benutzer"), 0, 1);
		grid.add(userNameText, 1, 1);
		grid.add(new Label("PIN"), 0, 2);
		grid.add(pinText, 1, 2);

		Button okButton = new Button("OK");
		Button cancelButton = new Button("Abbrechen");

		okButton.setOnAction(e -> {
			BankAccess bankAccess = new BankAccess();
			bankAccess.setBlz(blzText.getText());
			bankAccess.setUserId(userNameText.getText());
			bankAccess.setPin(pinText.getText().toCharArray());
			bankAccess.setTanProcedure(TanProcedure.APP_TAN);

			if (bean.addNewBankAccess(bankAccess)) {
				dialog.setScene(new Scene(createStep2(dialog, bankAccess), 800, 600));
			}
		});

		cancelButton.setOnAction(e -> dialog.close());

		VBox root = new VBox(10, new Label("Bankzugang Daten"), grid, new javafx.scene.layout.HBox(10, okButton, cancelButton));
		root.setPadding(new Insets(10));
		return root;
	}

	private VBox createStep2(Stage dialog, BankAccess bankAccess) {
		Label accountsLabel = new Label("Konten:");

		TableView<Object> accountTable = new TableView<>();
		// Hier könntest du später eine echte JavaFX-Kontenliste mit Checkbox-Spalte
		// einbauen.
		// Für die reine Migration lasse ich die Tabelle als Platzhalter-View, bis du
		// die
		// dazugehörige Dialog-Checkbox-Logik exakt brauchst.

		Button okButton = new Button("OK");
		Button cancelButton = new Button("Abbrechen");

		okButton.setOnAction(e -> {
			if (bean.saveBankAccessAccountsToDB(bankAccess)) {
				overviewPanel.getBankAccessListPanel().refreshModelBankAccess();
				dialog.close();
			} else {
				accountsLabel.setText("Fehler bei Speicherung des Bankzugangs!");
			}
		});

		cancelButton.setOnAction(e -> dialog.close());

		VBox root = new VBox(10, new Label("Bankzugang Konten"), accountsLabel, accountTable, new javafx.scene.layout.HBox(10, okButton, cancelButton));
		root.setPadding(new Insets(10));
		return root;
	}

	private VBox createDeletePanel(Stage dialog) {
		Label blzValue = new Label(currentBankAccess != null ? currentBankAccess.getBlz() : "");
		Label userValue = new Label(currentBankAccess != null ? currentBankAccess.getUserId() : "");
		Label question = new Label(getText("BANKACCESS_QUESTION_DELETE"));

		Button okButton = new Button("OK");
		Button cancelButton = new Button("Abbrechen");

		okButton.setOnAction(e -> {
			if (currentBankAccess == null) {
				dialog.close();
				return;
			}

			if (bean.deleteBankAccessFromDB(currentBankAccess)) {
				currentBankAccess = null;
				overviewPanel.getBankAccessListPanel().refreshModelBankAccess();
				question.setText(getText("BANKACCESS_SUCCESS_DELETE"));
				cancelButton.setDisable(true);
			} else {
				question.setText(getText("BANKACCESS_ERROR_DELETE"));
			}
		});

		cancelButton.setOnAction(e -> dialog.close());

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(6);
		grid.add(new Label("Bank"), 0, 0);
		grid.add(blzValue, 1, 0);
		grid.add(new Label("Benutzer"), 0, 1);
		grid.add(userValue, 1, 1);

		VBox root = new VBox(10, new Label("Bankzugang Daten"), grid, question, new javafx.scene.layout.HBox(10, okButton, cancelButton));
		root.setPadding(new Insets(10));
		return root;
	}
}