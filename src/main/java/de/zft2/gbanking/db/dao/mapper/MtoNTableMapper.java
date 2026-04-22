package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public class MtoNTableMapper extends AbstractDaoMapper<Dao, Integer> {

	@Override
	public void setParamsMn(Dao mTable, Set<Integer> entitySet, PreparedStatement ps) throws SQLException {

		ps.setInt(1, mTable.getId());
		for (Integer nTableId : entitySet) {
			ps.setInt(2, nTableId);
			ps.setDate(3, TypeConverter.toSqlDateNow());
			ps.addBatch();
		}
	}

	@Override
	public void setParamsFull(Dao dao, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsFull(Dao dao, PreparedStatement ps): not implemented for type " + this.getClass().getName());

	}

}
