package de.zft2.gbanking.gui.panel.category;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.CategoryRule.JoinType;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.AbstractTitledFormPanel;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormControlUtils;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.util.DecimalText;
import de.zft2.gbanking.util.Ranges;
import de.zft2.gbanking.util.TextValues;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class CategoryRuleInputPanel extends AbstractTitledFormPanel {

	private static final Logger log = LogManager.getLogger(CategoryRuleInputPanel.class);
	private static final double FIELD_WIDTH = 180.0;
	private static final double WIDE_FIELD_WIDTH = 380.0;
	private static final double PURPOSE_REGEX_FIELD_WIDTH = 110.0;
	private static final double CONDITION_FIELD_WIDTH = 240.0;
	private static final double PURPOSE_FIELD_WIDTH = 460.0;
	private static final double UPDATED_AT_FIELD_WIDTH = 125.0;
	private static final double RECIPIENT_FILTER_PANEL_WIDTH = 580.0;
	private static final double GROUP_GAP = 14.0;
	private static final int MAX_RULE_NAME_LENGTH = 40;

	private final CategoryOverviewPanel parentPanel;

	private final TextField ruleName = new TextField();
	private final ComboBox<AccountRuleScope> accountScopeCombo = new ComboBox<>();
	private final ComboBox<Category> categoryCombo = new ComboBox<>();
	private final ComboBox<JoinType> joinTypeCombo = new ComboBox<>();
	private final ComboBox<JoinType> joinTypePurposeCombo = new ComboBox<>();
	private final DatePicker filterDateFrom = new DatePicker();
	private final DatePicker filterDateTo = new DatePicker();
	private final TextField filterAmountFrom = new TextField();
	private final TextField filterAmountTo = new TextField();
	private final TextField filterRecipientName = new TextField();
	private final TextField filterRecipientIban = new TextField();
	private final TextField filterRecipientAccountNumber = new TextField();
	private final VBox purposeRows = new VBox(6);
	private final CheckBox filterRecipientRegexCheckbox = new CheckBox();
	private final CheckBox filterPurposeRegexCheckbox = new CheckBox();
	private final CheckBox overwriteExistingCategoriesCheckbox = new CheckBox();
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
		joinTypePurposeCombo.setItems(FXCollections.observableArrayList(JoinType.values()));
		joinTypePurposeCombo.setValue(JoinType.OR);
		accountScopeCombo.setValue(AccountRuleScope.CURRENT_ACCOUNT);
		overwriteExistingCategoriesCheckbox.setText(getText("UI_CATEGORY_RULE_OVERWRITE_EXISTING"));
		overwriteExistingCategoriesCheckbox.setTooltip(new Tooltip(getText("UI_TOOLTIP_CATEGORY_RULE_OVERWRITE_EXISTING")));
		overwriteExistingCategoriesCheckbox.setMinWidth(Region.USE_PREF_SIZE);
		overwriteExistingCategoriesCheckbox.setSelected(true);
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

		addFullWidthRow(createRuleHeaderPane(), 0, 2);
		addFieldAbove("UI_LABEL_ACCOUNT", accountScopeCombo, 0, 1);
		addFieldAbove("UI_LABEL_CATEGORY", categoryCombo, 1, 1, 2);
		addFullWidthRow(createFilterGroupsPane(), 4, 1);
		addFullWidthRow(createPurposeFilterPane(), 6, 1);

		addContentNode(FormStyleUtils.createButtonBar(buttonNew, buttonSubmit, buttonDelete, buttonCancel));
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
		if (Ranges.isReversed(filterDateFrom.getValue(), filterDateTo.getValue()) || Ranges.isReversed(amountFrom, amountTo)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_CATEGORY_RULE_INVALID_RANGE"));
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
		categoryRule.setJoinTypePurpose(joinTypePurposeCombo.getValue());
		categoryRule.setFilterDateFrom(toCalendar(filterDateFrom.getValue()));
		categoryRule.setFilterDateTo(toCalendar(filterDateTo.getValue()));
		categoryRule.setFilterAmountFrom(amountFrom);
		categoryRule.setFilterAmountTo(amountTo);
		categoryRule.setFilterRecipientName(TextValues.trimToNull(filterRecipientName.getText()));
		categoryRule.setFilterRecipientIban(TextValues.trimToNull(filterRecipientIban.getText()));
		categoryRule.setFilterRecipientAccountNumber(TextValues.trimToNull(filterRecipientAccountNumber.getText()));
		categoryRule.setFilterPurposes(purposeRows.getChildren().stream()
				.map(row -> ((TextField) ((HBox) row).getChildren().get(0)).getText()).toList());
		categoryRule.setFilterRecipientIsRegex(filterRecipientRegexCheckbox.isSelected());
		categoryRule.setFilterPurposeIsRegex(filterPurposeRegexCheckbox.isSelected());
		categoryRule.setOverwriteExistingCategories(overwriteExistingCategoriesCheckbox.isSelected());
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
		joinTypePurposeCombo.setValue(JoinType.OR);
		filterDateFrom.setValue(null);
		filterDateTo.setValue(null);
		filterAmountFrom.clear();
		filterAmountTo.clear();
		filterRecipientName.clear();
		filterRecipientIban.clear();
		filterRecipientAccountNumber.clear();
		setPurposeRows(List.of());
		filterRecipientRegexCheckbox.setSelected(false);
		filterPurposeRegexCheckbox.setSelected(false);
		overwriteExistingCategoriesCheckbox.setSelected(true);
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
		joinTypePurposeCombo.setValue(categoryRule.getJoinTypePurpose());
		filterDateFrom.setValue(toLocalDate(categoryRule.getFilterDateFrom()));
		filterDateTo.setValue(toLocalDate(categoryRule.getFilterDateTo()));
		filterAmountFrom.setText(formatAmount(categoryRule.getFilterAmountFrom()));
		filterAmountTo.setText(formatAmount(categoryRule.getFilterAmountTo()));
		filterRecipientName.setText(categoryRule.getFilterRecipientName() != null ? categoryRule.getFilterRecipientName() : "");
		filterRecipientIban.setText(categoryRule.getFilterRecipientIban() != null ? categoryRule.getFilterRecipientIban() : "");
		filterRecipientAccountNumber.setText(
				categoryRule.getFilterRecipientAccountNumber() != null ? categoryRule.getFilterRecipientAccountNumber() : "");
		setPurposeRows(categoryRule.getFilterPurposes());
		filterRecipientRegexCheckbox.setSelected(categoryRule.isFilterRecipientIsRegex());
		filterPurposeRegexCheckbox.setSelected(categoryRule.isFilterPurposeIsRegex());
		overwriteExistingCategoriesCheckbox.setSelected(categoryRule.isOverwriteExistingCategories());
		updatedAtText.setText(DateFormatUtils.formatLong(categoryRule.getUpdatedAt()));
	}

	public void updatePanelFieldValues(BankAccount bankAccount) {
		selectedAccount = bankAccount;
		refreshCategoryChoices();
		refreshAccountScopeDisplay();
	}

	public void prefillFromBooking(BankAccount account, Booking booking) {
		if (account == null || booking == null) {
			return;
		}
		selectedAccount = account;
		resetTextFields();
		updatePanelFieldValues(createRuleTemplate(account, booking));
		selectedCategoryRule = null;
		updatedAtText.clear();
	}

	static CategoryRule createRuleTemplate(BankAccount account, Booking booking) {
		CategoryRule template = new CategoryRule();
		template.setBankAccountList(List.of(account));
		template.setCategory(booking.getCategory());
		template.setJoinType(JoinType.AND);
		template.setFilterDateFrom(booking.getDateBooking());
		template.setFilterDateTo(booking.getDateBooking());
		template.setFilterAmountFrom(booking.getAmount());
		template.setFilterAmountTo(booking.getAmount());
		if (TextValues.hasText(booking.getPurpose())) {
			template.setFilterPurposes(List.of(booking.getPurpose()));
		}

		Recipient recipient = booking.getRecipient();
		if (recipient != null) {
			template.setFilterRecipientName(TextValues.trimToNull(recipient.getName()));
			template.setFilterRecipientIban(TextValues.trimToNull(recipient.getIban()));
			template.setFilterRecipientAccountNumber(TextValues.trimToNull(recipient.getAccountNumber()));
		}
		return template;
	}

	private BigDecimal parseAmountOrWarn(String value) {
		try {
			return DecimalText.parseGermanAmount(value);
		} catch (NumberFormatException ex) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING"));
			return null;
		}
	}

	private void configureCategoryCombo() {
		categoryCombo.setConverter(FormControlUtils.displayConverter(category -> category.getFullName()));
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
		trailingColumn.setMinWidth(UPDATED_AT_FIELD_WIDTH);
		trailingColumn.setPrefWidth(UPDATED_AT_FIELD_WIDTH);
		trailingColumn.setMaxWidth(UPDATED_AT_FIELD_WIDTH);
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

	private HBox createRuleHeaderPane() {
		VBox nameBox = createFieldBox(getText("UI_LABEL_RULE_NAME"), ruleName);
		nameBox.setMinWidth(0);
		HBox.setHgrow(nameBox, Priority.ALWAYS);
		HBox header = new HBox(GROUP_GAP, nameBox, overwriteExistingCategoriesCheckbox,
				createRightAlignedFieldBox(getText("UI_LABEL_UPDATED_AT"), updatedAtText, UPDATED_AT_FIELD_WIDTH));
		header.setAlignment(Pos.BOTTOM_LEFT);
		return header;
	}

	private HBox createFilterGroupsPane() {
		HBox filterGroups = new HBox(GROUP_GAP, createDateAmountFilterPane(), createRecipientFilterPane(), createCombinationsPane());
		filterGroups.setAlignment(Pos.TOP_LEFT);
		filterGroups.setFillHeight(true);
		filterGroups.setMinWidth(0);
		filterGroups.getChildren().forEach(group -> HBox.setHgrow(group, Priority.ALWAYS));
		return filterGroups;
	}

	private TitledPane createDateAmountFilterPane() {
		GridPane dateAmountGrid = FormGridHelper.createDefaultGrid();
		dateAmountGrid.setPadding(new Insets(6));
		FormGridHelper.setEqualGrowColumns(dateAmountGrid, 2);
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_DATE_FROM"), filterDateFrom, 0, 0);
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_DATE_TO"), filterDateTo, 1, 0);
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_AMOUNT_FROM"), filterAmountFrom, 0, 1);
		FormGridHelper.addFieldAbove(dateAmountGrid, getText("UI_LABEL_AMOUNT_TO"), filterAmountTo, 1, 1);

		return createFormGroupPane(getText("UI_PANEL_DATE_AMOUNT"), dateAmountGrid);
	}

	private TitledPane createRecipientFilterPane() {
		GridPane recipientGrid = FormGridHelper.createDefaultGrid();
		recipientGrid.setPadding(new Insets(6));
		FormGridHelper.setEqualGrowColumns(recipientGrid, 2);
		VBox nameBox = createFieldBox(getText("UI_LABEL_NAME"), filterRecipientName);
		nameBox.setMinWidth(0);
		HBox.setHgrow(nameBox, Priority.ALWAYS);
		VBox regexBox = createFieldBox(getText("UI_LABEL_REGEX"), filterRecipientRegexCheckbox);
		regexBox.setMinWidth(Region.USE_PREF_SIZE);
		recipientGrid.add(new HBox(GROUP_GAP, nameBox, regexBox), 0, 0, 2, 2);
		FormGridHelper.addFieldAbove(recipientGrid, getText("UI_LABEL_IBAN"), filterRecipientIban, 0, 1);
		FormGridHelper.addFieldAbove(recipientGrid, getText("UI_LABEL_ACCOUNT_NUMBER_SHORT"), filterRecipientAccountNumber, 1, 1);

		TitledPane recipientPane = createFormGroupPane(getText("UI_PANEL_RECIPIENT_SENDER"), recipientGrid);
		recipientPane.setPrefWidth(RECIPIENT_FILTER_PANEL_WIDTH);
		return recipientPane;
	}

	private TitledPane createCombinationsPane() {
		GridPane optionsGrid = FormGridHelper.createDefaultGrid();
		optionsGrid.setPadding(new Insets(6));
		FormGridHelper.setEqualGrowColumns(optionsGrid, 1);
		FormGridHelper.addFieldAbove(optionsGrid, getText("UI_LABEL_JOIN_TYPE"), joinTypeCombo, 0, 0);
		FormGridHelper.addFieldAbove(optionsGrid, getText("UI_LABEL_JOIN_TYPE_PURPOSE"), joinTypePurposeCombo, 0, 1);
		return createFormGroupPane(getText("UI_PANEL_CATEGORY_RULE_COMBINATIONS"), optionsGrid);
	}

	private TitledPane createPurposeFilterPane() {
		GridPane purposeGrid = FormGridHelper.createDefaultGrid();
		purposeGrid.setPadding(new Insets(6));
		setPurposeRows(List.of());
		purposeGrid.add(purposeRows, 0, 0);
		GridPane.setHgrow(purposeRows, Priority.ALWAYS);
		filterPurposeRegexCheckbox.setText(getText("UI_LABEL_REGEX"));
		filterPurposeRegexCheckbox.setWrapText(true);
		setWidth(filterPurposeRegexCheckbox, PURPOSE_REGEX_FIELD_WIDTH);
		purposeGrid.add(filterPurposeRegexCheckbox, 1, 0);
		GridPane.setValignment(filterPurposeRegexCheckbox, VPos.TOP);
		return createFormGroupPane(getText("UI_PANEL_PURPOSE"), purposeGrid);
	}

	private void setPurposeRows(List<String> purposes) {
		purposeRows.getChildren().clear();
		if (purposes.isEmpty()) {
			addPurposeRow("");
		} else {
			purposes.forEach(purpose -> addPurposeRow(purpose));
		}
	}

	private TextField addPurposeRow(String purpose) {
		TextField field = new TextField(purpose);
		setFlexibleWidth(field, PURPOSE_FIELD_WIDTH);
		HBox.setHgrow(field, Priority.ALWAYS);
		Button add = createPurposeRowButton("+", "UI_TOOLTIP_CATEGORY_RULE_ADD_PURPOSE");
		Button remove = createPurposeRowButton("-", "UI_TOOLTIP_CATEGORY_RULE_REMOVE_PURPOSE");
		HBox row = new HBox(6, field, add, remove);
		row.setAlignment(Pos.CENTER_LEFT);
		add.setOnAction(event -> addPurposeRow("").requestFocus());
		remove.setOnAction(event -> {
			if (field.getLength() == 0 || DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.WARNING,
					getText("UI_PANEL_PURPOSE"), null, getText("ALERT_CATEGORY_RULE_PURPOSE_DELETE"), ButtonType.OK, ButtonType.CANCEL)) {
				purposeRows.getChildren().remove(row);
			}
		});
		remove.disableProperty().bind(Bindings.size(purposeRows.getChildren()).lessThanOrEqualTo(1));
		purposeRows.getChildren().add(row);
		return field;
	}

	private Button createPurposeRowButton(String text, String tooltipKey) {
		Button button = new Button(text);
		button.getStyleClass().add("gbanking-form-button");
		button.setMinWidth(28);
		button.setPrefWidth(28);
		button.setMaxWidth(28);
		button.setTooltip(new Tooltip(getText(tooltipKey)));
		return button;
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
		groupPane.setMinWidth(0);
		groupPane.setMaxWidth(Double.MAX_VALUE);
		groupPane.setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 1;");
		return groupPane;
	}

	private void configureDatePickers() {
		FormControlUtils.applyLongDateConverter(filterDateFrom);
		FormControlUtils.applyLongDateConverter(filterDateTo);
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
		setWidth(joinTypePurposeCombo, CONDITION_FIELD_WIDTH);
		setWidth(filterDateFrom, FIELD_WIDTH);
		setWidth(filterDateTo, FIELD_WIDTH);
		setWidth(filterAmountFrom, FIELD_WIDTH);
		setWidth(filterAmountTo, FIELD_WIDTH);
		setFlexibleWidth(filterRecipientName, WIDE_FIELD_WIDTH);
		setWidth(filterRecipientIban, WIDE_FIELD_WIDTH);
		setWidth(filterRecipientAccountNumber, FIELD_WIDTH);
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
			BigDecimal amount = DecimalText.parseGermanAmount(field.getText());
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
