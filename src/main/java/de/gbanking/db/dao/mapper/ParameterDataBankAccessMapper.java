package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.gbanking.db.SqlFields;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.Bpd;
import de.gbanking.db.dao.Dao;
import de.gbanking.db.dao.ParameterData;
import de.gbanking.db.dao.ParameterDataBankAccess;
import de.gbanking.db.dao.Upd;
import de.gbanking.db.dao.enu.ParameterDataType;
import de.gbanking.exception.GBankingException;
import de.gbanking.util.TypeConverter;

public class ParameterDataBankAccessMapper extends AbstractDaoMapper<ParameterDataBankAccess, ParameterData> {

	@Override
	public void setParamsFull(ParameterDataBankAccess parameterDataBankAccess, PreparedStatement ps) throws SQLException {
		ps.setInt(1, parameterDataBankAccess.getBankAccessId());
		ps.setInt(2, parameterDataBankAccess.getParameterDataId());
		ps.setString(3, parameterDataBankAccess.getPdValue());
		ps.setString(4, TypeConverter.toTimestampStringNow());
	}

	@Override
	public void setParamsFull(Set<ParameterDataBankAccess> entitySet, PreparedStatement ps) throws SQLException {
		Iterator<ParameterDataBankAccess> parameterDataBankAccessIterator = entitySet.iterator();
		while (parameterDataBankAccessIterator.hasNext()) {
			ParameterDataBankAccess parameterDataBankAccess = parameterDataBankAccessIterator.next();
			setParamsFull(parameterDataBankAccess, ps);
			ps.addBatch();
		}
	}
	
	@Override
	public void setParamsFull(List<ParameterDataBankAccess> entitySet, PreparedStatement ps) throws SQLException {
		int i = 1;
		for (int x = 0; x < entitySet.size(); x++) {
			ps.setString(i++, entitySet.get(x).getPdKey());
			ps.setString(i++, entitySet.get(x).getPdType().name());
			ps.setString(i++, TypeConverter.toTimestampStringNow());
		}
	}
	
	@Override
	public void setParamsFull(List<ParameterData> entitySet, Dao mTable, PreparedStatement ps) throws SQLException {

		BankAccess bankAccess;
		try {
			bankAccess = (BankAccess) mTable;
		} catch (ClassCastException cce) {
			throw new GBankingException("Wrong ParameterDataType enum: ", cce);
		}

		int i = 1;
		for (int x = 0; x < entitySet.size(); x++) {
			ParameterDataType typ = entitySet.get(x).getPdType();
			ParameterDataBankAccess pd = typ == ParameterDataType.BPD ? new Bpd(entitySet.get(x)) : new Upd(entitySet.get(x));

			ps.setLong(i++, bankAccess.getId());
			ps.setLong(i++, pd.getId());
			ps.setString(i++, (typ == ParameterDataType.BPD ? bankAccess.getBpd() : bankAccess.getUpd()).getProperty(pd.getPdKey()));
			ps.setString(i++, TypeConverter.toTimestampStringNow());
		}
	}
	
	@Override
	public void setParamsDelete(ParameterDataBankAccess parameterDataBankAccess, PreparedStatement ps) throws SQLException {
		ps.setString(1, parameterDataBankAccess.getPdType().name());
		ps.setInt(2, parameterDataBankAccess.getBankAccessId());

//		ps.setString(1, typ.name());
//		ps.setInt(2, bankAccess.getId());
	}

	@Override
	public ParameterDataBankAccess toDao(ResultSet rs) throws SQLException {
		throw new GBankingException("ParameterDataBankAccess need to be specific type!");
	}

	protected ParameterDataBankAccess toDao(ResultSet rs, ParameterDataBankAccess pdBankAccess) throws SQLException {
		pdBankAccess.setBankAccessId(rs.getInt("bankAccess_id"));
		pdBankAccess.setPdKey(rs.getString("pdKey"));
		pdBankAccess.setPdValue(rs.getString("pdValue"));
		pdBankAccess.setUpdatedAt((TypeConverter.toCalendarFromTimestampStr(rs.getString(SqlFields.DAO_UPDATEDAT))));
		return pdBankAccess;
	}

}
