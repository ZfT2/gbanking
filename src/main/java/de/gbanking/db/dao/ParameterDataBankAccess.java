package de.gbanking.db.dao;

import java.util.Objects;

import de.gbanking.db.dao.enu.ParameterDataType;

public abstract class ParameterDataBankAccess extends ParameterData {

	private int bankAccessId;
	private int parameterDataId;
	private String pdValue;
	
	protected ParameterDataBankAccess(ParameterDataType pdType) {
		super(pdType);
	}
	
	protected ParameterDataBankAccess(String pdKey, ParameterDataType pdType) {
		super();
		this.pdKey = pdKey;
		this.pdType = pdType;
	}

	protected ParameterDataBankAccess(String key, String value, ParameterDataType bpd) {
		super();
		this.pdKey = key;
		this.pdValue = value;
		this.pdType = bpd;
	}
	
	protected ParameterDataBankAccess(ParameterData parameterData) {
		super(parameterData);
	}

	public int getBankAccessId() {
		return bankAccessId;
	}

	public void setBankAccessId(int bankAccessId) {
		this.bankAccessId = bankAccessId;
	}

	public int getParameterDataId() {
		return parameterDataId;
	}

	public void setParameterDataId(int parameterDataId) {
		this.parameterDataId = parameterDataId;
	}

	public String getPdValue() {
		return pdValue;
	}

	public void setPdValue(String pdValue) {
		this.pdValue = pdValue;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(pdValue);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!obj.getClass().isAssignableFrom(getClass()))
			return false;
		ParameterData other = (ParameterData) obj;
		return Objects.equals(pdKey, other.pdKey);
	}
}
