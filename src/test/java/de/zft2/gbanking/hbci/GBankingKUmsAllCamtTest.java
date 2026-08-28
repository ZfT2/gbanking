package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassportInternal;
import org.kapott.hbci.status.HBCIMsgStatus;

class GBankingKUmsAllCamtTest {

	@BeforeEach
	void initHbciUtils() {
		HBCIUtils.initThread(new Properties(), new HBCICallbackConsole());
	}

	@AfterEach
	void closeHbciUtils() {
		HBCIUtils.doneThread();
	}

	@Test
	void shouldParseUnbookedCamtWithoutBalanceAndPreserveRawResponse() {
		HBCIHandler handler = createHandler();
		GBankingKUmsAllCamt job = new GBankingKUmsAllCamt(handler);
		String originalCamt = pendingCamt();
		Properties data = new Properties();
		data.setProperty("result.notbooked", originalCamt);
		HBCIMsgStatus status = new HBCIMsgStatus();
		status.setData(data);

		job.extractResults(status, "result", 0);

		GVRKUms result = (GVRKUms) job.getJobResult();
		assertEquals(1, result.getFlatDataUnbooked().size());
		assertEquals(0, new BigDecimal("-12.34").compareTo(result.getFlatDataUnbooked().get(0).value.getBigDecimalValue()));
		assertEquals(originalCamt, result.camtNotBooked.get(0));
		assertEquals(originalCamt, data.getProperty("result.notbooked"));
	}

	@Test
	void shouldNotModifyCamtThatAlreadyContainsBalance() {
		String camt = pendingCamt().replace("<Ntry>", balance() + "<Ntry>");

		CamtPendingBalanceWorkaround.NormalizedCamt normalized = CamtPendingBalanceWorkaround.normalize(camt);

		assertEquals(0, normalized.addedBalances());
		assertEquals(camt, normalized.content());
	}

	private HBCIHandler createHandler() {
		HBCIHandler handler = mock(HBCIHandler.class);
		HBCIPassportInternal passport = mock(HBCIPassportInternal.class);
		Properties bpd = new Properties();
		bpd.setProperty("Params.KUmsZeitCamtPar1.SegHead.code", "HICAZS");
		when(passport.getBPD()).thenReturn(bpd);
		when(passport.getJobRestrictions(anyString())).thenReturn(new Properties());
		when(handler.getPassport()).thenReturn(passport);
		when(handler.getSupportedLowlevelJobs()).thenReturn(new Properties());
		when(handler.getLowlevelJobRestrictions(anyString())).thenReturn(new Properties());
		return handler;
	}

	private String pendingCamt() {
		return """
				<?xml version="1.0" encoding="ISO-8859-1"?>
				<Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.08">
				  <BkToCstmrAcctRpt>
				    <GrpHdr><MsgId>pending-test</MsgId><CreDtTm>2026-08-28T10:15:00</CreDtTm></GrpHdr>
				    <Rpt>
				      <Id>pending-report</Id>
				      <CreDtTm>2026-08-28T10:15:00</CreDtTm>
				      <Acct><Id><IBAN>DE02123456781234567890</IBAN></Id><Ccy>EUR</Ccy></Acct>
				      <Ntry>
				        <Amt Ccy="EUR">12.34</Amt>
				        <CdtDbtInd>DBIT</CdtDbtInd>
				        <Sts><Cd>PDNG</Cd></Sts>
				        <BookgDt><Dt>2026-08-28</Dt></BookgDt>
				        <ValDt><Dt>2026-08-28</Dt></ValDt>
				        <AcctSvcrRef>pending-1</AcctSvcrRef>
				        <BkTxCd/>
				        <AddtlNtryInf>Vorgemerkte Echtzeitueberweisung</AddtlNtryInf>
				      </Ntry>
				    </Rpt>
				  </BkToCstmrAcctRpt>
				</Document>
				""";
	}

	private String balance() {
		return """
				<Bal>
				  <Tp><CdOrPrtry><Cd>ITBD</Cd></CdOrPrtry></Tp>
				  <Amt Ccy="EUR">0</Amt><CdtDbtInd>CRDT</CdtDbtInd><Dt><Dt>2026-08-28</Dt></Dt>
				</Bal>
				""";
	}
}
