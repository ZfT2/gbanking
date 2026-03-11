package de.gbanking.gui.swing.panel.overview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.panel.account.AccountListPanel;
import de.gbanking.gui.swing.panel.category.CategoryInputPanel;
import de.gbanking.gui.swing.panel.category.CategoryListPanel;

public class CategoryOverviewPanel extends OverviewBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8540358516959751868L;

	private static Logger log = LogManager.getLogger(CategoryOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private CategoryInputPanel categoryInputPanel;
	private CategoryListPanel categoryListPanel;

	private BankAccount selectedAccount;

	public CategoryOverviewPanel() {
		categoryInputPanel = new CategoryInputPanel(this);
		categoryListPanel = new CategoryListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {

		setPageContext(PageContext.CATEGORIES);

		Border categoriesPanelBorder = BorderFactory.createTitledBorder("Kategorien");
		setBorder(categoriesPanelBorder);

		GridBagLayout categoryLayout = new GridBagLayout();
		setLayout(categoryLayout);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		accountListPanel = new AccountListPanel(this);

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridheight = 2;
		gbc.weightx = 0.10;

		add(accountListPanel, gbc);

		gbc.anchor = GridBagConstraints.NORTHEAST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 1;
		gbc.weightx = 0.45;
		gbc.weighty = 0.25;
		gbc.gridheight = 1;
		add(categoryInputPanel, gbc);

		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 2;
		gbc.weightx = 0.45;
		add(categoryListPanel, gbc);

		setEnabled(show);
	}

	public BankAccount getSelectedAccount() {
		return selectedAccount;
	}

	public void setSelectedAccount(BankAccount selectedAccount) {
		this.selectedAccount = selectedAccount;
	}

	public CategoryInputPanel getCategoryInputPanel() {
		return categoryInputPanel;
	}

	public CategoryListPanel getCategoryListPanel() {
		return categoryListPanel;
	}

}
