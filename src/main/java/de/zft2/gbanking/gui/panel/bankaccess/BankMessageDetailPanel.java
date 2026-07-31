package de.zft2.gbanking.gui.panel.bankaccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormFields;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;

public class BankMessageDetailPanel extends AbstractReadonlyDetailPanel {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

	private final TextField bankNameText = FormFields.textM();
	private final TextField retrievedAtText = FormFields.textS();
	private final TextField versionDateText = FormFields.textS();
	private final TextField codeText = FormFields.textS();
	private final TextField typeText = FormFields.textS();
	private final TextField formatText = FormFields.textS();
	private final TextField descriptionText = FormFields.textL();
	private final Button retrieveButton = new Button(getText("UI_BUTTON_BANK_MESSAGES_RETRIEVE"));
	private final Runnable retrieveAction;

	private BankAccess currentBankAccess;
	private boolean retrievalSupported;

	public BankMessageDetailPanel(Runnable retrieveAction) {
		super("UI_PANEL_BANK_MESSAGE_DETAILS");
		this.retrieveAction = retrieveAction;
		configureGrid();
		createPanel();
	}

	private void configureGrid() {
		formGrid.getColumnConstraints().clear();
		for (int i = 0; i < 4; i++) {
			ColumnConstraints constraints = new ColumnConstraints();
			constraints.setHgrow(Priority.ALWAYS);
			constraints.setFillWidth(true);
			formGrid.getColumnConstraints().add(constraints);
		}
	}

	private void createPanel() {
		addFieldInline("UI_LABEL_BANK", bankNameText, 0, 0);
		addFieldInline("UI_LABEL_BANK_MESSAGE_RETRIEVED_AT", retrievedAtText, 1, 0);
		addFieldInline("UI_LABEL_BANK_MESSAGE_VERSION_DATE", versionDateText, 2, 0);
		addFieldInline("UI_LABEL_BANK_MESSAGE_CODE", codeText, 3, 0);

		addFieldInline("UI_LABEL_BANK_MESSAGE_TYPE", typeText, 0, 1);
		addFieldInline("UI_LABEL_BANK_MESSAGE_FORMAT", formatText, 1, 1);
		addFieldInline("UI_LABEL_BANK_MESSAGE_DESCRIPTION", descriptionText, 2, 1, 2);

		makeReadOnly(bankNameText, retrievedAtText, versionDateText, codeText, typeText, formatText, descriptionText);
		FormStyleUtils.setReadOnlyStyle(true, bankNameText, retrievedAtText, versionDateText, codeText, typeText, formatText, descriptionText);
		retrieveButton.setOnAction(event -> retrieveAction.run());
		addContentNode(FormStyleUtils.createButtonBar(retrieveButton));
		updateRetrieveButton();
	}

	public void updateBankAccess(BankAccess bankAccess, boolean supported) {
		currentBankAccess = bankAccess;
		retrievalSupported = supported;
		if (bankAccess != null) {
			updateTitle(bankAccess.getBankName());
			bankNameText.setText(toText(bankAccess.getBankName()));
		} else {
			resetTitle();
			bankNameText.clear();
		}
		clearMessage();
		updateRetrieveButton();
	}

	public void updateMessage(BankMessage bankMessage) {
		if (bankMessage == null) {
			clearMessage();
			return;
		}

		retrievedAtText.setText(formatDateTime(bankMessage.getRetrievedAt()));
		versionDateText.setText(formatDate(bankMessage.getVersionDate()));
		codeText.setText(toText(bankMessage.getCode()));
		typeText.setText(formatType(bankMessage.getType()));
		formatText.setText(toText(bankMessage.getFormat()));
		descriptionText.setText(toText(bankMessage.getDescription()));
	}

	public void setRetrievalRunning(boolean running) {
		retrieveButton.setDisable(running || currentBankAccess == null || !retrievalSupported);
	}

	private void clearMessage() {
		retrievedAtText.clear();
		versionDateText.clear();
		codeText.clear();
		typeText.clear();
		formatText.clear();
		descriptionText.clear();
	}

	private void updateRetrieveButton() {
		retrieveButton.setDisable(currentBankAccess == null || !retrievalSupported);
	}

	private String formatDateTime(LocalDateTime dateTime) {
		return dateTime != null ? DATE_TIME_FORMAT.format(dateTime) : "";
	}

	private String formatDate(LocalDate date) {
		return date != null ? DateFormatUtils.formatLong(date) : "";
	}

	private String formatType(String type) {
		if (type == null || type.isBlank()) {
			return "";
		}
		return switch (type.trim().toUpperCase(Locale.ROOT)) {
		case "F" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_FREE_TEXT");
		case "D" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_FILE");
		case "S" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_DOCUMENT");
		case "T" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_TOPIC");
		default -> type.trim();
		};
	}

	private String toText(String value) {
		return value != null ? value : "";
	}
}
