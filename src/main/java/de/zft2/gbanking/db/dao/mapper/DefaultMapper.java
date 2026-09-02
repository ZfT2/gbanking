package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.exception.GBankingException;

public class DefaultMapper extends AbstractDaoMapper<Dao, Void> {

	public DefaultMapper() {
		super(unsupportedResultFactory(DefaultMapper.class));
	}

	@Override
	public void setParamsForeignKeyUpdate(List<Integer> idList, Dao targetDao, PreparedStatement ps) throws SQLException {

		ps.setInt(1, targetDao.getId());

		int index = 2;
		for (int pkId : idList) {
			ps.setInt(index++, pkId);
		}

		/**
		 * Array bookingIdsArray = connection.createArrayOf("VARCHAR",
		 * recipientEntry.getValue().toArray()); ps.setArray(2, bookingIdsArray);
		 **/
	}

	@Override
	public void setParamsFull(Dao dao, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsFull(Dao dao, PreparedStatement ps): not implemented for type " + this.getClass().getName());
	}
}
