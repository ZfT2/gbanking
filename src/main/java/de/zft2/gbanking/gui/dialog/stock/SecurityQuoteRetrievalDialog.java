package de.zft2.gbanking.gui.dialog.stock;

import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.quote.QuoteSource;
import de.zft2.gbanking.service.stock.quote.StockQuoteRetrievalService;
import de.zft2.gbanking.service.stock.quote.StockQuoteRetrievalService.RetrievalResult;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

final class SecurityQuoteRetrievalDialog implements BaseMessages {

	private final int securityId;
	private final String securityName;
	private final Runnable changedHandler;
	private final StockQuoteRetrievalService service;
	private final Stage dialog;
	private final ComboBox<QuoteSource> providerField = new ComboBox<>();
	private final TextField symbolField = new TextField();
	private final Label statusLabel = new Label();
	private final Button retrieveButton = new Button(getText("UI_STOCK_QUOTE_RETRIEVE"));
	private final Button closeButton = new Button(getText("UI_BUTTON_CLOSE"));
	private boolean retrieving;

	SecurityQuoteRetrievalDialog(Window owner, int securityId, String securityName, Runnable changedHandler) {
		this.securityId = securityId;
		this.securityName = securityName;
		this.changedHandler = changedHandler;
		service = ServiceRegistry.getService(StockQuoteRetrievalService.class);
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_QUOTE_DIALOG_TITLE");
		initialize();
	}

	void show() {
		dialog.showAndWait();
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_QUOTE_DIALOG_HEADER", securityName));
		header.getStyleClass().add("gbanking-form-section-title");

		List<QuoteSource> providers = service.getConfiguredSources();
		providerField.setItems(FXCollections.observableArrayList(providers));
		providerField.setMaxWidth(Double.MAX_VALUE);
		symbolField.setMaxWidth(Double.MAX_VALUE);
		statusLabel.setWrapText(true);

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_QUOTE_PROVIDER"), providerField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_QUOTE_SYMBOL"), symbolField, 1, 0);

		providerField.valueProperty().addListener((observable, previous, selected) -> updateSuggestedSymbol(selected));
		retrieveButton.setOnAction(event -> retrieve());
		closeButton.setOnAction(event -> dialog.close());
		dialog.setOnCloseRequest(event -> {
			if (retrieving) {
				event.consume();
			}
		});

		if (providers.isEmpty()) {
			statusLabel.setText(getText("UI_STOCK_QUOTE_NO_PROVIDER"));
			retrieveButton.setDisable(true);
		} else {
			providerField.setValue(providers.get(0));
		}

		VBox root = DialogWindowSupport.createDialogRoot(header, form, statusLabel,
				DialogWindowSupport.createButtonBar(retrieveButton, closeButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 620, 240));
	}

	private void updateSuggestedSymbol(QuoteSource provider) {
		if (provider != null) {
			symbolField.setText(service.getSuggestedSymbol(securityId, provider));
		}
	}

	private void retrieve() {
		QuoteSource provider = providerField.getValue();
		String symbol = symbolField.getText();
		setRetrieving(true);
		statusLabel.setText(getText("UI_STOCK_QUOTE_RETRIEVING"));

		Task<RetrievalResult> task = new Task<>() {
			@Override
			protected RetrievalResult call() {
				return service.retrieve(securityId, provider, symbol);
			}
		};
		task.setOnSucceeded(event -> retrievalSucceeded(task.getValue()));
		task.setOnFailed(event -> retrievalFailed(task.getException()));
		task.setOnCancelled(event -> setRetrieving(false));
		if (!BackgroundActionCoordinator.getInstance().start(task, "gbanking-security-quotes")) {
			setRetrieving(false);
			statusLabel.setText(getText("UI_STOCK_QUOTE_NOT_STARTED"));
		}
	}

	private void retrievalSucceeded(RetrievalResult result) {
		setRetrieving(false);
		statusLabel.setText(getText("UI_STOCK_QUOTE_RESULT", result.received(), result.inserted(),
				result.corrected(), result.unchanged()));
		changedHandler.run();
	}

	private void retrievalFailed(Throwable failure) {
		setRetrieving(false);
		Exception cause = failure instanceof Exception exception ? exception : new Exception(failure);
		RuntimeException exception = failure instanceof RuntimeException runtimeException
				? runtimeException : new GBankingException("Der Kursabruf ist fehlgeschlagen", cause);
		DialogWindowSupport.showAlert(dialog, Alert.AlertType.ERROR, exception.getMessage());
		statusLabel.setText(getText("UI_STOCK_QUOTE_FAILED"));
	}

	private void setRetrieving(boolean value) {
		retrieving = value;
		providerField.setDisable(value);
		symbolField.setDisable(value);
		retrieveButton.setDisable(value || providerField.getItems().isEmpty());
		closeButton.setDisable(value);
	}
}
