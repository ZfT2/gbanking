package de.zft2.gbanking.testdata;

import java.util.Properties;

public final class HbciParameterTestDataFactory {

	private HbciParameterTestDataFactory() {
	}

	public static Properties buildBpd() {
		Properties bpd = new Properties();
		bpd.setProperty("Params_31.Template2DPar.ParTemplate2D.dummy", "0;1;110000");
		bpd.setProperty("Params_52.Template2Par.SegHead.code", "HIBMLS");
		bpd.setProperty("Params_58.Template2DPar.ParTemplate2D.dummy_3", "15000");
		bpd.setProperty("Params_50.Template2DPar.SegHead.code", "HIIPSS");
		bpd.setProperty("Params_62.VoPCheckPar1.ParVoPCheck.segcode", "HKCCS");
		bpd.setProperty("Params_2.KUmsZeitPar4.ParKUmsZeit.canmaxentries", "J");
		bpd.setProperty("Params_59.Template2DPar.maxnum", "1");
		bpd.setProperty("Params_70.PinTanPar2.ParPinTan.PinTanGV_46.needtan", "J");
		bpd.setProperty("Params_72.Template2DPar.SegHead.ref", "4");
		bpd.setProperty("Params_67.Template2DPar.SegHead.version", "1");
		bpd.setProperty("Params_65.Template2DPar.ParTemplate2D.dummy", "0");
		bpd.setProperty("Params_39.Template2Par.SegHead.version", "2");
		bpd.setProperty("Params_68.TAN2StepPar6.ParTAN2Step.TAN2StepParams_3.needtanmedia", "0");
		bpd.setProperty("Params_27.TermSammelUebSEPAPar1.ParTermSammelUebSEPA.maxnum", "999");
		bpd.setProperty("Params_3.KUmsZeitPar5.SegHead.version", "5");
		bpd.setProperty("Params_70.PinTanPar2.ParPinTan.PinTanGV_46.segcode", "HKBSA");
		bpd.setProperty("Params_58.Template2DPar.ParTemplate2D.dummy_2", "500");
		bpd.setProperty("Params_45.DauerLastSEPANewPar1.ParDauerLastSEPANew.turnusmonths", "0102030612");
		bpd.setProperty("Params_68.TAN2StepPar6.ParTAN2Step.TAN2StepParams_4.name", "Smart-TAN plus optisch / USB");
		bpd.setProperty("Params_15.SEPAInfoPar1.ParSEPAInfo.suppformats_2", "sepade:xsd:pain.001.001.03.xsd");
		return bpd;
	}

	public static Properties buildUpd() {
		Properties upd = new Properties();
		upd.setProperty("_hbciversion", "300");
		upd.setProperty("KInfo.AllowedGV_3.code", "HKSSP");
		upd.setProperty("KInfo.AllowedGV_18.code", "HKKAA");
		upd.setProperty("KInfo.AllowedGV_3.reqSigs", "1");
		upd.setProperty("UPA.SegHead.code", "HIUPA");
		upd.setProperty("KInfo.AllowedGV_20.reqSigs", "1");
		upd.setProperty("UPA.usage", "0");
		upd.setProperty("KInfo.AllowedGV_24.reqSigs", "1");
		upd.setProperty("KInfo.AllowedGV_12.code", "HKBMB");
		upd.setProperty("KInfo.konto", "Termineinlage");
		return upd;
	}

	public static Properties buildBpd2() {
		Properties bpd = new Properties();
		bpd.setProperty("Params_31.Template2DPar.ParTemplate2D.dummy", "0;1;110000");
		bpd.setProperty("Params_52.Template2Par.SegHead.code", "HIBMLS");
		bpd.setProperty("Params_58.Template2DPar.ParTemplate2D.dummy_3", "20000");
		bpd.setProperty("Params_50.Template2DPar.SegHead.code", "HIIPSS");
		bpd.setProperty("Params_15.SEPAInfoPar1.ParSEPAInfo.suppformats_2", "sepade:xsd:pain.001.001.04.xsd");
		return bpd;
	}

	public static Properties buildUpd2() {
		Properties upd = new Properties();
		upd.setProperty("_hbciversion", "200");
		upd.setProperty("KInfo.AllowedGV_3.code", "HKSSP");
		upd.setProperty("KInfo.AllowedGV_18.code", "HKKAA");
		upd.setProperty("KInfo.AllowedGV_12.code", "HKBMB");
		upd.setProperty("KInfo.konto", "Kontokorrent");
		return upd;
	}

	public static Properties buildCapabilityBpd(String... businessCases) {
		Properties bpd = new Properties();
		for (int i = 0; i < businessCases.length; i++) {
			bpd.setProperty("Params_" + i + ".CapabilityPar1.SegHead.code", businessCases[i]);
		}
		return bpd;
	}

	public static Properties buildCapabilityUpd(String... businessCases) {
		Properties upd = new Properties();
		upd.setProperty("_hbciversion", "300");
		for (int i = 0; i < businessCases.length; i++) {
			upd.setProperty("KInfo.AllowedGV_" + i + ".code", businessCases[i]);
		}
		return upd;
	}
}
