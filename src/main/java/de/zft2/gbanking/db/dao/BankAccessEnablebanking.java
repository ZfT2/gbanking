package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.time.OffsetDateTime;

public class BankAccessEnablebanking implements Serializable {

	private static final long serialVersionUID = -5385990725296755257L;

	private int psd2ClientConfigurationId;
	private String aspspName;
	private String aspspCountry;
	private String psuType;
	private String authMethod;
	private String sessionId;
	private OffsetDateTime validUntil;
	private OffsetDateTime rateLimitUntil;

	public int getPsd2ClientConfigurationId() {
		return psd2ClientConfigurationId;
	}

	public void setPsd2ClientConfigurationId(int psd2ClientConfigurationId) {
		this.psd2ClientConfigurationId = psd2ClientConfigurationId;
	}

	public String getAspspName() {
		return aspspName;
	}

	public void setAspspName(String aspspName) {
		this.aspspName = aspspName;
	}

	public String getAspspCountry() {
		return aspspCountry;
	}

	public void setAspspCountry(String aspspCountry) {
		this.aspspCountry = aspspCountry;
	}

	public String getPsuType() {
		return psuType;
	}

	public void setPsuType(String psuType) {
		this.psuType = psuType;
	}

	public String getAuthMethod() {
		return authMethod;
	}

	public void setAuthMethod(String authMethod) {
		this.authMethod = authMethod;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public OffsetDateTime getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(OffsetDateTime validUntil) {
		this.validUntil = validUntil;
	}

	public OffsetDateTime getRateLimitUntil() {
		return rateLimitUntil;
	}

	public void setRateLimitUntil(OffsetDateTime rateLimitUntil) {
		this.rateLimitUntil = rateLimitUntil;
	}
}
