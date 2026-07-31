package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.util.TypeConverter;

public class InstituteMapper extends AbstractDaoMapper<Institute, Void> {

	@Override
	public void setParamsFull(Institute institute, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setString(index++, institute.getBlz());
		ps.setString(index++, institute.getBic());
		ps.setString(index++, institute.getBankName());
		ps.setString(index++, institute.getPlace());
		ps.setInt(index++, institute.getStateType().getDbStateId());
		index = setIntegerNullable(index, institute.getImportFile(), ps);
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());

		checkAndSetId(institute, ps, index);
	}

	private void checkAndSetId(Institute institute, PreparedStatement ps, int index) throws SQLException {
		if (institute.getId() > 0) {
			ps.setInt(index, institute.getId());
		}
	}

	@Override
	public void mapDao(Institute institute, ResultType resultType, ResultSet rs) throws SQLException {
		institute.setBlz(rs.getString("blz"));
		institute.setBic(rs.getString("bic"));
		institute.setBankName(rs.getString("bankName"));
		institute.setPlace(rs.getString("place"));
		institute.setLastChanged(TypeConverter.toLocalDateFromSqlDate(rs.getDate("lastChanged")));
		institute.setStateType(InstituteStatus.forInt(rs.getInt("stateType")));
		institute.setImportFile(getIntegerNullable("importFile", rs));
		institute.setImportFileName(rs.getString("importFileName"));

		institute.setImportNumber(rs.getInt("importNumber"));
		institute.setDataCenter(rs.getString("dataCenter"));
		institute.setOrganisation(rs.getString("organisation"));
		institute.setHbciDns(rs.getString("hbciDns"));
		institute.setHbciIp(rs.getString("hbciIp"));
		institute.setHbciVersion(getDoubleNullable("hbciVersion", rs));
		institute.setDdv(rs.getString("ddv"));
		institute.setRdh1(getBooleanNullable("rdh1", rs));
		institute.setRdh2(getBooleanNullable("rdh2", rs));
		institute.setRdh3(getBooleanNullable("rdh3", rs));
		institute.setRdh4(getBooleanNullable("rdh4", rs));
		institute.setRdh5(getBooleanNullable("rdh5", rs));
		institute.setRdh6(getBooleanNullable("rdh6", rs));
		institute.setRdh7(getBooleanNullable("rdh7", rs));
		institute.setRdh8(getBooleanNullable("rdh8", rs));
		institute.setRdh9(getBooleanNullable("rdh9", rs));
		institute.setRdh10(getBooleanNullable("rdh10", rs));
		institute.setPinUrl(rs.getString("pinUrl"));
		institute.setVersion(rs.getString("version"));

		institute.setDatasetNumber(rs.getString("datasetNumber"));
		institute.setFeature(rs.getInt("feature"));
		institute.setPostcode(rs.getString("postcode"));
		institute.setBankNameShort(rs.getString("bankNameShort"));
		institute.setPan(rs.getString("pan"));
		institute.setCheckdigitMethod(rs.getString("checkdigitMethod"));
		String featureChange = rs.getString("featureChange");
		if (featureChange != null && !featureChange.isEmpty())
			institute.setFeatureChange(featureChange.charAt(0));
		institute.setBlzDeletion(rs.getInt("blzDeletion"));
		institute.setBlzSuccession(rs.getString("blzSuccession"));

		institute.setCountry(rs.getString("country"));
		institute.setAddress(rs.getString("address"));
		institute.setReadinessDate(rs.getString("readinessDate"));
		institute.setSchemeLeavingDate(rs.getString("schemeLeavingDate"));
		institute.setSchemeOptions(rs.getString("schemeOptions"));
	}

	public boolean hasDkData(Institute institute) {
		return institute.getImportNumber() != 0 || hasText(institute.getDataCenter()) || hasText(institute.getOrganisation()) || hasText(institute.getHbciIp())
				|| (institute.getHbciVersion() != null) || hasText(institute.getDdv()) || hasText(institute.getVersion());
	}

	public boolean hasDbbData(Institute institute) {
		return hasText(institute.getDatasetNumber()) || hasText(institute.getPostcode()) || hasText(institute.getBankNameShort()) || hasText(institute.getPan())
				|| hasText(institute.getCheckdigitMethod()) || hasText(institute.getBlzSuccession());
	}

	public boolean hasEpcData(Institute institute) {
		return hasText(institute.getCountry()) || hasText(institute.getAddress()) || hasText(institute.getReadinessDate())
				|| hasText(institute.getSchemeLeavingDate()) || hasText(institute.getSchemeOptions());
	}

	public void setParamsDK(Institute institute, StatementType statementType, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, institute.getId());
		ps.setInt(index++, institute.getImportNumber());
		ps.setString(index++, institute.getDataCenter());
		ps.setString(index++, institute.getOrganisation());
		ps.setString(index++, institute.getHbciDns());
		ps.setString(index++, institute.getHbciIp());
		index = setDoubleNullable(index, institute.getHbciVersion(), ps);
		ps.setString(index++, institute.getDdv());
		index = setBooleanNullable(index, institute.getRdh1(), ps);
		index = setBooleanNullable(index, institute.getRdh2(), ps);
		index = setBooleanNullable(index, institute.getRdh3(), ps);
		index = setBooleanNullable(index, institute.getRdh4(), ps);
		index = setBooleanNullable(index, institute.getRdh5(), ps);
		index = setBooleanNullable(index, institute.getRdh6(), ps);
		index = setBooleanNullable(index, institute.getRdh7(), ps);
		index = setBooleanNullable(index, institute.getRdh8(), ps);
		index = setBooleanNullable(index, institute.getRdh9(), ps);
		index = setBooleanNullable(index, institute.getRdh10(), ps);
		ps.setString(index++, institute.getPinUrl());
		ps.setString(index++, institute.getVersion());
		setDateNullable(index++, TypeConverter.toSqlDateShort(institute.getLastChanged()), ps);
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());

		if (statementType == StatementType.UPDATE)
			checkAndSetId(institute, ps, index);
	}

	public void setParamsDBB(Institute institute, StatementType statementType, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, institute.getId());
		ps.setString(index++, institute.getDatasetNumber());
		ps.setInt(index++, institute.getFeature());
		ps.setString(index++, institute.getPostcode());
		ps.setString(index++, institute.getBankNameShort());
		ps.setString(index++, institute.getPan());
		ps.setString(index++, institute.getCheckdigitMethod());
		ps.setString(index++, String.valueOf(institute.getFeatureChange()));
		ps.setInt(index++, institute.getBlzDeletion());
		ps.setString(index++, institute.getBlzSuccession());
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());

		if (statementType == StatementType.UPDATE)
			checkAndSetId(institute, ps, index);
	}

	public void setParamsEPC(Institute institute, StatementType statementType, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, institute.getId());
		ps.setString(index++, institute.getCountry());
		ps.setString(index++, institute.getAddress());
		ps.setString(index++, institute.getReadinessDate());
		ps.setString(index++, institute.getSchemeLeavingDate());
		ps.setString(index++, institute.getSchemeOptions());
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());

		if (statementType == StatementType.UPDATE)
			checkAndSetId(institute, ps, index);
	}
}
