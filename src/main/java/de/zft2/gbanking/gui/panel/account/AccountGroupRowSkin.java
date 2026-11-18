package de.zft2.gbanking.gui.panel.account;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.enu.AccountGrouping;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.skin.TableRowSkin;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

final class AccountGroupRowSkin extends TableRowSkin<BankAccount> {

	static final double GROUP_HEIGHT = 15;
	static final double ACCOUNT_HEIGHT = 30;

	private final Label label = new Label();
	private final HBox separator;

	AccountGroupRowSkin(TableRow<BankAccount> row) {
		super(row);
		Region leftLine = new Region();
		Region rightLine = new Region();
		leftLine.getStyleClass().add("account-group-line");
		rightLine.getStyleClass().add("account-group-line");
		HBox.setHgrow(leftLine, Priority.ALWAYS);
		HBox.setHgrow(rightLine, Priority.ALWAYS);
		label.getStyleClass().add("account-group-label");
		separator = new HBox(7, leftLine, label, rightLine);
		separator.setAlignment(Pos.CENTER);
		separator.getStyleClass().add("account-group-separator");
		separator.setManaged(false);
		separator.setMouseTransparent(true);
		getChildren().add(separator);
		row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			if (separator.isVisible() && event.getY() < GROUP_HEIGHT) {
				event.consume();
			}
		});
		row.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
			if (separator.isVisible() && !event.isKeyboardTrigger() && event.getY() < GROUP_HEIGHT) {
				event.consume();
			}
		});
	}

	void updateGroup(AccountGrouping grouping) {
		TableRow<BankAccount> row = getSkinnable();
		int index = row.getIndex();
		boolean first = !row.isEmpty() && row.getItem() != null && grouping != AccountGrouping.NONE
				&& row.getTableView() != null && index >= 0 && index < row.getTableView().getItems().size()
				&& (index == 0 || !grouping.key(row.getItem()).equalsIgnoreCase(
						grouping.key(row.getTableView().getItems().get(index - 1))));
		label.setText(first ? grouping.label(row.getItem()) : null);
		separator.setVisible(first);
		row.setPrefHeight(ACCOUNT_HEIGHT + (first ? GROUP_HEIGHT : 0));
		row.requestLayout();
	}

	@Override
	protected void layoutChildren(double x, double y, double width, double height) {
		double offset = separator.isVisible() ? GROUP_HEIGHT : 0;
		super.layoutChildren(x, y + offset, width, height - offset);
		if (!getChildren().contains(separator)) {
			getChildren().add(separator);
		}
		separator.resizeRelocate(x, y, width, offset);
		separator.toFront();
	}
}
