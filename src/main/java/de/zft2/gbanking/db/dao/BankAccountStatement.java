package de.zft2.gbanking.db.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class BankAccountStatement extends Dao {

	private int accountId;
	private String accountName;
	private String fileName;
	private String format;
	private LocalDateTime retrievedAt;
	private LocalDate statementDate;
	private LocalDate startDate;
	private LocalDate endDate;
	private int year;
	private int number;
	private long size;
	private String iban;
	private String bic;
	private String sourceJob;
	private boolean receiptAvailable;
	private byte[] receipt;
	private boolean acknowledged;
	private LocalDateTime acknowledgedAt;

	public int getAccountId() {
		return accountId;
	}

	public void setAccountId(int accountId) {
		this.accountId = accountId;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public LocalDateTime getRetrievedAt() {
		return retrievedAt;
	}

	public void setRetrievedAt(LocalDateTime retrievedAt) {
		this.retrievedAt = retrievedAt;
	}

	public LocalDate getStatementDate() {
		return statementDate;
	}

	public void setStatementDate(LocalDate statementDate) {
		this.statementDate = statementDate;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getIban() {
		return iban;
	}

	public void setIban(String iban) {
		this.iban = iban;
	}

	public String getBic() {
		return bic;
	}

	public void setBic(String bic) {
		this.bic = bic;
	}

	public String getSourceJob() {
		return sourceJob;
	}

	public void setSourceJob(String sourceJob) {
		this.sourceJob = sourceJob;
	}

	public boolean isReceiptAvailable() {
		return receiptAvailable;
	}

	public void setReceiptAvailable(boolean receiptAvailable) {
		this.receiptAvailable = receiptAvailable;
	}

	public byte[] getReceipt() {
		return receipt;
	}

	public void setReceipt(byte[] receipt) {
		this.receipt = receipt;
	}

	public boolean isAcknowledged() {
		return acknowledged;
	}

	public void setAcknowledged(boolean acknowledged) {
		this.acknowledged = acknowledged;
	}

	public LocalDateTime getAcknowledgedAt() {
		return acknowledgedAt;
	}

	public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
		this.acknowledgedAt = acknowledgedAt;
	}
}
