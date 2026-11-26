package de.zft2.gbanking.db;

import static de.zft2.gbanking.testdata.TestDataFactory.createSecurityIdentifier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecuritySaveRequest;

class StockSecurityAdministrationServiceTest extends DBControllerIntegrationBaseTest {

	@ParameterizedTest
	@CsvSource({ "1,", "-2,-1", "-2,1" })
	void identifierSelectionShouldUseTheLowestStartedOpenEndedId(int fromDays, Integer toDays) {
		StockSecurityAdministrationService service = new StockSecurityAdministrationService();
		int securityId = service.save(request(null, "Aktie", null, null)).securityId();
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		db.insertOrUpdate(createSecurityIdentifier(securityId, StockIdentifierType.ISIN, "DE0000000001",
				today.plusDays(fromDays), toDays != null ? today.plusDays(toDays) : null));
		db.insertOrUpdate(createSecurityIdentifier(securityId, StockIdentifierType.ISIN, "DE0000000002", today, null));
		db.insertOrUpdate(createSecurityIdentifier(securityId, StockIdentifierType.ISIN, "DE0000000003", null, null));

		var details = service.getSecurities().get(0);

		assertEquals("DE0000000002", details.isin());
		assertEquals("", details.wkn());
		assertEquals("", details.ticker());
		assertEquals("", details.marketIdentifierCode());
	}

	@Test
	void shouldCreateAndUpdateSecurityAndIdentifiers() {
		StockSecurityAdministrationService service = new StockSecurityAdministrationService();
		var created = service.save(request(null, "Beispiel AG", "DE0000000001", "ABC123"));

		assertEquals("Beispiel AG", created.name());
		assertEquals("DE0000000001", created.isin());
		assertEquals("ABC123", created.wkn());

		var updated = service.save(request(created.securityId(), "Beispiel SE", "DE0000000002", "DEF456"));

		assertEquals("Beispiel SE", updated.name());
		assertEquals("DE0000000002", updated.isin());
		assertEquals("DEF456", updated.wkn());
		assertEquals(1, service.getSecurities().size());
	}

	@Test
	void shouldRejectDuplicateIsin() {
		StockSecurityAdministrationService service = new StockSecurityAdministrationService();
		service.save(request(null, "Erstes Wertpapier", "DE0000000001", null));

		SecuritySaveRequest duplicate = request(null, "Zweites Wertpapier", "DE0000000001", null);
		assertThrows(GBankingException.class, () -> service.save(duplicate));
	}

	@Test
	void shouldDeleteUnreferencedSecurityWithItsIdentifiers() {
		StockSecurityAdministrationService service = new StockSecurityAdministrationService();
		int id = service.save(request(null, "Unbenutzt AG", "DE0000000001", "ABC123")).securityId();
		assertTrue(service.canDelete(id));
		service.delete(id);
		assertTrue(service.getSecurities().isEmpty());
		assertTrue(db.getAll(StockSecurityIdentifier.class).isEmpty());
		assertFalse(service.canDelete(id));
		assertThrows(GBankingException.class, () -> service.delete(id));
	}

	private static SecuritySaveRequest request(Integer id, String name, String isin, String wkn) {
		return new SecuritySaveRequest(id, name, "Emittent", "DE", null, StockSecurityType.STOCK,
				StockQuantityType.UNITS, null, Currency.EUR, StockQuotationType.ABSOLUTE,
				null, StockSecurityState.ACTIVE, isin, wkn, null, null);
	}
}
