package de.zft2.gbanking.gui.panel.bankaccess;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessagesBean;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.action.PinAskDialog;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.service.bankaccess.BankMessageRetrievalResult;
import de.zft2.gbanking.service.bankaccess.BankMessageService;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.concurrent.Task;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import javafx.stage.Window;

public class BankMessagePanel extends DetailListPane implements BaseMessagesBean {

	private static final Logger log = LogManager.getLogger(BankMessagePanel.class);

	private final BankMessageDetailPanel detailPanel;
	private final BankMessageListPanel listPanel;
	private final BankingCapabilityService bankingCapabilityService;
	private final BankMessageService bankMessageService;
	private final TextArea messageText = FormStyleUtils.prepareLargeTextArea(new TextArea(), 12);

	private BankAccess currentBankAccess;

	public BankMessagePanel() {
		this(ServiceRegistry.getService(BankMessageService.class), ServiceRegistry.getService(BankingCapabilityService.class));
	}

	BankMessagePanel(BankMessageService bankMessageService, BankingCapabilityService bankingCapabilityService) {
		this.bankMessageService = bankMessageService;
		this.bankingCapabilityService = bankingCapabilityService;
		detailPanel = new BankMessageDetailPanel(() -> retrieveMessages());
		listPanel = new BankMessageListPanel(message -> handleMessageSelection(message));
		createPanel();
	}

	private void createPanel() {
		messageText.setEditable(false);
		FormStyleUtils.setReadOnlyStyle(true, messageText);
		TitledPane messagePane = new TitledPane(getText("UI_PANEL_BANK_MESSAGE_TEXT"), messageText);
		messagePane.setCollapsible(false);
		messagePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		FormStyleUtils.styleTitledPane(messagePane);

		MasterContentPane messageContent = new MasterContentPane(listPanel, messagePane, "bankMessages.content", 0.55d);
		setDetailAndList(detailPanel, messageContent);
	}

	public void updateBankAccess(BankAccess bankAccess) {
		currentBankAccess = bankAccess;
		boolean supported = bankAccess != null && bankingCapabilityService.supportsBankMessages(bankAccess);
		detailPanel.updateBankAccess(bankAccess, supported);
		reloadMessages();
	}

	public void reloadMessages() {
		List<BankMessage> messages = currentBankAccess != null ? bankMessageService.listBankMessages(currentBankAccess) : List.of();
		listPanel.updateModelMessages(messages);
		detailPanel.updateMessage(null);
		messageText.clear();
	}

	private void handleMessageSelection(BankMessage bankMessage) {
		detailPanel.updateMessage(bankMessage);
		messageText.setText(formatMessageText(bankMessage));
	}

	private String formatMessageText(BankMessage bankMessage) {
		if (bankMessage == null) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		appendSection(builder, bankMessage.getMessage());
		appendSection(builder, bankMessage.getComments());
		if (builder.isEmpty()) {
			appendSection(builder, bankMessage.getDescription());
		}
		return builder.toString();
	}

	private void appendSection(StringBuilder builder, String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		if (!builder.isEmpty()) {
			builder.append(System.lineSeparator()).append(System.lineSeparator());
		}
		builder.append(text.trim());
	}

	private void retrieveMessages() {
		if (currentBankAccess == null) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_BANK_ACCESS_NO_SELECTION"));
			return;
		}
		if (!bankingCapabilityService.supportsBankMessages(currentBankAccess)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING,
					getText("ALERT_BANK_MESSAGES_UNSUPPORTED", currentBankAccess.getBankName()));
			return;
		}

		char[] pin = requestPin();
		if (pin == null || pin.length == 0) {
			return;
		}

		startRetrievalTask(currentBankAccess, pin);
	}

	private char[] requestPin() {
		PinAskDialog pinWindow = new PinAskDialog(getOwnerWindow());
		pinWindow.setBankInfo(currentBankAccess.getFints().getBlz(), currentBankAccess.getBankName());
		Stage pinDialog = pinWindow.createNewPinAskDialog();
		pinDialog.showAndWait();
		return pinWindow.getPin();
	}

	private void startRetrievalTask(BankAccess bankAccess, char[] pin) {
		detailPanel.setRetrievalRunning(true);
		Task<BankMessageRetrievalResult> task = new Task<>() {
			@Override
			protected BankMessageRetrievalResult call() {
				return bankMessageService.retrieveBankMessagesWithResult(bankAccess, pin);
			}
		};
		task.setOnSucceeded(event -> handleRetrievalSuccess(task.getValue()));
		task.setOnFailed(event -> handleRetrievalFailure(task.getException(), pin));
		task.setOnCancelled(event -> {
			Arrays.fill(pin, '\0');
			detailPanel.setRetrievalRunning(false);
		});

		BackgroundActionCoordinator.getInstance().start(task, "gbanking-hbci-bank-messages");
	}

	private void handleRetrievalSuccess(BankMessageRetrievalResult result) {
		detailPanel.setRetrievalRunning(false);
		reloadMessages();
		if (result == null || result.wrongPin()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_BANK_MESSAGES_WRONG_PIN"));
			return;
		}
		if (!result.successful()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_BANK_MESSAGES_RETRIEVAL_FAILED"));
			return;
		}
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.INFORMATION,
				getText("UI_INFO_BANK_MESSAGES_RETRIEVED", Integer.toString(result.messages().size())));
	}

	private void handleRetrievalFailure(Throwable exception, char[] pin) {
		Arrays.fill(pin, '\0');
		detailPanel.setRetrievalRunning(false);
		log.error("Error retrieving bank messages", exception);
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_BANK_MESSAGES_RETRIEVAL_FAILED"));
	}

	private Window getOwnerWindow() {
		return getScene() != null ? getScene().getWindow() : null;
	}
}
