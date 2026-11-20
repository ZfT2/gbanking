package de.zft2.gbanking.gui.dialog.stock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService.TransferPlan;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

class StockPortfolioTransferDialogTest {

	@ParameterizedTest
	@ValueSource(strings = { "empty", "original", "entered", "manual", "cancel" })
	void transferShouldPreferOriginalDatesAndRequestAManualCutoffOnlyWhenNeeded(String scenario) {
		LocalDate original = LocalDate.of(2026, 9, 15);
		LocalDate entered = original.plusDays(5);
		PortfolioSummary source = portfolio(1);
		PortfolioSummary target = portfolio(2);
		TransferPlan plan = new TransferPlan(1, 2, List.of(10), List.of(20), List.of(original), List.of());
		StockPortfolioTransferService service = mock(StockPortfolioTransferService.class);
		LocalDate requested = scenario.equals("empty") || scenario.equals("manual") ? null : entered;
		when(service.findExistingDeliveries(source, target, requested)).thenReturn(scenario.equals("manual") ? null : plan);
		Runnable saved = mock(Runnable.class);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		JavaFxTestSupport.runFx(() -> {
			StockPortfolioTransferDialog dialog = new StockPortfolioTransferDialog(null, source, List.of(target), service, saved);
			DatePicker date = (DatePicker) dialog.dialog().getScene().getRoot().lookup(".date-picker");
			assertNull(date.getValue());
			date.setValue(requested);
			Platform.runLater(() -> answerDateDialog(scenario, requested, entered, failure));
			button(dialog.dialog().getScene().getRoot(), text("UI_STOCK_ACTION_TRANSFER")).fire();
			dialog.dialog().close();
			List.copyOf(Window.getWindows()).forEach(window -> { if (window instanceof Stage stage) { stage.close(); } });
		});
		assertNull(failure.get());
		if (scenario.equals("cancel")) {
			verify(service, never()).transferExistingDeliveries(any(), any(), any(), any(), any());
			verify(saved, never()).run();
		} else {
			if (scenario.equals("manual")) {
				verify(service).transferManually(source, target, entered, BigDecimal.ONE);
			} else {
				verify(service).transferExistingDeliveries(source, target, plan, scenario.equals("entered") ? entered : null, BigDecimal.ONE);
			}
			verify(saved).run();
		}
	}

	private static void answerDateDialog(String scenario, LocalDate requested, LocalDate entered, AtomicReference<Throwable> failure) {
		DialogPane pane = openDialog();
		try {
			if (scenario.equals("manual")) {
				assertTrue(pane.lookupAll(".label").stream().filter(node -> node instanceof Label)
						.anyMatch(node -> ((Label) node).getText().equals(text("UI_STOCK_PORTFOLIO_TRANSFER_MANUAL_HINT"))));
				((DatePicker) pane.lookup(".date-picker")).setValue(entered);
			} else if (requested != null) {
				assertTrue(button(pane, text("UI_STOCK_PORTFOLIO_TRANSFER_USE_ORIGINAL")).isDefaultButton());
				assertFalse(button(pane, text("UI_STOCK_PORTFOLIO_TRANSFER_USE_ENTERED")).isDefaultButton());
			}
		} catch (AssertionError error) {
			failure.set(error);
		} finally {
			answerConfirmation(pane, scenario, requested);
		}
	}

	private static void answerConfirmation(DialogPane pane, String scenario, LocalDate requested) {
		if (scenario.equals("cancel")) {
			pane.getButtonTypes().stream().filter(type -> type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE)
					.findFirst().ifPresent(type -> ((Button) pane.lookupButton(type)).fire());
			return;
		}
		if (requested != null) {
			Platform.runLater(() -> button(openDialog(), text("UI_STOCK_ACTION_TRANSFER")).fire());
		}
		String key = requested == null ? "UI_STOCK_ACTION_TRANSFER"
				: scenario.equals("entered") ? "UI_STOCK_PORTFOLIO_TRANSFER_USE_ENTERED" : "UI_STOCK_PORTFOLIO_TRANSFER_USE_ORIGINAL";
		button(pane, text(key)).fire();
	}

	private static DialogPane openDialog() {
		return Window.getWindows().stream().filter(window -> window.isShowing() && window.getScene() != null
				&& window.getScene().getRoot() instanceof DialogPane).map(window -> (DialogPane) window.getScene().getRoot())
				.findFirst().orElseThrow();
	}

	private static Button button(Parent parent, String text) {
		return parent.lookupAll(".button").stream().filter(node -> node instanceof Button)
				.map(node -> (Button) node).filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
	}

	private static String text(String key) {
		return BaseMessages.getTextStatic(key);
	}

	private static PortfolioSummary portfolio(int id) {
		return new PortfolioSummary(id, "Depot " + id, "Bank", "", LocalDate.of(2026, 1, 1),
				id + 10, "Konto", "Bank", "", Currency.EUR, BigDecimal.ZERO);
	}
}
