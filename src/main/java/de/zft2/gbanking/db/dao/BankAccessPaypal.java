package de.zft2.gbanking.db.dao;

import java.io.Serializable;

public class BankAccessPaypal implements Serializable {

	private static final long serialVersionUID = 7749105830112323027L;

	private String userId;
	private String apiUsername;
	private String apiSignature;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getApiUsername() {
		return apiUsername;
	}

	public void setApiUsername(String apiUsername) {
		this.apiUsername = apiUsername;
	}

	public String getApiSignature() {
		return apiSignature;
	}

	public void setApiSignature(String apiSignature) {
		this.apiSignature = apiSignature;
	}
}
