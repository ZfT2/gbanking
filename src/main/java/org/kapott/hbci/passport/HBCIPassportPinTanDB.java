package org.kapott.hbci.passport;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessFints;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.hbci.TanProcedureSupport;

/**
 * Implementation of PIN/TAN-Passport, which is persisting the data in database.
 */
public class HBCIPassportPinTanDB extends HBCIPassportPinTan implements BaseMessagesDb {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2055223807813566709L;

	/**
	 * ct.
	 * 
	 * @param init Generische Init-Daten.
	 */
	public HBCIPassportPinTanDB(Object init) {
		super(init);

		if (init instanceof String initString)
			setBLZ(initString);
	}

	/**
	 * Creates Database entry if needed.
	 */
	@Override
	protected void create() {

		String blz = (String) this.getClientData("init");
		this.setBLZ(blz);
		if (blz == null) {
			throw new NullPointerException("client.passport.PinTan.db entry BLZ must not be null");
		}
		BankAccess bankAccess = getDBController().getBankAccessByBlz(blz);
		if (bankAccess != null) {
			return;
		}

		HBCIUtils.log("have to create new passport db entry", HBCIUtils.LOG_WARN);
		askForMissingData(true, true, true, true, true, true, true);
		saveChanges();
	}

	/**
	 * Read passport data from database.
	 */
	@Override
	protected void read() {
		create();

		String blz = this.getBLZ();
		if (blz == null)
			throw new NullPointerException("client.passport.PinTan.db entry BLZ must not be null");

		DBController dbController = getDBController();
		BankAccess bankAccess = dbController.getBankAccessByBlz(blz);
		BankAccessFints fints = bankAccess.getFints();

		this.setCountry(fints.getCountry());
		this.setBLZ(fints.getBlz());
		String url = fints.getHbciURL();
		this.setHost(url != null && url.startsWith("https://") ? url : "https://" + url);
		this.setPort(fints.getPort());
		
		String userId = trimToNull(fints.getUserId());
		this.setUserId(userId);
		this.setCustomerId(firstNonBlank(fints.getCustomerId(), userId));
		this.setSysId(firstNonBlank(fints.getSysId(), "0"));
		this.setHBCIVersion(trimToNull(fints.getHbciVersion()));

		Properties bpd = toProperties(dbController.getAllByParent(Bpd.class, bankAccess.getId()));
		Properties upd = toProperties(dbController.getAllByParent(Upd.class, bankAccess.getId()));
		fints.setBpd(bpd);
		fints.setUpd(upd);
		this.setBPD(bpd);
		this.setUPD(upd);

		HbciEncodingFilterType filterType = fints.getFilterType();
		this.setFilterType(filterType != null ? filterType.getDescription() : HbciEncodingFilterType.BASE64.getDescription());
		this.setAllowedTwostepMechanisms(nonBlankList(fints.getAllowedTwostepMechanisms()));
		this.setCurrentTANMethod(toTanMethod(bankAccess));
	}

	private Properties toProperties(List<? extends ParameterDataBankAccess> parameterDataList) {
		Properties properties = new Properties();
		if (parameterDataList == null || parameterDataList.isEmpty()) {
			return properties;
		}

		for (ParameterDataBankAccess parameterData : parameterDataList) {
			if (parameterData.getPdKey() != null && parameterData.getPdValue() != null && !parameterData.getPdValue().isBlank()) {
				properties.setProperty(parameterData.getPdKey(), parameterData.getPdValue());
			}
		}
		return properties;
	}

	private List<String> nonBlankList(List<String> values) {
		List<String> result = new ArrayList<>();
		if (values == null) {
			return result;
		}

		for (String value : values) {
			if (value != null && !value.isBlank()) {
				result.add(value.trim());
			}
		}
		return result;
	}

	private String toTanMethod(BankAccess bankAccess) {
		return TanProcedureSupport.resolveTanMethodCode(bankAccess).orElse(null);
	}

	/**
	 * @see org.kapott.hbci.passport.HBCIPassport#saveChanges()
	 */
	@Override
	public void saveChanges() {
		try {

			DBController dbController = getDBController();
			BankAccess bankAccess = dbController.getBankAccessByBlz(this.getBLZ());
			if (bankAccess == null) {
				bankAccess = new BankAccess();
			}
			BankAccessFints fints = bankAccess.getFints();

			fints.setCountry(this.getCountry());
			fints.setBlz(this.getBLZ());
			bankAccess.setBankName(this.getInstName() != null ? this.getInstName() : "unbekannt");			
			
			fints.setHbciURL(this.getHost());
			fints.setPort(this.getPort());
			
			String userId = firstNonBlank(this.getUserId(), fints.getUserId());
			fints.setUserId(userId);
			fints.setCustomerId(firstNonBlank(this.getCustomerId(), userId));
			fints.setSysId(firstNonBlank(this.getSysId(), "0"));
			
			fints.setBpdVersion(this.getBPDVersion());
			fints.setUpdVersion(this.getUPDVersion());
			fints.setBpd(sanitizeProperties(this.getBPD()));
			fints.setUpd(sanitizeProperties(this.getUPD()));

			fints.setHbciVersion(trimToNull(this.getHBCIVersion()));
			fints.setFilterType(HbciEncodingFilterType.forString(this.getFilterType()));

			bankAccess.setActive(true);
			bankAccess.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));

			setTANProcedure(bankAccess);

			dbController.insertOrUpdate(bankAccess);
			dbController.insertOrUpdatePD(bankAccess);

		} catch (HBCI_Exception he) {
			throw he;
		} catch (Exception e) {
			throw new HBCI_Exception(HBCIUtilsInternal.getLocMsg("EXCMSG_PASSPORT_WRITEERR"), e);
		}
	}

	private void setTANProcedure(BankAccess bankAccess) {
		
		final List<String> l = nonBlankList(getAllowedTwostepMechanisms());
		HBCIUtils.log("saving two step mechs: " + l, HBCIUtils.LOG_DEBUG);
		bankAccess.getFints().setAllowedTwostepMechanisms(l);

		try {
			final String s = this.getCurrentTANMethod(false);
			HBCIUtils.log("saving current tan method: " + s, HBCIUtils.LOG_DEBUG);
			TanProcedure tanProcedure = TanProcedureSupport.resolveProcedureForCode(s, bankAccess).orElse(null);
			if (tanProcedure != null) {
				bankAccess.getFints().setTanProcedure(tanProcedure);
			}
		} catch (Exception e) {
			// Nur zur Sicherheit. In der obigen Funktion werden u.U. eine Menge Sachen
			// losgetreten.
			// Wenn da irgendwas schief laeuft, soll deswegen nicht gleich das Speichern der
			// Config
			// scheitern. Im Zweifel speichern wir dann halt das ausgewaehlte Verfahren
			// erstmal nicht
			// und der User muss es beim naechsten Mal neu waehlen
			HBCIUtils.log("could not determine current tan methode, skipping: " + e.getMessage(), HBCIUtils.LOG_DEBUG);
			HBCIUtils.log(e, HBCIUtils.LOG_DEBUG2);
		}
	}

	private Properties sanitizeProperties(Properties properties) {
		Properties sanitizedProperties = new Properties();
		if (properties == null || properties.isEmpty()) {
			return sanitizedProperties;
		}

		for (String propertyName : properties.stringPropertyNames()) {
			String value = properties.getProperty(propertyName);
			if (value != null && !value.isBlank()) {
				sanitizedProperties.setProperty(propertyName, value);
			}
		}
		return sanitizedProperties.isEmpty() ? null : sanitizedProperties;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.getFileName();
	}

}
