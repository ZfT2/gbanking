package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.enu.PageContext;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.HBox;

class GBankingNavigationBarTest {

	@Test
	void buttonsShouldBeSquareAccessibleAndInvokeTheirActions() {
		List<PageContext> activatedPages = new ArrayList<>();
		AtomicBoolean helpShown = new AtomicBoolean();

		JavaFxTestSupport.runFx(() -> {
			GBankingNavigationBar bar = new GBankingNavigationBar(new MenuBar(), activatedPages::add,
					() -> helpShown.set(true));
			HBox buttonBox = (HBox) bar.getRight();
			List<Button> buttons = buttonBox.getChildren().stream().map(Button.class::cast).toList();

			assertEquals(4, buttons.size());
			buttons.forEach(button -> {
				assertEquals(button.getPrefWidth(), button.getPrefHeight());
				assertFalse(button.getAccessibleText().isBlank());
				assertTrue(button.getStyleClass().contains("gbanking-form-button"));
				assertEquals(button.getAccessibleText(), button.getTooltip().getText());
				button.fire();
			});
		});

		assertEquals(List.of(PageContext.ACCOUNTS_TRANSACTIONS, PageContext.ACCOUNTS_MONEYTRANSFERS, PageContext.ANALYSIS),
				activatedPages);
		assertTrue(helpShown.get());
	}
}
