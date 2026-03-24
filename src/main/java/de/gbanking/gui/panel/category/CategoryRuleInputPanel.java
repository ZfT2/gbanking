package de.gbanking.gui.panel.category;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.CategoryRule;
import de.gbanking.db.dao.CategoryRule.JoinType;
import de.gbanking.gui.panel.AbstractTitledFormPanel;
import de.gbanking.gui.panel.overview.CategoryOverviewPanel;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class CategoryRuleInputPanel extends AbstractTitledFormPanel {

	private static final Logger log = LogManager.getLogger(CategoryRuleInputPanel.class);

	private final CategoryOverviewPanel parentPanel;

	private final TextField accountName = new TextField();
	private final ComboBox<Category> categoryCombo = new ComboBox<>();
	private final ComboBox<JoinType> joinTypeCombo = new ComboBox<>();
	private final DatePicker filterDateFrom = new DatePicker();
	private final DatePicker filterDateTo = new DatePicker();
	private final TextField filterAmountFrom = new TextField();
	private final TextField filterAmountTo = new TextField();
	private final TextField filterRecipient = new TextField();
	private final TextField filterPurpose = new TextField();
	private final CheckBox filterRecipientRegexCheckbox = new CheckBox();
	private final CheckBox filterPurposeRegexCheckbox = new CheckBox();
	private final TextField updatedAtText = new TextField();
	private final Button buttonSubmit = new Button();

	private CategoryRule selectedCategoryRule;
	private BankAccount selectedAccount;

	public CategoryRuleInputPanel(CategoryOverviewPanel parentPanel) {
		super("UI_PANEL_CATEGORY_RULES");
		this.parentPanel = parentPanel;
		createCategoryRuleInputPanel();
	}

	private void createCategoryRuleInputPanel() {
		categoryCombo.setItems(FXCollections.observableArrayList(dbController.getAll(Category.class)));
		joinTypeCombo.setItems(FXCollections.observableArrayList(JoinType.values()));
		joinTypeCombo.setValue(JoinType.OR);
		accountName.setEditable(false);
		updatedAtText.setEditable(false);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonSubmit.setText(getText("UI_BUTTON_SAVE"));
		Button buttonDelete = new Button(getText("UI_BUTTON_DELETE"));
		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));

		buttonNew.setOnAction(event -> resetTextFields());
		buttonSubmit.setOnAction(event -> saveCategoryRule());
		buttonDelete.setOnAction(event -> deleteCategoryRule());
		buttonCancel.setOnAction(event -> resetTextFields());

		addFieldAbove("UI_LABEL_ACCOUNT", accountName, 0, 0);
		addFieldAbove("UI_LABEL_CATEGORY", categoryCombo, 1, 0);
		addFieldAbove("UI_LABEL_JOIN_TYPE", joinTypeCombo, 2, 0);
		addFieldAbove("UI_LABEL_DATE_FROM", filterDateFrom, 0, 1);
		addFieldAbove("UI_LABEL_DATE_TO", filterDateTo, 1, 1);
		addFieldAbove("UI_LABEL_AMOUNT_FROM", filterAmountFrom, 0, 2);
		addFieldAbove("UI_LABEL_AMOUNT_TO", filterAmountTo, 1, 2);
		addFieldAbove("UI_LABEL_RECIPIENT", filterRecipient, 0, 3, 2);
		addFieldAbove("UI_LABEL_REGEX", filterRecipientRegexCheckbox, 2, 3);
		addFieldAbove("UI_LABEL_PURPOSE", filterPurpose, 0, 4, 2);
		addFieldAbove("UI_LABEL_REGEX", filterPurposeRegexCheckbox, 2, 4);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 0, 5);

		HBox buttonBar = new HBox(10, buttonNew, buttonSubmit, buttonDelete, buttonCancel);
		addContentNode(buttonBar);
	}

	private void saveCategoryRule() {
		if (categoryCombo.getValue() == null) {
			new Alert(Alert.AlertType.WARNING, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING")).showAndWait();
			return;
		}

		CategoryRule categoryRule = selectedCategoryRule != null ? selectedCategoryRule : new CategoryRule();
		categoryRule.setCategory(categoryCombo.getValue());
		categoryRule.setJoinType(joinTypeCombo.getValue() != null ? joinTypeCombo.getValue() : JoinType.OR);
		categoryRule.setFilterDateFrom(toCalendar(filterDateFrom.getValue()));
		categoryRule.setFilterDateTo(toCalendar(filterDateTo.getValue()));
		categoryRule.setFilterAmountFrom(parseAmount(filterAmountFrom.getText()));
		categoryRule.setFilterAmountTo(parseAmount(filterAmountTo.getText()));
		categoryRule.setFilterRecipient(blankToNull(filterRecipient.getText()));
		categoryRule.setFilterPurpose(blankToNull(filterPurpose.getText()));
		categoryRule.setFilterRecipientIsRegex(filterRecipientRegexCheckbox.isSelected());
		categoryRule.setFilterPurposeIsRegex(filterPurposeRegexCheckbox.isSelected());
		categoryRule.setBankAccountList(selectedAccount != null ? List.of(selectedAccount) : List.of());
		dbController.insertOrUpdate(categoryRule);

		parentPanel.getCategoryRuleListPanel().reload();
		parentPanel.getCategoryListPanel().reload();
		resetTextFields();
	}

	private void deleteCategoryRule() {
		if (selectedCategoryRule == null) {
			return;
		}

		dbController.delete(selectedCategoryRule, null);
		parentPanel.getCategoryRuleListPanel().reload();
		resetTextFields();
	}

	private void resetTextFields() {
		selectedCategoryRule = null;
		categoryCombo.setValue(null);
		joinTypeCombo.setValue(JoinType.OR);
		filterDateFrom.setValue(null);
		filterDateTo.setValue(null);
		filterAmountFrom.clear();
		filterAmountTo.clear();
		filterRecipient.clear();
		filterPurpose.clear();
		filterRecipientRegexCheckbox.setSelected(false);
		filterPurposeRegexCheckbox.setSelected(false);
		updatedAtText.clear();
		updatePanelFieldValues(selectedAccount);
	}

	public void updatePanelFieldValues(CategoryRule categoryRule) {
		log.log(Level.INFO, () -> getText("LOG_INFO_CATEGORY_SELECTED", categoryRule.getId()));
		selectedCategoryRule = categoryRule;
		categoryCombo.setValue(categoryRule.getCategory());
		joinTypeCombo.setValue(categoryRule.getJoinType());
		filterDateFrom.setValue(toLocalDate(categoryRule.getFilterDateFrom()));
		filterDateTo.setValue(toLocalDate(categoryRule.getFilterDateTo()));
		filterAmountFrom.setText(categoryRule.getFilterAmountFrom() != null ? categoryRule.getFilterAmountFrom().toPlainString() : "");
		filterAmountTo.setText(categoryRule.getFilterAmountTo() != null ? categoryRule.getFilterAmountTo().toPlainString() : "");
		filterRecipient.setText(categoryRule.getFilterRecipient() != null ? categoryRule.getFilterRecipient() : "");
		filterPurpose.setText(categoryRule.getFilterPurpose() != null ? categoryRule.getFilterPurpose() : "");
		filterRecipientRegexCheckbox.setSelected(categoryRule.isFilterRecipientIsRegex());
		filterPurposeRegexCheckbox.setSelected(categoryRule.isFilterPurposeIsRegex());
		updatedAtText.setText(categoryRule.getUpdatedAt() != null ? categoryRule.getUpdatedAt().toString() : "");
	}

	public void updatePanelFieldValues(BankAccount bankAccount) {
		selectedAccount = bankAccount;
		accountName.setText(bankAccount != null ? bankAccount.getAccountName() : "");
	}

	private BigDecimal parseAmount(String value) {
		String normalized = blankToNull(value);
		if (normalized == null) {
			return null;
		}
		return new BigDecimal(normalized.replace(',', '.'));
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private LocalDate toCalendar(LocalDate value) {
		return value;
	}

	private LocalDate toLocalDate(LocalDate value) {
		return value;
	}
}
