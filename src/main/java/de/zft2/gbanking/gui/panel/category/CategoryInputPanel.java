package de.zft2.gbanking.gui.panel.category;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.AbstractTitledFormPanel;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.service.GBankingBean.CategoryDeleteImpact;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

public class CategoryInputPanel extends AbstractTitledFormPanel {

	private static final Logger log = LogManager.getLogger(CategoryInputPanel.class);
	private static final Category NO_PARENT_CATEGORY = new Category("");

	private final CategoryOverviewPanel parentPanel;

	private final TextField categoryName = new TextField();
	private final ComboBox<Category> parentCategoryCombo = new ComboBox<>();
	private final ComboBox<Source> sourceCombo = new ComboBox<>();
	private final TextField updatedAtText = new TextField();
	private final Button buttonSubmit = new Button();

	private Category selectedCategory;

	public CategoryInputPanel(CategoryOverviewPanel parent) {
		super("UI_PANEL_CATEGORIES");
		this.parentPanel = parent;
		createCategoryInputPanel();
	}

	private void createCategoryInputPanel() {
		configureParentCategoryCombo();
		refreshCategoryChoices();
		sourceCombo.setItems(FXCollections.observableArrayList(Arrays.stream(Source.values()).filter(source -> !source.isNew()).toList()));
		sourceCombo.setValue(Source.MANUELL);
		sourceCombo.setDisable(true);
		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonSubmit.setText(getText("UI_BUTTON_SAVE"));
		Button buttonDelete = new Button(getText("UI_BUTTON_DELETE"));
		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));

		buttonNew.setOnAction(event -> resetTextFields());
		buttonSubmit.setOnAction(event -> saveCategory());
		buttonDelete.setOnAction(event -> deleteCategory());
		buttonCancel.setOnAction(event -> resetTextFields());
		KeyboardShortcutDispatcher.registerForm(this, buttonSubmit, buttonCancel);

		addFieldAbove("UI_LABEL_CATEGORY_NAME", categoryName, 0, 0);
		addFieldAbove("UI_LABEL_PARENT_CATEGORY", parentCategoryCombo, 1, 0);
		addFieldAbove("UI_LABEL_SOURCE", sourceCombo, 0, 1);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 1, 1);

		HBox buttonBar = new HBox(10, buttonNew, buttonSubmit, buttonDelete, buttonCancel);
		addContentNode(buttonBar);
	}

	private void saveCategory() {
		String trimmedCategoryName = categoryName.getText() != null ? categoryName.getText().trim() : "";
		if (trimmedCategoryName.isBlank()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING"));
			return;
		}

		Category category = selectedCategory != null ? selectedCategory : new Category(trimmedCategoryName, null);
		category.setName(trimmedCategoryName);
		category.setParentId(resolveParentIdForSave());
		bean.saveCategoryToDB(category);

		refreshCategoryViews();
		resetTextFields();
	}

	private void deleteCategory() {
		if (selectedCategory == null) {
			return;
		}

		CategoryDeleteImpact impact = bean.getCategoryDeleteImpact(selectedCategory);
		if (!confirmCategoryDeletion(impact)) {
			return;
		}

		if (!bean.deleteCategoryFromDB(selectedCategory)) {
			return;
		}
		refreshCategoryViews();
		resetTextFields();
	}

	private boolean confirmCategoryDeletion(CategoryDeleteImpact impact) {
		return DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.CONFIRMATION, getText("ALERT_CATEGORY_DELETE_TITLE"),
				getText("ALERT_CATEGORY_DELETE_HEADER"), getText("ALERT_CATEGORY_DELETE_TEXT", formatCategory(selectedCategory), impact.bookingCount(),
						impact.categoryRuleCount()), ButtonType.OK, ButtonType.CANCEL);
	}

	private void resetTextFields() {
		selectedCategory = null;
		categoryName.clear();
		parentCategoryCombo.setValue(NO_PARENT_CATEGORY);
		sourceCombo.setValue(Source.MANUELL);
		updatedAtText.clear();
	}

	void updatePanelFieldValues(Category category) {
		log.log(Level.INFO, () -> getText("LOG_INFO_CATEGORY_SELECTED", category.getId()));
		selectedCategory = category;
		categoryName.setText(category.getName());
		updatedAtText.setText(DateFormatUtils.formatLong(category.getUpdatedAt()));
		parentCategoryCombo.setValue(resolveParentCategory(category));
		sourceCombo.setValue(Source.MANUELL);
	}

	public void updatePanelFieldValues(BankAccount selectedAccount) {
		// categories are not account specific in the current data model
	}

	private void configureParentCategoryCombo() {
		parentCategoryCombo.setCellFactory(listView -> new CategoryListCell());
		parentCategoryCombo.setButtonCell(new CategoryListCell());
		parentCategoryCombo.setConverter(new StringConverter<>() {

			@Override
			public String toString(Category category) {
				return formatCategory(category);
			}

			@Override
			public Category fromString(String text) {
				if (text == null || parentCategoryCombo.getItems() == null) {
					return null;
				}
				return parentCategoryCombo.getItems().stream().filter(category -> formatCategory(category).equals(text)).findFirst().orElse(null);
			}
		});
	}

	private void refreshCategoryViews() {
		parentPanel.getCategoryListPanel().reload();
		parentPanel.getCategoryRuleListPanel().reload();
		parentPanel.getCategoryRuleInputPanel().updatePanelFieldValues((BankAccount) null);
		refreshCategoryChoices();
	}

	private void refreshCategoryChoices() {
		Category selectedParent = parentCategoryCombo.getValue();
		Integer selectedParentId = !isNoParentCategory(selectedParent) ? selectedParent.getId() : null;
		ArrayList<Category> categoryChoices = new ArrayList<>();
		categoryChoices.add(NO_PARENT_CATEGORY);
		categoryChoices.addAll(dbController.getAll(Category.class));
		parentCategoryCombo.setItems(FXCollections.observableArrayList(categoryChoices));
		parentCategoryCombo.setValue(selectedParentId != null ? findParentCategory(selectedParentId) : NO_PARENT_CATEGORY);
	}

	private Category resolveParentCategory(Category category) {
		if (category.getParentId() == null || parentCategoryCombo.getItems() == null) {
			return NO_PARENT_CATEGORY;
		}

		return findParentCategory(category.getParentId());
	}

	private Category findParentCategory(Integer parentId) {
		return parentCategoryCombo.getItems().stream().filter(parent -> parent.getId() == parentId).findFirst().orElse(NO_PARENT_CATEGORY);
	}

	private Integer resolveParentIdForSave() {
		Category parentCategory = parentCategoryCombo.getValue();
		return isNoParentCategory(parentCategory) ? null : parentCategory.getId();
	}

	private String formatCategory(Category category) {
		if (isNoParentCategory(category)) {
			return "";
		}

		String fullName = category.getFullName();
		if (fullName != null && !fullName.isBlank()) {
			return fullName;
		}
		return category.getName() != null ? category.getName() : "";
	}

	private boolean isNoParentCategory(Category category) {
		return category == null || category == NO_PARENT_CATEGORY;
	}

	private class CategoryListCell extends ListCell<Category> {

		@Override
		protected void updateItem(Category category, boolean empty) {
			super.updateItem(category, empty);
			setText(empty ? null : formatCategory(category));
		}
	}
}
