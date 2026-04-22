package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.time.LocalDate;

import de.zft2.gbanking.db.enu.IdType;

public class Institute extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1224375211194465889L;

	private int importNumber;
	private String blz;
	private String bic;
	private String bankName;
	private String place;
	private String dataCenter;
	private String organisation;
	private String hbciDns;
	private String hbciIp;
	private Double hbciVersion;
	private String ddv;
	private Boolean rdh1;
	private Boolean rdh2;
	private Boolean rdh3;
	private Boolean rdh4;
	private Boolean rdh5;
	private Boolean rdh6;
	private Boolean rdh7;
	private Boolean rdh8;
	private Boolean rdh9;
	private Boolean rdh10;
	private String pinUrl;
	private String version;
	private LocalDate lastChanged;

	private transient IdType stateType;

	public int getImportNumber() {
		return importNumber;
	}

	public void setImportNumber(int importNumber) {
		this.importNumber = importNumber;
	}

	public String getBlz() {
		return blz;
	}

	public void setBlz(String blz) {
		this.blz = blz;
	}

	public String getBic() {
		return bic;
	}

	public void setBic(String bic) {
		this.bic = bic;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getPlace() {
		return place;
	}

	public void setPlace(String place) {
		this.place = place;
	}

	public String getDataCenter() {
		return dataCenter;
	}

	public void setDataCenter(String dataCenter) {
		this.dataCenter = dataCenter;
	}

	public String getOrganisation() {
		return organisation;
	}

	public void setOrganisation(String organisation) {
		this.organisation = organisation;
	}

	public String getHbciDns() {
		return hbciDns;
	}

	public void setHbciDns(String hbciDns) {
		this.hbciDns = hbciDns;
	}

	public String getHbciIp() {
		return hbciIp;
	}

	public void setHbciIp(String hbciIp) {
		this.hbciIp = hbciIp;
	}

	public Double getHbciVersion() {
		return hbciVersion;
	}

	public void setHbciVersion(Double hbciVersion) {
		this.hbciVersion = hbciVersion;
	}

	public String getDdv() {
		return ddv;
	}

	public void setDdv(String ddv) {
		this.ddv = ddv;
	}

	public Boolean getRdh1() {
		return rdh1;
	}

	public void setRdh1(Boolean rdh1) {
		this.rdh1 = rdh1;
	}

	public Boolean getRdh2() {
		return rdh2;
	}

	public void setRdh2(Boolean rdh2) {
		this.rdh2 = rdh2;
	}

	public Boolean getRdh3() {
		return rdh3;
	}

	public void setRdh3(Boolean rdh3) {
		this.rdh3 = rdh3;
	}

	public Boolean getRdh4() {
		return rdh4;
	}

	public void setRdh4(Boolean rdh4) {
		this.rdh4 = rdh4;
	}

	public Boolean getRdh5() {
		return rdh5;
	}

	public void setRdh5(Boolean rdh5) {
		this.rdh5 = rdh5;
	}

	public Boolean getRdh6() {
		return rdh6;
	}

	public void setRdh6(Boolean rdh6) {
		this.rdh6 = rdh6;
	}

	public Boolean getRdh7() {
		return rdh7;
	}

	public void setRdh7(Boolean rdh7) {
		this.rdh7 = rdh7;
	}

	public Boolean getRdh8() {
		return rdh8;
	}

	public void setRdh8(Boolean rdh8) {
		this.rdh8 = rdh8;
	}

	public Boolean getRdh9() {
		return rdh9;
	}

	public void setRdh9(Boolean rdh9) {
		this.rdh9 = rdh9;
	}

	public Boolean getRdh10() {
		return rdh10;
	}

	public void setRdh10(Boolean rdh10) {
		this.rdh10 = rdh10;
	}

	public String getPinUrl() {
		return pinUrl;
	}

	public void setPinUrl(String pinUrl) {
		this.pinUrl = pinUrl;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public LocalDate getLastChanged() {
		return lastChanged;
	}

	public void setLastChanged(LocalDate lastChanged) {
		this.lastChanged = lastChanged;
	}

	public IdType getStateType() {
		return stateType;
	}

	public void setStateType(IdType stateType) {
		this.stateType = stateType;
	}

	@Override
	public String toString() {
		return "Institute [blz=" + blz + ", bic=" + bic + ", bankName=" + bankName + ", place=" + place + ", hbciVersion=" + hbciVersion + ", stateType="
				+ stateType + "]";
	}
}
