package de.gbanking.db.dao.logic;

class SqlParameter {

	private String param1;
	private String param2;
	private String param3;
	private String param4;
	boolean idLookup;
	boolean idUpdate;
	
	public SqlParameter(String param1) {
		this.param1 = param1;
		this.param2 = null;
		this.param3 = null;
		this.param4 = null;
		this.idLookup = true;
		this.idUpdate = true;
	}

	public SqlParameter(String param1, String param2) {
		super();
		this.param1 = param1;
		this.param2 = param2;
		this.param3 = null;
		this.param4 = null;
		this.idLookup = true;
		this.idUpdate = true;
	}

	public SqlParameter(String param1, String param2, boolean idLookup, boolean idUpdate) {
		super();
		this.param1 = param1;
		this.param2 = param2;
		this.param3 = null;
		this.param4 = null;
		this.idLookup = idLookup;
		this.idUpdate = idUpdate;
	}

	public SqlParameter(String param1, String param2, String param3, String param4, boolean idLookup, boolean idUpdate) {
		super();
		this.param1 = param1;
		this.param2 = param2;
		this.param3 = param3;
		this.param4 = param4;
		this.idLookup = idLookup;
		this.idUpdate = idUpdate;
	}

	public String getParam1() {
		return param1;
	}

	public void setParam1(String param1) {
		this.param1 = param1;
	}

	public String getParam2() {
		return param2;
	}

	public void setParam2(String param2) {
		this.param2 = param2;
	}

	public String getParam3() {
		return param3;
	}

	public void setParam3(String param3) {
		this.param3 = param3;
	}

	public String getParam4() {
		return param4;
	}

	public void setParam4(String param4) {
		this.param4 = param4;
	}

	public boolean isIdLookup() {
		return idLookup;
	}

	public void setIdLookup(boolean idLookup) {
		this.idLookup = idLookup;
	}

	public boolean isIdUpdate() {
		return idUpdate;
	}

	public void setIdUpdate(boolean idUpdate) {
		this.idUpdate = idUpdate;
	}

}
