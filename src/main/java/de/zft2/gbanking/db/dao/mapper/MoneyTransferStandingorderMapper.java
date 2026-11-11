package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.MoneyTransferStandingorder;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.util.TypeConverter;

public class MoneyTransferStandingorderMapper extends AbstractDaoMapper<MoneyTransferStandingorder, Void> {

	public MoneyTransferStandingorderMapper() {
		super(MoneyTransferStandingorder::new);
	}

	@Override
	public void setParamsFull(MoneyTransferStandingorder standingorder, PreparedStatement ps) throws SQLException {
		setParamsForUpsert(standingorder, ps);
		if (standingorder.getId() > 0) {
			ps.setInt(6, standingorder.getId());
		}
	}

	public void setParamsForUpsert(MoneyTransferStandingorder standingorder, PreparedStatement ps) throws SQLException {
		ps.setInt(1, standingorder.getMoneyTransferId());
		setIntegerNullable(2, standingorder.getExecutionDay(), ps);
		setEnumNullable(3, standingorder.getStandingorderMode(), ps);
		ps.setString(4, TypeConverter.toDateStringShort(standingorder.getExecutionDateNext()));
		ps.setTimestamp(5, TypeConverter.toSqlTimestampNow());
	}

	@Override
	public void mapDao(MoneyTransferStandingorder standingorder, ResultType resultType, ResultSet rs) throws SQLException {
		standingorder.setMoneyTransferId(rs.getInt("moneytransfer_id"));
		standingorder.setExecutionDay(getIntegerNullable("executionDay", rs));
		standingorder.setStandingorderMode(getEnumNullable("standingorderMode", StandingorderMode.class, rs));
		standingorder.setExecutionDateNext(TypeConverter.toLocalDateFromDateStrShort(rs.getString("executionDateNext")));
	}
}
