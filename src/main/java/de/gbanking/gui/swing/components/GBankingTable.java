package de.gbanking.gui.swing.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

public class GBankingTable extends JTable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4266711452561496598L;

	private static Color gBankingColorLightBlue = new Color(240, 245, 250);
	
	private static Color gBankingColorDarkGreen = new Color(0,100,0);
	
	private BigDecimalFormatTableCellRenderer bigDecimalRenderer;

	public GBankingTable(TableModel tableModel) {
		super(tableModel);
		initDefaultTableSettings();
		this.bigDecimalRenderer = new BigDecimalFormatTableCellRenderer(); 

	}

	private void initDefaultTableSettings() {
		removeColumn(getColumnModel().getColumn(1));
		getColumnModel().getColumn(0).setMaxWidth(25);

		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setColumnSelectionAllowed(false);
		setRowSelectionAllowed(true);

//		setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
//			@Override
//			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
//					int column) {
//				final Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
//				c.setBackground(row % 2 == 0 ? gBankingColorLightBlue : Color.WHITE);
//				return this;
//			}
//		});
	}
	
	@Override
	public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
		super.prepareRenderer(renderer, row, column);
		Component comp = super.prepareRenderer(renderer, row, column);
		comp.setBackground(row % 2 == 0 ? Color.WHITE : gBankingColorLightBlue);

		if (getValueAt(row, column) instanceof BigDecimal decimalValue) {
			comp.setForeground(getColor(decimalValue));
			
//			DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(Locale.GERMAN);
//			DecimalFormatSymbols symbols = df.getDecimalFormatSymbols();
//			symbols.setGroupingSeparator('.');
//			df.setDecimalFormatSymbols(symbols);
//			
////			DecimalFormat df = new DecimalFormat();
////			df.setMaximumFractionDigits(2); 
////			df.setMinimumFractionDigits(0);
////			df.setGroupingUsed(true);
//			System.out.println("BD: " + df.format(decimalValue.doubleValue()));
//			setValueAt(df.format(decimalValue.doubleValue()), row, column);
		} else {
			comp.setForeground(getForeground());
		}

		return comp;
	}

	private Color getColor(BigDecimal value) {
		Color color = null;
		if (value.compareTo(BigDecimal.ZERO) > 0) {
			color = gBankingColorDarkGreen;
		} else if (value.compareTo(BigDecimal.ZERO) < 0) {
			color = Color.RED;
		} else {
			color = getForeground();
		}
		return color;
	}
	
	public BigDecimalFormatTableCellRenderer getBigDecimalRenderer() {
		return bigDecimalRenderer;
	}

	public class BigDecimalFormatTableCellRenderer extends DefaultTableCellRenderer {

		/**
		 * 
		 */
		private static final long serialVersionUID = -647276722379113681L;
		
		private DecimalFormat df;
		
		private boolean cellToBold = true;
		
//		public BigDecimalFormatTableCellRenderer(boolean cellToBold) {
//            this.cellToBold = cellToBold;
//        }

		public void setCellToBold(boolean cellToBold) {
			this.cellToBold = cellToBold;
		}

		public DecimalFormat getDecimalFormat() {
			if (df == null) {
				DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
				symbols.setGroupingSeparator('.');
				df = new DecimalFormat("#,##0.00;#,##0.00", symbols);
			}
			return df;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			if (value instanceof BigDecimal) {
				value = getDecimalFormat().format(value);
			}

			Component cellComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (cellToBold) {
				cellComponent.setFont(cellComponent.getFont().deriveFont(Font.BOLD));
			}
			return cellComponent;
		}
	}

	public int getSelectedRowId() {
		int row = getSelectedRow();
		return (int) getModel().getValueAt(row, 1);
	}

}
