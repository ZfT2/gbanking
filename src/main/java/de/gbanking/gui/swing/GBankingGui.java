package de.gbanking.gui.swing;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.GBankingBean;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.gui.swing.enu.FileType;
import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.model.AccountTableModel;
import de.gbanking.gui.swing.panel.about.AboutPanel;
import de.gbanking.gui.swing.panel.action.PinAskDialog;
import de.gbanking.gui.swing.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.AllAccountsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.AllTransactionsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.swing.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.swing.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.swing.panel.overview.OverviewBasePanel;
import de.gbanking.gui.swing.panel.overview.RecipientOverviewPanel;
import de.gbanking.gui.swing.progress.FileExportProgressBarPanel;
import de.gbanking.gui.swing.progress.FileImportProgressBarPanel;
import de.gbanking.panel.swing.OverviewPanelFactory;

public class GBankingGui extends BaseGui {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3037619018064190287L;

	private static Logger log = LogManager.getLogger(GBankingGui.class);

	private static final String LAST_PATH_SELECTED = "lastPathSelected";
	static JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());

	private String choosenFile;
	private GBankingBean bean;
	
	private AccountsTransactionsOverviewPanel overviewPanel;
	private MoneyTransferOverviewPanel moneyTransferPanel;	
	private BankAccessOverviewPanel bankAccessOverviewPanel;
	private CategoryOverviewPanel categoryOverviewPanel;
	private RecipientOverviewPanel recipientOverviewPanel;
	private AllAccountsOverviewPanel allAccountsOverviewPanel;
	private AllTransactionsOverviewPanel allTransactionsOverviewPanel;
	
	private Map<String, OverviewBasePanel> overviewPanelMap = new HashMap<>();
	

	public GBankingGui(String text) {
		super(text != null ? text : "GBanking");

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				try {
					RestoreHandler.storeOptions(optionsMap);
				} catch (IOException e) {
					log.error("IOException in store: {}", e.getMessage());
				}
				System.exit(0);
			}
		});

		setSize(1600, 1200);
		setLocationByPlatform(true);
		setResizable(true);

		optionsMap = new HashMap<>();
		try {
			RestoreHandler.restoreOptions(optionsMap);
		} catch (IOException ioe) {
			log.error("IOException in restore: {}", ioe.getMessage());
		}
		
		if(optionsMap.get(LAST_PATH_SELECTED) != null) {
			fileChooser.setCurrentDirectory(new File(optionsMap.get(LAST_PATH_SELECTED)));
		}

		createAndshowMenu();
		createAndShowStartGui();

		statusLabel = new JLabel("Info");
		add(statusLabel, BorderLayout.SOUTH);

		setTitle("GBanking");
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setVisible(true);

		try {
			setIconImage(ImageIO.read(getClass().getResource("/icon_coin.png")));
		} catch (IOException e) {
			log.error("Error setting icon: {}", e.getMessage());
		}
		
		createOverviewPanelMap();

		bean = new GBankingBean();
		
		bean.setup();
	}

	private void createAndShowStartGui() {
		createAndShowAccountsTransactionsPanel();
	}
	
	private void createOverviewPanelMap(){
		overviewPanelMap.put(PageContext.ACCOUNTS_TRANSACTIONS.name(), overviewPanel);
		overviewPanelMap.put(PageContext.ACCOUNTS_MONEYTRANSFERS.name(), moneyTransferPanel);
		overviewPanelMap.put(PageContext.BANKACCESS.name(), bankAccessOverviewPanel);
		overviewPanelMap.put(PageContext.CATEGORIES.name(), categoryOverviewPanel);
		overviewPanelMap.put(PageContext.RECIPIENTS.name(), recipientOverviewPanel);
		overviewPanelMap.put(PageContext.ALL_ACCOUNTS.name(), allAccountsOverviewPanel);
		overviewPanelMap.put(PageContext.ALL_TRANSACTIONS.name(), allTransactionsOverviewPanel);
	}
	
	private void createAndShowAccountsTransactionsPanel() {
		overviewPanel = new AccountsTransactionsOverviewPanel();
		overviewPanel.createOverallPanel(true);
		add(overviewPanel);
	}
	

	private void createAndshowMenu() {
		/**  create a menu bar **/
		final JMenuBar menuBar = new JMenuBar();

		/**  create menus **/
		JMenu fileMenu = new JMenu("Datei");
		JMenu editMenu = new JMenu("Bearbeiten");
		final JMenu aboutMenu = new JMenu("Über");

		/** create menu items **/
		JMenuItem filenewMenuItem = new JMenuItem("Neu");
		filenewMenuItem.setMnemonic(KeyEvent.VK_N);
		filenewMenuItem.setActionCommand("New");

		JMenuItem fileOpenMenuItem = new JMenuItem("Öffnen");
		fileOpenMenuItem.setActionCommand("open");

		JMenuItem fileSaveMenuItem = new JMenuItem("Speichern");
		fileSaveMenuItem.setActionCommand("Save");

		JMenu fileImportMenu = new JMenu("Importieren...");
		fileImportMenu.setActionCommand("Import");

		JMenuItem fileImportXML = new JMenuItem("XML");
		fileImportXML.setActionCommand("XML");

		fileImportMenu.add(fileImportXML);
		fileImportXML.addActionListener(e ->  processImport());
		
		JMenu fileExportMenu = new JMenu("Exportieren...");
		fileExportMenu.setActionCommand("Export");
		
		JMenuItem fileExportCSV = new JMenuItem("CSV");
		fileExportCSV.setActionCommand("CSV");
		fileExportMenu.add(fileExportCSV);
		fileExportCSV.addActionListener(e -> processExport(FileType.CSV));

		JMenuItem fileExportXML = new JMenuItem("XML");
		fileExportXML.setActionCommand("XML");
		fileExportMenu.add(fileExportXML);
		fileExportXML.addActionListener(e -> processExport(FileType.XML));

		JMenuItem fileExitMenuItem = new JMenuItem("Beenden");
		fileExitMenuItem.setActionCommand("Exit");
		fileExitMenuItem.addActionListener(e -> shutdownApplication());

		JMenuItem editAccountsMenuItem = new JMenuItem("Konten...");
		editAccountsMenuItem.setActionCommand(PageContext.ACCOUNTS_TRANSACTIONS.name());

		JMenuItem editOrdersMenuItem = new JMenuItem("Aufträge...");
		editOrdersMenuItem.setActionCommand(PageContext.ACCOUNTS_MONEYTRANSFERS.name());
		editOrdersMenuItem.addItemListener(e -> switchToEditOrdersMask());

		JMenuItem editBankAccessMenuItem = new JMenuItem("Bankzugänge...");
		editBankAccessMenuItem.setActionCommand(PageContext.BANKACCESS.name());
		editBankAccessMenuItem.addItemListener(e -> switchToEditBankAccessesMask());
		
		JMenuItem editCategoriesMenuItem = new JMenuItem("Kategorien...");
		editCategoriesMenuItem.setActionCommand(PageContext.CATEGORIES.name());
		editCategoriesMenuItem.addItemListener(e -> switchToEditCategoriesMask());
		
		JMenuItem editRecipientsMenuItem = new JMenuItem("Adressbuch...");
		editRecipientsMenuItem.setActionCommand(PageContext.RECIPIENTS.name());
		editRecipientsMenuItem.addItemListener(e -> switchToEditRecipientsMask());
		
		JMenuItem editAllAccountsMenuItem = new JMenuItem("Alle Konten...");
		editAllAccountsMenuItem.setActionCommand(PageContext.ALL_ACCOUNTS.name());
		editAllAccountsMenuItem.addItemListener(e ->switchToEditAllAccountsMask());
		
		JMenuItem editAllTransactionsMenuItem = new JMenuItem("Alle Umsätze...");
		editAllTransactionsMenuItem.setActionCommand(PageContext.ALL_TRANSACTIONS.name());
		editAllTransactionsMenuItem.addItemListener(e ->switchToEditAllTransactionsMask());
		
		JMenu executeMenu = new JMenu("Ausführen");
		executeMenu.setActionCommand("execute");
		
		JMenuItem updateAccountsMenuItem = new JMenuItem("Umsätze abrufen");
		updateAccountsMenuItem.setActionCommand("updateAccounts");
		PinAskDialog pinWindow = new PinAskDialog(this);
		updateAccountsMenuItem.addActionListener(e -> updateAccounts(pinWindow));
		
		JMenuItem executeTransfersMenuItem = new JMenuItem("Aufträge ausführen");
		executeTransfersMenuItem.setActionCommand("executeTransfers");
		executeTransfersMenuItem.addActionListener( e -> executeTransfers(pinWindow));
		

		JMenuItem aboutMenuItem = new JMenuItem("Über GBanking...");
		aboutMenuItem.setActionCommand("about");
		aboutMenuItem.addActionListener( e -> showAboutWindow());

		MenuItemListener menuItemListener = new MenuItemListener(/*this*/);

		filenewMenuItem.addActionListener(menuItemListener);
		fileOpenMenuItem.addActionListener(menuItemListener);
		fileSaveMenuItem.addActionListener(menuItemListener);
		fileExitMenuItem.addActionListener(menuItemListener);
		editAccountsMenuItem.addActionListener(menuItemListener);
		editOrdersMenuItem.addActionListener(menuItemListener);
		editBankAccessMenuItem.addActionListener(menuItemListener);
		editCategoriesMenuItem.addActionListener(menuItemListener);
		editRecipientsMenuItem.addActionListener(menuItemListener);
		editAllAccountsMenuItem.addActionListener(menuItemListener);
		editAllTransactionsMenuItem.addActionListener(menuItemListener);

		/* add menu items to menus */
		fileMenu.add(filenewMenuItem);
		fileMenu.add(fileOpenMenuItem);
		fileMenu.add(fileSaveMenuItem);
		fileMenu.add(fileImportMenu);
		fileMenu.add(fileExportMenu);
		fileMenu.addSeparator();
		fileMenu.addSeparator();
		fileMenu.add(fileExitMenuItem);

		editMenu.add(editAccountsMenuItem);
		editMenu.add(editOrdersMenuItem);
		editMenu.add(editBankAccessMenuItem);
		editMenu.add(editCategoriesMenuItem);
		editMenu.add(editRecipientsMenuItem);
		editMenu.add(editAllAccountsMenuItem);
		editMenu.add(editAllTransactionsMenuItem);
		
		executeMenu.add(updateAccountsMenuItem);
		executeMenu.add(executeTransfersMenuItem);

		aboutMenu.add(aboutMenuItem);

		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		menuBar.add(executeMenu);
		menuBar.add(aboutMenu);

		setJMenuBar(menuBar);
	}
	
	private void switchToEditOrdersMask() {
		overviewPanel.setEnabled(false);
		remove(overviewPanel);
		moneyTransferPanel = new MoneyTransferOverviewPanel();
		moneyTransferPanel.createOverallPanel(true);
	}
	
	private void switchToEditBankAccessesMask() {
		overviewPanel.setEnabled(false);
		remove(overviewPanel);
		bankAccessOverviewPanel = new BankAccessOverviewPanel();
		bankAccessOverviewPanel.createOverallPanel(true);
	}
	
	private void switchToEditCategoriesMask() {
		overviewPanel.setEnabled(false);
		remove(overviewPanel);
		categoryOverviewPanel = new CategoryOverviewPanel();
		categoryOverviewPanel.createOverallPanel(true);
	}
	
	private void switchToEditRecipientsMask() {
		overviewPanel.setEnabled(false);
		remove(overviewPanel);
		recipientOverviewPanel = new RecipientOverviewPanel();
		recipientOverviewPanel.createOverallPanel(true);
	}
	
	private void switchToEditAllAccountsMask() {
		overviewPanel.setEnabled(false);
		remove(overviewPanel);
		allAccountsOverviewPanel = new AllAccountsOverviewPanel();
		allAccountsOverviewPanel.createOverallPanel(true);
	}
	
	private void switchToEditAllTransactionsMask() {
		overviewPanel.setEnabled(false);
		remove(overviewPanel);
		allTransactionsOverviewPanel = new AllTransactionsOverviewPanel();
		allTransactionsOverviewPanel.createOverallPanel(true);
	}

	private void updateAccounts(PinAskDialog pinWindow) {
		log.info("updateAccounts called.");
		AccountTableModel modelAccount = overviewPanel.getAccountListPanel().getModelAccount();
		List<BankAccount> checkedAccounts = modelAccount.getCheckedAccounts();
		if (checkedAccounts.isEmpty()) {
			log.info("no Accounts selected!");
			JOptionPane.showMessageDialog(this, getText("ALERT_ACCOUNT_NO_SELECTION"));
			return;
		}
		for (BankAccount bankAccount : checkedAccounts) {
			JDialog pinDialog = pinWindow.createNewPinAskDialog();
			pinDialog.setVisible(true);
			char[] pin = pinWindow.getPin();
			log.info("Account to update: {}", bankAccount.getAccountName());
			bean.retrieveAccountTransactions(bankAccount, pin);
		}
		bean.postRetriveActions(checkedAccounts);
	}
	
	private void executeTransfers(PinAskDialog pinWindow) {
		log.info("executeTransfers called.");
		List<MoneyTransfer> moneytransferList = bean.retrieveOpenTransfers();
		int accountId = -1;
		BankAccount bankAccount = null;
		char[] pin = null;
		for (MoneyTransfer moneytransfer : moneytransferList) {
			if (accountId != moneytransfer.getAccountId()) {
				accountId = moneytransfer.getAccountId();
				bankAccount = bean.getAccountForOpenMoneytransfers(accountId);
				JDialog pinDialog = pinWindow.createNewPinAskDialog();
				pinDialog.setVisible(true);
				pin = pinWindow.getPin();
			}
			bean.executeTransfer(moneytransfer, bankAccount, pin);
		}
	}
	
	private void showAboutWindow() {
		AboutPanel aboutPanel = new AboutPanel(this);
		JDialog aboutwindow = aboutPanel.createNewAboutWindow();
		aboutwindow.setVisible(true);
	}
	
	private void processImport() {
		handeFileChooserDialog(FileType.XML);
		try {
			importFile(FileType.XML);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void processExport(FileType fileType) {
		
		AccountTableModel modelAccount = overviewPanel.getAccountListPanel().getModelAccount();
		List<BankAccount> checkedAccounts = modelAccount.getCheckedAccounts();
		if (checkedAccounts.isEmpty()) {
			log.info("no Accounts selected, so using all!");
			checkedAccounts = modelAccount.getAllAccounts();
		}
		
		handeFileChooserDialog(fileType);
		try {
			exportFile(checkedAccounts, fileType);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void handeFileChooserDialog(FileType fileType) {
		fileChooser.setFileFilter(new FileFilter() {
			public String getDescription() {
				return fileType.getDescription();
			}

			public boolean accept(File f) {
				if (f.isDirectory()) {
					return true;
				} else {
					String filename = f.getName().toLowerCase();
					return filename.endsWith(fileType.getSuffix());
				}
			}
		});

		int r = fileChooser.showOpenDialog(null);

		if (r == JFileChooser.APPROVE_OPTION) {
			choosenFile = fileChooser.getSelectedFile().getAbsolutePath();
			optionsMap.put(LAST_PATH_SELECTED, fileChooser.getSelectedFile().getParent());
		}
	}
	
	private void importFile(FileType fileType) throws Exception {
		if (choosenFile != null) {
			FileImportProgressBarPanel fileImportProgressBarPanel = new FileImportProgressBarPanel(GBankingGui.this);
			JDialog progressWindow = fileImportProgressBarPanel.createNewFileImportProgressBarWindow();
			fileImportProgressBarPanel.startTask(choosenFile, fileType, overviewPanel.getAccountListPanel());
			progressWindow.setVisible(true);
		}  else {
			log.warn("no import file choosen!");
		}
	}
	
	private void exportFile(List<BankAccount> checkedAccounts, FileType fileType) throws Exception {
		if (choosenFile != null) {
			FileExportProgressBarPanel fileExportProgressBarPanel = new FileExportProgressBarPanel(GBankingGui.this, checkedAccounts);
			JDialog progressWindow = fileExportProgressBarPanel.createNewFileImportProgressBarWindow();
			fileExportProgressBarPanel.startTask(choosenFile, fileType, overviewPanel.getAccountListPanel());
			progressWindow.setVisible(true);
		}  else {
			log.warn("no export file choosen!");
		}
	}
	
	private void shutdownApplication() {
		try {
			RestoreHandler.storeOptions(optionsMap);
		} catch (Exception e) {
			log.error("IOException: {}", e.getMessage());
		}
		System.exit(0);
	}
	
	class MenuItemListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			String actionCommand = e.getActionCommand();
			statusLabel.setText(actionCommand + " JMenuItem clicked");

			OverviewBasePanel panelToActivate = null;
			for (Entry<String, OverviewBasePanel> panelEntry : overviewPanelMap.entrySet()) {
				if (panelEntry.getKey().equalsIgnoreCase(actionCommand)) {
					panelToActivate = overviewPanelMap.get(actionCommand);
				} else {
					OverviewBasePanel panelToDisable = panelEntry.getValue();
					if (panelToDisable != null) {
						panelToDisable.setEnabled(false);
						remove(panelToDisable);
					}
				}
			}
			if (panelToActivate == null) {
				panelToActivate = OverviewPanelFactory.getInstance(actionCommand);
				overviewPanelMap.put(actionCommand, panelToActivate);
				panelToActivate.createOverallPanel(true);
			}

			add(panelToActivate, BorderLayout.CENTER);
			panelToActivate.setEnabled(true);
		}
	}

}