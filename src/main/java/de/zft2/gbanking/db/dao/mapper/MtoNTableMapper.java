package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public class MtoNTableMapper extends AbstractDaoMapper<Dao, Integer> {

	public MtoNTableMapper() {
		super(unsupportedResultFactory(MtoNTableMapper.class));
	}

	@Override
	public void setParamsMn(Dao mTable, Integer entityId, PreparedStatement ps) throws SQLException {
		ps.setInt(1, mTable.getId());
		ps.setInt(2, entityId);
		ps.setTimestamp(3, TypeConverter.toSqlTimestampNow());
	}

	@Override
	public void setParamsFull(Dao dao, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsFull(Dao dao, PreparedStatement ps): not implemented for type " + this.getClass().getName());

	}

}
