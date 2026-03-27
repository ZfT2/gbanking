package de.gbanking.gui.component;

import javafx.css.PseudoClass;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

public class GBankingTableView<T> extends TableView<T> {

	private static final PseudoClass STRIPED_LIGHT = PseudoClass.getPseudoClass("striped-light");
	private static final PseudoClass STRIPED_WHITE = PseudoClass.getPseudoClass("striped-white");

	public GBankingTableView() {
		super();
		initDefaultTableSettings();
	}

	private void initDefaultTableSettings() {
		setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

		setRowFactory(tv -> new TableRow<>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);
				updateStripeState();
			}

			@Override
			public void updateSelected(boolean selected) {
				super.updateSelected(selected);
				updateStripeState();
			}

			private void updateStripeState() {
				pseudoClassStateChanged(STRIPED_LIGHT, false);
				pseudoClassStateChanged(STRIPED_WHITE, false);

				if (isEmpty() || getItem() == null || isSelected()) {
					return;
				}

				if (getIndex() % 2 == 0) {
					pseudoClassStateChanged(STRIPED_WHITE, true);
				} else {
					pseudoClassStateChanged(STRIPED_LIGHT, true);
				}
			}
		});
	}
}