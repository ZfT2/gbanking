package de.gbanking.gui.swing.panel.category;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.Category;
import de.gbanking.gui.swing.components.GBankingTable;
import de.gbanking.gui.swing.model.CategoryTableModel;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.overview.CategoryOverviewPanel;

public class CategoryListPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7986806367964863515L;

	private static Logger log = LogManager.getLogger(CategoryListPanel.class);

	private transient TableRowSorter<CategoryTableModel> sorter;
	private JTextField filterText;

	private JTable tableCategories;

	private CategoryTableModel categoryTableModel;

	private JPanel parentPanel;

	public CategoryListPanel(JPanel parentPanel) {
		this.parentPanel = parentPanel;
		createInnerCategoryListPanel();
	}

	private void createInnerCategoryListPanel() {

		log.debug("createInnerCategoryListPanel()");

		TitledBorder recipientsPanelBorder = BorderFactory.createTitledBorder("Kategorien");
		setBorder(recipientsPanelBorder);
		setLayout(new BorderLayout());

		List<Category> categoryList = dbController.getAllFull(Category.class);

		String[] titlesCategory = new String[] { "*", "ID", "Name", "übergeordnete Kategorie", "Stand" };
		categoryTableModel = new CategoryTableModel(titlesCategory, categoryList);

		tableCategories = new GBankingTable(categoryTableModel);

		/* "*", "ID", "Name", fullName" */

		tableCategories.getColumnModel().getColumn(1).setMinWidth(150);
		tableCategories.getColumnModel().getColumn(2).setWidth(150);
		tableCategories.getColumnModel().getColumn(3).setMaxWidth(50);

		ListSelectionModel cellSelectionModel = tableCategories.getSelectionModel();
		cellSelectionModel.addListSelectionListener(getCategoryTableSelectionListener());

		sorter = new TableRowSorter<>(categoryTableModel);
		tableCategories.setRowSorter(sorter);

		JScrollPane scrollPaneCategories = new JScrollPane(tableCategories);
		scrollPaneCategories.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		setLayout(new BorderLayout());
		add(scrollPaneCategories, BorderLayout.CENTER);

		JPanel filterPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbcFilter = new GridBagConstraints();
		gbcFilter.insets = new Insets(2, 2, 2, 2);
		gbcFilter.weightx = 0.05;
		gbcFilter.gridx = 0;
		
		JLabel labelFilter = new JLabel("Suche:", SwingConstants.TRAILING);
		filterPanel.add(labelFilter, gbcFilter);
		filterText = new JTextField();
		filterText.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				categoryFilter();
			}

			public void insertUpdate(DocumentEvent e) {
				categoryFilter();
			}

			public void removeUpdate(DocumentEvent e) {
				categoryFilter();
			}
		});
		labelFilter.setLabelFor(filterText);
		gbcFilter.weightx = 0.95;
		gbcFilter.gridx = 1;
		gbcFilter.fill = GridBagConstraints.HORIZONTAL;
		filterPanel.add(filterText, gbcFilter);
		add(filterPanel, BorderLayout.PAGE_END);

	}

	/**
	 * Update the row filter regular expression from the expression in the text box.
	 */
	private void categoryFilter() {
		RowFilter<CategoryTableModel, Object> rf = null;
		try {
			String text = filterText.getText();
			List<RowFilter<Object, Object>> rfs = new ArrayList<>(2);
			rfs.add(RowFilter.regexFilter("(?i)" + text));
			rf = RowFilter.andFilter(rfs);
		} catch (java.util.regex.PatternSyntaxException e) {
			return;
		}
		sorter.setRowFilter(rf);
	}

	private ListSelectionListener getCategoryTableSelectionListener() {
		return (ListSelectionEvent e) -> {
			if (!e.getValueIsAdjusting()) {

				int column = 1; // Recipient-Id
				int row = tableCategories.getSelectedRow();
				if (tableCategories.getRowSorter() != null) {
					row = tableCategories.getRowSorter().convertRowIndexToModel(row);
				}
				int recipientId = (int) tableCategories.getModel().getValueAt(row, column);

				log.info(messages.getFormattedMessage("LOG_INFO_RECIPIENT_SELECTED", recipientId));

				final Category selectedCategory = categoryTableModel.getSelectedCategory(row);

				CategoryOverviewPanel parent = ((CategoryOverviewPanel) parentPanel);
				CategoryInputPanel categoryInputPanel = parent.getCategoryInputPanel();

				categoryInputPanel.updatePanelFieldValues(selectedCategory);
				categoryInputPanel.revalidate();
				categoryInputPanel.repaint();
			}
		};
	}

}
