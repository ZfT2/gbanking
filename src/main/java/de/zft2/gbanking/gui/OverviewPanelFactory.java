package de.zft2.gbanking.gui;

import java.util.EnumMap;
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

final class OverviewPanelFactory {

	private static final Logger log = LogManager.getLogger(OverviewPanelFactory.class);

	private static final Map<PageContext, OverviewBasePanel> overviewPanelMap = new EnumMap<>(PageContext.class);

	private OverviewPanelFactory() {
		/* This utility class should not be instantiated */
	}

	static OverviewBasePanel retrievePanel(String actionCommand) {
		PageContext pageContext = PageContext.valueOf(actionCommand);
		return overviewPanelMap.computeIfAbsent(pageContext, context -> createOverviewPanel(context));
	}

	static OverviewBasePanel findPanel(String actionCommand) {
		return overviewPanelMap.get(PageContext.valueOf(actionCommand));
	}

	private static OverviewBasePanel createOverviewPanel(PageContext pageContext) {
		log.info("Creating overview panel {}", pageContext);
		return switch (pageContext) {
		case ACCOUNTS_TRANSACTIONS -> new AccountsTransactionsOverviewPanel();
		case ACCOUNTS_MONEYTRANSFERS -> new MoneyTransferOverviewPanel();
		case OPEN_ACTIONS -> new OpenActionsOverviewPanel();
		case BANKACCESS -> new BankAccessOverviewPanel();
		case INSTITUTES -> new InstituteOverviewPanel();
		case CATEGORIES -> new CategoryOverviewPanel();
		case RECIPIENTS -> new RecipientOverviewPanel();
		case ALL_ACCOUNTS -> new AllAccountsOverviewPanel();
		case ALL_TRANSACTIONS -> new AllTransactionsOverviewPanel();
		case ANALYSIS -> AnalysisOverviewPanel.create();
		case CATEGORY_ANALYSIS -> new CategoryAnalysisOverviewPanel();
		};
	}

	static void clear() {
		overviewPanelMap.clear();
	}

}
