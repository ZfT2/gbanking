package de.gbanking.db.dao.mapper;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.SqlFields;
import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.Dao;
import de.gbanking.exception.GBankingException;
import de.gbanking.util.TypeConverter;

public abstract class AbstractDaoMapper<T extends Dao, V> {

	private static Logger log = LogManager.getLogger(AbstractDaoMapper.class);

	protected AbstractDaoMapper() {
		log.info("instantiated Mapper: {}", this.getClass().getName());
	}

	public abstract void setParamsFull(T dao, PreparedStatement ps) throws SQLException;

	public void setParamsFull(Set<T> entitySet, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsFull(Set<T> entitySet, PreparedStatement ps): not implemented for type " + this.getClass().getName());
	}

	public void setParamsFull(List<T> entitySet, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsFull(List<T> entitySet, PreparedStatement ps): not implemented for type " + this.getClass().getName());
	}

	public void setParamsFull(List<V> entityList, Dao mTable, PreparedStatement ps) throws SQLException {
		throw new GBankingException(
				"setParamsFull(List<V> entitySet, Dao mTable, PreparedStatement ps): not implemented for type " + this.getClass().getName());
	}

	public void setParamsMn(Dao mTable, Set<Integer> entitySet, PreparedStatement ps) throws SQLException {
		throw new GBankingException(
				"setParamsMn(Set<Integer> entitySet, Dao mTable, PreparedStatement ps): not implemented for type " + this.getClass().getName());
	}

	public void setParamsForeignKeyUpdate(Set<Integer> idList, Dao targetDao, PreparedStatement ps) throws SQLException {
		throw new GBankingException(
				"setParamsFull(Set<Integer> idList, T targetDao, PreparedStatement ps): not implemented for type " + this.getClass().getName());
	}

	public <W> void setParamsForUpdateSimpleField(T dao, Class<W> typeToUpdate, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsForUpdateSimpleField: not implemented for type " + this.getClass().getName());
	}

	public <W> void setParamsForUpdateSimpleField(List<T> entitySet, Class<W> typeToUpdate, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsForUpdateSimpleField: not implemented for type " + this.getClass().getName());
	}

	void setParamsForUpdateSource(T dao, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsForUpdateSource: not implemented for type " + this.getClass().getName());
	}

	public void setParamsFind(T dao, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsFind: not implemented for type " + this.getClass().getName());
	}

	public void setParamsDelete(T dao, PreparedStatement ps) throws SQLException {
		ps.setInt(1, dao.getId());
	}

	void mapDao(T dao, ResultSet rs) throws SQLException {
		if (dao == null)
			return;
		// throw new GBankingException("mapDao(T dao, ResultSet rs): must be called from
		// Subclass");
		dao.setId(rs.getInt("id"));
		dao.setUpdatedAt((TypeConverter.toCalendarFromSqlDate(rs.getDate(SqlFields.DAO_UPDATEDAT))));
	}

	void mapDao(T dao, ResultType resultType, ResultSet rs) throws SQLException {
		// mapDao(dao, rs);
		throw new GBankingException("toDao(ResultSet rs, ResultType resultType): not implemented for type " + this.getClass().getName());
	}

	protected int setBooleanNullable(int index, Boolean value, PreparedStatement ps) throws SQLException {
		if (value == null)
			ps.setNull(index++, Types.BOOLEAN);
		else
			ps.setBoolean(index++, value);
		return index;
	}

	protected int setDoubleNullable(int index, Double value, PreparedStatement ps) throws SQLException {
		if (value == null)
			ps.setNull(index++, Types.DOUBLE);
		else
			ps.setDouble(index++, value);
		return index;
	}

	protected int setDateNullable(int index, Date value, PreparedStatement ps) throws SQLException {
		if (value == null)
			ps.setNull(index++, Types.DATE);
		else
			ps.setDate(index++, value);
		return index;
	}

	protected Boolean getBooleanNullable(final String field, ResultSet rs) throws SQLException {
		Boolean value = (rs.getBoolean(field));
		if (rs.wasNull())
			value = null;
		return value;
	}

	protected Double getDoubleNullable(final String field, ResultSet rs) throws SQLException {
		Double value = (rs.getDouble(field));
		if (rs.wasNull())
			value = null;
		return value;
	}

	protected Date getDateNullable(final String field, ResultSet rs) throws SQLException {
		Date value = (rs.getDate(field));
		if (rs.wasNull())
			value = null;
		return value;
	}

	T initResultDao(Class<T> type, ResultSet rs) throws SQLException {
		try {
			return type.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

}
