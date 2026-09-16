package de.zft2.gbanking.gui.dialog.stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlAccount;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlImportAssignments;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlImportPreview;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlPortfolio;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlTargetOption;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class PortfolioPerformanceImportMappingDialog implements BaseMessages {

	private final PortfolioPerformanceImportService service;

	public PortfolioPerformanceImportMappingDialog(PortfolioPerformanceImportService service) {
		this.service = service;
	}

	public Optional<XmlImportAssignments> show(Window owner, XmlImportPreview preview, PortfolioSummary selectedPortfolio,
			boolean includeAdditionalAccounts) {
		List<MappingRow> rows = createRows(preview, selectedPortfolio, includeAdditionalAccounts);
		Stage dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_PP_MAPPING_TITLE");
		TableView<MappingRow> table = createTable(rows);
		DialogResult result = new DialogResult();

		Button importButton = new Button(getText("UI_BUTTON_IMPORT"));
		importButton.setDefaultButton(true);
		importButton.setOnAction(event -> saveSelection(owner, dialog, rows, result));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		cancelButton.setCancelButton(true);
		cancelButton.setOnAction(event -> dialog.close());

		Label header = new Label(getText("UI_STOCK_PP_MAPPING_HEADER"));
		header.setWrapText(true);
		DialogWindowSupport.setVgrowAlways(table);
		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(header, table,
				DialogWindowSupport.createButtonBar(importButton, cancelButton)), 1050, 560));
		dialog.setMinWidth(800);
		dialog.setMinHeight(420);
		dialog.showAndWait();
		return Optional.ofNullable(result.assignments);
	}

	private List<MappingRow> createRows(XmlImportPreview preview, PortfolioSummary selectedPortfolio,
			boolean includeAdditionalAccounts) {
		List<XmlTargetOption> portfolioTargets = service.getPortfolioImportTargets();
		List<XmlTargetOption> accountTargets = service.getAccountImportTargets();
		List<MappingRow> rows = new ArrayList<>();
		for (XmlPortfolio portfolio : preview.portfolios()) {
			Integer preferredId = preview.portfolios().size() == 1 && selectedPortfolio != null
					? Integer.valueOf(selectedPortfolio.portfolioId()) : portfolio.suggestedTargetId();
			rows.add(createRow(MappingType.PORTFOLIO, portfolio.externalId(), portfolio.name(), portfolio.currency(),
					getText("UI_STOCK_PP_MAPPING_PORTFOLIO_DETAIL", portfolio.settlementAccountName()),
					portfolio.settlementAccountExternalId(), portfolioTargets, preferredId));
		}
		for (XmlAccount account : preview.accounts()) {
			if (account.additional() && !includeAdditionalAccounts) {
				continue;
			}
			Integer preferredId = preferredAccountId(account, preview, selectedPortfolio);
			MappingType type = account.additional() ? MappingType.ADDITIONAL_ACCOUNT : MappingType.SETTLEMENT_ACCOUNT;
			rows.add(createRow(type, account.externalId(), account.name(), account.currency(),
					getText("UI_STOCK_PP_MAPPING_TRANSACTION_COUNT", account.transactionCount()),
					null, accountTargets, preferredId));
		}
		return rows;
	}

	private static Integer preferredAccountId(XmlAccount account, XmlImportPreview preview,
			PortfolioSummary selectedPortfolio) {
		if (selectedPortfolio != null && preview.portfolios().size() == 1
				&& account.externalId().equals(preview.portfolios().get(0).settlementAccountExternalId())) {
			return selectedPortfolio.settlementAccountId();
		}
		return account.suggestedTargetId();
	}

	private MappingRow createRow(MappingType type, String externalId, String name, Currency currency,
			String detail, String relatedAccountId, List<XmlTargetOption> targets, Integer preferredId) {
		List<TargetChoice> choices = new ArrayList<>();
		choices.add(new TargetChoice(0, getText("UI_STOCK_PP_MAPPING_NEW"), null));
		targets.stream().filter(target -> isCompatible(type, currency, target))
				.forEach(target -> choices.add(new TargetChoice(target.id(), target.name(), target)));
		TargetChoice selected = choices.stream().filter(choice -> preferredId != null && choice.id() == preferredId)
				.findFirst().orElse(choices.get(0));
		return new MappingRow(type, externalId, name, currency, detail, relatedAccountId, choices, selected);
	}

	private static boolean isCompatible(MappingType type, Currency currency, XmlTargetOption target) {
		if (target.currency() != currency) {
			return false;
		}
		return type != MappingType.SETTLEMENT_ACCOUNT
				|| target.accountType() == AccountType.CURRENT_ACCOUNT
				|| target.accountType() == AccountType.DEPOT_ACCOUNT;
	}

	private TableView<MappingRow> createTable(List<MappingRow> rows) {
		TableView<MappingRow> table = new TableView<>(FXCollections.observableArrayList(rows));
		table.getColumns().setAll(List.<TableColumn<MappingRow, ?>>of(
				textColumn("UI_STOCK_PP_MAPPING_TYPE", row -> getText(row.type().messageKey), 150),
				textColumn("UI_STOCK_PP_MAPPING_SOURCE", MappingRow::name, 230),
				textColumn("UI_STOCK_FIELD_CURRENCY", row -> row.currency().name(), 90),
				textColumn("UI_STOCK_PP_MAPPING_DETAILS", MappingRow::detail, 230), assignmentColumn()));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
		GuiLayoutState.configureTable(table, "dialog.portfolioPerformanceImportMapping");
		return table;
	}

	private TableColumn<MappingRow, String> textColumn(String titleKey,
			java.util.function.Function<MappingRow, String> provider, double width) {
		TableColumn<MappingRow, String> column = new TableColumn<>(getText(titleKey));
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(provider.apply(data.getValue())));
		column.setPrefWidth(width);
		return column;
	}

	private TableColumn<MappingRow, TargetChoice> assignmentColumn() {
		TableColumn<MappingRow, TargetChoice> column = new TableColumn<>(getText("UI_STOCK_PP_MAPPING_TARGET"));
		column.setCellValueFactory(data -> data.getValue().choiceProperty());
		column.setCellFactory(ignored -> new TargetChoiceCell());
		column.setPrefWidth(300);
		column.setSortable(false);
		return column;
	}

	private void saveSelection(Window owner, Stage dialog, List<MappingRow> rows, DialogResult result) {
		if (!validate(owner, rows)) {
			return;
		}
		Map<String, Integer> portfolios = new HashMap<>();
		Map<String, Integer> accounts = new HashMap<>();
		for (MappingRow row : rows) {
			Map<String, Integer> target = row.type() == MappingType.PORTFOLIO ? portfolios : accounts;
			target.put(row.externalId(), row.choice().id());
		}
		result.assignments = new XmlImportAssignments(portfolios, accounts);
		dialog.close();
	}

	private boolean validate(Window owner, List<MappingRow> rows) {
		Set<Integer> assignedPortfolios = new HashSet<>();
		Set<Integer> assignedAccounts = new HashSet<>();
		for (MappingRow row : rows) {
			if (row.choice() == null) {
				return showValidationWarning(owner, "UI_STOCK_PP_MAPPING_REQUIRED");
			}
			if (row.type() == MappingType.PORTFOLIO && row.choice().id() > 0
					&& !assignedPortfolios.add(row.choice().id())) {
				return showValidationWarning(owner, "UI_STOCK_PP_MAPPING_DUPLICATE_PORTFOLIO");
			}
			if (row.type() != MappingType.PORTFOLIO && row.choice().id() > 0
					&& !assignedAccounts.add(row.choice().id())) {
				return showValidationWarning(owner, "UI_STOCK_PP_MAPPING_DUPLICATE_ACCOUNT");
			}
		}
		for (MappingRow portfolioRow : rows.stream().filter(row -> row.type() == MappingType.PORTFOLIO).toList()) {
			XmlTargetOption target = portfolioRow.choice().target();
			if (target == null) {
				continue;
			}
			MappingRow accountRow = rows.stream()
					.filter(row -> row.externalId().equals(portfolioRow.relatedAccountId()))
					.findFirst().orElse(null);
			if (accountRow != null && !java.util.Objects.equals(target.settlementAccountId(), accountRow.choice().id())) {
				return showValidationWarning(owner, "UI_STOCK_PP_MAPPING_ACCOUNT_MISMATCH");
			}
		}
		return true;
	}

	private boolean showValidationWarning(Window owner, String messageKey) {
		DialogWindowSupport.showAlert(owner, Alert.AlertType.WARNING, getText(messageKey));
		return false;
	}

	private static final class TargetChoiceCell extends TableCell<MappingRow, TargetChoice> {

		private final ComboBox<TargetChoice> comboBox = new ComboBox<>();

		private TargetChoiceCell() {
			comboBox.setMaxWidth(Double.MAX_VALUE);
			comboBox.prefWidthProperty().bind(widthProperty().subtract(16));
			comboBox.setOnAction(event -> {
				MappingRow row = getTableRow() != null ? getTableRow().getItem() : null;
				if (row != null && comboBox.getValue() != null) {
					row.setChoice(comboBox.getValue());
				}
			});
			setAlignment(Pos.CENTER_LEFT);
		}

		@Override
		protected void updateItem(TargetChoice choice, boolean empty) {
			super.updateItem(choice, empty);
			MappingRow row = getTableRow() != null ? getTableRow().getItem() : null;
			if (empty || row == null) {
				setGraphic(null);
				return;
			}
			comboBox.setItems(FXCollections.observableArrayList(row.choices()));
			comboBox.setValue(choice);
			setGraphic(comboBox);
		}
	}

	private enum MappingType {
		PORTFOLIO("UI_STOCK_PP_MAPPING_PORTFOLIO"),
		SETTLEMENT_ACCOUNT("UI_STOCK_PP_MAPPING_SETTLEMENT_ACCOUNT"),
		ADDITIONAL_ACCOUNT("UI_STOCK_PP_MAPPING_ADDITIONAL_ACCOUNT");

		private final String messageKey;

		MappingType(String messageKey) {
			this.messageKey = messageKey;
		}
	}

	private record TargetChoice(int id, String label, XmlTargetOption target) {
		@Override
		public String toString() {
			return label;
		}
	}

	private record MappingRow(MappingType type, String externalId, String name, Currency currency, String detail,
			String relatedAccountId,
			List<TargetChoice> choices, ObjectProperty<TargetChoice> choiceProperty) {

		private MappingRow(MappingType type, String externalId, String name, Currency currency, String detail,
				String relatedAccountId, List<TargetChoice> choices, TargetChoice choice) {
			this(type, externalId, name, currency, detail, relatedAccountId, List.copyOf(choices),
					new SimpleObjectProperty<>(choice));
		}

		private TargetChoice choice() {
			return choiceProperty.get();
		}

		private void setChoice(TargetChoice choice) {
			choiceProperty.set(choice);
		}
	}

	private static final class DialogResult {
		private XmlImportAssignments assignments;
	}
}
