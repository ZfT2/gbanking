package de.zft2.gbanking.db.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class BankMessage extends Dao {

	private int bankAccessId;
	private String bankName;
	private String messageKey;
	private String code;
	private String type;
	private String format;
	private String description;
	private LocalDate versionDate;
	private String comments;
	private String message;
	private LocalDateTime retrievedAt;

	public int getBankAccessId() {
		return bankAccessId;
	}

	public void setBankAccessId(int bankAccessId) {
		this.bankAccessId = bankAccessId;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public void setMessageKey(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getVersionDate() {
		return versionDate;
	}

	public void setVersionDate(LocalDate versionDate) {
		this.versionDate = versionDate;
	}

	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public LocalDateTime getRetrievedAt() {
		return retrievedAt;
	}

	public void setRetrievedAt(LocalDateTime retrievedAt) {
		this.retrievedAt = retrievedAt;
	}
}
