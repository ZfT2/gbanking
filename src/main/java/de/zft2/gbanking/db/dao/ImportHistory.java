package de.zft2.gbanking.db.dao;

import java.io.Serializable;

public class ImportHistory extends Dao implements Serializable {

	private static final long serialVersionUID = -3026188750800845830L;

	private String importFileName;

	public ImportHistory() {
	}

	public ImportHistory(String importFileName) {
		this.importFileName = importFileName;
	}

	public String getImportFileName() {
		return importFileName;
	}

	public void setImportFileName(String importFileName) {
		this.importFileName = importFileName;
	}
}
