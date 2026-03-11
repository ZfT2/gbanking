package de.gbanking.gui.swing;

import java.awt.Component;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.messages.Messages;

public abstract class BaseGui extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3308337165919384772L;

	private static Logger log = LogManager.getLogger(BaseGui.class);

	protected static Map<String, String> optionsMap;

	protected static JLabel statusLabel;
	
	protected static Messages messages;
	
	static {
		messages = Messages.getInstance();
	}

	protected BaseGui(String text) {
		super(text);
		log.debug("BaseGui()");
	}
	
	protected String getText(String key) {
		return messages.getMessage(key);
	}
	
	protected String getText(String key, int value) {
		return messages.getFormattedMessage(key, value);
	}

	protected static void adjustTableColumnWidth(JTable accountListTable) {
		
		for (int column = 0; column < accountListTable.getColumnCount(); column++)
		{
		    TableColumn tableColumn = accountListTable.getColumnModel().getColumn(column);
		    int preferredWidth = tableColumn.getMinWidth();
		    int maxWidth = tableColumn.getMaxWidth();

		    for (int row = 0; row < accountListTable.getRowCount(); row++)
		    {
		        TableCellRenderer cellRenderer = accountListTable.getCellRenderer(row, column);
		        Component c = accountListTable.prepareRenderer(cellRenderer, row, column);
		        int width = c.getPreferredSize().width + accountListTable.getIntercellSpacing().width;
		        preferredWidth = Math.max(preferredWidth, width);

		        //  We've exceeded the maximum width, no need to check other rows

		        if (preferredWidth >= maxWidth)
		        {
		            preferredWidth = maxWidth;
		            break;
		        }
		    }

		    tableColumn.setPreferredWidth( preferredWidth );
		}
	}
	
}
