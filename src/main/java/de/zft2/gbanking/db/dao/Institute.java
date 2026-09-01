package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.time.LocalDate;

import de.zft2.gbanking.db.enu.IdType;

public class Institute extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1224375211194465889L;

	private String blz;
	private String bic;
	private String bankName;
	private String place;
	private transient IdType stateType;
	private Integer importFile;
	private String importFileName;

	/** DK data **/
	private int importNumber;
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

	/** DBB data **/
	private String datasetNumber;
	private int feature;
	private String postcode;
	private String bankNameShort;
	private String pan;
	private String checkdigitMethod;
	private char featureChange;
	private int blzDeletion;
	private String blzSuccession;

	/** EPC data **/
	private String country;
	private String address;
	private String readinessDate;
	private String schemeLeavingDate;
	private String schemeOptions;

	/** Reachable data **/
	private Integer serviceSct;
	private Integer serviceCor;
	private Integer serviceCor1;
	private Integer serviceB2b;
	private Integer serviceScc;

	/** Additional data **/
	private String additionalBankNameShort;
	private String additionalCheckdigitMethod;
	private String additionalPostcode;
	private String additionalDeletionMarker;
	private String additionalBlzSuccession;
	private String additionalIbanRule;
	private String additionalIbanRuleVersion;

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

	public String getDatasetNumber() {
		return datasetNumber;
	}

	public void setDatasetNumber(String datasetNumber) {
		this.datasetNumber = datasetNumber;
	}

	public int getFeature() {
		return feature;
	}

	public void setFeature(int feature) {
		this.feature = feature;
	}

	public String getPostcode() {
		return postcode;
	}

	public void setPostcode(String postcode) {
		this.postcode = postcode;
	}

	public String getBankNameShort() {
		return bankNameShort;
	}

	public void setBankNameShort(String bankNameShort) {
		this.bankNameShort = bankNameShort;
	}

	public String getPan() {
		return pan;
	}

	public void setPan(String pan) {
		this.pan = pan;
	}

	public String getCheckdigitMethod() {
		return checkdigitMethod;
	}

	public void setCheckdigitMethod(String checkdigitMethod) {
		this.checkdigitMethod = checkdigitMethod;
	}

	public char getFeatureChange() {
		return featureChange;
	}

	public void setFeatureChange(char featureChange) {
		this.featureChange = featureChange;
	}

	public int getBlzDeletion() {
		return blzDeletion;
	}

	public void setBlzDeletion(int blzDeletion) {
		this.blzDeletion = blzDeletion;
	}

	public String getBlzSuccession() {
		return blzSuccession;
	}

	public void setBlzSuccession(String blzSuccession) {
		this.blzSuccession = blzSuccession;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getReadinessDate() {
		return readinessDate;
	}

	public void setReadinessDate(String readinessDate) {
		this.readinessDate = readinessDate;
	}

	public String getSchemeLeavingDate() {
		return schemeLeavingDate;
	}

	public void setSchemeLeavingDate(String schemeLeavingDate) {
		this.schemeLeavingDate = schemeLeavingDate;
	}

	public String getSchemeOptions() {
		return schemeOptions;
	}

	public void setSchemeOptions(String schemeOptions) {
		this.schemeOptions = schemeOptions;
	}

	public Integer getServiceSct() {
		return serviceSct;
	}

	public void setServiceSct(Integer serviceSct) {
		this.serviceSct = serviceSct;
	}

	public Integer getServiceCor() {
		return serviceCor;
	}

	public void setServiceCor(Integer serviceCor) {
		this.serviceCor = serviceCor;
	}

	public Integer getServiceCor1() {
		return serviceCor1;
	}

	public void setServiceCor1(Integer serviceCor1) {
		this.serviceCor1 = serviceCor1;
	}

	public Integer getServiceB2b() {
		return serviceB2b;
	}

	public void setServiceB2b(Integer serviceB2b) {
		this.serviceB2b = serviceB2b;
	}

	public Integer getServiceScc() {
		return serviceScc;
	}

	public void setServiceScc(Integer serviceScc) {
		this.serviceScc = serviceScc;
	}

	public String getAdditionalBankNameShort() {
		return additionalBankNameShort;
	}

	public void setAdditionalBankNameShort(String additionalBankNameShort) {
		this.additionalBankNameShort = additionalBankNameShort;
	}

	public String getAdditionalCheckdigitMethod() {
		return additionalCheckdigitMethod;
	}

	public void setAdditionalCheckdigitMethod(String additionalCheckdigitMethod) {
		this.additionalCheckdigitMethod = additionalCheckdigitMethod;
	}

	public String getAdditionalPostcode() {
		return additionalPostcode;
	}

	public void setAdditionalPostcode(String additionalPostcode) {
		this.additionalPostcode = additionalPostcode;
	}

	public String getAdditionalDeletionMarker() {
		return additionalDeletionMarker;
	}

	public void setAdditionalDeletionMarker(String additionalDeletionMarker) {
		this.additionalDeletionMarker = additionalDeletionMarker;
	}

	public String getAdditionalBlzSuccession() {
		return additionalBlzSuccession;
	}

	public void setAdditionalBlzSuccession(String additionalBlzSuccession) {
		this.additionalBlzSuccession = additionalBlzSuccession;
	}

	public String getAdditionalIbanRule() {
		return additionalIbanRule;
	}

	public void setAdditionalIbanRule(String additionalIbanRule) {
		this.additionalIbanRule = additionalIbanRule;
	}

	public String getAdditionalIbanRuleVersion() {
		return additionalIbanRuleVersion;
	}

	public void setAdditionalIbanRuleVersion(String additionalIbanRuleVersion) {
		this.additionalIbanRuleVersion = additionalIbanRuleVersion;
	}

	public IdType getStateType() {
		return stateType;
	}

	public void setStateType(IdType stateType) {
		this.stateType = stateType;
	}

	public Integer getImportFile() {
		return importFile;
	}

	public void setImportFile(Integer importFile) {
		this.importFile = importFile;
	}

	public String getImportFileName() {
		return importFileName;
	}

	public void setImportFileName(String importFileName) {
		this.importFileName = importFileName;
	}

	@Override
	public String toString() {
		return "Institute [id=" + id + ", importNr=" + importNumber + ", datasetNr=" + datasetNumber + ", blz=" + blz + ", bic=" + bic + ", bankName="
				+ bankName + ", place=" + place + ", hbciVersion=" + hbciVersion + ", stateType=" + stateType + "]";
	}
}
