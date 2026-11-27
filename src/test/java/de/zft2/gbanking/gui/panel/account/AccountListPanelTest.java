package de.zft2.gbanking.gui.panel.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.AccountGrouping;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.model.AccountTableModel;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

class AccountListPanelTest {

	private static final String ALPHA = "Alpha Girokonto";
	private static final String BETA = "Beta Tagesgeldkonto";
	private static final String GAMMA = "Gamma Girokonto";
	private static final String DELTA = "Delta Sparkonto";
	private static final String GROUPING_CHOICE = "#account-grouping";
	private static final String FIRST_BLZ = "10000000";

	@RegisterExtension
	static final TestDatabaseExtension database = TestDatabaseExtension.withoutClearing();

	@AfterEach
	void resetViewSetting() {
		JavaFxTestSupport.runFx(() -> GuiLayoutState.initialize(new HashMap<>()));
	}

	@Test
	void shouldKeepClosedGroupingCaptionWhilePreservingChoices() {
		JavaFxTestSupport.runFx(() -> {
			TestPanel panel = new TestPanel();
			ComboBox<?> choice = (ComboBox<?>) panel.lookup(GROUPING_CHOICE);
			for (AccountGrouping mode : AccountGrouping.values()) {
				GuiLayoutState.accountGroupingProperty().set(mode);
				panel.applyCss();
				panel.layout();
				assertEquals(BaseMessages.getTextStatic("UI_ACCOUNT_GROUPING_CAPTION"), choice.getButtonCell().getText());
				assertEquals(mode, choice.getValue());
			}
			assertEquals(List.of(AccountGrouping.values()), choice.getItems());
		});
	}

	@Test
	void shouldGroupRealAccountsAndKeepSelectionAndColumnSorting() {
		JavaFxTestSupport.runFx(() -> {
			TestPanel panel = new TestPanel();
			BankAccount alpha = panel.getModelAccount().getAccounts().get(2);
			panel.selectAccount(alpha);
			GuiLayoutState.accountGroupingProperty().set(AccountGrouping.TYPE);
			assertNames(panel, ALPHA, GAMMA, BETA, DELTA);
			TableColumn<BankAccount, ?> name = panel.table().getColumns().get(1);
			name.setSortType(TableColumn.SortType.DESCENDING);
			panel.table().getSortOrder().setAll(List.of(name));
			assertNames(panel, GAMMA, ALPHA, BETA, DELTA);
			assertSame(alpha, panel.getSelectedAccount());
			GuiLayoutState.accountGroupingProperty().set(AccountGrouping.BANK);
			assertNames(panel, DELTA, BETA, ALPHA, GAMMA);
			assertSame(alpha, panel.getSelectedAccount());
			assertEquals(4, panel.getModelAccount().getAccounts().size());
			assertEquals(AccountGrouping.BANK, ((ComboBox<?>) panel.lookup(GROUPING_CHOICE)).getValue());
			TestPanel other = new TestPanel();
			assertEquals(AccountGrouping.BANK, ((ComboBox<?>) other.lookup(GROUPING_CHOICE)).getValue());
			((ComboBox<?>) panel.lookup(GROUPING_CHOICE)).getSelectionModel().select(0);
			assertEquals(AccountGrouping.NONE, GuiLayoutState.accountGroupingProperty().get());
			assertEquals(AccountGrouping.NONE, ((ComboBox<?>) other.lookup(GROUPING_CHOICE)).getValue());
			assertNames(panel, GAMMA, DELTA, BETA, ALPHA);
		});
	}

	@Test
	void shouldCountFilteredAccountsAndRefreshGroupBoundaries() {
		JavaFxTestSupport.runFx(() -> {
			TestPanel panel = new TestPanel();
			GuiLayoutState.accountGroupingProperty().set(AccountGrouping.TYPE);
			assertEquals("(4)", panel.count());
			panel.filter("konto");
			assertEquals("(4/4)", panel.count());
			panel.filter("Gamma");
			assertEquals("(1/4)", panel.count());
			panel.applyCss();
			panel.layout();
			assertEquals(List.of(AccountGrouping.TYPE.label(panel.table().getItems().get(0))), visibleGroupLabels(panel));
			panel.filter("missing");
			assertEquals("(0/4)", panel.count());
			panel.applyCss();
			panel.layout();
			assertTrue(visibleGroupLabels(panel).isEmpty());
			panel.filter("");
			panel.getModelAccount().removeAccount(panel.getModelAccount().getAccounts().get(0));
			assertEquals("(3)", panel.count());
		});
	}

	@Test
	void shouldGroupBanksByBlzAndUseBicOnlyWhenBlzIsMissing() {
		JavaFxTestSupport.runFx(() -> {
			TestPanel panel = new TestPanel();
			String bankName = "Bank";
			BankAccount alpha = namedAccount(1, ALPHA, bankName, AccountType.CURRENT_ACCOUNT, " " + FIRST_BLZ + " ", "FIRST");
			BankAccount beta = namedAccount(2, BETA, "Another name", AccountType.CURRENT_ACCOUNT, FIRST_BLZ, "OTHER");
			BankAccount gamma = namedAccount(3, GAMMA, bankName, AccountType.CURRENT_ACCOUNT, "20000000", "FIRST");
			BankAccount delta = namedAccount(4, DELTA, bankName, AccountType.CURRENT_ACCOUNT, " ", "abcddef1xxx");
			BankAccount echo = namedAccount(5, "Echo", null, AccountType.CURRENT_ACCOUNT, null, " ABCDDEF1XXX ");
			BankAccount foxtrot = namedAccount(6, "Foxtrot", bankName, AccountType.CURRENT_ACCOUNT, null, "ZZZZDEF1XXX");
			BankAccount unknown = namedAccount(7, "Unknown", bankName, AccountType.CURRENT_ACCOUNT, null, null);
			panel.getModelAccount().getAccounts().setAll(foxtrot, echo, gamma, beta, unknown, delta, alpha);
			GuiLayoutState.accountGroupingProperty().set(AccountGrouping.BANK);
			assertNames(panel, "Unknown", ALPHA, BETA, GAMMA, DELTA, "Echo", "Foxtrot");
			panel.applyCss();
			panel.layout();
			assertEquals(5, visibleGroupLabels(panel).size());
			panel.filter("echo");
			panel.applyCss();
			panel.layout();
			assertEquals(List.of("ABCDDEF1XXX"), visibleGroupLabels(panel));
			echo.setBlz("30000000");
			assertEquals("30000000", AccountGrouping.BANK.label(echo));
		});
	}

	@Test
	void shouldRenderHalfHeightSeparatorsWithoutChangingAccountSelection() {
		JavaFxTestSupport.runFx(() -> {
			AccountSelectionTarget target = mock(AccountSelectionTarget.class);
			when(target.getPageContext()).thenReturn(PageContext.ACCOUNTS_TRANSACTIONS);
			when(target.canChangeAccountSelection()).thenReturn(true);
			TestPanel panel = new TestPanel(target);
			GuiLayoutState.accountGroupingProperty().set(AccountGrouping.TYPE);
			panel.applyCss();
			panel.layout();
			assertEquals(3, visibleGroupLabels(panel).size());
			TableRow<?> first = panel.lookupAll(".table-row-cell").stream().filter(node -> node instanceof TableRow<?>)
					.map(node -> (TableRow<?>) node).filter(row -> row.getIndex() == 0).findFirst().orElseThrow();
			TableRow<?> second = panel.lookupAll(".table-row-cell").stream().filter(node -> node instanceof TableRow<?>)
					.map(node -> (TableRow<?>) node).filter(row -> row.getIndex() == 1).findFirst().orElseThrow();
			assertEquals(30, second.getHeight(), 0.1);
			assertEquals(15, first.getHeight() - second.getHeight(), 0.1);
			assertEquals(15, first.lookup(".account-group-separator").getBoundsInParent().getHeight(), 0.1);
			BankAccount selected = panel.getModelAccount().getAccounts().get(0);
			panel.selectAccount(selected);
			first.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 10, 5, 10, 5, MouseButton.PRIMARY,
					1, false, false, false, false, true, false, false, false, false, true, null));
			assertSame(selected, panel.getSelectedAccount());
			panel.table().getSelectionModel().select(0);
			first.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 10, 5, 10, 5, MouseButton.PRIMARY,
					1, false, false, false, false, true, false, false, false, false, true, null));
			verify(target, never()).handleAccountReselection(panel.getSelectedAccount());
			ComboBox<?> choice = (ComboBox<?>) panel.lookup(GROUPING_CHOICE);
			assertTrue(choice.localToScene(choice.getBoundsInLocal()).getMaxX() <= 335);
			Label count = (Label) panel.lookup("#account-count");
			assertTrue(count.localToScene(count.getBoundsInLocal()).getMaxX() < choice.localToScene(choice.getBoundsInLocal()).getMinX());
		});
	}

	private static List<String> visibleGroupLabels(TestPanel panel) {
		return panel.lookupAll(".account-group-label").stream()
				.filter(node -> node instanceof Label && node.getParent().isVisible()).map(node -> ((Label) node).getText()).toList();
	}

	private static void assertNames(TestPanel panel, String... names) {
		assertEquals(List.of(names), panel.table().getItems().stream().map(account -> account.getAccountName()).toList());
	}

	private static class TestPanel extends AccountListPanel {
		TestPanel() {
			this(() -> PageContext.ACCOUNTS_TRANSACTIONS);
		}

		TestPanel(AccountSelectionTarget target) {
			super(target, AccountListScope.ALL, "UI_PANEL_ACCOUNT",
					new AccountTableModel(List.of(namedAccount(3, GAMMA, "ING", AccountType.CURRENT_ACCOUNT, "20000000", null),
							namedAccount(2, BETA, " dKb ", AccountType.OVERNIGHT_MONEY, FIRST_BLZ, null),
							namedAccount(1, ALPHA, "DKB", AccountType.CURRENT_ACCOUNT, FIRST_BLZ, null),
							namedAccount(4, DELTA, null, null, null, null))));
			Scene scene = new Scene(this, 335, 400);
			scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
			applyCss();
			layout();
		}

		void filter(String text) {
			filterText.setText(text);
			applyFilter();
		}

		String count() {
			return ((Label) lookup("#account-count")).getText();
		}

		TableView<BankAccount> table() {
			return tableView;
		}
	}

	private static BankAccount namedAccount(int id, String name, String bank, AccountType type, String blz, String bic) {
		BankAccount account = new BankAccount();
		account.setId(id);
		account.setAccountName(name);
		account.setBankName(bank);
		account.setAccountType(type);
		account.setBlz(blz);
		account.setBic(bic);
		return account;
	}

	@Test
	void shouldHideOnlyPortfolioAccountsFromGeneralAccountList() {
		assertFalse(AccountListPanel.isGeneralAccount(account(AccountType.DEPOT)));
		assertTrue(AccountListPanel.isGeneralAccount(account(AccountType.CURRENT_ACCOUNT)));
		assertTrue(AccountListPanel.isGeneralAccount(account(AccountType.DEPOT_ACCOUNT)));
	}

	@Test
	void allScopeShouldIgnoreOnlineViewSetting() {
		assertFalse(AccountListScope.ALL.usesOnlineFilter(true));
		assertFalse(AccountListScope.FOLLOW_VIEW_SETTING.usesOnlineFilter(false));
		assertTrue(AccountListScope.FOLLOW_VIEW_SETTING.usesOnlineFilter(true));
		assertTrue(AccountListScope.ONLINE_ONLY.usesOnlineFilter(false));
	}

	private BankAccount account(AccountType accountType) {
		BankAccount account = new BankAccount();
		account.setAccountType(accountType);
		return account;
	}
}
