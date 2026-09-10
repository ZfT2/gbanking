package de.zft2.gbanking.gui.panel.account;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

final class AccountUpdatedAtColumn extends TableColumn<BankAccount, BankAccount> {

	private static final double MIN_WIDTH = 65;
	private static final double MAX_WIDTH = 120;
	private static final double CELL_INSETS = 18;
	private static final double HEADER_INSETS = 32;

	private final Clock clock;

	AccountUpdatedAtColumn(String title) {
		this(title, Clock.systemDefaultZone());
	}

	AccountUpdatedAtColumn(String title, Clock clock) {
		super(title);
		this.clock = clock;
		setMinWidth(MIN_WIDTH);
		setMaxWidth(MAX_WIDTH);
		setPrefWidth(90);
		setResizable(false);
		setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		setComparator(Comparator.comparing(account -> sortTime(account), Comparator.nullsFirst(Comparator.naturalOrder())));
		setCellFactory(column -> new TableCell<>() {
			{
				fontProperty().addListener((obs, oldFont, font) -> fitContent(font));
			}

			@Override
			protected void updateItem(BankAccount account, boolean empty) {
				super.updateItem(account, empty);
				setText(empty || account == null ? null : displayText(account));
				fitContent(getFont());
			}
		});
	}

	private void fitContent(Font font) {
		Text header = new Text(getText());
		header.setFont(Font.font(font.getFamily(), FontWeight.BOLD, font.getSize()));
		double width = header.getLayoutBounds().getWidth() + HEADER_INSETS;
		if (getTableView() != null) {
			Text content = new Text();
			content.setFont(font);
			for (BankAccount account : getTableView().getItems()) {
				content.setText(displayText(account));
				width = Math.max(width, content.getLayoutBounds().getWidth() + CELL_INSETS);
			}
		}
		setPrefWidth(Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, Math.ceil(width))));
	}

	private String displayText(BankAccount account) {
		return DateFormatUtils.formatRetrievalAt(account.getSessionRetrievalAt(), account.getUpdatedAt(), LocalDate.now(clock));
	}

	private LocalDateTime sortTime(BankAccount account) {
		LocalDateTime retrievalTime = account.getSessionRetrievalAt();
		if (retrievalTime != null) {
			return retrievalTime;
		}
		return account.getUpdatedAt() == null ? null : account.getUpdatedAt().atStartOfDay();
	}
}
