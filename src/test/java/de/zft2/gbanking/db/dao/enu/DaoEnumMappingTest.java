package de.zft2.gbanking.db.dao.enu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.enu.IdType;

class DaoEnumMappingTest {

	@Test
	void idTypeEnums_shouldResolveKnownIds() {
		assertSame(AccountIdentifierType.ACCOUNT_TRANSFER, AccountIdentifierType.forInt(2));
		assertSame(AccountRetrievalStatus.WRONG_PIN, AccountRetrievalStatus.forInt(3));
		assertSame(AccountState.ACTIVE, AccountState.forInt(1));
		assertSame(AccountType.CURRENT_ACCOUNT, AccountType.forInt(1));
		assertSame(BookingType.REMOVAL, BookingType.forInt(2));
		assertSame(DataType.BIGDECIMAL, DataType.forInt(4));
		assertSame(HbciEncodingFilterType.BASE64, HbciEncodingFilterType.forInt(1));
		assertSame(InstituteStatus.DUPLICATE, InstituteStatus.forInt(2));
		assertSame(MoneyTransferStatus.ERROR, MoneyTransferStatus.forInt(4));
		assertSame(MoneyTransferStatus.INVENTORY, MoneyTransferStatus.forInt(5));
		assertSame(MoneyTransferStatus.DELETED, MoneyTransferStatus.forInt(6));
		assertSame(MoneyTransferStatus.SUPERSEDED, MoneyTransferStatus.forInt(8));
		assertSame(MoneyTransferStatus.NOT_IN_BANK_INVENTORY, MoneyTransferStatus.forInt(9));
		assertSame(MoneyTransferStatus.DELETE_PENDING, MoneyTransferStatus.forInt(10));
		assertSame(OrderType.STANDING_ORDER, OrderType.forInt(4));
		assertSame(OrderType.FOREIGN_TRANSFER, OrderType.forInt(5));
		assertSame(OrderType.URGENT_TRANSFER, OrderType.forInt(6));
		assertSame(ParameterDataType.BPD, ParameterDataType.forInt(1));
		assertSame(ParameterDataType.UPD, ParameterDataType.forInt(2));
		assertSame(Source.MANUELL_NEW, Source.forInt(14));
		assertSame(StandingorderMode.QUARTERLY, StandingorderMode.forInt(3));
		assertSame(TanProcedure.CHIP_TAN, TanProcedure.forInt(8));
	}

	@Test
	void idTypeEnums_shouldReturnNullForUnknownIds() {
		assertNull(AccountIdentifierType.forInt(99));
		assertNull(AccountRetrievalStatus.forInt(99));
		assertNull(AccountState.forInt(99));
		assertNull(ParameterDataType.forInt(99));
		assertNull(TanProcedure.forInt(99));
	}

	@Test
	void idTypeEnums_shouldHaveUniqueDbStateIds() {
		assertUniqueDbStateIds(AccountIdentifierType.class);
		assertUniqueDbStateIds(AccountRetrievalStatus.class);
		assertUniqueDbStateIds(AccountState.class);
		assertUniqueDbStateIds(AccountType.class);
		assertUniqueDbStateIds(BookingType.class);
		assertUniqueDbStateIds(DataType.class);
		assertUniqueDbStateIds(HbciEncodingFilterType.class);
		assertUniqueDbStateIds(InstituteStatus.class);
		assertUniqueDbStateIds(MoneyTransferStatus.class);
		assertUniqueDbStateIds(OrderType.class);
		assertUniqueDbStateIds(ParameterDataType.class);
		assertUniqueDbStateIds(Source.class);
		assertUniqueDbStateIds(StandingorderMode.class);
		assertUniqueDbStateIds(TanProcedure.class);
	}

	@Test
	void enumSpecificLookups_shouldResolveExpectedValues() {
		assertSame(AccountIdentifierType.ACCOUNT, AccountIdentifierType.forPropertyValue("account"));
		assertSame(AccountState.ACTIVE, AccountState.forString(AccountState.ACTIVE.toString()));
		assertSame(AccountType.CURRENT_ACCOUNT, AccountType.forString(AccountType.CURRENT_ACCOUNT.toString()));
		assertSame(BookingType.DEPOSIT, BookingType.forString("DEPOSIT"));
		assertSame(DataType.BIGDECIMAL, DataType.forType(BigDecimal.class));
		assertSame(HbciEncodingFilterType.BASE64, HbciEncodingFilterType.forString(HbciEncodingFilterType.BASE64.getDescription()));
		assertSame(OrderType.TRANSFER, OrderType.forString(OrderType.TRANSFER.toString()));
		assertSame(ParameterDataType.UPD, ParameterDataType.forString(ParameterDataType.UPD.toString()));
		assertSame(Source.IMPORT, Source.forString(Source.IMPORT.toString()));
		assertSame(StandingorderMode.MONTHLY, StandingorderMode.forString(StandingorderMode.MONTHLY.toString()));
		assertSame(TanProcedure.APP_TAN, TanProcedure.forCode(999));
		assertTrue(TanProcedure.PHOTO_TAN.getCodes().containsAll(List.of(900, 902, 903, 932)));
		assertEquals(List.of(TanProcedure.PUSH_TAN), TanProcedure.forCodeAndDescription(921, "pushTAN"));
		assertEquals(List.of(TanProcedure.BESTSIGN), TanProcedure.forCodeAndDescription(921, "BestSign-Push"));

		assertNull(AccountState.forString("unknown"));
		assertNull(DataType.forType(StringBuilder.class));
		assertNull(TanProcedure.forCode(-1));
	}

	@Test
	void sourceCorresponding_shouldRoundTripBetweenExistingAndNewSources() {
		for (Source source : Source.values()) {
			Source corresponding = source.getCorresponding();

			assertEquals(source.getGroup(), corresponding.getGroup());
			assertEquals(source.getSymbol(), corresponding.getSymbol());
			assertNotEquals(source.isNew(), corresponding.isNew());
			assertSame(source, corresponding.getCorresponding());
		}
	}

	private static <E extends Enum<E> & IdType> void assertUniqueDbStateIds(Class<E> enumClass) {
		Set<Integer> ids = new HashSet<>();
		for (E value : enumClass.getEnumConstants()) {
			assertTrue(ids.add(value.getDbStateId()), () -> enumClass.getSimpleName() + " has duplicate id " + value.getDbStateId());
		}
	}
}
