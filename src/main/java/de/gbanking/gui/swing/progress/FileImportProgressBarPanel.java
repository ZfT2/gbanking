package de.gbanking.gui.swing.progress;

import java.beans.PropertyChangeEvent;

import javax.swing.JFrame;

import de.gbanking.fileimport.swing.FileImportTask;
import de.gbanking.gui.swing.enu.FileType;
import de.gbanking.gui.swing.panel.account.AccountListPanel;

public class FileImportProgressBarPanel extends BaseFileProgressBarPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -607262295683346394L;

	public FileImportProgressBarPanel(JFrame parent) {
		super(parent);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		super.propertyChange(evt);
		if (task.getProgress() == 100) {
			accountListPanel.refreshModelAccount();
			accountListPanel.revalidate();
			accountListPanel.repaint();
		}
	}

	@Override
	public void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception {
		task = new FileImportTask(fileName);
		super.startTask(accountListPanel);
	}

}