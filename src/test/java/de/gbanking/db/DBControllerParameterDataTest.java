package de.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.Bpd;
import de.gbanking.db.dao.ParameterData;
import de.gbanking.db.dao.Upd;

class DBControllerParameterDataTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - PD (BPD/UPD) insertion
	// ------------------------------------------------------------

	@Test
	void insertPD_BPD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		// prepare pd properties
		Properties bpd = TestData.buildBPD();

		ba.setBpd(bpd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Bpd> bpdList = db.getAllByParent(Bpd.class, ba.getId());

		assertEquals(20, bpdList.size());
		Bpd bpdToTest = bpdList.stream().filter(item -> "Params_70.PinTanPar2.ParPinTan.PinTanGV_46.segcode".equals(item.getPdKey()))
				.findAny().orElse(null);
		assertNotNull(bpdToTest.getPdValue());
		assertEquals("HKBSA", bpdToTest.getPdValue());
	}

	@Test
	void updatePD_BPD_addPD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		Properties bpd = TestData.buildBPD();
		ba.setBpd(bpd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Bpd> bpdList = db.getAllByParent(Bpd.class, ba.getId());
		assertEquals(20, bpdList.size());

		bpd.put("KEY_added_BP", "VALUE added BP");
		ba.setBpd(bpd);

		ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		bpdList = db.getAllByParent(Bpd.class, ba.getId());
		assertEquals(21, bpdList.size());
		Bpd bpdToFind = bpdList.stream().filter(item -> "KEY_added_BP".equals(item.getPdKey())).findAny().orElse(null);
		assertNotNull(bpdToFind.getPdValue());
		assertEquals("VALUE added BP", bpdToFind.getPdValue());
	}

	@Test
	void updatePD_BPD_deletePD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		Properties bpd = TestData.buildBPD();
		ba.setBpd(bpd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Bpd> bpdList = db.getAllByParent(Bpd.class, ba.getId());
		assertEquals(20, bpdList.size());

		bpd.remove("Params_68.TAN2StepPar6.ParTAN2Step.TAN2StepParams_4.name");
		ba.setBpd(bpd);

		ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		bpdList = db.getAllByParent(Bpd.class, ba.getId());
		assertEquals(19, bpdList.size());
		Bpd bpdToFind = bpdList.stream().filter(item -> "Params_68.TAN2StepPar6.ParTAN2Step.TAN2StepParams_4.name".equals(item.getPdKey()))
				.findAny().orElse(null);
		assertNull(bpdToFind);
	}

	@Test
	void updatePD_BPD_changePD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		Properties bpd = TestData.buildBPD();
		ba.setBpd(bpd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Bpd> bpdList = db.getAllByParent(Bpd.class, ba.getId());
		assertEquals(20, bpdList.size());

		bpd.put("Params_65.Template2DPar.ParTemplate2D.dummy", "1");
		ba.setBpd(bpd);
		ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		bpdList = db.getAllByParent(Bpd.class, ba.getId());
		assertEquals(20, bpdList.size());
		Bpd bpdToFind = bpdList.stream().filter(item -> "Params_65.Template2DPar.ParTemplate2D.dummy".equals(item.getPdKey())).findAny()
				.orElse(null);
		assertNotNull(bpdToFind.getPdValue());
		assertEquals("1", bpdToFind.getPdValue());
	}

	@Test
	void insertPD_UPD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		// prepare pd properties
		Properties upd = TestData.buildUPD();

		ba.setUpd(upd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Upd> updList = db.getAllByParent(Upd.class, ba.getId());

		assertEquals(10, updList.size());
		Upd updToTest = updList.stream().filter(item -> "UPA.SegHead.code".equals(item.getPdKey())).findAny().orElse(null);
		assertNotNull(updToTest.getPdValue());
		assertEquals("HIUPA", updToTest.getPdValue());
	}

	@Test
	void updatePD_UPD_addPD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		Properties upd = TestData.buildUPD();
		ba.setUpd(upd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Upd> updList = db.getAllByParent(Upd.class, ba.getId());
		assertEquals(10, updList.size());

		upd.put("KEY_added_UP", "VALUE added UP");
		ba.setUpd(upd);

		ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		updList = db.getAllByParent(Upd.class, ba.getId());
		assertEquals(11, updList.size());
		Upd updToFind = updList.stream().filter(item -> "KEY_added_UP".equals(item.getPdKey())).findAny().orElse(null);
		assertNotNull(updToFind.getPdValue());
		assertEquals("VALUE added UP", updToFind.getPdValue());
	}

	@Test
	void updatePD_UPD_deletePD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		Properties upd = TestData.buildUPD();
		ba.setUpd(upd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Upd> updList = db.getAllByParent(Upd.class, ba.getId());
		assertEquals(10, updList.size());

		upd.remove("KInfo.konto");
		ba.setUpd(upd);

		ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		updList = db.getAllByParent(Upd.class, ba.getId());
		assertEquals(9, updList.size());
		Upd updToFind = updList.stream().filter(item -> "KInfo.konto".equals(item.getPdKey())).findAny().orElse(null);
		assertNull(updToFind);
	}

	@Test
	void updatePD_UPD_changePD_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("88888888");
		ba = db.insertOrUpdate(ba);

		Properties upd = TestData.buildUPD();
		ba.setUpd(upd);

		boolean ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		List<Upd> updList = db.getAllByParent(Upd.class, ba.getId());
		assertEquals(10, updList.size());

		upd.put("KInfo.AllowedGV_20.reqSigs", "2");
		ba.setUpd(upd);
		ok = db.insertOrUpdatePD(ba);
		assertTrue(ok);

		updList = db.getAllByParent(Upd.class, ba.getId());
		assertEquals(10, updList.size());
		Upd updToFind = updList.stream().filter(item -> "KInfo.AllowedGV_20.reqSigs".equals(item.getPdKey())).findAny().orElse(null);
		assertNotNull(updToFind.getPdValue());
		assertEquals("2", updToFind.getPdValue());
	}
	
	@Test
	void updatePD_BPDtwoBankAccess_shouldWork() {
		BankAccess ba1 = TestData.createSampleBankAccess("88888888");
		ba1 = db.insertOrUpdate(ba1);
		
		assertTrue(ba1.getId() > 0);
		
		Properties bpd1 = new Properties();
		bpd1.put("Params_15.SEPAInfoPar1.ParSEPAInfo.suppformats_2", "sepade:xsd:pain.001.001.03.xsd");
		bpd1.put("Params_50.Template2DPar.SegHead.code", "HIIPSS");
		ba1.setBpd(bpd1);
		
		BankAccess ba2 = TestData.createSampleBankAccess("44444444");
		ba2 = db.insertOrUpdate(ba2);
		
		Properties bpd2 = new Properties();
		bpd2.put("Params_15.SEPAInfoPar1.ParSEPAInfo.suppformats_2", "sepade:xsd:pain.001.001.04.xsd");
		ba2.setBpd(bpd2);
		
		boolean ok = db.insertOrUpdatePD(ba1);
		assertTrue(ok);
		
		ok = db.insertOrUpdatePD(ba2);
		assertTrue(ok);

		List<ParameterData> pdList = db.getAll(ParameterData.class);
		assertEquals(2, pdList.size());
		
		List<Bpd> bpdList = db.getAll(Bpd.class);
		assertEquals(3, bpdList.size());
		
		bpd1.remove("Params_50.Template2DPar.SegHead.code");
		ba1.setBpd(bpd1); // delete one BPD
		ok = db.insertOrUpdatePD(ba1);
		assertTrue(ok);
		
		bpdList = db.getAll(Bpd.class);
		assertEquals(2, bpdList.size());
		
		pdList = db.getAll(ParameterData.class);
		assertEquals(1, pdList.size());
	}
	
	@Test
	void updatePD_BPD_UPD_twoBankAccess_shouldWork() {
		BankAccess ba1 = TestData.createSampleBankAccess("88888888");
		ba1 = db.insertOrUpdate(ba1);
		
		Properties bpd1 = TestData.buildBPD();
		ba1.setBpd(bpd1);
		
		Properties upd1 = TestData.buildUPD();
		ba1.setUpd(upd1);
		
		boolean ok = db.insertOrUpdatePD(ba1);
		assertTrue(ok);

		ok = db.insertOrUpdatePD(ba1);
		assertTrue(ok);

		BankAccess ba2 = TestData.createSampleBankAccess("44444444");
		ba2 = db.insertOrUpdate(ba2);
		
		Properties bpd2 = TestData.buildBPD2();
		ba2.setBpd(bpd2);
		
		Properties upd2 = TestData.buildUPD2();
		ba2.setUpd(upd2);
		
		ok = db.insertOrUpdatePD(ba2);
		assertTrue(ok);

		ok = db.insertOrUpdatePD(ba2);
		assertTrue(ok);
		
		List<ParameterData> pdList = db.getAll(ParameterData.class);
		assertEquals(30, pdList.size());
		
		List<Upd> updList = db.getAllByParent(Upd.class, ba1.getId());
		assertEquals(10, updList.size());

		upd1.put("KInfo.AllowedGV_20.reqSigs", "2");
		ba1.setUpd(upd1);
		ok = db.insertOrUpdatePD(ba1);
		assertTrue(ok);

		updList = db.getAllByParent(Upd.class, ba1.getId());
		assertEquals(10, updList.size());
		Upd updToFind = updList.stream().filter(item -> "KInfo.AllowedGV_20.reqSigs".equals(item.getPdKey())).findAny().orElse(null);
		assertNotNull(updToFind.getPdValue());
		assertEquals("2", updToFind.getPdValue());
		
		List<Bpd> bpdList2 = db.getAllByParent(Bpd.class, ba2.getId());
		assertEquals(5, bpdList2.size());
		
		List<Upd> updList2 = db.getAllByParent(Upd.class, ba2.getId());
		assertEquals(5, updList2.size());
		
		pdList = db.getAll(ParameterData.class);
		assertEquals(30, pdList.size());
		
		bpd1 = new Properties();
		bpd1.put("only one", "left");
		ba1.setBpd(bpd1);
		ok = db.insertOrUpdatePD(ba1);
		assertTrue(ok);
		
		List<Bpd> bpdList = db.getAllByParent(Bpd.class, ba1.getId());
		assertEquals(1, bpdList.size());
		
		List<Bpd> bpdListAllBa = db.getAll(Bpd.class);
		assertEquals(6, bpdListAllBa.size());
		
		List<Upd> updListAllBa = db.getAll(Upd.class);
		assertEquals(15, updListAllBa.size());
		
		pdList = db.getAll(ParameterData.class);
		assertEquals(16, pdList.size());
		
	}
}