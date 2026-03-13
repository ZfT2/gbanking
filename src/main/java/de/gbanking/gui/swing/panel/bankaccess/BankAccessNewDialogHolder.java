package de.gbanking.gui.swing.panel.bankaccess;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.enu.TanProcedure;
import de.gbanking.gui.swing.components.GBankingTable;
import de.gbanking.gui.swing.enu.ButtonContext;
import de.gbanking.gui.swing.model.AccountTableModel;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.overview.BankAccessOverviewPanel;

public class BankAccessNewDialogHolder extends BasePanelHolder {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4817160995019620709L;

	private static Logger log = LogManager.getLogger(BankAccessNewDialogHolder.class);
	
	JFrame parentFrame;
	JPanel parentPanel;
	
	private JDialog modelDialog;
	
	private BankAccess currentBankAccess;

	JButton okButton;
	JButton cancelButton;
	
	ActionListener actionListenerStep1;
	ActionListener actionListenerStep2;
	
	ActionListener actionListenerDelete;
	
	ButtonContext buttonContext;

	
	public BankAccessNewDialogHolder(JFrame parent, ButtonContext buttonContext, BankAccessOverviewPanel overviewPanel) {
		this.parentFrame = parent;
		this.buttonContext = buttonContext;
		this.parentPanel = overviewPanel;
		this.currentBankAccess = overviewPanel.getCurrentBankAccess();
	}

	public JDialog createNewBankAccessDialog() {
		modelDialog = new JDialog(parentFrame, buttonContext.getHeadline(), Dialog.ModalityType.DOCUMENT_MODAL);
		modelDialog.setBounds(132, 132, 400, 200);

		switch (buttonContext) {
		case BUTTON_NEW, BUTTON_EDIT:
			createSPaneltep1();
			break;
		case BUTTON_DELETE:
			createPanelDelete();
			break;
		}

		return modelDialog;
	}
	
	private void createSPaneltep1() {
		
		Container dialogContainer = modelDialog.getContentPane();
		GridBagLayout dialogPanelLayout = new GridBagLayout();

		JPanel panel1 = new JPanel();
		panel1.setLayout(dialogPanelLayout);
		Border panel1Border = BorderFactory.createTitledBorder("Bankzugang Daten"); 
		panel1.setBorder(panel1Border);
		
		JLabel blzLabel = new JLabel("Bank");
		JLabel userNameLabel = new JLabel("Benutzer");
		JLabel pinLabel = new JLabel("PIN");

		JTextField blzText = new JTextField("30530500");
		JTextField userNameText = new JTextField("");
		
		if (buttonContext == ButtonContext.BUTTON_EDIT) {
			blzText.setText(currentBankAccess.getBlz());
			userNameText.setText(currentBankAccess.getUserId());
		}
		
		JPasswordField pinText = new JPasswordField("");
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel1.add(blzLabel, gbc);
		gbc.gridx = 1;
		panel1.add(blzText, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		panel1.add(userNameLabel, gbc);
		gbc.gridx = 1;
		panel1.add(userNameText, gbc);
		gbc.gridx = 0;
		gbc.gridy = 2;
		panel1.add(pinLabel, gbc);
		gbc.gridx = 1;
		panel1.add(pinText, gbc);
				
		okButton = new JButton("OK");
		actionListenerStep1 = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				BankAccess bankAccess = new BankAccess();
				bankAccess.setBlz(blzText.getText());
				bankAccess.setUserId(userNameText.getText());
				bankAccess.setPin(pinText.getPassword());
				bankAccess.setTanProcedure(TanProcedure.APP_TAN);
				
				if(bean.addNewBankAccess(bankAccess)) {
//					List<BankAccount> accounts = bankAccess.getAccounts();
//					bankAccess = dbController.getBankAccessByBlz(bankAccess.getBlz());
//					bankAccess.setAccounts(accounts);
					dialogContainer.remove(panel1);
					modelDialog.setSize(750, 600);
					dialogContainer.add(createSPaneltep2(bankAccess));
					dialogContainer.repaint();
				}
			}
		};
		okButton.addActionListener(actionListenerStep1);
		
		cancelButton = new JButton("Abbrechen");
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				modelDialog.setVisible(false);
			}
		});

		gbc.gridx = 0;
		gbc.gridy = 4;
		panel1.add(okButton, gbc);
		gbc.gridx = 1;
		panel1.add(cancelButton, gbc);
		dialogContainer.add(panel1);
	}
	
	private JPanel createSPaneltep2(BankAccess bankAccess) {
		
		GridBagLayout dialogPanelLayout = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		JPanel panel2 = new JPanel();
		panel2.setLayout(dialogPanelLayout);
		Border panel1Border = BorderFactory.createTitledBorder("Bankzugang Konten"); 
		panel2.setBorder(panel1Border);
		
		JLabel accountsLabel = new JLabel("Konten:");

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.weighty = 1.0;
		gbc.gridwidth = 2;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel2.add(accountsLabel, gbc);

		String[] titlesAccount = new String[] { "*", "ID", "Konto Name", "Stand" };
		final AccountTableModel modelAccount = new AccountTableModel(titlesAccount, bankAccess.getAccounts());

		JTable accountListTable = new GBankingTable(modelAccount);

		accountListTable.getColumnModel().getColumn(1).setMinWidth(75);
		accountListTable.getColumnModel().getColumn(2).setMaxWidth(60);

		JScrollPane scrollPaneAccounts = new JScrollPane(accountListTable);
		scrollPaneAccounts.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		
		gbc.gridy = 1;
		panel2.add(scrollPaneAccounts, gbc);
		
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 1;
		gbc.weighty = 0.1;
		gbc.anchor = GridBagConstraints.PAGE_END;
		panel2.add(okButton, gbc);
		gbc.gridx = 1;
		panel2.add(cancelButton, gbc);
		
		okButton.removeActionListener(actionListenerStep1);
		actionListenerStep2 = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(bean.saveBankAccessAccountsToDB(bankAccess)) {
					log.info("Saving of BankAccess successful.");
					((BankAccessOverviewPanel) parentPanel).getBankAccessListPanel().refreshModelBankAccess();
					modelDialog.setVisible(false);
				} else {
					log.error("'Error in saving BankAccess to database!");
					accountsLabel.setText("Fehler bei Speicherung des Bankzugangs!");
				}
			}
		};
		okButton.addActionListener(actionListenerStep2);

		return panel2;
	}
	
	private void createPanelDelete() {
		
		Container dialogContainer = modelDialog.getContentPane();
		GridBagLayout dialogPanelLayout = new GridBagLayout();

		JPanel panel1 = new JPanel();
		panel1.setLayout(dialogPanelLayout);
		Border panel1Border = BorderFactory.createTitledBorder("Bankzugang Daten"); 
		panel1.setBorder(panel1Border);
		
		JLabel blzLabel = new JLabel("Bank");
		JLabel userNameLabel = new JLabel("Benutzer");

		JLabel blzText = new JLabel(currentBankAccess.getBlz()); 
		JLabel userNameText = new JLabel(currentBankAccess.getUserId());
		
		JLabel question = new JLabel(getText("BANKACCESS_QUESTION_DELETE"));
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel1.add(blzLabel, gbc);
		gbc.gridx = 1;
		panel1.add(blzText, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		panel1.add(userNameLabel, gbc);
		gbc.gridx = 1;
		panel1.add(userNameText, gbc);
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth=2;
		panel1.add(question, gbc);
				
		okButton = new JButton("OK");
		actionListenerDelete = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
//				BankAccess bankAccess = new BankAccess();
//				bankAccess.setBlz(blzText.getText());
//				bankAccess.setUserId(userNameText.getText());
				if (currentBankAccess == null) {
					modelDialog.setVisible(false);
				} else {
					if (bean.deleteBankAccessFromDB(currentBankAccess)) {
						log.info("Deletion of BankAccess successful.");
						currentBankAccess = null;
						question.setText(getText("BANKACCESS_SUCCESS_DELETE"));
						panel1.remove(cancelButton);
//					dialogContainer.remove(panel1);
//					modelDialog.setSize(750, 600);
//					dialogContainer.add(createStep2PanelDelete(bankAccess));
					} else {
						log.info("Deletion of BankAccess failed!");
						question.setText(getText("BANKACCESS_ERROR_DELETE"));
					}
					dialogContainer.repaint();
				}
			}
		};
		okButton.addActionListener(actionListenerDelete);

		cancelButton = new JButton("Abbrechen");
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				modelDialog.setVisible(false);
			}
		});

		gbc.gridwidth=1;
		gbc.gridx = 0;
		gbc.gridy = 4;
		panel1.add(okButton, gbc);
		gbc.gridx = 1;
		panel1.add(cancelButton, gbc);
		dialogContainer.add(panel1);
	}

}
