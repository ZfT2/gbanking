package de.zft2.gbanking.file;

import java.io.IOException;
import java.nio.file.Path;

import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.PortfolioPerformanceExportService;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.ImportResult;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlImportAssignments;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;

public class PortfolioPerformanceFileTask extends BaseFileTask {

	private final PortfolioSummary portfolio;
	private final boolean importOperation;
	private final boolean importAdditionalAccounts;
	private final XmlImportAssignments assignments;
	private ImportResult importResult;

	public PortfolioPerformanceFileTask(String fileName, ExportType exportType, PortfolioSummary portfolio,
			boolean importOperation) {
		this(fileName, exportType, portfolio, importOperation, false);
	}

	public PortfolioPerformanceFileTask(String fileName, ExportType exportType, PortfolioSummary portfolio,
			boolean importOperation, boolean importAdditionalAccounts) {
		this(fileName, exportType, portfolio, importOperation, importAdditionalAccounts, null);
	}

	public PortfolioPerformanceFileTask(String fileName, ExportType exportType, PortfolioSummary portfolio,
			boolean importOperation, XmlImportAssignments assignments) {
		this(fileName, exportType, portfolio, importOperation, false, assignments);
	}

	private PortfolioPerformanceFileTask(String fileName, ExportType exportType, PortfolioSummary portfolio,
			boolean importOperation, boolean importAdditionalAccounts, XmlImportAssignments assignments) {
		super(fileName);
		this.exportType = exportType;
		this.portfolio = portfolio;
		this.importOperation = importOperation;
		this.importAdditionalAccounts = importAdditionalAccounts;
		this.assignments = assignments;
	}

	@Override
	protected Void call() throws IOException {
		setWorkerProgress(0);
		setProcessingState(importOperation ? "Portfolio-Performance-Datei wird importiert" : "Portfolio-Performance-Datei wird exportiert");
		if (importOperation) {
			PortfolioPerformanceImportService service = ServiceRegistry.getService(PortfolioPerformanceImportService.class);
			importResult = assignments != null
					? service.importFile(Path.of(fileName), exportType, portfolio, assignments)
					: service.importFile(Path.of(fileName), exportType, portfolio, importAdditionalAccounts);
		} else {
			ServiceRegistry.getService(PortfolioPerformanceExportService.class)
					.exportFile(Path.of(fileName), exportType, portfolio);
		}
		setWorkerProgress(100);
		return null;
	}

	public ImportResult getImportResult() {
		return importResult;
	}
}
