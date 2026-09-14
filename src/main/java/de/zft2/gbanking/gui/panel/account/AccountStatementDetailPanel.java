package de.zft2.gbanking.gui.panel.account;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormFields;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.service.account.AccountStatement;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

final class AccountStatementDetailPanel extends AbstractReadonlyDetailPanel {

	private final TextField retrievedAtText = FormFields.textS();
	private final TextField statementDateText = FormFields.textS();
	private final TextField periodText = FormFields.textM();
	private final TextField statementNumberText = FormFields.textS();
	private final TextField formatText = FormFields.textS();
	private final TextField fileNameText = FormFields.textL();
	private final TextField sizeText = FormFields.textS();
	private final TextField acknowledgedText = FormFields.textS();
	private final Button retrieveButton = new Button(getText("UI_BUTTON_ACCOUNT_STATEMENTS_RETRIEVE"));
	private final Button acknowledgeButton = new Button(getText("UI_BUTTON_ACCOUNT_STATEMENTS_ACKNOWLEDGE"));
	private final Runnable retrieveAction;
	private final Runnable acknowledgeAction;

	private BankAccount currentAccount;
	private boolean retrievalSupported;

	AccountStatementDetailPanel(Runnable retrieveAction, Runnable acknowledgeAction) {
		super("UI_PANEL_ACCOUNT_STATEMENT_DETAILS");
		this.retrieveAction = retrieveAction;
		this.acknowledgeAction = acknowledgeAction;
		configureGrid();
		createPanel();
	}

	private void configureGrid() {
		FormGridHelper.setEqualGrowColumns(formGrid, 4);
	}

	private void createPanel() {
		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_RETRIEVED_AT", retrievedAtText, 0, 0);
		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_DATE", statementDateText, 1, 0);
		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_PERIOD", periodText, 2, 0);
		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_NUMBER", statementNumberText, 3, 0);

		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_FORMAT", formatText, 0, 1);
		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_FILE", fileNameText, 1, 1, 2);
		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_SIZE", sizeText, 3, 1);

		addFieldInline("UI_LABEL_ACCOUNT_STATEMENT_ACKNOWLEDGED", acknowledgedText, 0, 2);

		makeReadOnly(retrievedAtText, statementDateText, periodText, statementNumberText, formatText, fileNameText, sizeText, acknowledgedText);
		retrieveButton.setOnAction(event -> retrieveAction.run());
		acknowledgeButton.setOnAction(event -> acknowledgeAction.run());
		addContentNode(FormStyleUtils.createButtonBar(retrieveButton, acknowledgeButton));
		updateRetrieveButton();
	}

	void updateAccount(BankAccount account, boolean supported) {
		currentAccount = account;
		retrievalSupported = supported;
		if (account != null) {
			updateTitle(account.getAccountName());
		} else {
			resetTitle();
		}
		clearStatement();
		updateRetrieveButton();
	}

	void updateStatement(AccountStatement statement) {
		if (statement == null) {
			clearStatement();
			return;
		}

		retrievedAtText.setText(DateFormatUtils.formatDateTime(statement.retrievedAt()));
		statementDateText.setText(DateFormatUtils.formatLong(statement.statementDate()));
		periodText.setText(AccountStatementFormatUtils.formatPeriod(statement.startDate(), statement.endDate()));
		statementNumberText.setText(formatStatementNumber(statement));
		formatText.setText(statement.format());
		fileNameText.setText(statement.fileName());
		sizeText.setText(AccountStatementFormatUtils.formatFileSize(statement.size()));
		acknowledgedText.setText(formatBoolean(statement.acknowledged()));
	}

	void setRetrievalRunning(boolean running) {
		retrieveButton.setDisable(running || currentAccount == null || !retrievalSupported);
		acknowledgeButton.setDisable(running || currentAccount == null || !retrievalSupported);
	}

	private void clearStatement() {
		retrievedAtText.clear();
		statementDateText.clear();
		periodText.clear();
		statementNumberText.clear();
		formatText.clear();
		fileNameText.clear();
		sizeText.clear();
		acknowledgedText.clear();
	}

	private void updateRetrieveButton() {
		retrieveButton.setDisable(currentAccount == null || !retrievalSupported);
		acknowledgeButton.setDisable(currentAccount == null || !retrievalSupported);
	}

	private String formatStatementNumber(AccountStatement statement) {
		return AccountStatementFormatUtils.formatStatementNumber(statement);
	}

	private String formatBoolean(boolean value) {
		return getText(value ? "UI_LABEL_BOOLEAN_TRUE" : "UI_LABEL_BOOLEAN_FALSE");
	}
}
