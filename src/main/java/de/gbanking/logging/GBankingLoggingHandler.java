package de.gbanking.logging;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.passport.AbstractPinTanPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.structures.Konto;

public class GBankingLoggingHandler {
	
	private static Logger log = LogManager.getLogger(GBankingLoggingHandler.class);
	
	public static GBankingLoggingHandler getInstance() {
		return new GBankingLoggingHandler();
	}
	
	public void logRetrivedBankAccessInfo(HBCIPassport passport, boolean withPD) {
		if (!log.isInfoEnabled()) {
			return;
		}

		if (withPD) {
			log.info(
					"Access: InstName: {}, Host: {}, Port: {}, filterType: {}, "
							+ "HBCIVersion: {}, SuppVersion: {}, "
							+ "BPDVersion: {}, BPD: {}, UPDVersion: {}, UPD: {}, "
							+ "UserId: {}, CustomerId: {}, Country: {}, DefaultLang: {}",
							passport::getInstName, passport::getHost, passport::getPort, passport::getFilterType,
							passport::getHBCIVersion, () -> Arrays.toString(passport.getSuppVersions()),
							passport::getBPDVersion, () -> SensitiveDataMasker.describePresence(passport.getBPD()), passport::getUPDVersion,
							() -> SensitiveDataMasker.describePresence(passport.getUPD()), () -> SensitiveDataMasker.maskIdentifier(passport.getUserId()),
							() -> SensitiveDataMasker.maskIdentifier(passport.getCustomerId()), passport::getCountry, passport::getDefaultLang);
		} else {
			log.info(
					"Access: InstName: {}, Host: {}, Port: {}, FilterType: {}, "
							+ "HBCIVersion: {}, SuppVersion: {}, "
							+ "BPDVersion: {}, UPDVersion: {}, "
							+ "UserId: {}, CustomerId: {}, Country: {}, DefaultLang: {}",
							passport::getInstName, passport::getHost, passport::getPort, passport::getFilterType,
							passport::getHBCIVersion, () -> Arrays.toString(passport.getSuppVersions()),
							passport::getBPDVersion, passport::getUPDVersion,
							() -> SensitiveDataMasker.maskIdentifier(passport.getUserId()),
							() -> SensitiveDataMasker.maskIdentifier(passport.getCustomerId()), passport::getCountry, passport::getDefaultLang);
		}
		if (passport instanceof AbstractPinTanPassport pinTanPassport) {
			log.info("Access HBCIPassport Details: AllowedTwostepMechanisms: {}, CurrentTANMethod: {}",
					() -> pinTanPassport.getAllowedTwostepMechanisms() != null ? pinTanPassport.getAllowedTwostepMechanisms().size() : 0,
					() -> pinTanPassport.getCurrentTANMethod(false));
		}
	}
	
	public void logRetrievedAccountInfo(Konto konto) {
		if (!log.isDebugEnabled()) {
			return;
		}

		log.info(
				"Konto: acctype {}, bic {}, blz {}, country {}, creditorid {}, curr {}, customerid {}, iban {}, limit {}, name {}, name2 {}, number {}, subnumber {}, type {}, isSEPAAccount? {}",
				konto.acctype, konto.bic, konto.blz, konto.country, konto.creditorid, konto.curr,
				SensitiveDataMasker.maskIdentifier(konto.customerid), SensitiveDataMasker.maskIban(konto.iban), konto.limit, konto.name, konto.name2,
				SensitiveDataMasker.maskAccountNumber(konto.number), SensitiveDataMasker.maskAccountNumber(konto.subnumber), konto.type, konto.isSEPAAccount());
	}
	
	public void logRetrivedBookingInfo(UmsLine buchung) {
		if (!log.isDebugEnabled()) {
			return;
		}

		log.info(
				"Ums: id {}, additional {}, addkey {}, bdate {}, charge_value {}, customerref {}, "
				+ "endToEndId {}, gvcode {}, instref {}, isCamt {}, isSepa {}, isStorno {}, "
				+ "mandateId {}, orig_value {}, other {}, primanota {}, purposecode? {}, saldo {}, text {}, usage {}, value {}, valuta {}",
				buchung.id, buchung.additional, buchung.addkey, buchung.bdate, buchung.charge_value, buchung.customerref,
				buchung.endToEndId, buchung.gvcode, buchung.instref, buchung.isCamt, buchung.isSepa, buchung.isStorno,
				buchung.mandateId, buchung.orig_value, SensitiveDataMasker.describePresence(buchung.other), buchung.primanota, buchung.purposecode, buchung.saldo,
				buchung.text, buchung.usage, buchung.value, buchung.valuta);
	}

}
