package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public class ParameterDataBankAccessMapper extends AbstractDaoMapper<ParameterDataBankAccess, ParameterData> {

	public ParameterDataBankAccessMapper() {
		super(unsupportedResultFactory(ParameterDataBankAccessMapper.class));
	}

	protected ParameterDataBankAccessMapper(Supplier<? extends ParameterDataBankAccess> resultFactory) {
		super(resultFactory);
	}

	@Override
	public void setParamsFull(ParameterDataBankAccess parameterDataBankAccess, PreparedStatement ps) throws SQLException {
		ps.setInt(1, parameterDataBankAccess.getBankAccessId());
		ps.setString(2, parameterDataBankAccess.getPdValue());
		ps.setTimestamp(3, TypeConverter.toSqlTimestampNow());
		ps.setInt(4, parameterDataBankAccess.getPdType().getDbStateId());
		ps.setString(5, parameterDataBankAccess.getPdKey());
	}

	public void setParamsDeleteByKey(ParameterDataBankAccess parameterDataBankAccess, PreparedStatement ps)
			throws SQLException {
		ps.setInt(1, parameterDataBankAccess.getBankAccessId());
		ps.setInt(2, parameterDataBankAccess.getPdType().getDbStateId());
		ps.setString(3, parameterDataBankAccess.getPdKey());
	}

	@Override
	public void setParamsDelete(ParameterDataBankAccess parameterDataBankAccess, PreparedStatement ps) throws SQLException {
		ps.setInt(1, parameterDataBankAccess.getPdType().getDbStateId());
		ps.setInt(2, parameterDataBankAccess.getBankAccessId());
	}

	@Override
	public void mapDao(ParameterDataBankAccess pdBankAccess, ResultType resultType, ResultSet rs) throws SQLException {
		throw new GBankingException("ParameterDataBankAccess need to be specific type!");
	}

	protected ParameterDataBankAccess toDao(ResultSet rs, ParameterDataBankAccess pdBankAccess) throws SQLException {
		pdBankAccess.setBankAccessId(rs.getInt("bankAccess_id"));
		pdBankAccess.setPdKey(rs.getString("pdKey"));
		pdBankAccess.setPdValue(rs.getString("pdValue"));
		pdBankAccess.setUpdatedAt((TypeConverter.toLocalDateFromSqlDate(rs.getDate(SqlFields.DAO_UPDATEDAT))));
		return pdBankAccess;
	}

}
