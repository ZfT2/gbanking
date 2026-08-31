package de.zft2.gbanking.gui.panel.moneytransfer;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.EnvironmentOptions;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.MoneyTransferImportStatusDialog;
import de.zft2.gbanking.gui.dialog.MoneyTransferProtocolDialog;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.FileType;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.action.PinAskDialog;
import de.zft2.gbanking.gui.progress.MoneyTransferImportProgressBarPanel;
import de.zft2.gbanking.gui.util.FileChooserDirectorySupport;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.moneytransfer.MoneyTransferService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MoneyTransferListPanel extends AbstractFilterableTablePanel<MoneyTransfer> {

	private static final Logger log = LogManager.getLogger(MoneyTransferListPanel.class);
	private static final double RECIPIENT_COLUMN_MIN_WIDTH = 120;
	private static final double RECIPIENT_COLUMN_PREF_WIDTH = 220;
	private static final double PURPOSE_COLUMN_MIN_WIDTH = 140;
	private static final double PURPOSE_COLUMN_PREF_WIDTH = 420;
	private static final double AMOUNT_COLUMN_MIN_WIDTH = 110;
	private static final double AMOUNT_COLUMN_PREF_WIDTH = 120;
	private static final double INSTANT_STATUS_COLUMN_MIN_WIDTH = 180;
	private static final double INSTANT_STATUS_COLUMN_PREF_WIDTH = 230;

	private final MoneyTransferDetailListTabPanel parentPanel;
	private final OrderType orderType;
	private final boolean archive;

	public MoneyTransferListPanel(OrderType orderType, MoneyTransferDetailListTabPanel parent, boolean archive) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parent;
		this.orderType = orderType;
		this.archive = archive;
		createInnerMoneyTransfersListPanel();
	}

	private void createInnerMoneyTransfersListPanel() {
		setPanelTitle("");
		setColumns(createColumns());
		configureTableLayout("moneyTransfers." + orderType.name() + "." + (archive ? "archive" : "active"));
		configureContextMenu();
		tableView.setFixedCellSize(60);
		onSelection(this::handleSelection);
		reload();
	}

	private List<TableColumn<MoneyTransfer, ?>> createColumns() {
		TableColumn<MoneyTransfer, Boolean> selectedCol = createSelectAllSelectionColumn(
				transfer -> transfer.isSelected(), (transfer, selected) -> transfer.setSelected(selected));
		TableColumn<MoneyTransfer, java.time.LocalDate> dateCol = TableColumnFactory.createCalendarDateColumn(getText("UI_TABLE_DATE"),
				MoneyTransfer::getExecutionDate, 95);
		TableColumn<MoneyTransfer, String> recipientCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_RECIPIENT"),
				transfer -> transfer.getRecipient() != null ? transfer.getRecipient().getName() : "", RECIPIENT_COLUMN_MIN_WIDTH,
				RECIPIENT_COLUMN_PREF_WIDTH);
		TableColumn<MoneyTransfer, String> purposeCol = TableColumnFactory.createWrappedTextColumn(getText("UI_TABLE_PURPOSE"), MoneyTransfer::getPurpose,
				PURPOSE_COLUMN_MIN_WIDTH, PURPOSE_COLUMN_PREF_WIDTH);
		TableColumn<MoneyTransfer, java.math.BigDecimal> amountCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_AMOUNT"),
				MoneyTransfer::getAmount);
		FxTableUtils.setPreferredWidth(amountCol, AMOUNT_COLUMN_MIN_WIDTH, AMOUNT_COLUMN_PREF_WIDTH);
		TableColumn<MoneyTransfer, String> ibanCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"),
				transfer -> transfer.getRecipient() != null ? transfer.getRecipient().getIban() : "", 220, 240);
		TableColumn<MoneyTransfer, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"),
				transfer -> transfer.getRecipient() != null ? transfer.getRecipient().getBank() : "", 150, 180);
		TableColumn<MoneyTransfer, String> statusCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_STATUS"),
				transfer -> transfer.getMoneytransferStatus() != null ? transfer.getMoneytransferStatus().toString() : "", 120);
		TableColumn<MoneyTransfer, String> instantStatusCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_INSTANT_PAYMENT_STATUS"),
				transfer -> transfer.getSepaOrderStatus() != null ? transfer.getSepaOrderStatus().toString() : "", INSTANT_STATUS_COLUMN_MIN_WIDTH,
				INSTANT_STATUS_COLUMN_PREF_WIDTH);

		return List.of(selectedCol, dateCol, recipientCol, purposeCol, amountCol, ibanCol, bankCol, statusCol, instantStatusCol);
	}

	private void configureContextMenu() {
		ContextMenu contextMenu = createContextMenu();
		installRowContextMenu(contextMenu, this::handleSelection);
		if (archive && orderType == OrderType.TRANSFER) {
			tableView.setContextMenu(contextMenu);
		}
	}

	private void handleSelection(MoneyTransfer selected) {
		Recipient recipient = dbController.getByIdFull(Recipient.class, selected.getRecipientId());
		selected.setRecipient(recipient);
		parentPanel.getMoneyTransferInputPanel().updatePanelFieldValues(selected);
	}

	private ContextMenu createContextMenu() {
		MenuItem showProtocolItem = new MenuItem(getText("UI_MENU_MONEYTRANSFER_PROTOCOL_SHOW"));
		showProtocolItem.setOnAction(event -> showSelectedProtocol());
		MenuItem retrieveInstantStatusItem = new MenuItem(getText("UI_MENU_MONEYTRANSFER_INSTANT_STATUS_RETRIEVE"));
		retrieveInstantStatusItem.setOnAction(event -> retrieveSelectedInstantPaymentStatuses());

		ContextMenu contextMenu = new ContextMenu(showProtocolItem, retrieveInstantStatusItem);
		MenuItem importCsvItem = createImportCsvItem();
		if (importCsvItem != null) {
			contextMenu.getItems().add(importCsvItem);
		}

		contextMenu.setOnShowing(event -> {
			showProtocolItem.setDisable(getSelectedMoneyTransfer() == null);
			retrieveInstantStatusItem.setDisable(!canRetrieveInstantPaymentStatus(getStatusQuerySelection()));
			if (importCsvItem != null) {
				importCsvItem.setDisable(parentPanel.getSelectedAccount() == null);
			}
		});
		return contextMenu;
	}

	private void retrieveSelectedInstantPaymentStatuses() {
		List<MoneyTransfer> selectedTransfers = getStatusQuerySelection();
		if (!canRetrieveInstantPaymentStatus(selectedTransfers)) {
			return;
		}
		if (selectedTransfers.stream().anyMatch(transfer -> transfer.getBankOrderId() == null || transfer.getBankOrderId().isBlank())) {
			showWarning(getText("ALERT_MONEYTRANSFER_INSTANT_STATUS_REFERENCE_MISSING"));
			return;
		}

		BankAccount selectedAccount = parentPanel.getSelectedAccount();
		if (selectedAccount == null || selectedAccount.getBankAccessId() == null) {
			showWarning(getText("ALERT_MONEYTRANSFER_INSTANT_STATUS_BANK_ACCESS_MISSING"));
			return;
		}

		char[] pin = requestPin(selectedAccount);
		if (pin == null || pin.length == 0) {
			return;
		}
		startInstantPaymentStatusTask(selectedTransfers, selectedAccount, pin);
	}

	private char[] requestPin(BankAccount account) {
		PinAskDialog pinWindow = new PinAskDialog(getTableWindow());
		pinWindow.setBankInfo(account.getBlz(), account.getBankName());
		Stage pinDialog = pinWindow.createNewPinAskDialog();
		pinDialog.showAndWait();
		return pinWindow.getPin();
	}

	private void startInstantPaymentStatusTask(List<MoneyTransfer> selectedTransfers, BankAccount account, char[] pin) {
		Task<Integer> task = new Task<>() {
			@Override
			protected Integer call() {
				return ServiceRegistry.getService(MoneyTransferService.class).retrieveInstantPaymentStatuses(selectedTransfers, account, pin);
			}
		};
		task.setOnSucceeded(event -> handleInstantPaymentStatusResult(task.getValue(), selectedTransfers.size()));
		task.setOnFailed(event -> {
			Arrays.fill(pin, '\0');
			log.error("Instant payment status retrieval failed", task.getException());
			showWarning(getText("ALERT_MONEYTRANSFER_INSTANT_STATUS_RETRIEVAL_FAILED"));
		});
		task.setOnCancelled(event -> Arrays.fill(pin, '\0'));
		if (!BackgroundActionCoordinator.getInstance().start(task, "gbanking-hbci-instant-payment-status")) {
			Arrays.fill(pin, '\0');
		}
	}

	private void handleInstantPaymentStatusResult(int successfulRequests, int requestedTransfers) {
		parentPanel.reloadListPanels();
		Alert.AlertType alertType = successfulRequests == requestedTransfers ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING;
		DialogWindowSupport.showAlert(getTableWindow(), alertType, getText("UI_INFO_MONEYTRANSFER_INSTANT_STATUS_RESULT",
				Integer.toString(successfulRequests), Integer.toString(requestedTransfers)));
	}

	private List<MoneyTransfer> getStatusQuerySelection() {
		List<MoneyTransfer> checkedTransfers = tableView.getItems().stream().filter(transfer -> transfer.isSelected()).toList();
		MoneyTransfer selectedTransfer = getSelectedMoneyTransfer();
		return checkedTransfers.isEmpty() && selectedTransfer != null ? List.of(selectedTransfer) : checkedTransfers;
	}

	static boolean canRetrieveInstantPaymentStatus(List<MoneyTransfer> moneyTransfers) {
		return moneyTransfers != null && !moneyTransfers.isEmpty()
				&& moneyTransfers.stream().allMatch(transfer -> transfer != null && transfer.getOrderType() == OrderType.REALTIME_TRANSFER);
	}

	private MenuItem createImportCsvItem() {
		if (!archive || orderType != OrderType.TRANSFER) {
			return null;
		}

		MenuItem importCsvItem = new MenuItem(getText("UI_MENU_MONEYTRANSFER_IMPORT_CSV"));
		importCsvItem.setOnAction(event -> importCsvForSelectedAccount());
		return importCsvItem;
	}

	private void importCsvForSelectedAccount() {
		BankAccount selectedAccount = parentPanel.getSelectedAccount();
		if (selectedAccount == null) {
			showWarning(getText("ERROR_MONEYTRANSFER_IMPORT_NO_ACCOUNT_SELECTED"));
			return;
		}

		Path importFile = chooseCsvImportFile();
		if (importFile == null) {
			return;
		}
		var importStatus = MoneyTransferImportStatusDialog.show(getTableWindow());
		if (importStatus.isEmpty()) {
			return;
		}

		try {
			MoneyTransferImportProgressBarPanel progressPanel = new MoneyTransferImportProgressBarPanel(getTableWindow(), selectedAccount,
					parentPanel::reloadListPanels, ExportType.MONEYTRANSFERS_CSV, importStatus.get());
			var progressWindow = progressPanel.createNewFileImportProgressBarWindow();
			progressPanel.startTask(importFile.toString(), ExportType.MONEYTRANSFERS_CSV, null);
			progressWindow.show();
		} catch (Exception e) {
			log.error("Money transfer CSV import failed", e);
			showWarning(e.getMessage());
		}
	}

	private Path chooseCsvImportFile() {
		FileChooser fileChooser = new FileChooser();
		FileChooserDirectorySupport.configure(fileChooser, EnvironmentOptions.DEFAULT_DIR_IMPORT);
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(FileType.CSV.getDescription(), "*" + FileType.CSV.getSuffix()));
		File selectedFile = fileChooser.showOpenDialog(getTableWindow());
		return FileChooserDirectorySupport.remember(selectedFile, EnvironmentOptions.DEFAULT_DIR_IMPORT);
	}

	private void showWarning(String text) {
		DialogWindowSupport.showAlert(getTableWindow(), Alert.AlertType.WARNING, text);
	}

	private void showSelectedProtocol() {
		MoneyTransfer selected = getSelectedMoneyTransfer();
		if (selected == null) {
			return;
		}

		List<MoneyTransferProtocol> protocols = dbController.getAllByParent(MoneyTransferProtocol.class, selected.getId());
		new MoneyTransferProtocolDialog().show(getTableWindow(), selected, protocols);
	}

	@Override
	protected boolean matchesFilter(MoneyTransfer transfer, String filter) {
		String recipientName = transfer.getRecipient() != null ? transfer.getRecipient().getName() : "";
		String iban = transfer.getRecipient() != null ? transfer.getRecipient().getIban() : "";
		String bank = transfer.getRecipient() != null ? transfer.getRecipient().getBank() : "";
		String status = transfer.getMoneytransferStatus() != null ? transfer.getMoneytransferStatus().toString() : "";
		String instantStatus = transfer.getSepaOrderStatus() != null ? transfer.getSepaOrderStatus().toString() : "";

		return matchesAny(filter, recipientName, transfer.getPurpose(), iban, bank, status, instantStatus);
	}

	public void updateModelMoneytransfer(List<MoneyTransfer> orderList) {
		replaceItems(filterByOrderTypeAndArchive(orderList));
	}

	public void updatePanelBorder(String borderTitle) {
		setPanelTitle(borderTitle);
	}

	public void reload() {
		if (parentPanel.getSelectedAccount() == null) {
			replaceItems(List.of());
			return;
		}
		updateModelMoneytransfer(dbController.getAllByParent(MoneyTransfer.class, parentPanel.getSelectedAccount().getId()));
	}

	public void refresh() {
		reload();
	}

	private List<MoneyTransfer> filterByOrderTypeAndArchive(List<MoneyTransfer> orderList) {
		OrderType effectiveOrderType = orderType == OrderType.TRANSFER ? parentPanel.getMoneyTransferInputPanel().getOrderType() : orderType;
		Set<Integer> pendingChangePredecessorIds = orderList.stream()
				.filter(transfer -> transfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED)
				.map(transfer -> transfer.getHistoryorderId()).filter(historyOrderId -> historyOrderId != null).collect(Collectors.toSet());
		return orderList.stream().filter(transfer -> transfer.getOrderType() == effectiveOrderType)
				.filter(transfer -> !pendingChangePredecessorIds.contains(transfer.getId()))
				.filter(transfer -> transfer.getMoneytransferStatus().isArchiveStatus() == archive).toList();
	}

	private MoneyTransfer getSelectedMoneyTransfer() {
		return getSelectedItem();
	}
}
