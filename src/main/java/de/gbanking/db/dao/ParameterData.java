package de.gbanking.db.dao;

import java.util.Objects;

import de.gbanking.db.dao.enu.ParameterDataType;

public class ParameterData extends Dao {

	protected String pdKey;
	protected ParameterDataType pdType;
	
	public ParameterData() {
		super();
	}

	protected ParameterData(ParameterDataType pdType) {
		super();
		this.pdType = pdType;
	}

	public ParameterData(String pdKey, ParameterDataType pdType) {
		super();
		this.pdKey = pdKey;
		this.pdType = pdType;
	}

	protected ParameterData(ParameterData parameterData) {
		id = parameterData.getId();
		pdKey = parameterData.getPdKey();
		pdType	= parameterData.getPdType();
	}

	public String getPdKey() {
		return pdKey;
	}

	public void setPdKey(String pdKey) {
		this.pdKey = pdKey;
	}

	public ParameterDataType getPdType() {
		return pdType;
	}

	public void setPdType(ParameterDataType pdType) {
		this.pdType = pdType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(pdKey, pdType);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass() &&
				!(getClass().isAssignableFrom(obj.getClass()) || obj.getClass().isAssignableFrom(getClass())))
			return false;
		ParameterData other = (ParameterData) obj;
		return Objects.equals(pdKey, other.pdKey) && pdType == other.pdType;
	}
}
