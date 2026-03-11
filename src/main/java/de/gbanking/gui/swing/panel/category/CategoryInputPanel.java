package de.gbanking.gui.swing.panel.category;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.overview.RecipientOverviewPanel;

public class CategoryInputPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8310067884682054946L;

	private JPanel parentPanel;
	
	protected GridBagConstraints gbc;
	
	private DefaultComboBoxModel<Category> categoryItemList;

	protected JTextField categoryName = new JTextField();
	protected JComboBox<Category> subCategoryName = new JComboBox<Category>();
	protected JTextField filterDateFrom = new JTextField();
	protected JTextField filterDateTo = new JTextField();
	protected JTextField filterAmountFrom = new JTextField();
	protected JTextField filterAmountTo = new JTextField();
	protected JTextField filterRecipient = new JTextField();
	protected JTextField filterPurpose = new JTextField();
	protected JTextField updatedAtText = new JTextField();
	
	protected JCheckBox filterRecipientRegexCheckbox = new JCheckBox();
	protected JCheckBox filterPurposeRegexCheckbox = new JCheckBox();

	protected JButton buttonSubmit = new JButton("");

	private Category selectedCategory;
	

	public CategoryInputPanel(JPanel parent) {
		this.parentPanel = parent;
		createCategoryInputPanel();
	}

	private void createCategoryInputPanel() {

		setLayout(new GridBagLayout());
		gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		
		setCategoryItems();

		// row col
		addLabelAndFieldAbove("Kategorie Name", categoryName, gbc, 0, 0, 1, 1);
		addLabelAndFieldAbove("Unterkategorie", subCategoryName, gbc, 0, 1, 1, 1);
		addLabelAndFieldAbove("Datum", filterDateFrom, gbc, 1, 0, 1, 1);
		addLabelAndFieldAbove("", filterDateTo, gbc, 1, 1, 1, 1);
		addLabelAndFieldAbove("Betrag", filterAmountFrom, gbc, 2, 0, 2, 1);
		addLabelAndFieldAbove("", filterAmountTo, gbc, 2, 1, 1, 1);
		addLabelAndFieldAbove("regulärer Ausdruck?", filterRecipientRegexCheckbox, gbc, 2, 2, 1, 1);
		addLabelAndFieldAbove("Empfänger", filterRecipient, gbc, 3, 1, 1, 1);
		addLabelAndFieldAbove("", filterPurposeRegexCheckbox, gbc, 3, 2, 1, 1);
		addLabelAndFieldAbove("Verwendungszweck", filterPurpose, gbc, 4, 1, 1, 1);

		JButton buttonNew = new JButton("Neu...");
		buttonNew.addActionListener(e -> resetTextFields());

		buttonSubmit.addActionListener(e -> saveCategory());
		
		JButton buttonDelete = new JButton("Löschen");
		buttonDelete.addActionListener(e -> deleteCategory());

		JButton buttonCancel = new JButton("Abbrechen");
		buttonCancel.addActionListener(e -> resetTextFields());
		
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		buttonsPanel.add(buttonNew);		
		buttonsPanel.add(buttonSubmit);
		buttonsPanel.add(buttonDelete);
		buttonsPanel.add(buttonCancel);
		
		gbc.gridx = 0;
		gbc.gridwidth = 3;
		gbc.gridy = 14;
		add(buttonsPanel, gbc);
	}
	
	private void saveCategory() {

		if (categoryName.getText().isEmpty() && 
				(filterDateFrom.getText().isEmpty()
				|| filterDateFrom.getText().isEmpty()
				|| filterAmountFrom.getText().isEmpty()
				|| filterAmountTo.getText().isEmpty()
				|| filterRecipient.getText().isEmpty()
				|| filterPurpose.getText().isEmpty())
				) {
			JOptionPane.showMessageDialog(parentPanel, getText("ALERT_CATEGORY_REQUIRED_FIELD_MISSING"));
		} else {
			Category category = new Category(categoryName.getText());
			category.setSource(Source.MANUELL);

			bean.saveCategoryToDB(category);

			((RecipientOverviewPanel) parentPanel).getRecipientListPanel().revalidate();
			((RecipientOverviewPanel) parentPanel).getRecipientListPanel().repaint();
		}
	}

	private void deleteCategory() {
		bean.deleteCategoryFromDB(selectedCategory);
	}

	private void resetTextFields() {
		categoryName.setText(null);
		subCategoryName.setSelectedItem(null);
		filterDateFrom.setText(null);
		filterAmountFrom.setText(null);
		filterAmountTo.setText(null);
		filterRecipient.setText(null);
		filterPurpose.setText(null);
		updatedAtText.setText(null);
		
		//enableInputFields(true);
	}
	
	private void setCategoryItems() {
		if (categoryItemList == null) {
			categoryItemList = new DefaultComboBoxModel<>();
		}
		for (Category category : dbController.getAll(Category.class)) {
			categoryItemList.addElement(category);
		}
	}
	
	void updatePanelFieldValues(Category selectedCategory) {
		// =  selectedCategory;
		categoryName.setText(selectedCategory.getName().toString());
		//tfPurpose.setText(selectedCategory.getPurpose());
	}
	
}
