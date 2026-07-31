package de.zft2.gbanking.db.dao.mapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.DaoView;
import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public abstract class AbstractDaoMapper<T extends Dao, V> {

	private static Logger log = LogManager.getLogger(AbstractDaoMapper.class);

	protected AbstractDaoMapper() {
		log.debug("instantiated Mapper: {}", this.getClass().getName());
	}

	public abstract void setParamsFull(T dao, PreparedStatement ps) throws SQLException;

	public void setParamsFull(Set<T> entitySet, PreparedStatement ps) throws SQLException {
		Iterator<T> entityIterator = entitySet.iterator();
		while (entityIterator.hasNext()) {
			T entity = entityIterator.next();
			AbstractDaoMapper<T, ?> mapper = StatementsConfig.getMapperForDaoType(entity.getClass());
			mapper.setParamsFull(entity, ps);
			ps.addBatch();
		}
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

	public int setParamsSpecific(T dao, StatementsConfig.StatementType statementType, int parameterIndex, PreparedStatement ps) throws SQLException {
		if (dao == null || ps == null || parameterIndex < 1)
			throw new SQLException(String.format("needed parameters are null or invalid! statementType = %s, dao = %s, ps= %s,  parameterIdex = %d",
					statementType, dao, ps, parameterIndex));
		return parameterIndex;
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

	void mapDao(T dao, ResultType resultType, ResultSet rs) throws SQLException {
		throw new GBankingException("mapDao(T dao, ResultType resultType, ResultSet rs): not implemented for type " + this.getClass().getName());
	}

	protected int setIntegerNullable(int index, Integer value, PreparedStatement ps) throws SQLException {
		if (value != null && value <= 0)
			value = null;
		return setNullable(index, value, Types.INTEGER, ps);
	}

	protected int setBooleanNullable(int index, Boolean value, PreparedStatement ps) throws SQLException {
		return setNullable(index, value, Types.BOOLEAN, ps);
	}

	protected int setDoubleNullable(int index, Double value, PreparedStatement ps) throws SQLException {
		return setNullable(index, value, Types.DOUBLE, ps);
	}

	protected int setBigDecimalNullable(int index, BigDecimal value, PreparedStatement ps) throws SQLException {
		return setNullable(index, value == null ? null : value.doubleValue(), Types.DOUBLE, ps);
	}

	protected int setDateNullable(int index, Date value, PreparedStatement ps) throws SQLException {
		return setNullable(index, value, Types.DATE, ps);
	}

	protected int setEnumNullable(int index, IdType value, PreparedStatement ps) throws SQLException {
		Integer dbStateValue = null;
		if (value != null && value.getDbStateId() > 0) {
			dbStateValue = value.getDbStateId();
		}
		return setNullable(index, dbStateValue, Types.INTEGER, ps);
	}

	private int setNullable(int index, Object value, int type, PreparedStatement ps) throws SQLException {
		if (value == null)
			ps.setNull(index++, type);
		else {
			ps.setObject(index++, value, type);
		}
		return index;
	}

	protected Integer getIntegerNullable(final String field, ResultSet rs) throws SQLException {
		Integer value = (rs.getInt(field));
		if (rs.wasNull())
			value = null;
		return value;
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

	protected BigDecimal getBigDecimalNullable(final String field, ResultSet rs) throws SQLException {
		BigDecimal value = rs.getBigDecimal(field);
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

	protected <E extends Enum<E> & IdType> E getEnumNullable(final String field, Class<E> targetEnum, ResultSet rs) throws SQLException {
		Integer value = getIntegerNullable(field, rs);
		if (value != null)
			return IdType.forId(targetEnum, value.intValue());
		return null;
	}

	protected boolean hasColumn(ResultSet rs, String columnName) {
		try {
			rs.findColumn(columnName);
			return true;
		} catch (SQLException ex) {
			return false;
		}
	}

	T initResultDao(Class<T> type, ResultSet rs) throws SQLException {
		T dao = null;
		try {
			dao = type.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new SQLException("Could not instantiate dao type: " + type.getName(), e);
		}
		initDefaultFields(dao, rs);
		return dao;
	}

	void initDefaultFields(Dao dao, ResultSet rs) throws SQLException {
		if (!(dao instanceof DaoView))
			dao.setId(rs.getInt("id"));
		dao.setUpdatedAt((TypeConverter.toLocalDateFromSqlDate(rs.getDate(SqlFields.DAO_UPDATEDAT))));
	}

	protected boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
