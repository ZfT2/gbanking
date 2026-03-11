package de.gbanking;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.AbstractPinTanPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.structures.Konto;

import de.gbanking.util.TypeConverter;

public class GBankingLoggingHandler {
	
	private static Logger log = LogManager.getLogger(GBankingLoggingHandler.class);
	
	public static GBankingLoggingHandler getInstance() {
		return new GBankingLoggingHandler();
	}
	
	void logRetrivedBankAccessInfo(HBCIPassport passport, boolean withPD) {
		if (withPD) {
			log.info(
					"Access: InstName: {}, Host: {}, Port: {}, filterType: {}, " 
							+ "HBCIVersion: {}, SuppVersion: {}, "
							+ "BPDVersion: {}, BPD: {}, UPDVersion: {}, UPD: {}, "
							+ "UserId: {}, CustomerId: {}, Country: {}, DefaultLang: {}",
							passport::getInstName, passport::getHost, passport::getPort, passport::getFilterType,
							passport::getHBCIVersion, () ->  Arrays.toString(passport.getSuppVersions()), 
							passport::getBPDVersion, passport::getBPD, passport::getUPDVersion, passport::getUPD, 
							passport::getUserId, passport::getCustomerId, passport::getCountry, passport::getDefaultLang);
		} else {
			log.info(
					"Access: InstName: {}, Host: {}, Port: {}, FilterType: {}, " 
							+ "HBCIVersion: {}, SuppVersion: {}, "
							+ "BPDVersion: {}, UPDVersion: {}, "
							+ "UserId: {}, CustomerId: {}, Country: {}, DefaultLang: {}",
							passport::getInstName, passport::getHost, passport::getPort, passport::getFilterType,
							passport::getHBCIVersion, () -> Arrays.toString(passport.getSuppVersions()), 
							passport::getBPDVersion, passport::getUPDVersion,
							passport::getUserId, passport::getCustomerId, passport::getCountry, passport::getDefaultLang);
		}
		if (passport instanceof AbstractHBCIPassport) {
			AbstractPinTanPassport pinTanPassport = ((AbstractPinTanPassport) passport);
			log.info("Access HBCIPassport Details: AllowedTwostepMechanisms: {}, CurrentTANMethod: {}", 
					() -> TypeConverter.toCommaSeparatedString(pinTanPassport.getAllowedTwostepMechanisms()),  () -> pinTanPassport.getCurrentTANMethod(false));
		}
	}
	
	void logRetrievedAccountInfo(Konto konto) {
		log.info(
				"Konto: acctype {}, bic {}, blz {}, country {}, creditorid {}, curr {}, customerid {}, iban {}, limit {}, name {}, name2 {}, number {}, subnumber {}, type {}, isSEPAAccount? {}",
				konto.acctype, konto.bic, konto.blz, konto.country, konto.creditorid, konto.curr,
				konto.customerid, konto.iban, konto.limit, konto.name, konto.name2, konto.number,
				konto.subnumber, konto.type, konto.isSEPAAccount());
	}
	
	void logRetrivedBookingInfo(UmsLine buchung) {
		log.info(
				"Ums: id {}, additional {}, addkey {}, bdate {}, charge_value {}, customerref {}, "
				+ "endToEndId {}, gvcode {}, instref {}, isCamt {}, isSepa {}, isStorno {}, "
				+ "mandateId {}, orig_value {}, other {}, primanota {}, purposecode? {}, saldo {}, text {}, usage {}, value {}, valuta {}",
				buchung.id, buchung.additional, buchung.addkey, buchung.bdate, buchung.charge_value, buchung.customerref,
				buchung.endToEndId, buchung.gvcode, buchung.instref, buchung.isCamt, buchung.isSepa, buchung.isStorno,
				buchung.mandateId, buchung.orig_value, buchung.other, buchung.primanota, buchung.purposecode, buchung.saldo, buchung.text, buchung.usage, buchung.value, buchung.valuta);
	}

}
