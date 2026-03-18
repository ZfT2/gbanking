package org.kapott.hbci.passport;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;

import de.gbanking.db.DBController;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.gbanking.db.dao.enu.TanProcedure;

/**
 * Implementation of PIN/TAN-Passport, which is persisting the data in database.
 */
public class HBCIPassportPinTanDB extends HBCIPassportPinTan {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2055223807813566709L;

	private static DBController dbController;

	static {
		dbController = DBController.getInstance(".");
	}

	/**
	 * ct.
	 * 
	 * @param init Generische Init-Daten.
	 */
	public HBCIPassportPinTanDB(Object init) {
		super(init);

		if (init instanceof String)
			setBLZ((String) init);

		// dbController = DBController.getInstance(".");
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
		BankAccess bankAccess = dbController.getBankAccessByBlz(blz);
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

		BankAccess bankAccess = dbController.getBankAccessByBlz(blz);

		this.setCountry(bankAccess.getCountry());
		this.setBLZ(bankAccess.getBlz());
		String url = bankAccess.getHbciURL();
		this.setHost(url != null && url.startsWith("https://") ? url : "https://" + url);
		this.setPort(bankAccess.getPort());
		
		this.setUserId(bankAccess.getUserId());
		this.setCustomerId(bankAccess.getCustomerId());
		/* this.setSysId(bankAccess.getSysId()); */ this.setSysId(null);
		/* this.setHBCIVersion(bankAccess.getHbciVersion()); */ this.setHBCIVersion(null);
		
		this.setBPD(bankAccess.getBpd());
		this.setUPD(bankAccess.getUpd());
		
		this.setFilterType(bankAccess.getFilterType().getTranslation());
		this.setAllowedTwostepMechanisms(bankAccess.getAllowedTwostepMechanisms());
		/* this.setCurrentTANMethod(bankAccess.getTanProcedure().toString()); */ this.setCurrentTANMethod(null);
		this.setAllowedTwostepMechanisms(new ArrayList<String>());
	}

	/**
	 * @see org.kapott.hbci.passport.HBCIPassport#saveChanges()
	 */
	@Override
	public void saveChanges() {
		try {

			BankAccess bankAccess = dbController.getBankAccessByBlz(this.getBLZ());
			if (bankAccess == null) {
				bankAccess = new BankAccess();
			}

			bankAccess.setCountry(this.getCountry());
			bankAccess.setBlz(this.getBLZ());
			bankAccess.setBankName(this.getInstName() != null ? this.getInstName() : "unbekannt");			
			
			bankAccess.setHbciURL(this.getHost());
			bankAccess.setPort(this.getPort());
			
			bankAccess.setUserId(this.getUserId());
			bankAccess.setCustomerId(this.getCustomerId());
			bankAccess.setSysId(this.getSysId());
			
			bankAccess.setBpdVersion(this.getBPDVersion());
			bankAccess.setUpdVersion(this.getUPDVersion());
			bankAccess.setBpd(this.getBPD());
			bankAccess.setUpd(this.getUPD());

			bankAccess.setHbciVersion(this.getHBCIVersion());
			bankAccess.setFilterType(HbciEncodingFilterType.forString(this.getFilterType()));

			bankAccess.setActive(true);
			bankAccess.setUpdatedAt(Calendar.getInstance());

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
		
		final List<String> l = getAllowedTwostepMechanisms();
		HBCIUtils.log("saving two step mechs: " + l, HBCIUtils.LOG_DEBUG);
		bankAccess.setAllowedTwostepMechanisms(l);

		try {
			final String s = this.getCurrentTANMethod(false);
			HBCIUtils.log("saving current tan method: " + s, HBCIUtils.LOG_DEBUG);
			bankAccess.setTanProcedure(TanProcedure.forCode(Integer.valueOf(s)));
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

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.getFileName();
	}

}
