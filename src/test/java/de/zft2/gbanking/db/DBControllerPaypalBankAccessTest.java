package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.testdata.TestDataFactory;

class DBControllerPaypalBankAccessTest extends DBControllerIntegrationBaseTest {

	@Test
	void paypalBankAccesses_shouldBeDistinctByEmailAndPersistApiCredentials() {
		BankAccess first = paypalAccess("first@example.org", "api-first", "signature-first");
		BankAccess second = paypalAccess("second@example.org", "api-second", "signature-second");

		db.insertOrUpdate(first);
		db.insertOrUpdate(second);

		assertNotEquals(first.getId(), second.getId());
		BankAccess loaded = db.getBankAccessByBlzAndUserId("PAYPAL", "second@example.org");
		assertEquals(BankAccessType.PAYPAL, loaded.getAccessType());
		assertEquals("api-second", loaded.getPaypal().getApiUsername());
		assertEquals("signature-second", loaded.getPaypal().getApiSignature());
	}

	private BankAccess paypalAccess(String email, String apiUsername, String apiSignature) {
		BankAccess access = TestDataFactory.createSampleBankAccess("PAYPAL");
		access.setBankName("PayPal");
		access.getPaypal().setUserId(email);
		access.setAccessType(BankAccessType.PAYPAL);
		access.getPaypal().setApiUsername(apiUsername);
		access.getPaypal().setApiSignature(apiSignature);
		return access;
	}
}
