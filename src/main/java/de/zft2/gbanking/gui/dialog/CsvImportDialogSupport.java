package de.zft2.gbanking.gui.dialog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Analysis;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Match;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Problem;
import de.zft2.gbanking.messages.Messages;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Window;

public final class CsvImportDialogSupport {

	private CsvImportDialogSupport() {
	}

	public static Optional<Selection> prepare(Window owner, Path importFile, BankAccount contextAccount, BankAccount suggestedAccount)
			throws IOException {
		Analysis analysis = new CsvImportAnalyzer().analyze(importFile);
		if (analysis.problem() != null) {
			showProblem(owner, analysis);
			return Optional.empty();
		}

		Optional<Match> selectedMatch = selectMatch(owner, analysis.matches());
		if (selectedMatch.isEmpty() || !confirmHeaderDifferences(owner, selectedMatch.get())) {
			return Optional.empty();
		}

		BankAccount targetAccount = contextAccount;
		if (targetAccount == null && !selectedMatch.get().definition().hasAccountIdentifier(selectedMatch.get().actualHeaders())) {
			Optional<BankAccount> selectedAccount = selectAccount(owner, suggestedAccount, selectedMatch.get().definition().getName());
			if (selectedAccount.isEmpty()) {
				return Optional.empty();
			}
			targetAccount = selectedAccount.get();
		}
		return Optional.of(new Selection(selectedMatch.get().definition().getName(), targetAccount));
	}

	private static void showProblem(Window owner, Analysis analysis) {
		String message;
		if (analysis.problem() == Problem.MISSING_REQUIRED_FIELDS) {
			message = missingFieldsMessage(analysis.matches());
		} else {
			message = text("ERROR_CSV_IMPORT_UNKNOWN_DEFINITION", analysis.definitionFile().toString());
		}
		DialogWindowSupport.showAlert(owner, Alert.AlertType.ERROR, text("UI_CSV_IMPORT_TITLE"), null, message);
	}

	private static String missingFieldsMessage(List<Match> matches) {
		if (matches.size() == 1) {
			Match match = matches.get(0);
			return text("ERROR_CSV_IMPORT_MISSING_REQUIRED_FIELDS", match.definition().getName(), join(match.missingRequiredHeaders()));
		}
		String details = matches.stream().map(match -> match.definition().getName() + ": " + join(match.missingRequiredHeaders()))
				.collect(java.util.stream.Collectors.joining(System.lineSeparator()));
		return text("ERROR_CSV_IMPORT_MISSING_REQUIRED_FIELDS_MULTIPLE", details);
	}

	private static Optional<Match> selectMatch(Window owner, List<Match> matches) {
		if (matches.size() == 1) {
			return Optional.of(matches.get(0));
		}
		ChoiceDialog<Match> dialog = new ChoiceDialog<>(matches.get(0), matches);
		dialog.initOwner(owner);
		dialog.setTitle(text("UI_CSV_IMPORT_TITLE"));
		dialog.setHeaderText(text("UI_CSV_IMPORT_FILTER_SELECT_HEADER"));
		dialog.setContentText(text("UI_CSV_IMPORT_FILTER_SELECT_TEXT"));
		return dialog.showAndWait();
	}

	private static boolean confirmHeaderDifferences(Window owner, Match match) {
		List<String> warnings = new ArrayList<>();
		if (!match.unknownHeaders().isEmpty()) {
			warnings.add(text("WARNING_CSV_IMPORT_UNKNOWN_FIELDS", join(match.unknownHeaders())));
		}
		if (!match.missingOptionalHeaders().isEmpty()) {
			warnings.add(text("WARNING_CSV_IMPORT_MISSING_OPTIONAL_FIELDS", join(match.missingOptionalHeaders())));
		}
		if (warnings.isEmpty()) {
			return true;
		}
		ButtonType continueButton = new ButtonType(text("UI_BUTTON_CONTINUE"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButton = new ButtonType(text("UI_BUTTON_CANCEL"), ButtonBar.ButtonData.CANCEL_CLOSE);
		return DialogWindowSupport.showConfirmation(owner, Alert.AlertType.WARNING, text("UI_CSV_IMPORT_TITLE"),
				text("WARNING_CSV_IMPORT_HEADER", match.definition().getName()), String.join(System.lineSeparator(), warnings), continueButton,
				cancelButton);
	}

	private static Optional<BankAccount> selectAccount(Window owner, BankAccount suggestedAccount, String definitionName) {
		List<BankAccount> accounts = DBController.getInstance(".").getAll(BankAccount.class).stream()
				.sorted(Comparator.comparing(BankAccount::getAccountName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
		if (accounts.isEmpty()) {
			DialogWindowSupport.showAlert(owner, Alert.AlertType.ERROR, text("ERROR_CSV_IMPORT_NO_TARGET_ACCOUNT"));
			return Optional.empty();
		}
		BankAccount defaultAccount = findSuggestedAccount(accounts, suggestedAccount);
		ChoiceDialog<BankAccount> dialog = new ChoiceDialog<>(defaultAccount, accounts);
		dialog.initOwner(owner);
		dialog.setTitle(text("UI_CSV_IMPORT_TITLE"));
		dialog.setHeaderText(text("UI_CSV_IMPORT_ACCOUNT_SELECT_HEADER", definitionName));
		dialog.setContentText(text("UI_CSV_IMPORT_ACCOUNT_SELECT_TEXT"));
		return dialog.showAndWait();
	}

	private static BankAccount findSuggestedAccount(List<BankAccount> accounts, BankAccount suggestedAccount) {
		if (suggestedAccount != null) {
			for (BankAccount account : accounts) {
				if (account.getId() == suggestedAccount.getId()) {
					return account;
				}
			}
		}
		return accounts.get(0);
	}

	private static String join(Set<String> headers) {
		return String.join(", ", headers.stream().sorted().toList());
	}

	private static String text(String key, Object... values) {
		return Messages.getInstance().getFormattedMessage(key, values);
	}

	public record Selection(String definitionName, BankAccount account) {
	}
}
