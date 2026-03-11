package de.gbanking.gui.swing.progress;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

import de.gbanking.file.swing.BaseFileTask;
import de.gbanking.gui.swing.enu.FileType;
import de.gbanking.gui.swing.panel.account.AccountListPanel;

public abstract class BaseFileProgressBarPanel extends JPanel implements PropertyChangeListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2364514800118640507L;

	private JProgressBar progressBar;
	private JTextArea taskOutput;
	protected BaseFileTask task;

	private JFrame parentFrame;
	private JDialog modelDialog;

	protected AccountListPanel accountListPanel;

	/**
	 * Invoked when task's progress property changes.
	 */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if ("progress".equalsIgnoreCase(evt.getPropertyName())) {
			int progress = (Integer) evt.getNewValue();
			progressBar.setValue(progress);
			// taskOutput.append(String.format("Completed %d%% of task.\n",
			// task.getProgress()));
			taskOutput.append(task.getProcessingState() + "\n");

			if (task.getProgress() == 100) {
//				accountListPanel.refreshModelAccount();
//				accountListPanel.revalidate();
//				accountListPanel.repaint();
				setCursor(null);
				modelDialog.setEnabled(false);
				modelDialog.dispose();
			}
		}
	}

	protected BaseFileProgressBarPanel(JFrame parent) {
		this.parentFrame = parent;
	}

	public JDialog createNewFileImportProgressBarWindow() {
		modelDialog = new JDialog(parentFrame, "Importiere...", Dialog.ModalityType.DOCUMENT_MODAL);
		modelDialog.setBounds(132, 132, 300, 200);

		createProgressPanel();

		return modelDialog;
	}

	private void createProgressPanel() {

		// super(new BorderLayout());

		Container dialogContainer = modelDialog.getContentPane();

		setLayout(new BorderLayout());

		progressBar = new JProgressBar(0, 100);
		progressBar.setValue(0);
		progressBar.setStringPainted(true);

		taskOutput = new JTextArea(20, 100);
		taskOutput.setMargin(new Insets(5, 5, 5, 5));
		taskOutput.setEditable(false);

		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());

		panel.add(progressBar, BorderLayout.PAGE_START);

		JScrollPane scrollPane = new JScrollPane(taskOutput);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		panel.add(scrollPane, BorderLayout.CENTER);

		add(panel);
		setBorder(BorderFactory.createEmptyBorder(40, 20, 20, 40));

		dialogContainer.add(panel);
	}

//	/**
//	 * Create the GUI and show it. As with all GUI code, this must run on the
//	 * event-dispatching thread.
//	 */
//	public static FileImportProgressBarPanel createAndShowGUI(JFrame parent) {
//
//		FileImportProgressBarPanel newContentPane = new FileImportProgressBarPanel(parent);
//		newContentPane.setOpaque(true); // content panes must be opaque
//		
//		return newContentPane;
//	}
	
	protected abstract void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception;

	protected void startTask(AccountListPanel accountListPanel) throws Exception {
		// Instances of javax.swing.SwingWorker are not reusuable, so
		// we create new instances as needed.
		this.accountListPanel = accountListPanel;
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		// //task = new FileImportTask(fileName);
		task.addPropertyChangeListener(this);
		task.execute();
	}

}