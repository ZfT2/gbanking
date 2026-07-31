package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;
import de.zft2.gbanking.messages.Messages;

public enum OrderType implements IdType, LocalizedEnumValue {
	
	TRANSFER(1),
	REALTIME_TRANSFER(2),
	URGENT_TRANSFER(6),
	SCHEDULED_TRANSFER(3),
	STANDING_ORDER(4),
	FOREIGN_TRANSFER(5);

	private final int dbStateId;

	private OrderType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static OrderType forInt(int intValue) {
		return IdType.forId(OrderType.class, intValue);
	}

	public static OrderType forString(String strValue) {
		return LocalizedEnumValue.forString(OrderType.class, strValue);
	}

	public String getPlural() {
		return Messages.getInstance().getMessage(getMessageKey() + "_PLURAL");
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}
}
