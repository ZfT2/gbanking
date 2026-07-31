package de.zft2.gbanking.db.dao.enu;

import de.zft2.fp3xmlextract.data.Fp3XmlBooking.SepaTyp;
import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum SepaType implements IdType, LocalizedEnumValue {

	BANK_TRANSFER(SepaTyp.BANK_TRANSFER, 1),
	BANK_TRANSFER_ONLINE(SepaTyp.BANK_TRANSFER_ONLINE, 2),
	BANK_TRANSFER_EU(SepaTyp.BANK_TRANSFER_EU, 3),
	DIRECT_DEBIT(SepaTyp.DIRECT_DEBIT, 4),
	DIRECT_DEBIT_OTHER(SepaTyp.DIRECT_DEBIT_OTHER, 5),
	STANDING_ORDER(SepaTyp.STANDING_ORDER, 6),
	REBOOKING(SepaTyp.REBOOKING, 7),
	ACCOUNT_COMPLETION(SepaTyp.ACCOUNT_COMPLETION, 8),
	INTEREST(SepaTyp.INTEREST, 9),
	TAX_CAPITALGAINS(SepaTyp.TAX_CAPITALGAINS, 10),
	TAX_SOLIDARITY_SURCHARGE(SepaTyp.TAX_SOLIDARITY_SURCHARGE, 11),
	TAX_CHURCH(SepaTyp.TAX_CHURCH, 12),
	CANCELLATION(SepaTyp.CANCELLATION, 13);

	private final SepaTyp sepaTyp;
	private final int dbStateId;

	private SepaType(SepaTyp sepaTyp, int dbStateId) {
		this.sepaTyp = sepaTyp;
		this.dbStateId = dbStateId;
	}

	public static SepaType forSepaTyp(SepaTyp sepaTyp) {
		for (SepaType x : SepaType.values()) {
			if (x.sepaTyp.equals(sepaTyp))
				return x;
		}
		return null;
	}

	public static SepaType forInt(Integer intValue) {
		return IdType.forId(SepaType.class, intValue);
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
