package de.gbanking.gui.swing.panel.overview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.panel.account.AccountDetailPanel;
import de.gbanking.gui.swing.panel.account.AccountListPanel;

public class AllAccountsOverviewPanel extends OverviewBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8202687637410144952L;
	
	private static Logger log = LogManager.getLogger(AllAccountsOverviewPanel.class);
	
	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;

	@Override
	public void createOverallPanel(boolean show) {

		setPageContext(PageContext.ALL_ACCOUNTS);

		Border mainPanelBorder = BorderFactory.createTitledBorder("Alle Konten");
		setBorder(mainPanelBorder);
		log.info("main Panel Width / Height: {} / {}", getWidth(), getHeight());
		GridBagLayout mainlayout = new GridBagLayout();
		setLayout(mainlayout);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		accountDetailPanel = new AccountDetailPanel(true);

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0;
		gbc.gridy = 0;
//		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0.1;
		add(accountDetailPanel, gbc);

		accountListPanel = new AccountListPanel(this);

		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weighty = 0.9;

		add(accountListPanel, gbc);

		setEnabled(show);
	}
	
	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

}
