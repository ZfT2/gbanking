package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.util.List;
import java.util.Properties;

import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;

public class BankAccess extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5537813108036780637L;

	private String blz;
	private String bankName;
	private String country;
	private String hbciURL;
	private Integer port;
	private String userId;
	private String customerId;
	private String sysId;
	private char[] pin;
	private TanProcedure tanProcedure;
	private List<String> allowedTwostepMechanisms;
	private String hbciVersion;
	private String bpdVersion;
	private String updVersion;
	private HbciEncodingFilterType filterType;
	private boolean active;

	private Properties upd;
	private Properties bpd;

	private List<BankAccount> accounts;

	public String getBlz() {
		return blz;
	}

	public void setBlz(String blz) {
		this.blz = blz;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getHbciURL() {
		return hbciURL;
	}

	public void setHbciURL(String hbciURL) {
		this.hbciURL = hbciURL;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getSysId() {
		return sysId;
	}

	public void setSysId(String sysId) {
		this.sysId = sysId;
	}

	public char[] getPin() {
		return pin;
	}

	public void setPin(char[] pin) {
		this.pin = pin;
	}

	public TanProcedure getTanProcedure() {
		return tanProcedure;
	}

	public void setTanProcedure(TanProcedure tanProcedure) {
		this.tanProcedure = tanProcedure;
	}

	public List<String> getAllowedTwostepMechanisms() {
		return allowedTwostepMechanisms;
	}

	public void setAllowedTwostepMechanisms(List<String> allowedTwostepMechanisms) {
		this.allowedTwostepMechanisms = allowedTwostepMechanisms;
	}

	public String getHbciVersion() {
		return hbciVersion;
	}

	public void setHbciVersion(String hbciVersion) {
		this.hbciVersion = hbciVersion;
	}

	public String getBpdVersion() {
		return bpdVersion;
	}

	public void setBpdVersion(String bpdVersion) {
		this.bpdVersion = bpdVersion;
	}

	public String getUpdVersion() {
		return updVersion;
	}

	public void setUpdVersion(String updVersion) {
		this.updVersion = updVersion;
	}

	public HbciEncodingFilterType getFilterType() {
		return filterType;
	}

	public void setFilterType(HbciEncodingFilterType filterType) {
		this.filterType = filterType;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Properties getUpd() {
		return upd;
	}

	public void setUpd(Properties upd) {
		this.upd = upd;
	}

	public Properties getBpd() {
		return bpd;
	}

	public void setBpd(Properties bpd) {
		this.bpd = bpd;
	}

	public List<BankAccount> getAccounts() {
		return accounts;
	}

	public void setAccounts(List<BankAccount> accounts) {
		this.accounts = accounts;
	}
}
