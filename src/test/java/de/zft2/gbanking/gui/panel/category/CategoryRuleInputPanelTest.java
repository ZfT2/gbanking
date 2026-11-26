package de.zft2.gbanking.gui.panel.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

class CategoryRuleInputPanelTest {

	@Test
	void shouldKeepResponsiveStyledGroups(@TempDir Path directory) {
		withPanel(directory, panel -> {
			TitledPane combinations = pane(panel, "UI_PANEL_CATEGORY_RULE_COMBINATIONS");
			CheckBox overwrite = overwriteField(panel);
			HBox header = (HBox) overwrite.getParent();
			assertSame(overwrite, header.getChildren().get(1));
			assertFalse(overwrite.isWrapText());
			HBox groups = (HBox) combinations.getParent();
			assertSame(combinations, groups.getChildren().get(2));
			assertEquals(BaseMessages.getTextStatic("UI_PANEL_RECIPIENT_SENDER"),
					((TitledPane) groups.getChildren().get(1)).getText());
			assertTrue(combinations.getBoundsInParent().getMinX() >= groups.getChildren().get(1).getBoundsInParent().getMaxX());
			assertTrue(combinations.getBoundsInParent().getMaxX() <= groups.getWidth());
			GridPane recipientGrid = (GridPane) ((TitledPane) groups.getChildren().get(1)).getContent();
			HBox nameRow = (HBox) recipientGrid.getChildren().get(0);
			Region ibanBox = (Region) recipientGrid.getChildren().get(1);
			Region accountBox = (Region) recipientGrid.getChildren().get(2);
			double[] previousWidths = new double[3];
			for (double width : new double[] { 1150, 1350, 1650 }) {
				panel.resize(width, 700);
				panel.layout();
				assertEquals(groups.getWidth(), combinations.getBoundsInParent().getMaxX(), 1);
				assertEquals(groups.getWidth(), header.getWidth(), 1);
				assertTrue(header.getChildren().get(0).getBoundsInParent().getMaxX() <= overwrite.getLayoutX());
				Region updated = (Region) header.getChildren().get(2);
				assertEquals(125, updated.getWidth(), 1);
				assertTrue(overwrite.getBoundsInParent().getMaxX() <= updated.getLayoutX());
				assertEquals(header.getWidth(), updated.getBoundsInParent().getMaxX(), 1);
				assertEquals(overwrite.prefWidth(-1), overwrite.getWidth(), 1);
				for (int index = 0; index < previousWidths.length; index++) {
					double groupWidth = ((Region) groups.getChildren().get(index)).getWidth();
					assertTrue(groupWidth > previousWidths[index]);
					previousWidths[index] = groupWidth;
				}
				assertTrue(ibanBox.getWidth() > accountBox.getWidth());
				assertEquals(ibanBox.getLayoutX(), nameRow.getLayoutX(), 1);
				assertEquals(accountBox.getBoundsInParent().getMaxX(), nameRow.getBoundsInParent().getMaxX(), 1);
				assertEquals(nameRow.getWidth(), nameRow.getChildren().get(1).getBoundsInParent().getMaxX(), 1);
				assertTrue(nameRow.getChildren().get(0).lookup(".text-field").getBoundsInParent().getMaxX()
						<= ((Region) nameRow.getChildren().get(0)).getWidth() + 1);
			}
			assertFalse(combinations.isCollapsible());
			var buttons = panel.lookupAll(".button").stream().filter(node -> node instanceof Button)
					.map(node -> (Button) node).toList();
			assertEquals(6, buttons.size());
			assertTrue(buttons.stream().allMatch(button -> button.getStyleClass().contains("gbanking-form-button")));
		});
	}

	@Test
	void shouldEditPurposeRowsAndConfirmRemovalOfEnteredText(@TempDir Path directory) {
		withPanel(directory, panel -> {
			VBox rows = purposeRows(panel);
			assertEquals(1, rows.getChildren().size());
			HBox firstRow = (HBox) rows.getChildren().get(0);
			assertEquals("", ((TextField) firstRow.getChildren().get(0)).getText());
			assertTrue(firstRow.lookupAll(".label").isEmpty());
			assertTrue(firstRow.getChildren().get(2).isDisabled());
			((Button) firstRow.getChildren().get(1)).fire();
			assertEquals(2, rows.getChildren().size());
			assertFalse(firstRow.getChildren().get(2).isDisabled());
			try (var dialogs = mockStatic(DialogWindowSupport.class)) {
				((Button) firstRow.getChildren().get(2)).fire();
				assertEquals(1, rows.getChildren().size());
				dialogs.verifyNoInteractions();
				HBox filledRow = (HBox) rows.getChildren().get(0);
				((Button) filledRow.getChildren().get(1)).fire();
				TextField purpose = (TextField) filledRow.getChildren().get(0);
				purpose.setText("Ticket");
				dialogs.when(() -> DialogWindowSupport.showConfirmation(null, AlertType.WARNING,
						BaseMessages.getTextStatic("UI_PANEL_PURPOSE"), null,
						BaseMessages.getTextStatic("ALERT_CATEGORY_RULE_PURPOSE_DELETE"), ButtonType.OK, ButtonType.CANCEL))
						.thenReturn(false, true);
				Button remove = (Button) filledRow.getChildren().get(2);
				remove.fire();
				assertEquals(2, rows.getChildren().size());
				assertEquals("Ticket", purpose.getText());
				assertSame(filledRow, rows.getChildren().get(0));
				remove.fire();
			}
			assertEquals(1, rows.getChildren().size());
			assertTrue(((HBox) rows.getChildren().get(0)).getChildren().get(2).isDisabled());
		});
	}

	@Test
	void shouldSavePurposeConditionsAndResetForNewBookings(@TempDir Path directory) {
		withPanel(directory, panel -> {
			CheckBox overwrite = overwriteField(panel);
			GridPane options = (GridPane) pane(panel, "UI_PANEL_CATEGORY_RULE_COMBINATIONS").getContent();
			assertEquals(2, options.getChildren().size());
			ComboBox<?> join = (ComboBox<?>) options.getChildren().get(0).lookup(".combo-box");
			ComboBox<?> purposeJoin = (ComboBox<?>) options.getChildren().get(1).lookup(".combo-box");
			assertTrue(overwrite.isSelected());
			assertEquals(CategoryRule.JoinType.OR, join.getValue());
			assertEquals(CategoryRule.JoinType.OR, purposeJoin.getValue());
			VBox rows = purposeRows(panel);
			CategoryRule rule = new CategoryRule();
			rule.setCategory(DBController.getInstance(directory.toString()).insertOrUpdate(new Category("Bahn")));
			rule.setJoinType(CategoryRule.JoinType.AND);
			rule.setJoinTypePurpose(CategoryRule.JoinType.AND);
			rule.setFilterPurposes(List.of("Ticket", "Berlin"));
			rule.setOverwriteExistingCategories(false);
			panel.updatePanelFieldValues(rule);
			assertFalse(overwrite.isSelected());
			assertEquals(CategoryRule.JoinType.AND, join.getValue());
			assertEquals(CategoryRule.JoinType.AND, purposeJoin.getValue());
			assertEquals(List.of("Ticket", "Berlin"), rows.getChildren().stream()
					.map(row -> ((TextField) ((HBox) row).getChildren().get(0)).getText()).toList());
			((TextField) ((HBox) rows.getChildren().get(1)).getChildren().get(0)).setText(" München ");
			panel.lookupAll(".button").stream().filter(node -> node instanceof Button)
					.map(node -> (Button) node).filter(button -> button.getText().equals(BaseMessages.getTextStatic("UI_BUTTON_SAVE")))
					.findFirst().orElseThrow().fire();
			CategoryRule savedRule = DBController.getInstance(directory.toString()).getAllFull(CategoryRule.class).get(0);
			assertEquals(List.of("Ticket", "München"), savedRule.getFilterPurposes());
			assertEquals(CategoryRule.JoinType.AND, savedRule.getJoinTypePurpose());
			assertFalse(savedRule.isOverwriteExistingCategories());
			panel.prefillFromBooking(new BankAccount(), new Booking());
			assertEquals(1, rows.getChildren().size());
			assertEquals("", ((TextField) ((HBox) rows.getChildren().get(0)).getChildren().get(0)).getText());
			assertEquals(CategoryRule.JoinType.OR, purposeJoin.getValue());
			assertTrue(overwrite.isSelected());
		});
	}

	private static void withPanel(Path directory, Consumer<CategoryRuleInputPanel> action) {
		DBController.getInstance(directory.toString());
		try {
			JavaFxTestSupport.runFx(() -> {
				CategoryOverviewPanel parent = mock(CategoryOverviewPanel.class);
				CategoryRuleListPanel rules = mock(CategoryRuleListPanel.class);
				CategoryListPanel categories = mock(CategoryListPanel.class);
				when(parent.getCategoryRuleListPanel()).thenReturn(rules);
				when(parent.getCategoryListPanel()).thenReturn(categories);
				CategoryRuleInputPanel panel = new CategoryRuleInputPanel(parent);
				Scene scene = new Scene(panel, 1450, 700);
				scene.getStylesheets().add(CategoryRuleInputPanelTest.class.getResource("/css/gbanking-table.css").toExternalForm());
				panel.resize(1450, 700);
				panel.applyCss();
				panel.layout();
				action.accept(panel);
			});
		} finally {
			DBControllerTestUtil.closeAndNullifyConnection();
		}
	}

	private static TitledPane pane(CategoryRuleInputPanel panel, String key) {
		return panel.lookupAll(".titled-pane").stream().filter(node -> node instanceof TitledPane)
				.map(node -> (TitledPane) node).filter(item -> item.getText().equals(BaseMessages.getTextStatic(key)))
				.findFirst().orElseThrow();
	}

	private static VBox purposeRows(CategoryRuleInputPanel panel) {
		return (VBox) ((GridPane) pane(panel, "UI_PANEL_PURPOSE").getContent()).getChildren().get(0);
	}

	private static CheckBox overwriteField(CategoryRuleInputPanel panel) {
		return panel.lookupAll(".check-box").stream().map(node -> (CheckBox) node)
				.filter(box -> box.getText().equals(BaseMessages.getTextStatic("UI_CATEGORY_RULE_OVERWRITE_EXISTING")))
				.findFirst().orElseThrow();
	}

	@Test
	void createRuleTemplateShouldPrefillBookingDataWithAndCombination() {
		BankAccount account = new BankAccount();
		Category category = new Category("Versicherungen");
		Recipient recipient = new Recipient("Max Mustermann", "DE02120300000000202051");
		recipient.setAccountNumber("202051");
		Booking booking = new Booking();
		booking.setDateBooking(LocalDate.of(2026, Month.SEPTEMBER, 17));
		booking.setAmount(new BigDecimal("-123.45"));
		booking.setPurpose("Monatlicher Beitrag");
		booking.setCategory(category);
		booking.setRecipient(recipient);

		CategoryRule template = CategoryRuleInputPanel.createRuleTemplate(account, booking);

		assertEquals(CategoryRule.JoinType.AND, template.getJoinType());
		assertEquals(List.of(account), template.getBankAccountList());
		assertSame(category, template.getCategory());
		assertEquals(booking.getDateBooking(), template.getFilterDateFrom());
		assertEquals(booking.getDateBooking(), template.getFilterDateTo());
		assertEquals(booking.getAmount(), template.getFilterAmountFrom());
		assertEquals(booking.getAmount(), template.getFilterAmountTo());
		assertEquals(recipient.getName(), template.getFilterRecipientName());
		assertEquals(recipient.getIban(), template.getFilterRecipientIban());
		assertEquals(recipient.getAccountNumber(), template.getFilterRecipientAccountNumber());
		assertEquals(List.of(booking.getPurpose()), template.getFilterPurposes());
	}
}
