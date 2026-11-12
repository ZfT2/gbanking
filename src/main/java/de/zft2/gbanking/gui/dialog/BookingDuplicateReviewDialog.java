package de.zft2.gbanking.gui.dialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Function;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.service.account.BookingDuplicateReviewHandler;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class BookingDuplicateReviewDialog implements BaseMessages, BookingDuplicateReviewHandler {

	private final Window owner;

	public BookingDuplicateReviewDialog(Window owner) {
		this.owner = owner;
	}

	@Override
	public Set<Booking> selectImports(List<Match> matches) {
		if (Platform.isFxApplicationThread()) {
			return showDialog(matches);
		}
		FutureTask<Set<Booking>> task = new FutureTask<>(() -> showDialog(matches));
		Platform.runLater(task);
		try {
			return task.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new CancellationException("Duplicate review interrupted.");
		} catch (ExecutionException exception) {
			if (exception.getCause() instanceof CancellationException cancelled) {
				throw cancelled;
			}
			throw new GBankingException("Duplicate review failed.", exception);
		}
	}

	private Set<Booking> showDialog(List<Match> matches) {
		Set<Booking> selected = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Row> rows = new ArrayList<>();
		for (Match match : matches) {
			rows.add(new Row(match.existing(), false));
			rows.add(new Row(match.incoming(), true));
		}
		TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows));
		table.setId("booking-duplicate-review");
		var amountFormat = FxTableUtils.createGermanDecimalFormat();
		table.getColumns().setAll(List.of(
				column("UI_DUPLICATE_ORIGIN", row -> getText(row.incoming() ? "UI_DUPLICATE_FILE" : "UI_DUPLICATE_ACCOUNT")
						+ " - " + java.util.Objects.toString(row.booking().getAccountName(),
								Integer.toString(row.booking().getAccountId())), 150, selected),
				column("UI_DUPLICATE_BOOKING_DATE", row -> DateFormatUtils.formatShort(row.booking().getDateBooking()), 100, selected),
				column("UI_DUPLICATE_VALUE_DATE", row -> DateFormatUtils.formatShort(row.booking().getDateValue()), 100, selected),
				column("UI_DUPLICATE_PURPOSE", row -> row.booking().getPurpose(), 330, selected),
				column("UI_DUPLICATE_AMOUNT", row -> row.booking().getAmount() != null
						? amountFormat.format(row.booking().getAmount()) : "", 110, selected),
				column("UI_DUPLICATE_RECIPIENT", row -> recipientName(row.booking().getRecipient()), 180, selected),
				column("UI_DUPLICATE_IDENTIFIER", row -> recipientIdentifier(row.booking().getRecipient()), 230, selected)));
		table.setRowFactory(ignored -> {
			TableRow<Row> row = new TableRow<>();
			MenuItem toggle = new MenuItem(getText("UI_DUPLICATE_TOGGLE"));
			toggle.setOnAction(event -> toggle(row.getItem(), selected, table));
			ContextMenu menu = new ContextMenu(toggle);
			row.itemProperty().addListener((observable, oldValue, value) ->
					row.setContextMenu(value != null && value.incoming() ? menu : null));
			row.setOnMouseClicked(event -> {
				if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
					toggle(row.getItem(), selected, table);
				}
			});
			return row;
		});
		Stage dialog = DialogWindowSupport.createModalStage(owner, "UI_DUPLICATE_TITLE");
		boolean[] confirmed = { false };
		Button confirm = new Button(getText("UI_DUPLICATE_CONFIRM"));
		confirm.setDefaultButton(true);
		confirm.setOnAction(event -> {
			confirmed[0] = true;
			dialog.close();
		});
		Button cancel = new Button(getText("UI_BUTTON_CANCEL_IMPORT"));
		cancel.setCancelButton(true);
		cancel.setOnAction(event -> dialog.close());
		Label explanation = new Label(getText("UI_DUPLICATE_EXPLANATION"));
		explanation.setWrapText(true);
		DialogWindowSupport.setVgrowAlways(table);
		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(explanation, table,
				DialogWindowSupport.createButtonBar(confirm, cancel)), 1250, 620));
		dialog.showAndWait();
		if (!confirmed[0]) {
			throw new CancellationException("Booking import cancelled by user.");
		}
		return selected;
	}

	private TableColumn<Row, String> column(String title, Function<Row, String> value, double width, Set<Booking> selected) {
		TableColumn<Row, String> column = new TableColumn<>(getText(title));
		column.setPrefWidth(width);
		column.setSortable(false);
		column.setReorderable(false);
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
		column.setCellFactory(ignored -> new TableCell<>() {
			private final Text text = new Text();

			{
				text.wrappingWidthProperty().bind(widthProperty().subtract(16));
				text.setStyle("-fx-fill: -fx-text-base-color;");
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				Row row = getTableRow() != null ? getTableRow().getItem() : null;
				text.setText(item);
				text.setStrikethrough(row != null && row.incoming() && !selected.contains(row.booking()));
				setGraphic(empty ? null : text);
				setText(null);
			}
		});
		return column;
	}

	private static void toggle(Row row, Set<Booking> selected, TableView<Row> table) {
		if (row != null && row.incoming()) {
			if (!selected.remove(row.booking())) {
				selected.add(row.booking());
			}
			table.refresh();
		}
	}

	private static String recipientName(Recipient recipient) {
		return recipient != null ? recipient.getName() : "";
	}

	private static String recipientIdentifier(Recipient recipient) {
		if (recipient == null) {
			return "";
		}
		return recipient.getIban() != null && !recipient.getIban().isBlank() ? recipient.getIban() : recipient.getAccountNumber();
	}

	private record Row(Booking booking, boolean incoming) {
	}
}
