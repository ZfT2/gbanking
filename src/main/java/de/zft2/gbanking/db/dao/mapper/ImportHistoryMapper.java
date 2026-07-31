package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.util.TypeConverter;

public class ImportHistoryMapper extends AbstractDaoMapper<ImportHistory, Void> {

	@Override
	public void setParamsFull(ImportHistory importHistory, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setString(index++, importHistory.getImportFileName());
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());

		if (importHistory.getId() > 0) {
			ps.setInt(index, importHistory.getId());
		}
	}

	@Override
	public void mapDao(ImportHistory importHistory, ResultType resultType, ResultSet rs) throws SQLException {
		importHistory.setImportFileName(rs.getString("importFileName"));
	}
}
