package de.gbanking.gui.swing.model;

import java.util.ArrayList;
import java.util.List;

import de.gbanking.db.dao.Category;

public class CategoryTableModel extends GBankingTableModel<Category> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7082043034380715920L;

	public CategoryTableModel(String[] columnNames, List<Category> categories) {
		this.columnNames = columnNames;
		this.resultlist = new ArrayList<>(categories);
	}

	@Override
	public Object getValueAt(int row, int column) {

		if (resultlist.isEmpty()) {
			return null;
		}
		switch (column) {
		case 0:
			return resultlist.get(row).isSelected();
		case 1:
			return resultlist.get(row).getId();
		case 2:
			return resultlist.get(row).getName();
		case 3:
			return resultlist.get(row).getFullName();
		case 4:
			return dateFormat.format(resultlist.get(row).getUpdatedAt().getTime());
		default:
			return null;
		}
		
	}


	public Category getSelectedCategory(int rowIndex) {
		return resultlist.get(rowIndex);
	}

	public int getSelectedCategoryId(int rowIndex) {
		return resultlist.get(rowIndex).getId();
	}

}