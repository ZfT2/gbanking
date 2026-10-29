package de.zft2.gbanking.db.dao.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.DaoView;
import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public abstract class AbstractDaoMapper<T extends Dao, V> {

	private static final Logger log = LogManager.getLogger(AbstractDaoMapper.class);
	private final Supplier<? extends T> resultFactory;

	protected AbstractDaoMapper(Supplier<? extends T> resultFactory) {
		this.resultFactory = Objects.requireNonNull(resultFactory, "resultFactory");
		log.debug("instantiated Mapper: {}", this.getClass().getName());
	}

	public abstract void setParamsFull(T dao, PreparedStatement ps) throws SQLException;

	public void setParamsFull(Set<T> entitySet, PreparedStatement ps) throws SQLException {
		for (T entity : entitySet) {
			setParamsFull(entity, ps);
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
		for (Integer entityId : entitySet) {
			setParamsMn(mTable, entityId, ps);
			ps.addBatch();
		}
	}

	public void setParamsMn(Dao mTable, Integer entityId, PreparedStatement ps) throws SQLException {
		throw new GBankingException(
				"setParamsMn(Dao mTable, Integer entityId, PreparedStatement ps): not implemented for type "
						+ getClass().getName());
	}

	public void setParamsForeignKeyUpdate(List<Integer> idList, Dao targetDao, PreparedStatement ps) throws SQLException {
		throw new GBankingException(
				"setParamsForeignKeyUpdate(List<Integer>, Dao, PreparedStatement): not implemented for type " + getClass().getName());
	}

	public <W> void setParamsForUpdateSimpleField(T dao, Class<W> typeToUpdate, PreparedStatement ps) throws SQLException {
		throw new GBankingException("setParamsForUpdateSimpleField: not implemented for type " + this.getClass().getName());
	}

	public List<? extends Dao> getSimpleFieldUpdateTargets(T dao, Class<? extends Dao> typeToUpdate) {
		return List.of(dao);
	}

	public int setParamsSpecific(T dao, StatementType statementType, int parameterIndex, PreparedStatement ps) throws SQLException {
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

	public final T map(ResultSet resultSet, ResultType resultType) throws SQLException {
		T dao = initResultDao(resultSet);
		mapDao(dao, resultType, resultSet);
		return dao;
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
		if (value == null) {
			ps.setNull(index, Types.NUMERIC);
		} else {
			ps.setBigDecimal(index, value);
		}
		return index + 1;
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
		return nullIfWasNull(rs.getInt(field), rs);
	}

	protected Boolean getBooleanNullable(final String field, ResultSet rs) throws SQLException {
		return nullIfWasNull(rs.getBoolean(field), rs);
	}

	protected Double getDoubleNullable(final String field, ResultSet rs) throws SQLException {
		return nullIfWasNull(rs.getDouble(field), rs);
	}

	protected <E extends Enum<E> & IdType> E getEnumNullable(final String field, Class<E> targetEnum, ResultSet rs) throws SQLException {
		Integer value = getIntegerNullable(field, rs);
		return value != null ? IdType.forId(targetEnum, value) : null;
	}

	protected BigDecimal getScaledBigDecimalNullable(String field, ResultSet rs) throws SQLException {
		BigDecimal value = rs.getBigDecimal(field);
		return value != null ? value.setScale(2, RoundingMode.HALF_UP) : null;
	}

	// Primitive JDBC getters return 0/false for SQL NULL, so the null state has to be read via wasNull().
	private static <W> W nullIfWasNull(W value, ResultSet rs) throws SQLException {
		return rs.wasNull() ? null : value;
	}

	T initResultDao(ResultSet rs) throws SQLException {
		T dao = resultFactory.get();
		if (dao == null) {
			throw new SQLException("Result factory returned null for mapper " + getClass().getName());
		}
		initDefaultFields(dao, rs);
		return dao;
	}

	protected static <D extends Dao> Supplier<D> unsupportedResultFactory(Class<?> mapperType) {
		return () -> {
			throw new GBankingException("Result mapping is not supported by " + mapperType.getName());
		};
	}

	void initDefaultFields(Dao dao, ResultSet rs) throws SQLException {
		if (!(dao instanceof DaoView))
			dao.setId(rs.getInt("id"));
		dao.setUpdatedAt((TypeConverter.toLocalDateFromSqlDate(rs.getDate(SqlFields.DAO_UPDATEDAT))));
	}

}
