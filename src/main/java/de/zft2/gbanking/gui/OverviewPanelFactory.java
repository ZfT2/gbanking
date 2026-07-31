package de.zft2.gbanking.gui;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AllAccountsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AllTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AnalysisOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.BankAccessOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.CategoryAnalysisOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.InstituteOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.OpenActionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.OverviewBasePanel;
import de.zft2.gbanking.gui.panel.overview.RecipientOverviewPanel;

public class OverviewPanelFactory {

	private static final Logger log = LogManager.getLogger(OverviewPanelFactory.class);

	private static final Map<String, OverviewBasePanel> overviewPanelMap = new HashMap<>();

	private OverviewPanelFactory() {
		/* This utility class should not be instantiated */
	}

	static OverviewBasePanel retrievePanel(String actionCommand) {
		OverviewBasePanel panelToActivate = overviewPanelMap.get(actionCommand);
		if (panelToActivate == null) {
			log.info("Creating overview panel {}", actionCommand);
			panelToActivate = createOverviewPanel(actionCommand);
			overviewPanelMap.put(actionCommand, panelToActivate);
		}
		return panelToActivate;
	}

	static OverviewBasePanel findPanel(String actionCommand) {
		return overviewPanelMap.get(actionCommand);
	}

	private static OverviewBasePanel createOverviewPanel(String actionCommand) {
		return switch (PageContext.valueOf(actionCommand)) {
		case ACCOUNTS_TRANSACTIONS -> {
			AccountsTransactionsOverviewPanel panel = new AccountsTransactionsOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case ACCOUNTS_MONEYTRANSFERS -> {
			MoneyTransferOverviewPanel panel = new MoneyTransferOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case OPEN_ACTIONS -> {
			OpenActionsOverviewPanel panel = new OpenActionsOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case BANKACCESS -> {
			BankAccessOverviewPanel panel = new BankAccessOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case INSTITUTES -> {
			InstituteOverviewPanel panel = new InstituteOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case CATEGORIES -> {
			CategoryOverviewPanel panel = new CategoryOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case RECIPIENTS -> {
			RecipientOverviewPanel panel = new RecipientOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case ALL_ACCOUNTS -> {
			AllAccountsOverviewPanel panel = new AllAccountsOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case ALL_TRANSACTIONS -> {
			AllTransactionsOverviewPanel panel = new AllTransactionsOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case ANALYSIS -> {
			AnalysisOverviewPanel panel = new AnalysisOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		case CATEGORY_ANALYSIS -> {
			CategoryAnalysisOverviewPanel panel = new CategoryAnalysisOverviewPanel();
			panel.createOverallPanel(true);
			yield panel;
		}
		};
	}

	static void clear() {
		overviewPanelMap.clear();
	}

}
