package de.zft2.gbanking.db.dao;

import de.zft2.gbanking.db.dao.enu.ParameterDataType;

public class Upd extends ParameterDataBankAccess implements DaoView {

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
