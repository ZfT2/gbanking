package de.zft2.gbanking.gui.dialog.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecurityExchangeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecuritySummary;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

class StockSecurityExchangeDialogTest {

	@Test
	void manualDialogShouldDistinguishIdentifiersAndSaveOneReplacement() {
		StockPortfolioService service = mock(StockPortfolioService.class);
		PortfolioSummary portfolio = new PortfolioSummary(1, "Depot", "Bank", "123", LocalDate.of(2026, 1, 1),
				2, "Verrechnung", "Bank", "DE123", Currency.EUR, BigDecimal.ZERO);
		SecuritySummary predecessor = new SecuritySummary(1, "Aktie", StockQuantityType.UNITS, Currency.EUR, "DE0000000001", "OLD001");
		SecuritySummary successor = new SecuritySummary(2, "Aktie", StockQuantityType.UNITS, Currency.EUR, "DE0000000002", "NEW001");
		when(service.getTransactionSecurities()).thenReturn(List.of(predecessor, successor));
		PositionSummary position = new PositionSummary(1, 1, "Aktie", predecessor.isin(), predecessor.wkn(),
				StockQuantityType.UNITS, BigDecimal.TEN, null, null, null, null, Currency.EUR, null);
		Runnable saved = mock(Runnable.class);

		JavaFxTestSupport.runFx(() -> {
			StockSecurityExchangeDialog dialog = new StockSecurityExchangeDialog(null, service, portfolio, position, false, saved);
			@SuppressWarnings("unchecked")
			ComboBox<SecuritySummary> target = (ComboBox<SecuritySummary>) dialog.dialog().getScene().getRoot()
					.lookupAll(".combo-box").stream().filter(ComboBox.class::isInstance)
					.map(ComboBox.class::cast).filter(field -> !field.getItems().isEmpty()
							&& field.getItems().get(0) instanceof SecuritySummary && field.getValue() == null)
					.findFirst().orElseThrow();
			assertEquals("Aktie | DE0000000002 | NEW001", target.getConverter().toString(successor));
			target.setValue(successor);
			dialog.dialog().getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance)
					.map(Button.class::cast).filter(button -> button.getText().equals(BaseMessages.getTextStatic("UI_BUTTON_SAVE")))
					.findFirst().orElseThrow().fire();

			verify(service).recordSecurityExchange(eq(portfolio),
					eq(new SecurityExchangeRequest(1, 2, BigDecimal.TEN, LocalDate.now(), "")));
			verify(service, never()).linkSecurityExchange(any(), anyInt(), anyInt());
			verify(saved).run();
		});
	}
}
