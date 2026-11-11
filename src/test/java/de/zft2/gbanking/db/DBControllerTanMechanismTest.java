package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessTanMechanism;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.TanProcedureSupport;
import de.zft2.gbanking.testdata.TestDataFactory;

class DBControllerTanMechanismTest extends DBControllerIntegrationBaseTest {

	@Test
	void shouldRoundTripBankCodesDescriptionsAndOrderThroughEveryAccessLookup() throws Exception {
		List<String> values = List.of("921:BestSign-Push, Banklabel: individuell", "999:Unknown, future method", "907:flateXSecure");
		BankAccess access = insertAccess("11111111", values);
		List<BankAccess> loaded = List.of(
				db.getById(BankAccess.class, access.getId()),
				db.getByIdFull(BankAccess.class, access.getId()),
				db.getBankAccessById(access.getId()),
				db.getBankAccessByBlz(access.getFints().getBlz()),
				db.getBankAccessByBlzAndUserId(access.getFints().getBlz(), access.getFints().getUserId()),
				db.getAll(BankAccess.class).get(0),
				db.getAllFull(BankAccess.class).get(0));

		for (BankAccess result : loaded) {
			assertEquals(values, result.getFints().getAllowedTwostepMechanisms());
			assertEquals("921", TanProcedureSupport.resolveTanMethodCode(result).orElseThrow());
		}
		assertEquals(List.of(
				new BankAccessTanMechanism("921", "BestSign-Push, Banklabel: individuell"),
				new BankAccessTanMechanism("999", "Unknown, future method"),
				new BankAccessTanMechanism("907", "flateXSecure")), storedMechanisms(access.getId()));
	}

	@Test
	void shouldLoadMechanismsForMultipleAccessesInOneAdditionalQuery() {
		BankAccess first = insertAccess("11111111", List.of("921:BestSign", "907"));
		BankAccess second = insertAccess("22222222", List.of("946"));

		DatabaseQueryCounter.Measurement<List<BankAccess>> measurement =
				DatabaseQueryCounter.measure(() -> db.getAll(BankAccess.class));

		assertEquals(2, measurement.queryCount());
		for (BankAccess result : measurement.result()) {
			List<String> expected = result.getId() == first.getId()
					? first.getFints().getAllowedTwostepMechanisms() : second.getFints().getAllowedTwostepMechanisms();
			assertEquals(expected, result.getFints().getAllowedTwostepMechanisms());
		}
	}

	@Test
	void shouldReplaceMechanismsWithoutChangingAnotherAccess() throws Exception {
		BankAccess first = insertAccess("11111111", List.of("921:BestSign", "907", "999"));
		BankAccess second = insertAccess("22222222", List.of("946"));
		first.getFints().setAllowedTwostepMechanisms(List.of("907:New, bank label", "921"));

		db.insertOrUpdate(first);

		assertEquals(first.getFints().getAllowedTwostepMechanisms(),
				db.getBankAccessById(first.getId()).getFints().getAllowedTwostepMechanisms());
		assertEquals(List.of(new BankAccessTanMechanism("946", null)), storedMechanisms(second.getId()));
		assertEquals(2, storedMechanisms(first.getId()).size());
	}

	@ParameterizedTest
	@NullAndEmptySource
	void shouldRemoveMechanismsWhenTheBankReturnsNoAllowedMethods(List<String> values) throws Exception {
		BankAccess access = insertAccess("11111111", List.of("921", "907"));
		access.getFints().setAllowedTwostepMechanisms(values);

		db.insertOrUpdate(access);

		assertTrue(storedMechanisms(access.getId()).isEmpty());
		assertTrue(db.getBankAccessById(access.getId()).getFints().getAllowedTwostepMechanisms().isEmpty());
	}

	@Test
	void shouldIgnoreBlankPlaceholdersAndKeepTheFirstOccurrenceOfEachCode() throws Exception {
		BankAccess access = insertAccess("11111111",
				Arrays.asList(null, " ", "921:BestSign, first", "921:Ignored duplicate", "907"));

		assertEquals(List.of(new BankAccessTanMechanism("921", "BestSign, first"),
				new BankAccessTanMechanism("907", null)), storedMechanisms(access.getId()));
	}

	@Test
	void shouldRollBackAccessChangesAndDeletedMechanismsWhenReplacementFails() throws Exception {
		BankAccess access = insertAccess("11111111", List.of("921:Original", "907"));
		String originalName = access.getBankName();
		access.setBankName("Must be rolled back");
		access.getFints().setAllowedTwostepMechanisms(List.of("946", "999"));
		try (var statement = DBController.getConnection().createStatement()) {
			statement.execute("""
					CREATE TRIGGER reject_test_tan_mechanism BEFORE INSERT ON bankAccessTanMechanism
					WHEN NEW.mechanismCode = '999'
					BEGIN SELECT RAISE(FAIL, 'simulated mechanism write failure'); END
					""");
			try {
				assertThrows(GBankingException.class, () -> db.insertOrUpdate(access));
			} finally {
				statement.execute("DROP TRIGGER reject_test_tan_mechanism");
			}
		}

		BankAccess restored = db.getBankAccessById(access.getId());
		assertEquals(originalName, restored.getBankName());
		assertEquals(List.of("921:Original", "907"), restored.getFints().getAllowedTwostepMechanisms());
	}

	@Test
	void shouldDeleteMechanismsWithTheAccessOrItsFintsDetails() throws Exception {
		BankAccess removed = insertAccess("11111111", List.of("921"));
		BankAccess changed = insertAccess("22222222", List.of("946"));

		db.delete(removed, null);
		changed.setAccessType(BankAccessType.PAYPAL);
		changed.getPaypal().setUserId("paypal-user");
		changed.getPaypal().setApiUsername("paypal-api-user");
		changed.getPaypal().setApiSignature("test-signature");
		db.insertOrUpdate(changed);

		assertTrue(storedMechanisms(removed.getId()).isEmpty());
		assertTrue(storedMechanisms(changed.getId()).isEmpty());
	}

	private BankAccess insertAccess(String blz, List<String> values) {
		BankAccess access = TestDataFactory.createSampleBankAccess(blz);
		access.getFints().setTanProcedure(TanProcedure.BESTSIGN);
		access.getFints().setAllowedTwostepMechanisms(values);
		return db.insertOrUpdate(access);
	}

	private List<BankAccessTanMechanism> storedMechanisms(int bankAccessId) throws Exception {
		List<BankAccessTanMechanism> result = new ArrayList<>();
		try (var statement = DBController.getConnection().prepareStatement("""
				SELECT mechanismCode, description, sortOrder
				FROM bankAccessTanMechanism WHERE bankAccess_id = ? ORDER BY sortOrder
				""")) {
			statement.setInt(1, bankAccessId);
			try (var rows = statement.executeQuery()) {
				while (rows.next()) {
					assertEquals(result.size(), rows.getInt("sortOrder"));
					result.add(new BankAccessTanMechanism(rows.getString("mechanismCode"), rows.getString("description")));
				}
			}
		}
		return result;
	}
}
