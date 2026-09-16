package de.zft2.gbanking.service.stock;

import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;

public final class StockPortfolioSettings {

	public static final String SETTING_RETRIEVE_SETTLEMENT_ACCOUNTS = "stockportfolio.retrieve.settlement.accounts";

	private static final boolean DEFAULT_RETRIEVE_SETTLEMENT_ACCOUNTS = true;
	private static final String COMMENT_RETRIEVE_SETTLEMENT_ACCOUNTS =
			"Zugeordnete Verrechnungskonten beim Depotabruf automatisch mit abrufen";
	private static final SettingDefinition RETRIEVE_SETTLEMENT_ACCOUNTS = new SettingDefinition(
			SETTING_RETRIEVE_SETTLEMENT_ACCOUNTS, Boolean.toString(DEFAULT_RETRIEVE_SETTLEMENT_ACCOUNTS),
			DataType.BOOLEAN, true, true, COMMENT_RETRIEVE_SETTLEMENT_ACCOUNTS);

	private StockPortfolioSettings() {
	}

	public static void ensureSettingsExist() {
		SettingsStore.current().ensure(RETRIEVE_SETTLEMENT_ACCOUNTS);
	}

	public static boolean isSettlementAccountRetrievalEnabled() {
		ensureSettingsExist();
		return SettingsStore.current().getBoolean(SETTING_RETRIEVE_SETTLEMENT_ACCOUNTS,
				DEFAULT_RETRIEVE_SETTLEMENT_ACCOUNTS);
	}
}
