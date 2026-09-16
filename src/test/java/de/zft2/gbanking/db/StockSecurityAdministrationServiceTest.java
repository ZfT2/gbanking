package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecuritySaveRequest;

class StockSecurityAdministrationServiceTest extends DBControllerIntegrationBaseTest {

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

		assertThrows(GBankingException.class,
				() -> service.save(request(null, "Zweites Wertpapier", "DE0000000001", null)));
	}

	private static SecuritySaveRequest request(Integer id, String name, String isin, String wkn) {
		return new SecuritySaveRequest(id, name, "Emittent", "DE", null, StockSecurityType.STOCK,
				StockQuantityType.UNITS, null, Currency.EUR, StockQuotationType.ABSOLUTE,
				null, StockSecurityState.ACTIVE, isin, wkn, null, null);
	}
}
