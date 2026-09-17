package de.zft2.gbanking.db.dao.enu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.enu.IdType;

class StockEnumMappingTest {

	private static final List<Class<? extends Enum<?>>> ID_TYPES = List.of(
			StockCashLegRole.class,
			StockCouponRateStatus.class,
			StockCustodyType.class,
			StockDataSourceType.class,
			StockDayCountConvention.class,
			StockFactorType.class,
			StockIdentifierType.class,
			StockImportRecordStatus.class,
			StockImportStatus.class,
			StockInterestType.class,
			StockNumericValueType.class,
			StockPriceBasis.class,
			StockPriceType.class,
			StockQuantityType.class,
			StockQuotationType.class,
			StockSecurityLegRole.class,
			StockSecurityState.class,
			StockSecurityType.class,
			StockStatementStatus.class,
			StockSubBalanceQualifier.class,
			StockTransactionStatus.class,
			StockTransactionType.class);

	@Test
	void stockEnumIdsShouldBePositiveAndUnique() {
		for (Class<? extends Enum<?>> type : ID_TYPES) {
			Set<Integer> ids = new HashSet<>();
			for (Enum<?> value : type.getEnumConstants()) {
				IdType idType = assertInstanceOf(IdType.class, value);
				assertTrue(idType.getDbStateId() > 0, () -> type.getSimpleName() + " has a non-positive database ID");
				assertTrue(ids.add(idType.getDbStateId()),
						() -> type.getSimpleName() + " has duplicate database ID " + idType.getDbStateId());
			}
		}
	}

	@Test
	void numericValueTypesShouldDefineCanonicalScales() {
		assertEquals(9, StockNumericValueType.QUANTITY.getScaleDigits());
		assertEquals(1_000_000_000L, StockNumericValueType.QUANTITY.getScaleFactor());
		assertEquals(8, StockNumericValueType.PRICE.getScaleDigits());
		assertEquals(100_000_000L, StockNumericValueType.PRICE.getScaleFactor());
		assertEquals(9, StockNumericValueType.RATE.getScaleDigits());
		assertEquals(1_000_000_000L, StockNumericValueType.RATE.getScaleFactor());
		assertEquals(12, StockNumericValueType.FACTOR.getScaleDigits());
		assertEquals(1_000_000_000_000L, StockNumericValueType.FACTOR.getScaleFactor());
	}

	@Test
	void custodyTypeShouldResolveFinTsCode() {
		assertSame(StockCustodyType.COLLECTIVE_SAFE_CUSTODY, StockCustodyType.forInt(1));
		assertSame(StockCustodyType.OTHER, StockCustodyType.forInt(9));
	}
}
