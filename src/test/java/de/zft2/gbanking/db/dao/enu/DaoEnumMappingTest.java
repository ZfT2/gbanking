package de.zft2.gbanking.db.dao.enu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.enu.IdType;

class DaoEnumMappingTest {

	@Test
	void idTypeEnums_shouldResolveKnownIds() {
		assertSame(AccountState.ACTIVE, AccountState.forInt(1));
		assertSame(AccountType.CURRENT_ACCOUNT, AccountType.forInt(1));
		assertSame(BookingType.REMOVAL, BookingType.forInt(2));
		assertSame(CategoryRuleMode.IMPORT, CategoryRuleMode.forInt(3));
		assertSame(CategoryRuleType.COMBINED, CategoryRuleType.forInt(4));
		assertSame(DataType.BIGDECIMAL, DataType.forInt(4));
		assertSame(HbciEncodingFilterType.BASE64, HbciEncodingFilterType.forInt(1));
		assertSame(InstituteStatus.DUPLICATE, InstituteStatus.forInt(2));
		assertSame(MoneyTransferStatus.ERROR, MoneyTransferStatus.forInt(2));
		assertSame(OrderType.STANDING_ORDER, OrderType.forInt(4));
		assertSame(ParameterDataType.BPD, ParameterDataType.forInt(1));
		assertSame(ParameterDataType.UPD, ParameterDataType.forInt(2));
		assertSame(Source.MANUELL_NEW, Source.forInt(14));
		assertSame(StandingorderMode.QUARTERLY, StandingorderMode.forInt(3));
		assertSame(TanProcedure.CHIP_TAN, TanProcedure.forInt(6));
	}

	@Test
	void idTypeEnums_shouldReturnNullForUnknownIds() {
		assertNull(AccountState.forInt(99));
		assertNull(CategoryRuleType.forInt(99));
		assertNull(ParameterDataType.forInt(99));
		assertNull(TanProcedure.forInt(99));
	}

	@Test
	void idTypeEnums_shouldHaveUniqueDbStateIds() {
		assertUniqueDbStateIds(AccountState.class);
		assertUniqueDbStateIds(AccountType.class);
		assertUniqueDbStateIds(BookingType.class);
		assertUniqueDbStateIds(CategoryRuleMode.class);
		assertUniqueDbStateIds(CategoryRuleType.class);
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
		assertSame(AccountState.ACTIVE, AccountState.forString(AccountState.ACTIVE.toString()));
		assertSame(AccountType.CURRENT_ACCOUNT, AccountType.forString(AccountType.CURRENT_ACCOUNT.toString()));
		assertSame(BookingType.DEPOSIT, BookingType.forString("DEPOSIT"));
		assertSame(CategoryRuleMode.MANUAL, CategoryRuleMode.forString(CategoryRuleMode.MANUAL.toString()));
		assertSame(DataType.BIGDECIMAL, DataType.forType(BigDecimal.class));
		assertSame(HbciEncodingFilterType.BASE64, HbciEncodingFilterType.forString(HbciEncodingFilterType.BASE64.getDescription()));
		assertSame(OrderType.TRANSFER, OrderType.forString(OrderType.TRANSFER.toString()));
		assertSame(ParameterDataType.UPD, ParameterDataType.forString(ParameterDataType.UPD.toString()));
		assertSame(Source.IMPORT, Source.forString(Source.IMPORT.toString()));
		assertSame(StandingorderMode.MONTHLY, StandingorderMode.forString(StandingorderMode.MONTHLY.toString()));
		assertSame(TanProcedure.APP_TAN, TanProcedure.forCode(999));

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
			assertFalse(source.isNew() == corresponding.isNew());
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
