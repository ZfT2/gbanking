package de.zft2.gbanking.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.service.account.BookingDuplicateReviewHandler.Match;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

class BookingDuplicateReviewDialogTest {

	@ParameterizedTest
	@ValueSource(strings = { "skip", "double-click", "context-menu" })
	void shouldDisplayPairsAndOnlyImportActivatedFileRows(String action) {
		JavaFxTestSupport.runFx(() -> {
			Booking existing = booking("Account purpose");
			Booking incoming = booking("File purpose");
			incoming.setRecipient(new Recipient("Recipient", "DE123", null, "123", null, null, Source.IMPORT));
			FutureTask<Void> interaction = new FutureTask<>(() -> {
				Stage stage = dialogStage();
				try {
					TableView<?> table = (TableView<?>) stage.getScene().lookup("#booking-duplicate-review");
					stage.getScene().getRoot().applyCss();
					stage.getScene().getRoot().layout();
					assertEquals(4, table.getItems().size());
					assertEquals("Account purpose", table.getColumns().get(3).getCellData(0));
					assertEquals("File purpose", table.getColumns().get(3).getCellData(1));
					assertEquals("Recipient", table.getColumns().get(5).getCellData(1));
					assertEquals("DE123", table.getColumns().get(6).getCellData(1));
					TableRow<?> row = table.lookupAll(".table-row-cell").stream()
							.filter(node -> node instanceof TableRow<?> candidate && candidate.getIndex() == 1)
							.map(node -> (TableRow<?>) node).findFirst().orElseThrow();
					assertTrue(row.lookupAll("Text").stream().anyMatch(node -> ((Text) node).isStrikethrough()));
					if ("context-menu".equals(action)) {
						row.getContextMenu().getItems().get(0).fire();
					} else if ("double-click".equals(action)) {
						row.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
								false, false, false, false, false, false, false, false, false, true, null));
					}
					table.layout();
					if (!"skip".equals(action)) {
						assertFalse(table.lookupAll("Text").stream().anyMatch(node -> ((Text) node).isStrikethrough()));
					}
				} finally {
					Button confirm = (Button) stage.getScene().getRoot().lookupAll(".button").stream()
							.filter(node -> node instanceof Button button
									&& button.getText().equals(BaseMessages.getTextStatic("UI_DUPLICATE_CONFIRM")))
							.findFirst().orElseThrow();
					confirm.fire();
				}
				return null;
			});
			Platform.runLater(interaction);
			var selected = new BookingDuplicateReviewDialog(null).selectImports(
					List.of(new Match(existing, incoming), new Match(booking("Second account match"), incoming)));
			try {
				interaction.get();
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
			assertEquals(!"skip".equals(action), selected.contains(incoming));
			assertFalse(selected.contains(existing));
		});
	}

	@Test
	void closingReviewShouldCancelImport() {
		JavaFxTestSupport.runFx(() -> {
			Platform.runLater(() -> dialogStage().close());
			BookingDuplicateReviewDialog dialog = new BookingDuplicateReviewDialog(null);
			List<Match> matches = List.of(new Match(booking("Account"), booking("File")));
			assertThrows(CancellationException.class, () -> dialog.selectImports(matches));
		});
	}

	private static Stage dialogStage() {
		return (Stage) Window.getWindows().stream().filter(window -> window.isShowing() && window.getScene() != null
				&& window.getScene().lookup("#booking-duplicate-review") != null).findFirst().orElseThrow();
	}

	private static Booking booking(String purpose) {
		return new Booking(1, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), purpose, BigDecimal.TEN, BookingType.DEPOSIT, 0);
	}
}
