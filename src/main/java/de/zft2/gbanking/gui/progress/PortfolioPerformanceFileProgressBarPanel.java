package de.zft2.gbanking.gui.progress;

import de.zft2.gbanking.file.PortfolioPerformanceFileTask;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.ImportResult;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlImportAssignments;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import javafx.scene.control.Label;
import javafx.stage.Window;

public class PortfolioPerformanceFileProgressBarPanel extends BaseFileProgressBarPanel {

	private final PortfolioSummary portfolio;
	private final boolean importOperation;
	private final boolean importAdditionalAccounts;
	private final XmlImportAssignments assignments;
	private final Runnable successCallback;

	public PortfolioPerformanceFileProgressBarPanel(Window parent, PortfolioSummary portfolio,
			boolean importOperation, Runnable successCallback) {
		this(parent, portfolio, importOperation, false, successCallback);
	}

	public PortfolioPerformanceFileProgressBarPanel(Window parent, PortfolioSummary portfolio,
			boolean importOperation, boolean importAdditionalAccounts, Runnable successCallback) {
		this(parent, portfolio, importOperation, importAdditionalAccounts, null, successCallback);
	}

	public PortfolioPerformanceFileProgressBarPanel(Window parent, PortfolioSummary portfolio,
			boolean importOperation, XmlImportAssignments assignments, Runnable successCallback) {
		this(parent, portfolio, importOperation, false, assignments, successCallback);
	}

	private PortfolioPerformanceFileProgressBarPanel(Window parent, PortfolioSummary portfolio,
			boolean importOperation, boolean importAdditionalAccounts, XmlImportAssignments assignments,
			Runnable successCallback) {
		super(parent);
		this.portfolio = portfolio;
		this.importOperation = importOperation;
		this.importAdditionalAccounts = importAdditionalAccounts;
		this.assignments = assignments;
		this.successCallback = successCallback;
	}

	@Override
	protected String getWindowTitle() {
		return getText(importOperation ? "UI_STOCK_PP_IMPORT_TITLE" : "UI_STOCK_PP_EXPORT_TITLE");
	}

	@Override
	protected boolean keepDialogOpenOnSuccess() {
		return true;
	}

	@Override
	protected void onTaskSucceeded() {
		if (successCallback != null) {
			successCallback.run();
		}
		if (importOperation && task instanceof PortfolioPerformanceFileTask fileTask) {
			addImportResult(fileTask.getImportResult());
		}
	}

	private void addImportResult(ImportResult result) {
		if (result == null) {
			return;
		}
		String key = result.alreadyImported() ? "UI_STOCK_PP_IMPORT_ALREADY_IMPORTED" : "UI_STOCK_PP_IMPORT_RESULT";
		String text = result.alreadyImported() ? getText(key)
				: getText(key, result.securities(), result.transactions(), result.bookings(), result.prices(),
						result.portfolios(), result.accounts());
		contentBox.getChildren().add(new Label(text));
	}

	@Override
	public void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
		task = assignments != null
				? new PortfolioPerformanceFileTask(fileName, exportType, portfolio, importOperation, assignments)
				: new PortfolioPerformanceFileTask(fileName, exportType, portfolio, importOperation,
						importAdditionalAccounts);
		super.startTask(accountListPanel);
	}
}
