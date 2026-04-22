package de.zft2.gbanking.db.dao;

import de.zft2.gbanking.db.dao.enu.ParameterDataType;

public class Bpd extends ParameterDataBankAccess implements DaoView {

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
