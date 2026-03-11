package de.gbanking.db.dao;

import de.gbanking.db.dao.enu.ParameterDataType;

public class Bpd extends ParameterDataBankAccess {

	public Bpd() {
		super(ParameterDataType.BPD);
	}

	public Bpd(String key, String value) {
		super(key, value, ParameterDataType.BPD);
	}

	public Bpd(ParameterData parameterData) {
		super(parameterData);
	}
}
