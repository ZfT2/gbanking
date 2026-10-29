package de.zft2.gbanking.gui.panel.account;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

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
	private Font measuredFont;
	private List<String> measuredTexts = List.of();

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
		List<String> texts = getTableView() != null
				? getTableView().getItems().stream().map(account -> displayText(account)).toList() : List.of();
		// updateItem runs for every cell, so only re-measure when font or displayed texts have changed
		if (font.equals(measuredFont) && texts.equals(measuredTexts)) {
			return;
		}
		measuredFont = font;
		measuredTexts = texts;
		Text header = new Text(getText());
		header.setFont(Font.font(font.getFamily(), FontWeight.BOLD, font.getSize()));
		double width = header.getLayoutBounds().getWidth() + HEADER_INSETS;
		Text content = new Text();
		content.setFont(font);
		for (String text : new HashSet<>(texts)) {
			content.setText(text);
			width = Math.max(width, content.getLayoutBounds().getWidth() + CELL_INSETS);
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
