package de.gbanking.gui.fx.panel.category;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.fx.panel.AbstractTitledFormPanel;
import de.gbanking.gui.fx.panel.overview.CategoryOverviewPanel;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class CategoryInputPanel extends AbstractTitledFormPanel {

	private static final Logger log = LogManager.getLogger(CategoryInputPanel.class);

	private final CategoryOverviewPanel parentPanel;

	private final ComboBox<Category> subCategoryName = new ComboBox<>();
	private final TextField categoryName = new TextField();
	private final TextField filterDateFrom = new TextField();
	private final TextField filterDateTo = new TextField();
	private final TextField filterAmountFrom = new TextField();
	private final TextField filterAmountTo = new TextField();
	private final TextField filterRecipient = new TextField();
	private final TextField filterPurpose = new TextField();
	private final TextField updatedAtText = new TextField();
	private final CheckBox filterRecipientRegexCheckbox = new CheckBox();
	private final CheckBox filterPurposeRegexCheckbox = new CheckBox();
	private final Button buttonSubmit = new Button();

	private Category selectedCategory;

	public CategoryInputPanel(CategoryOverviewPanel parent) {
		super("UI_PANEL_CATEGORIES");
		this.parentPanel = parent;
		createCategoryInputPanel();
	}

	private void createCategoryInputPanel() {
		subCategoryName.setItems(FXCollections.observableArrayList(dbController.getAll(Category.class)));
		updatedAtText.setEditable(false);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonSubmit.setText(getText("UI_BUTTON_SAVE"));
		Button buttonDelete = new Button(getText("UI_BUTTON_DELETE"));
		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));

		buttonNew.setOnAction(e -> resetTextFields());
		buttonSubmit.setOnAction(e -> saveCategory());
		buttonDelete.setOnAction(e -> deleteCategory());
		buttonCancel.setOnAction(e -> resetTextFields());

		addFieldAbove("UI_LABEL_CATEGORY_NAME", categoryName, 0, 0);
		addFieldAbove("UI_LABEL_SUBCATEGORY", subCategoryName, 1, 0);
		addFieldAbove("UI_LABEL_DATE_FROM", filterDateFrom, 0, 1);
		addFieldAbove("UI_LABEL_DATE_TO", filterDateTo, 1, 1);
		addFieldAbove("UI_LABEL_AMOUNT_FROM", filterAmountFrom, 0, 2);
		addFieldAbove("UI_LABEL_AMOUNT_TO", filterAmountTo, 1, 2);
		addFieldAbove("UI_LABEL_REGEX", filterRecipientRegexCheckbox, 2, 2);
		addFieldAbove("UI_LABEL_RECIPIENT", filterRecipient, 1, 3);
		addFieldAbove("UI_LABEL_REGEX", filterPurposeRegexCheckbox, 2, 3);
		addFieldAbove("UI_LABEL_PURPOSE", filterPurpose, 1, 4);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 2, 4);

		HBox buttonBar = new HBox(10, buttonNew, buttonSubmit, buttonDelete, buttonCancel);
		addContentNode(buttonBar);
	}

	private void saveCategory() {
		if (categoryName.getText().isBlank() && (filterDateFrom.getText().isBlank() || filterDateTo.getText().isBlank() || filterAmountFrom.getText().isBlank()
				|| filterAmountTo.getText().isBlank() || filterRecipient.getText().isBlank() || filterPurpose.getText().isBlank())) {
			new Alert(Alert.AlertType.WARNING, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING")).showAndWait();
			return;
		}

		Category category = new Category(categoryName.getText());
		category.setSource(Source.MANUELL);
		bean.saveCategoryToDB(category);
		parentPanel.getCategoryListPanel().refresh();
	}

	private void deleteCategory() {
		if (selectedCategory != null) {
			bean.deleteCategoryFromDB(selectedCategory);
			parentPanel.getCategoryListPanel().refresh();
			resetTextFields();
		}
	}

	private void resetTextFields() {
		categoryName.clear();
		subCategoryName.setValue(null);
		filterDateFrom.clear();
		filterDateTo.clear();
		filterAmountFrom.clear();
		filterAmountTo.clear();
		filterRecipient.clear();
		filterPurpose.clear();
		updatedAtText.clear();
		filterRecipientRegexCheckbox.setSelected(false);
		filterPurposeRegexCheckbox.setSelected(false);
		selectedCategory = null;
	}

	void updatePanelFieldValues(Category selectedCategory) {
		log.log(Level.INFO, () -> getText("LOG_INFO_CATEGORY_SELECTED", selectedCategory.getId()));

		this.selectedCategory = selectedCategory;
		categoryName.setText(selectedCategory.getName());
		updatedAtText.setText(selectedCategory.getUpdatedAt() != null ? selectedCategory.getUpdatedAt().getTime().toString() : "");
	}

	public void updatePanelFieldValues(BankAccount selectedAccount) {
		// aktuell kein direktes Account-spezifisches Mapping im alten Swing-Code
	}
}