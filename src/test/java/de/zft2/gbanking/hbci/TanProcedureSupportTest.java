package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.enu.TanProcedure;

class TanProcedureSupportTest {

	@Test
	void determineSupportedProceduresShouldResolveDuplicateCodeByBankDescription() {
		BankAccess access = createAccess(TanProcedure.PUSH_TAN, List.of("921"));
		List<Bpd> bpd = tan2StepParams("921", "pushTAN");

		List<TanProcedure> procedures = TanProcedureSupport.determineSupportedProcedures(access, bpd, List.of()).stream()
				.map(TanProcedureSupport.SupportedTanProcedure::procedure)
				.toList();

		assertEquals(List.of(TanProcedure.PUSH_TAN), procedures);
	}

	@Test
	void determineSupportedProceduresShouldKeepBankSpecificMeaningForBestSignPush() {
		BankAccess access = createAccess(TanProcedure.BESTSIGN, List.of("921"));
		List<Bpd> bpd = tan2StepParams("921", "BestSign-Push");

		List<TanProcedure> procedures = TanProcedureSupport.determineSupportedProcedures(access, bpd, List.of()).stream()
				.map(TanProcedureSupport.SupportedTanProcedure::procedure)
				.toList();

		assertEquals(List.of(TanProcedure.BESTSIGN), procedures);
	}

	@Test
	void determineSupportedProceduresShouldIncludeCardReaderProcedureWhenBankSupportsUsb() {
		BankAccess access = createAccess(TanProcedure.CHIP_TAN_USB, List.of("912"));
		List<Bpd> bpd = tan2StepParams("912", "chipTAN optisch / USB");

		List<TanProcedureSupport.SupportedTanProcedure> supportedProcedures = TanProcedureSupport.determineSupportedProcedures(access, bpd, List.of());
		List<TanProcedure> procedures = supportedProcedures.stream().map(TanProcedureSupport.SupportedTanProcedure::procedure).toList();

		assertTrue(procedures.contains(TanProcedure.CHIP_TAN_OPTICAL));
		assertTrue(procedures.contains(TanProcedure.CHIP_TAN_USB));
		assertTrue(supportedProcedures.stream()
				.filter(supportedProcedure -> supportedProcedure.procedure() == TanProcedure.CHIP_TAN_USB)
				.findFirst()
				.orElseThrow()
				.requiresConfiguredCardReader());
	}

	@Test
	void resolveTanMethodCodeShouldUseBankSupportedCodeForSelectedProcedure() {
		BankAccess access = createAccess(TanProcedure.PHOTO_TAN, List.of("902"));
		access.getFints().setBpd(toProperties(tan2StepParams("902", "photoTAN")));

		assertEquals("902", TanProcedureSupport.resolveTanMethodCode(access).orElseThrow());
	}

	@Test
	void resolveProcedureForCodeShouldUseParameterDescriptionWhenCodeIsAmbiguous() {
		BankAccess access = createAccess(null, List.of("920"));
		access.getFints().setBpd(toProperties(tan2StepParams("920", "BestSign")));

		assertEquals(TanProcedure.BESTSIGN, TanProcedureSupport.resolveProcedureForCode("920", access).orElseThrow());
	}

	private BankAccess createAccess(TanProcedure tanProcedure, List<String> allowedTwostepMechanisms) {
		BankAccess access = new BankAccess();
		access.getFints().setTanProcedure(tanProcedure);
		access.getFints().setAllowedTwostepMechanisms(allowedTwostepMechanisms);
		return access;
	}

	private List<Bpd> tan2StepParams(String code, String name) {
		return List.of(
				new Bpd("Params_1.TAN2StepPar6.ParTAN2Step.TAN2StepParams_1.secfunc", code),
				new Bpd("Params_1.TAN2StepPar6.ParTAN2Step.TAN2StepParams_1.name", name));
	}

	private java.util.Properties toProperties(List<Bpd> bpdList) {
		java.util.Properties properties = new java.util.Properties();
		for (Bpd bpd : bpdList) {
			properties.setProperty(bpd.getPdKey(), bpd.getPdValue());
		}
		return properties;
	}
}
