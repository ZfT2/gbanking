package de.gbanking.db.dao;

import de.gbanking.db.dao.enu.ParameterDataType;

public class Upd extends ParameterDataBankAccess {

	public Upd() {
		super(ParameterDataType.UPD);
	}
	
	public Upd(String key, String value) {
		super(key, value, ParameterDataType.UPD);
	}
	
	public Upd(ParameterData parameterData) {
		super(parameterData);
	}
}
