package de.zft2.gbanking.gui.panel.category;

import java.util.Arrays;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.panel.AbstractTitledFormPanel;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class CategoryInputPanel extends AbstractTitledFormPanel {

	private static final Logger log = LogManager.getLogger(CategoryInputPanel.class);

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
		refreshCategoryChoices();
		sourceCombo.setItems(FXCollections.observableArrayList(Arrays.stream(Source.values()).filter(source -> !source.isNew()).toList()));
		sourceCombo.setValue(Source.MANUELL);
		sourceCombo.setDisable(true);
		updatedAtText.setEditable(false);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonSubmit.setText(getText("UI_BUTTON_SAVE"));
		Button buttonDelete = new Button(getText("UI_BUTTON_DELETE"));
		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));

		buttonNew.setOnAction(event -> resetTextFields());
		buttonSubmit.setOnAction(event -> saveCategory());
		buttonDelete.setOnAction(event -> deleteCategory());
		buttonCancel.setOnAction(event -> resetTextFields());

		addFieldAbove("UI_LABEL_CATEGORY_NAME", categoryName, 0, 0);
		addFieldAbove("UI_LABEL_PARENT_CATEGORY", parentCategoryCombo, 1, 0);
		addFieldAbove("UI_LABEL_SOURCE", sourceCombo, 2, 0);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 0, 1);

		HBox buttonBar = new HBox(10, buttonNew, buttonSubmit, buttonDelete, buttonCancel);
		addContentNode(buttonBar);
	}

	private void saveCategory() {
		String trimmedCategoryName = categoryName.getText() != null ? categoryName.getText().trim() : "";
		if (trimmedCategoryName.isBlank()) {
			new Alert(Alert.AlertType.WARNING, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING")).showAndWait();
			return;
		}

		Category category = selectedCategory != null ? selectedCategory : new Category(trimmedCategoryName, null);
		category.setName(trimmedCategoryName);
		category.setParentId(parentCategoryCombo.getValue() != null ? parentCategoryCombo.getValue().getId() : null);
		bean.saveCategoryToDB(category);

		parentPanel.getCategoryListPanel().reload();
		parentPanel.getCategoryRuleInputPanel().updatePanelFieldValues((BankAccount) null);
		refreshCategoryChoices();
		resetTextFields();
	}

	private void deleteCategory() {
		if (selectedCategory == null) {
			return;
		}

		bean.deleteCategoryFromDB(selectedCategory);
		parentPanel.getCategoryListPanel().reload();
		refreshCategoryChoices();
		resetTextFields();
	}

	private void resetTextFields() {
		selectedCategory = null;
		categoryName.clear();
		parentCategoryCombo.setValue(null);
		sourceCombo.setValue(Source.MANUELL);
		updatedAtText.clear();
	}

	void updatePanelFieldValues(Category category) {
		log.log(Level.INFO, () -> getText("LOG_INFO_CATEGORY_SELECTED", category.getId()));
		selectedCategory = category;
		categoryName.setText(category.getName());
		updatedAtText.setText(category.getUpdatedAt() != null ? category.getUpdatedAt().toString() : "");
		parentCategoryCombo.setValue(resolveParentCategory(category));
		sourceCombo.setValue(Source.MANUELL);
	}

	public void updatePanelFieldValues(BankAccount selectedAccount) {
		// categories are not account specific in the current data model
	}

	private void refreshCategoryChoices() {
		parentCategoryCombo.setItems(FXCollections.observableArrayList(dbController.getAll(Category.class)));
	}

	private Category resolveParentCategory(Category category) {
		if (category.getParentId() == null || parentCategoryCombo.getItems() == null) {
			return null;
		}

		return parentCategoryCombo.getItems().stream().filter(parent -> parent.getId() == category.getParentId()).findFirst().orElse(null);
	}
}
