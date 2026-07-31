package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;

public class BookingAdditionalDetails implements Serializable {

	private static final long serialVersionUID = 1L;

	private String instref;
	private String gvcode;
	private String text;
	private String primanota;
	private String key;
	private Boolean storno;
	private BigDecimal origValue;
	private BigDecimal chargeValue;
	private String rawData;
	private Boolean sepa;
	private Boolean camt;
	private BigDecimal bankSaldo;

	public BookingAdditionalDetails() {
	}

	public BookingAdditionalDetails(BookingAdditionalDetails detailsToCopy) {
		this.instref = detailsToCopy.instref;
		this.gvcode = detailsToCopy.gvcode;
		this.text = detailsToCopy.text;
		this.primanota = detailsToCopy.primanota;
		this.key = detailsToCopy.key;
		this.storno = detailsToCopy.storno;
		this.origValue = detailsToCopy.origValue;
		this.chargeValue = detailsToCopy.chargeValue;
		this.rawData = detailsToCopy.rawData;
		this.sepa = detailsToCopy.sepa;
		this.camt = detailsToCopy.camt;
		this.bankSaldo = detailsToCopy.bankSaldo;
	}

	public boolean isEmpty() {
		return !hasText(instref)
				&& !hasText(gvcode)
				&& !hasText(text)
				&& !hasText(primanota)
				&& !hasText(key)
				&& storno == null
				&& origValue == null
				&& chargeValue == null
				&& !hasText(rawData)
				&& sepa == null
				&& camt == null
				&& bankSaldo == null;
	}

	public String getInstref() {
		return instref;
	}

	public void setInstref(String instref) {
		this.instref = instref;
	}

	public String getGvcode() {
		return gvcode;
	}

	public void setGvcode(String gvcode) {
		this.gvcode = gvcode;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getPrimanota() {
		return primanota;
	}

	public void setPrimanota(String primanota) {
		this.primanota = primanota;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public Boolean getStorno() {
		return storno;
	}

	public void setStorno(Boolean storno) {
		this.storno = storno;
	}

	public BigDecimal getOrigValue() {
		return origValue;
	}

	public void setOrigValue(BigDecimal origValue) {
		this.origValue = origValue;
	}

	public BigDecimal getChargeValue() {
		return chargeValue;
	}

	public void setChargeValue(BigDecimal chargeValue) {
		this.chargeValue = chargeValue;
	}

	public String getRawData() {
		return rawData;
	}

	public void setRawData(String rawData) {
		this.rawData = rawData;
	}

	public Boolean getSepa() {
		return sepa;
	}

	public void setSepa(Boolean sepa) {
		this.sepa = sepa;
	}

	public Boolean getCamt() {
		return camt;
	}

	public void setCamt(Boolean camt) {
		this.camt = camt;
	}

	public BigDecimal getBankSaldo() {
		return bankSaldo;
	}

	public void setBankSaldo(BigDecimal bankSaldo) {
		this.bankSaldo = bankSaldo;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
