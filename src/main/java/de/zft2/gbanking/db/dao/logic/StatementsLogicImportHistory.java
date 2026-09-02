package de.zft2.gbanking.db.dao.logic;

import java.sql.SQLException;

import de.zft2.gbanking.db.SqlErrors;
import de.zft2.gbanking.db.SqlTemplateRepository;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicImportHistory extends StatementsLogicDefault<ImportHistory> {

	private static final String LOOKUP_INDEX_KEY = "SQL_SETUP_CREATE_INDEX_INSTITUTE_BLZ_STATE";

	@Override
	public ImportHistory insertOrUpdateSingle(ImportHistory importHistory) {
		ImportHistory persistedImportHistory = super.insertOrUpdateSingle(importHistory);
		try {
			executeSetupStatementOncePerSession(LOOKUP_INDEX_KEY, SqlTemplateRepository.getDdl(LOOKUP_INDEX_KEY));
		} catch (SQLException exception) {
			throw new GBankingException(getText(SqlErrors.ERROR_DB), exception);
		}
		return persistedImportHistory;
	}
}
