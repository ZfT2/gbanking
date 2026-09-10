package de.zft2.gbanking.gui;

import java.util.function.Consumer;

import de.zft2.gbanking.gui.enu.PageContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;

final class GBankingNavigationBar extends BorderPane implements BaseGui {

	private static final double BUTTON_SIZE = 36;
	private static final String HOUSE_ICON = "M2 8.5 L9 2.5 L16 8.5 M4 7 V16 H14 V7 M7 16 V11 H11 V16";
	private static final String ORDER_ICON = "M3 2 H13 L16 5 V16 H3 Z M13 2 V5 H16 M6 8 H13 M6 11 H13 M6 14 H11";
	private static final String ANALYSIS_ICON = "M3 16 V3 M3 16 H16 M6 14 V10 H8 V14 M10 14 V7 H12 V14 M14 14 V4 H16 V14";
	private static final String HELP_ICON = "M6 6.5 C6.4 4.8 7.7 4 9.2 4 C11 4 12.2 5.1 12.2 6.7 C12.2 8.1 11.4 8.8 10.2 9.6 C9.4 10.2 9 10.8 9 12 M9 15 V15.1";

	GBankingNavigationBar(GBankingGui gui) {
		this(new GBankingMenuBar(gui), gui::activateOverview, gui::showManual);
	}

	GBankingNavigationBar(MenuBar menuBar, Consumer<PageContext> navigator, Runnable helpAction) {
		HBox buttons = new HBox(4,
				createButton("UI_NAVIGATION_ACCOUNTS_TRANSACTIONS", HOUSE_ICON,
						() -> navigator.accept(PageContext.ACCOUNTS_TRANSACTIONS)),
				createButton("UI_NAVIGATION_ORDERS", ORDER_ICON,
						() -> navigator.accept(PageContext.ACCOUNTS_MONEYTRANSFERS)),
				createButton("UI_NAVIGATION_ANALYSIS", ANALYSIS_ICON, () -> navigator.accept(PageContext.ANALYSIS)),
				createButton("UI_NAVIGATION_HELP", HELP_ICON, helpAction));
		buttons.setAlignment(Pos.CENTER_RIGHT);
		buttons.getStyleClass().add("gbanking-navigation-buttons");
		getStyleClass().add("gbanking-navigation-bar");
		setCenter(menuBar);
		setRight(buttons);
	}

	private Button createButton(String textKey, String iconPath, Runnable action) {
		String text = getText(textKey);
		SVGPath icon = new SVGPath();
		icon.setContent(iconPath);
		icon.getStyleClass().add("gbanking-navigation-icon");
		Button button = new Button(null, icon);
		button.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
		button.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
		button.setMaxSize(BUTTON_SIZE, BUTTON_SIZE);
		button.setTooltip(new Tooltip(text));
		button.setAccessibleText(text);
		button.setOnAction(event -> action.run());
		button.getStyleClass().addAll("gbanking-form-button", "gbanking-navigation-button");
		return button;
	}
}
