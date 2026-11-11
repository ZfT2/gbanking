package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockCorporateActionMemberRole implements IdType {
	PRIMARY(1), RELATED(2);

	private final int id;

	StockCorporateActionMemberRole(int id) { this.id = id; }

	@Override
	public int getDbStateId() { return id; }
}
