package de.gbanking.gui.swing.model;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public abstract class GBankingTableModel<T> extends AbstractTableModel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3518162481685505176L;

	protected SimpleDateFormat dateFormat= new SimpleDateFormat("dd.MM.yy");

	protected String[] columnNames;
	protected transient List<T> resultlist;
	
	public void setResultlist(List<T> resultlist) {
		this.resultlist = resultlist;
	}

//	@Override
//	public Object getValueAt(int row, int column) {
//		
//		if (resultlist.isEmpty()) {
//			return null;
//		}
//		return resultlist;
//	}

	@Override
	public int getColumnCount() {
        return columnNames.length;
    }

	@Override
    public int getRowCount() {
        return resultlist.size();
    }

	@Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

//    public Object getValueAt(int row, int col) {
//        return resultlist.get(row);
//    }

    /*
     * JTable uses this method to determine the default renderer/
     * editor for each cell.  If we didn't implement this method,
     * then the last column would contain text ("true"/"false"),
     * rather than a check box.
     */
	@Override
    public Class<?> getColumnClass(int c) {
		if (c == 0) {
			return Boolean.class;
		}
    	if (getValueAt(0, c) == null) {
    		return String.class;
    	}
    	if (getValueAt(0, c) instanceof Calendar) {
    		return Date.class;
    	}
        return getValueAt(0, c).getClass();
    }

    /*
     * Don't need to implement this method unless your table's
     * editable.
     */
	@Override
    public boolean isCellEditable(int row, int col) {
        //Note that the data/cell address is constant,
        //no matter where the cell appears onscreen.
//        if (col < 2) {
//            return false;
//        } else {
//            return true;
//        }
		return true;
    }

    /*
     * Don't need to implement this method unless your table's
     * data can change.
     */
	@Override
    public void setValueAt(Object value, int row, int col) {
        fireTableCellUpdated(row, col);
    }
	
	

}