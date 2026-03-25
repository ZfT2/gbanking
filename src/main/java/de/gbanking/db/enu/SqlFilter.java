package de.gbanking.db.enu;

public enum SqlFilter implements StateType {
 
	SPECIFIC_QUERY("SQL Bedingung");

	private final String translation;
	private String sql;

	private SqlFilter(String translation) {
		this.translation = translation;
	}

	public static SqlFilter forString(String strValue) {
		for (SqlFilter x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}

	public String getSql() {
		return sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	@Override
	public final String toString() {
		return translation;
	}

}
