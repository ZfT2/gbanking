package de.zft2.gbanking.hbci;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV.HBCIJobImpl;
import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;

final class HbciStatusDescriptionProvider implements BaseMessages {
	private static final String UI_SELECTED_ACCOUNT = "UI_DIALOG_HBCI_JOB_SELECTED_ACCOUNT";

	private final Map<HBCIJob<?>, String> registeredJobDescriptions = new IdentityHashMap<>();

	void registerJobDescription(HBCIJob<?> job, String description) {
		if (job != null && description != null && !description.isBlank()) {
			registeredJobDescriptions.put(job, description.trim());
		}
	}

	String describeStatus(int statusTag, Object[] statusPayload) {
		return switch (statusTag) {
		case HBCICallback.STATUS_SEND_TASK -> describeJob(findJob(statusPayload));
		case HBCICallback.STATUS_INST_BPD_INIT -> getText("UI_DIALOG_HBCI_STATUS_BANK_PARAMETERS");
		case HBCICallback.STATUS_INST_GET_KEYS -> getText("UI_DIALOG_HBCI_STATUS_BANK_KEYS");
		case HBCICallback.STATUS_SEND_KEYS -> getText("UI_DIALOG_HBCI_STATUS_SEND_KEYS");
		case HBCICallback.STATUS_INIT_SYSID -> getText("UI_DIALOG_HBCI_STATUS_SYSTEM_ID");
		case HBCICallback.STATUS_INIT_UPD -> getText("UI_DIALOG_HBCI_STATUS_USER_DATA");
		case HBCICallback.STATUS_LOCK_KEYS -> getText("UI_DIALOG_HBCI_STATUS_LOCK_KEYS");
		case HBCICallback.STATUS_INIT_SIGID -> getText("UI_DIALOG_HBCI_STATUS_SIGNATURE_ID");
		case HBCICallback.STATUS_DIALOG_INIT -> getText("UI_DIALOG_HBCI_STATUS_DIALOG_INIT");
		case HBCICallback.STATUS_DIALOG_END -> getText("UI_DIALOG_HBCI_STATUS_DIALOG_END");
		default -> null;
		};
	}

	String describeException(Throwable throwable) {
		String causeMessage = findMostSpecificMessage(throwable);
		return causeMessage != null ? formatError(causeMessage) : getText("UI_DIALOG_HBCI_STATUS_ERROR_WITHOUT_DETAILS");
	}

	String describeFailure(String failureMessage) {
		if (failureMessage == null || failureMessage.isBlank()) {
			return null;
		}
		List<String> messageLines = HbciStatusMessageExtractor.extractMessageLines(failureMessage);
		String readableMessage = messageLines.isEmpty() ? failureMessage.trim() : messageLines.get(messageLines.size() - 1);
		return formatError(readableMessage);
	}

	String getAccountDescription(BankAccount bankAccount) {
		if (bankAccount == null) {
			return getText(UI_SELECTED_ACCOUNT);
		}
		return firstText(bankAccount.getAccountName(), bankAccount.getIban(), bankAccount.getNumber(),
				getText(UI_SELECTED_ACCOUNT));
	}

	private HBCIJob<?> findJob(Object[] statusPayload) {
		if (statusPayload == null) {
			return null;
		}
		for (Object payloadEntry : statusPayload) {
			if (payloadEntry instanceof HBCIJob<?> job) {
				return job;
			}
		}
		return null;
	}

	private String describeJob(HBCIJob<?> job) {
		if (job == null) {
			return getText("UI_DIALOG_HBCI_JOB_GENERIC");
		}
		String registeredDescription = registeredJobDescriptions.get(job);
		if (registeredDescription != null) {
			return registeredDescription;
		}

		String account = resolveAccount(job);
		String jobName = normalizeJobName(job.getName());
		return switch (jobName) {
		case "SaldoReq" -> getText("UI_DIALOG_HBCI_JOB_BALANCE", account);
		case "KUmsAllCamt", "KUmsAll", "KUmsZeitCamt", "KUmsZeit" -> getText("UI_DIALOG_HBCI_JOB_TRANSACTIONS", account);
		case "Vormerkposten" -> getText("UI_DIALOG_HBCI_JOB_PENDING_TRANSACTIONS", account);
		case "Kontoauszug", "KontoauszugPdf" -> getText("UI_DIALOG_HBCI_JOB_STATEMENT_GENERIC", account);
		case "KontoauszugUebersicht" -> getText("UI_DIALOG_HBCI_JOB_STATEMENT_OVERVIEW");
		case "Receipt" -> getText("UI_DIALOG_HBCI_JOB_STATEMENT_RECEIPT_GENERIC");
		case "InfoList" -> getText("UI_DIALOG_HBCI_JOB_BANK_MESSAGE_LIST");
		case "InfoDetails" -> getText("UI_DIALOG_HBCI_JOB_BANK_MESSAGE_DETAILS", "1", "1");
		case "DauerSEPAList" -> getText("UI_DIALOG_HBCI_JOB_STANDING_ORDERS", account);
		case "TermUebSEPAList" -> getText("UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFERS", account);
		case "UebSEPA" -> getText("UI_DIALOG_HBCI_JOB_TRANSFER", account);
		case "InstUebSEPA" -> getText("UI_DIALOG_HBCI_JOB_REALTIME_TRANSFER", account);
		case "UebEil" -> getText("UI_DIALOG_HBCI_JOB_URGENT_TRANSFER", account);
		case "TermUebSEPA" -> getText("UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFER", account);
		case "DauerSEPANew" -> getText("UI_DIALOG_HBCI_JOB_STANDING_ORDER", account);
		case "TermUebSEPAEdit" -> getText("UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFER_EDIT", account);
		case "TermUebSEPADel" -> getText("UI_DIALOG_HBCI_JOB_SCHEDULED_TRANSFER_DELETE", account);
		case "DauerSEPAEdit" -> getText("UI_DIALOG_HBCI_JOB_STANDING_ORDER_EDIT", account);
		case "DauerSEPADel" -> getText("UI_DIALOG_HBCI_JOB_STANDING_ORDER_DELETE", account);
		case "UebForeign" -> getText("UI_DIALOG_HBCI_JOB_FOREIGN_TRANSFER", account);
		case "TAN2Step" -> getText("UI_DIALOG_HBCI_JOB_AUTHORIZE");
		case "VoP" -> getText("UI_DIALOG_HBCI_JOB_RECIPIENT_CHECK");
		default -> getText("UI_DIALOG_HBCI_JOB_NAMED", job.getName());
		};
	}

	private String resolveAccount(HBCIJob<?> job) {
		Konto account = job instanceof HBCIJobImpl<?> jobImplementation ? jobImplementation.getOrderAccount() : null;
		if (account == null) {
			return getText(UI_SELECTED_ACCOUNT);
		}
		return firstText(account.name, account.iban, account.number, getText(UI_SELECTED_ACCOUNT));
	}

	private String normalizeJobName(String jobName) {
		if (jobName == null) {
			return "";
		}
		int endIndex = jobName.length();
		while (endIndex > 0 && Character.isDigit(jobName.charAt(endIndex - 1))) {
			endIndex--;
		}
		return jobName.substring(0, endIndex);
	}

	private String findMostSpecificMessage(Throwable throwable) {
		String mostSpecificMessage = null;
		Throwable current = throwable;
		while (current != null) {
			if (current.getMessage() != null && !current.getMessage().isBlank()) {
				mostSpecificMessage = current.getMessage().trim();
			}
			Throwable cause = current.getCause();
			current = cause != current ? cause : null;
		}
		return mostSpecificMessage;
	}

	private String formatError(String message) {
		return getText("UI_DIALOG_HBCI_STATUS_ERROR", message);
	}

	private String firstText(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return "";
	}
}
