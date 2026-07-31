package de.zft2.gbanking.logging;

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

		log.debug(
				"Konto: acctype {}, bic {}, blz {}, country {}, creditorid {}, curr {}, customerid {}, iban {}, limit {}, name {}, name2 {}, number {}, subnumber {}, type {}, isSEPAAccount? {}",
				konto.acctype, konto.bic, konto.blz, konto.country, SensitiveDataMasker.maskIdentifier(konto.creditorid), konto.curr,
				SensitiveDataMasker.maskIdentifier(konto.customerid), SensitiveDataMasker.maskIban(konto.iban),
				SensitiveDataMasker.describePresence(konto.limit), SensitiveDataMasker.describeText(konto.name), SensitiveDataMasker.describeText(konto.name2),
				SensitiveDataMasker.maskAccountNumber(konto.number), SensitiveDataMasker.maskAccountNumber(konto.subnumber), konto.type, konto.isSEPAAccount());
	}
	
	public void logRetrivedBookingInfo(UmsLine buchung) {
		if (!log.isDebugEnabled()) {
			return;
		}

		log.debug(
				"Ums: id {}, additional {}, addkey {}, bdate {}, charge_value {}, customerref {}, "
				+ "endToEndId {}, gvcode {}, instref {}, isCamt {}, isSepa {}, isStorno {}, "
				+ "mandateId {}, orig_value {}, other {}, primanota {}, purposecode? {}, saldo {}, text {}, usage {}, value {}, valuta {}",
				buchung.id, SensitiveDataMasker.describeText(buchung.additional), buchung.addkey, buchung.bdate,
				SensitiveDataMasker.describeAmount(buchung.charge_value), SensitiveDataMasker.describeText(buchung.customerref),
				buchung.endToEndId, buchung.gvcode, buchung.instref, buchung.isCamt, buchung.isSepa, buchung.isStorno,
				SensitiveDataMasker.describeText(buchung.mandateId), SensitiveDataMasker.describeAmount(buchung.orig_value),
				SensitiveDataMasker.describePresence(buchung.other), buchung.primanota, buchung.purposecode,
				SensitiveDataMasker.describeAmount(buchung.saldo), SensitiveDataMasker.describeText(buchung.text),
				SensitiveDataMasker.describeText(buchung.usage), SensitiveDataMasker.describeAmount(buchung.value), buchung.valuta);
	}

}
