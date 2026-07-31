package de.zft2.gbanking.gui.panel.category;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.CategoryRule.JoinType;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.AbstractTitledFormPanel;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.util.TextValues;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class CategoryRuleInputPanel extends AbstractTitledFormPanel {

	private static final Logger log = LogManager.getLogger(CategoryRuleInputPanel.class);
	private static final Pattern GERMAN_GROUPED_AMOUNT = Pattern.compile("-?\\d{1,3}(\\.\\d{3})+");
	private static final DateTimeFormatter GERMAN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final double FIELD_WIDTH = 180.0;
	private static final double WIDE_FIELD_WIDTH = 380.0;
	private static final double PURPOSE_REGEX_FIELD_WIDTH = 110.0;
	private static final double CONDITION_FIELD_WIDTH = 240.0;
	private static final double PURPOSE_FIELD_WIDTH = 460.0;
	private static final double UPDATED_AT_FIELD_WIDTH = 125.0;
	private static final double GROUP_GAP = 14.0;
	private static final double RIGHT_COLUMN_WIDTH = PURPOSE_REGEX_FIELD_WIDTH + GROUP_GAP + CONDITION_FIELD_WIDTH;
	private static final int MAX_RULE_NAME_LENGTH = 40;

	private final CategoryOverviewPanel parentPanel;

	private final TextField ruleName = new TextField();
	private final ComboBox<AccountRuleScope> accountScopeCombo = new ComboBox<>();
	private final ComboBox<Category> categoryCombo = new ComboBox<>();
	private final ComboBox<JoinType> joinTypeCombo = new ComboBox<>();
	private final DatePicker filterDateFrom = new DatePicker();
	private final DatePicker filterDateTo = new DatePicker();
	private final TextField filterAmountFrom = new TextField();
	private final TextField filterAmountTo = new TextField();
	private final TextField filterRecipientName = new TextField();
	private final TextField filterRecipientIban = new TextField();
	private final TextField filterRecipientAccountNumber = new TextField();
	private final TextField filterPurpose = new TextField();
	private final CheckBox filterRecipientRegexCheckbox = new CheckBox();
	private final CheckBox filterPurposeRegexCheckbox = new CheckBox();
	private final TextField updatedAtText = new TextField();
	private final Button buttonSubmit = new Button();
	private final DecimalFormat amountFormat = FxTableUtils.createGermanDecimalFormat();

	private CategoryRule selectedCategoryRule;
	private BankAccount selectedAccount;
	private List<BankAccount> selectedRuleAccounts = List.of();

	private enum AccountRuleScope {
		CURRENT_ACCOUNT,
		SELECTED_ACCOUNTS,
		ALL_ACCOUNTS
	}

	public CategoryRuleInputPanel(CategoryOverviewPanel parentPanel) {
		super("UI_PANEL_CATEGORY_RULES");
		this.parentPanel = parentPanel;
		createCategoryRuleInputPanel();
	}

	private void createCategoryRuleInputPanel() {
		refreshCategoryChoices();
		configureFormGrid();
		configureCategoryCombo();
		configureAccountScopeCombo();
		configureDatePickers();
		configureAmountFields();
		configureRuleNameField();
		applyFieldWidths();
		joinTypeCombo.setItems(FXCollections.observableArrayList(JoinType.values()));
		joinTypeCombo.setValue(JoinType.OR);
		accountScopeCombo.setValue(AccountRuleScope.CURRENT_ACCOUNT);
		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		updatedAtText.setAlignment(Pos.CENTER_RIGHT);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonSubmit.setText(getText("UI_BUTTON_SAVE"));
		Button buttonDelete = new Button(getText("UI_BUTTON_DELETE"));
		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));

		buttonNew.setOnAction(event -> resetTextFields());
		buttonSubmit.setOnAction(event -> saveCategoryRule());
		buttonDelete.setOnAction(event -> deleteCategoryRule());
		buttonCancel.setOnAction(event -> resetTextFields());
		KeyboardShortcutDispatcher.registerForm(this, buttonSubmit, buttonCancel);

		addExpandingFieldAbove("UI_LABEL_RULE_NAME", ruleName, 0, 0, 2);
		addRightAlignedNode(createRightAlignedFieldBox(getText("UI_LABEL_UPDATED_AT"), updatedAtText, UPDATED_AT_FIELD_WIDTH), 2, 0);
		addFieldAbove("UI_LABEL_ACCOUNT", accountScopeCombo, 0, 1);
		addFieldAbove("UI_LABEL_CATEGORY", categoryCombo, 1, 1, 2);
		addFullWidthRow(createFilterGroupsPane(), 4, 1);
		addExpandingFieldAbove("UI_LABEL_PURPOSE", filterPurpose, 0, 3, 2);
		addRightAlignedNode(createPurposeOptionsPane(), 2, 3);

		HBox buttonBar = new HBox(10, buttonNew, buttonSubmit, buttonDelete, buttonCancel);
		addContentNode(buttonBar);
	}

	private void saveCategoryRule() {
		if (categoryCombo.getValue() == null) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING"));
			return;
		}

		BigDecimal amountFrom = parseAmountOrWarn(filterAmountFrom.getText());
		if (amountFrom == null && TextValues.trimToNull(filterAmountFrom.getText()) != null) {
			return;
		}
		BigDecimal amountTo = parseAmountOrWarn(filterAmountTo.getText());
		if (amountTo == null && TextValues.trimToNull(filterAmountTo.getText()) != null) {
			return;
		}

		List<BankAccount> ruleAccounts = resolveRuleAccounts();
		if (ruleAccounts == null) {
			return;
		}

		CategoryRule categoryRule = selectedCategoryRule != null ? selectedCategoryRule : new CategoryRule();
		categoryRule.setName(TextValues.trimToNull(ruleName.getText()));
		categoryRule.setCategory(categoryCombo.getValue());
		categoryRule.setJoinType(joinTypeCombo.getValue() != null ? joinTypeCombo.getValue() : JoinType.OR);
		categoryRule.setFilterDateFrom(toCalendar(filterDateFrom.getValue()));
		categoryRule.setFilterDateTo(toCalendar(filterDateTo.getValue()));
		categoryRule.setFilterAmountFrom(amountFrom);
		categoryRule.setFilterAmountTo(amountTo);
		categoryRule.setFilterRecipientName(TextValues.trimToNull(filterRecipientName.getText()));
		categoryRule.setFilterRecipientIban(TextValues.trimToNull(filterRecipientIban.getText()));
		categoryRule.setFilterRecipientAccountNumber(TextValues.trimToNull(filterRecipientAccountNumber.getText()));
		categoryRule.setFilterPurpose(TextValues.trimToNull(filterPurpose.getText()));
		categoryRule.setFilterRecipientIsRegex(filterRecipientRegexCheckbox.isSelected());
		categoryRule.setFilterPurposeIsRegex(filterPurposeRegexCheckbox.isSelected());
		categoryRule.setBankAccountList(ruleAccounts);
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
		selectedRuleAccounts = List.of();
		ruleName.clear();
		accountScopeCombo.setValue(AccountRuleScope.CURRENT_ACCOUNT);
		categoryCombo.setValue(null);
		joinTypeCombo.setValue(JoinType.OR);
		filterDateFrom.setValue(null);
		filterDateTo.setValue(null);
		filterAmountFrom.clear();
		filterAmountTo.clear();
		filterRecipientName.clear();
		filterRecipientIban.clear();
		filterRecipientAccountNumber.clear();
		filterPurpose.clear();
		filterRecipientRegexCheckbox.setSelected(false);
		filterPurposeRegexCheckbox.setSelected(false);
		updatedAtText.clear();
		updatePanelFieldValues(selectedAccount);
	}

	public void updatePanelFieldValues(CategoryRule categoryRule) {
		log.log(Level.INFO, () -> getText("LOG_INFO_CATEGORY_SELECTED", categoryRule.getId()));
		selectedCategoryRule = categoryRule;
		selectedRuleAccounts = categoryRule.getBankAccountList() != null ? categoryRule.getBankAccountList() : List.of();
		ruleName.setText(categoryRule.getName() != null ? categoryRule.getName() : "");
		accountScopeCombo.setValue(resolveAccountRuleScope(categoryRule));
		updateCheckedAccountsForRule();
		if (accountScopeCombo.getValue() == AccountRuleScope.CURRENT_ACCOUNT && !selectedRuleAccounts.isEmpty()) {
			selectedAccount = selectedRuleAccounts.get(0);
			refreshAccountScopeDisplay();
		}
		categoryCombo.setValue(categoryRule.getCategory());
		joinTypeCombo.setValue(categoryRule.getJoinType());
		filterDateFrom.setValue(toLocalDate(categoryRule.getFilterDateFrom()));
		filterDateTo.setValue(toLocalDate(categoryRule.getFilterDateTo()));
		filterAmountFrom.setText(formatAmount(categoryRule.getFilterAmountFrom()));
		filterAmountTo.setText(formatAmount(categoryRule.getFilterAmountTo()));
		filterRecipientName.setText(categoryRule.getFilterRecipientName() != null ? categoryRule.getFilterRecipientName() : "");
		filterRecipientIban.setText(categoryRule.getFilterRecipientIban() != null ? categoryRule.getFilterRecipientIban() : "");
		filterRecipientAccountNumber.setText(
				categoryRule.getFilterRecipientAccountNumber() != null ? categoryRule.getFilterRecipientAccountNumber() : "");
		filterPurpose.setText(categoryRule.getFilterPurpose() != null ? categoryRule.getFilterPurpose() : "");
		filterRecipientRegexCheckbox.setSelected(categoryRule.isFilterRecipientIsRegex());
		filterPurposeRegexCheckbox.setSelected(categoryRule.isFilterPurposeIsRegex());
		updatedAtText.setText(DateFormatUtils.formatLong(categoryRule.getUpdatedAt()));
	}

	public void updatePanelFieldValues(BankAccount bankAccount) {
		selectedAccount = bankAccount;
		refreshCategoryChoices();
		refreshAccountScopeDisplay();
	}

	private BigDecimal parseAmount(String value) {
		String normalized = TextValues.trimToNull(value);
		if (normalized == null) {
			return null;
		}
		String compact = normalized.replace(" ", "");
		if (compact.contains(",")) {
			return new BigDecimal(compact.replace(".", "").replace(',', '.'));
		}
		if (GERMAN_GROUPED_AMOUNT.matcher(compact).matches()) {
			return new BigDecimal(compact.replace(".", ""));
		}
		return new BigDecimal(compact);
	}

	private BigDecimal parseAmountOrWarn(String value) {
		try {
			return parseAmount(value);
		} catch (NumberFormatException ex) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING"));
			return null;
		}
	}

	private void configureCategoryCombo() {
		categoryCombo.setCellFactory(listView -> createCategoryCell());
		categoryCombo.setButtonCell(createCategoryCell());
		categoryCombo.setConverter(new StringConverter<>() {
			@Override
			public String toString(Category category) {
				return category != null ? category.getFullName() : "";
			}

			@Override
			public Category fromString(String string) {
				return null;
			}
		});
	}

	private ListCell<Category> createCategoryCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(Category category, boolean empty) {
				super.updateItem(category, empty);
				setText(empty || category == null ? null : category.getFullName());
			}
		};
	}

	private void configureAccountScopeCombo() {
		accountScopeCombo.setItems(FXCollections.observableArrayList(AccountRuleScope.values()));
		accountScopeCombo.setCellFactory(listView -> createAccountScopeCell());
		accountScopeCombo.setButtonCell(createAccountScopeCell());
	}

	private ListCell<AccountRuleScope> createAccountScopeCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(AccountRuleScope scope, boolean empty) {
				super.updateItem(scope, empty);
				setText(empty || scope == null ? null : getAccountScopeText(scope));
			}
		};
	}

	private String getAccountScopeText(AccountRuleScope scope) {
		return switch (scope) {
		case CURRENT_ACCOUNT -> selectedAccount != null && selectedAccount.getAccountName() != null
				? selectedAccount.getAccountName()
				: getText("UI_CATEGORY_RULE_SCOPE_CURRENT_ACCOUNT");
		case SELECTED_ACCOUNTS -> getText("UI_CATEGORY_RULE_SCOPE_SELECTED_ACCOUNTS");
		case ALL_ACCOUNTS -> getText("UI_CATEGORY_RULE_SCOPE_ALL_ACCOUNTS");
		};
	}

	private void refreshAccountScopeDisplay() {
		AccountRuleScope selectedScope = accountScopeCombo.getValue();
		accountScopeCombo.setButtonCell(createAccountScopeCell());
		accountScopeCombo.setValue(selectedScope);
	}

	private void configureAmountFields() {
		configureAmountField(filterAmountFrom);
		configureAmountField(filterAmountTo);
	}

	private void configureRuleNameField() {
		ruleName.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().length() <= MAX_RULE_NAME_LENGTH ? change : null));
	}

	private void configureFormGrid() {
		ColumnConstraints firstColumn = new ColumnConstraints();
		firstColumn.setMinWidth(FIELD_WIDTH);
		firstColumn.setPrefWidth(FIELD_WIDTH);
		firstColumn.setHgrow(Priority.NEVER);

		ColumnConstraints growingColumn = new ColumnConstraints();
		growingColumn.setMinWidth(0);
		growingColumn.setHgrow(Priority.ALWAYS);

		ColumnConstraints trailingColumn = new ColumnConstraints();
		trailingColumn.setMinWidth(RIGHT_COLUMN_WIDTH);
		trailingColumn.setPrefWidth(RIGHT_COLUMN_WIDTH);
		trailingColumn.setMaxWidth(RIGHT_COLUMN_WIDTH);
		trailingColumn.setHgrow(Priority.NEVER);

		formGrid.getColumnConstraints().setAll(firstColumn, growingColumn, trailingColumn);
		formGrid.setMinWidth(0);
		formGrid.setMaxWidth(Double.MAX_VALUE);
	}

	private void addFullWidthRow(Node node, int row, int rowSpan) {
		if (node instanceof Region region) {
			region.setMinWidth(0);
			region.setMaxWidth(Double.MAX_VALUE);
		}
		formGrid.add(node, 0, row, 3, rowSpan);
		GridPane.setHgrow(node, Priority.ALWAYS);
		GridPane.setFillWidth(node, true);
	}

	private void addExpandingFieldAbove(String key, Node field, int col, int rowGroup, int colspan) {
		VBox fieldBox = createFieldBox(getText(key), field);
		fieldBox.setMinWidth(0);
		fieldBox.setMaxWidth(Double.MAX_VALUE);
		formGrid.add(fieldBox, col, rowGroup * 2, colspan, 2);
		GridPane.setHgrow(fieldBox, Priority.ALWAYS);
		GridPane.setFillWidth(fieldBox, true);
	}

	private void addRightAlignedNode(Node node, int col, int rowGroup) {
		formGrid.add(node, col, rowGroup * 2, 1, 2);
		GridPane.setHalignment(node, HPos.RIGHT);
		GridPane.setFillWidth(node, false);
	}

	private HBox createFilterGroupsPane() {
		TitledPane dateAmountPane = createDateAmountFilterPane();
		TitledPane recipientPane = createRecipientFilterPane();

		HBox filterGroups = new HBox(GROUP_GAP, dateAmountPane, recipientPane);
		filterGroups.setFillHeight(true);
		filterGroups.setMinWidth(0);
		dateAmountPane.setMinWidth(0);
		recipientPane.setMinWidth(0);
		HBox.setHgrow(recipientPane, Priority.ALWAYS);
		return filterGroups;
	}

	private TitledPane createDateAmountFilterPane() {
		GridPane dateAmountGrid = FormGridHelper.createDefaultGrid();
		dateAmountGrid.setPadding(new Insets(6));
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_DATE_FROM"), filterDateFrom, 0, 0);
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_DATE_TO"), filterDateTo, 1, 0);
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_AMOUNT_FROM"), filterAmountFrom, 0, 1);
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_AMOUNT_TO"), filterAmountTo, 1, 1);

		return createFormGroupPane(getText("UI_PANEL_DATE_AMOUNT"), dateAmountGrid);
	}

	private TitledPane createRecipientFilterPane() {
		GridPane recipientGrid = FormGridHelper.createDefaultGrid();
		recipientGrid.setPadding(new Insets(6));
		FormGridHelper.addFieldAbove(recipientGrid, getText("UI_LABEL_NAME"), filterRecipientName, 0, 0, 2);
		FormGridHelper.addFieldAbove(recipientGrid, getText("UI_LABEL_REGEX"), filterRecipientRegexCheckbox, 2, 0);
		FormGridHelper.addFieldAbove(recipientGrid, getText("UI_LABEL_IBAN"), filterRecipientIban, 0, 1);
		FormGridHelper.addFieldAbove(recipientGrid, getText("UI_LABEL_ACCOUNT_NUMBER_SHORT"), filterRecipientAccountNumber, 1, 1, 2);

		return createFormGroupPane(getText("UI_PANEL_RECIPIENT_SENDER"), recipientGrid);
	}

	private HBox createPurposeOptionsPane() {
		VBox regexBox = createRightAlignedFieldBox(getText("UI_LABEL_REGEX"), filterPurposeRegexCheckbox, PURPOSE_REGEX_FIELD_WIDTH);
		VBox joinTypeBox = createRightAlignedFieldBox(getText("UI_LABEL_JOIN_TYPE"), joinTypeCombo, CONDITION_FIELD_WIDTH);

		HBox purposeOptions = new HBox(GROUP_GAP, regexBox, joinTypeBox);
		purposeOptions.setAlignment(Pos.TOP_RIGHT);
		purposeOptions.setMinWidth(Region.USE_PREF_SIZE);
		purposeOptions.setPrefWidth(PURPOSE_REGEX_FIELD_WIDTH + GROUP_GAP + CONDITION_FIELD_WIDTH);
		purposeOptions.setMaxWidth(Region.USE_PREF_SIZE);
		return purposeOptions;
	}

	private VBox createFieldBox(String labelText, Node field) {
		Label label = new Label(labelText == null ? "" : labelText);
		label.getStyleClass().add("gbanking-form-label");

		VBox box = new VBox(2);
		box.getStyleClass().add("gbanking-form-field-box");
		box.getChildren().addAll(label, field);
		box.setMaxWidth(Double.MAX_VALUE);

		if (field instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
		}
		if (field instanceof Control control) {
			VBox.setVgrow(control, Priority.NEVER);
		}

		return box;
	}

	private VBox createRightAlignedFieldBox(String labelText, Node field, double width) {
		VBox box = createFieldBox(labelText, field);
		box.setAlignment(Pos.TOP_RIGHT);
		box.setMinWidth(width);
		box.setPrefWidth(width);
		box.setMaxWidth(width);
		return box;
	}

	private TitledPane createFormGroupPane(String title, GridPane content) {
		TitledPane groupPane = new TitledPane(title, content);
		groupPane.setCollapsible(false);
		groupPane.setMaxWidth(Double.MAX_VALUE);
		groupPane.setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 1;");
		return groupPane;
	}

	private void configureDatePickers() {
		configureDatePicker(filterDateFrom);
		configureDatePicker(filterDateTo);
	}

	private void configureDatePicker(DatePicker datePicker) {
		datePicker.setConverter(new StringConverter<>() {

			@Override
			public String toString(LocalDate date) {
				return DateFormatUtils.formatLong(date);
			}

			@Override
			public LocalDate fromString(String text) {
				String trimmedText = TextValues.trimToNull(text);
				if (trimmedText == null) {
					return null;
				}
				try {
					return LocalDate.parse(trimmedText, GERMAN_DATE_FORMAT);
				} catch (DateTimeParseException ex) {
					return datePicker.getValue();
				}
			}
		});
	}

	private void configureAmountField(TextField field) {
		field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
			if (Boolean.FALSE.equals(isFocused)) {
				formatAmountField(field);
			}
		});
	}

	private void applyFieldWidths() {
		setFlexibleWidth(ruleName, WIDE_FIELD_WIDTH);
		setWidth(accountScopeCombo, FIELD_WIDTH);
		setWidth(categoryCombo, WIDE_FIELD_WIDTH);
		setWidth(joinTypeCombo, CONDITION_FIELD_WIDTH);
		setWidth(filterDateFrom, FIELD_WIDTH);
		setWidth(filterDateTo, FIELD_WIDTH);
		setWidth(filterAmountFrom, FIELD_WIDTH);
		setWidth(filterAmountTo, FIELD_WIDTH);
		setWidth(filterRecipientName, WIDE_FIELD_WIDTH);
		setWidth(filterRecipientIban, FIELD_WIDTH);
		setWidth(filterRecipientAccountNumber, WIDE_FIELD_WIDTH);
		setFlexibleWidth(filterPurpose, PURPOSE_FIELD_WIDTH);
		setWidth(updatedAtText, UPDATED_AT_FIELD_WIDTH);
	}

	private void setWidth(javafx.scene.control.Control control, double width) {
		control.setMinWidth(width);
		control.setPrefWidth(width);
	}

	private void setFlexibleWidth(javafx.scene.control.Control control, double prefWidth) {
		control.setMinWidth(0);
		control.setPrefWidth(prefWidth);
		control.setMaxWidth(Double.MAX_VALUE);
	}

	private AccountRuleScope resolveAccountRuleScope(CategoryRule categoryRule) {
		List<BankAccount> accounts = categoryRule.getBankAccountList();
		if (accounts == null || accounts.isEmpty()) {
			return AccountRuleScope.ALL_ACCOUNTS;
		}
		if (accounts.size() == 1) {
			selectedAccount = accounts.get(0);
			return AccountRuleScope.CURRENT_ACCOUNT;
		}
		return AccountRuleScope.SELECTED_ACCOUNTS;
	}

	private List<BankAccount> resolveRuleAccounts() {
		AccountRuleScope scope = accountScopeCombo.getValue() != null ? accountScopeCombo.getValue() : AccountRuleScope.CURRENT_ACCOUNT;
		return switch (scope) {
		case CURRENT_ACCOUNT -> {
			if (selectedAccount == null) {
				DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_NO_SELECTION"));
				yield null;
			}
			yield List.of(selectedAccount);
		}
		case SELECTED_ACCOUNTS -> resolveSelectedAccounts();
		case ALL_ACCOUNTS -> List.of();
		};
	}

	private List<BankAccount> resolveSelectedAccounts() {
		List<BankAccount> checkedAccounts = parentPanel.getAccountListPanel() != null
				? parentPanel.getAccountListPanel().getModelAccount().getCheckedAccounts()
				: List.of();
		if (!checkedAccounts.isEmpty()) {
			return checkedAccounts;
		}
		if (selectedCategoryRule != null && !selectedRuleAccounts.isEmpty()) {
			return selectedRuleAccounts;
		}
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_CATEGORY_RULE_NO_SELECTED_ACCOUNTS"));
		return Collections.emptyList();
	}

	private void updateCheckedAccountsForRule() {
		if (parentPanel.getAccountListPanel() == null) {
			return;
		}
		parentPanel.getAccountListPanel().setCheckedAccounts(selectedRuleAccounts, accountScopeCombo.getValue() == AccountRuleScope.ALL_ACCOUNTS);
	}

	private void formatAmountField(TextField field) {
		try {
			BigDecimal amount = parseAmount(field.getText());
			field.setText(formatAmount(amount));
		} catch (NumberFormatException ex) {
			// Keep the user's input visible; save validation will handle invalid values.
		}
	}

	private String formatAmount(BigDecimal amount) {
		return amount != null ? amountFormat.format(amount) : "";
	}

	private void refreshCategoryChoices() {
		categoryCombo.setItems(FXCollections.observableArrayList(dbController.getAll(Category.class)));
	}

	private LocalDate toCalendar(LocalDate value) {
		return value;
	}

	private LocalDate toLocalDate(LocalDate value) {
		return value;
	}
}
