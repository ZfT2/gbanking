package de.zft2.gbanking.db.dao;

import java.util.Arrays;

import de.zft2.gbanking.db.dao.enu.Psd2ClientMode;

public class Psd2ClientConfiguration extends Dao {

	public static final String DEFAULT_CALLBACK_URL = "https://127.0.0.1:18443/callback";

	private Psd2ClientMode clientMode = Psd2ClientMode.PERSONAL;
	private String applicationId;
	private byte[] privateKeyPkcs8;
	private String callbackUrl = DEFAULT_CALLBACK_URL;
	private byte[] callbackPrivateKeyPkcs8;
	private byte[] callbackCertificate;

	public Psd2ClientMode getClientMode() {
		return clientMode;
	}

	public void setClientMode(Psd2ClientMode clientMode) {
		this.clientMode = clientMode;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public byte[] getPrivateKeyPkcs8() {
		return copy(privateKeyPkcs8);
	}

	public void setPrivateKeyPkcs8(byte[] privateKeyPkcs8) {
		this.privateKeyPkcs8 = copy(privateKeyPkcs8);
	}

	public String getCallbackUrl() {
		return callbackUrl;
	}

	public void setCallbackUrl(String callbackUrl) {
		this.callbackUrl = callbackUrl;
	}

	public byte[] getCallbackPrivateKeyPkcs8() {
		return copy(callbackPrivateKeyPkcs8);
	}

	public void setCallbackPrivateKeyPkcs8(byte[] callbackPrivateKeyPkcs8) {
		this.callbackPrivateKeyPkcs8 = copy(callbackPrivateKeyPkcs8);
	}

	public byte[] getCallbackCertificate() {
		return copy(callbackCertificate);
	}

	public void setCallbackCertificate(byte[] callbackCertificate) {
		this.callbackCertificate = copy(callbackCertificate);
	}

	private byte[] copy(byte[] value) {
		return value != null ? Arrays.copyOf(value, value.length) : null;
	}
}
