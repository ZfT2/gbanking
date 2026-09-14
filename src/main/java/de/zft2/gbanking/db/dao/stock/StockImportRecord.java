package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.StockImportRecordStatus;

@StockTable(name = "stockImportRecord", parentColumn = "importBatch_id", createdAt = true)
public class StockImportRecord extends StockDao {

	private int importBatchId;
	private int recordNumber;
	private int occurrenceNumber = 1;
	private String recordType;
	private String externalReference;
	private String fingerprint;
	private byte[] rawRecord;
	private StockImportRecordStatus recordStatus;
	private String errorText;

	public int getImportBatchId() { return importBatchId; }
	public void setImportBatchId(int importBatchId) { this.importBatchId = importBatchId; }
	public int getRecordNumber() { return recordNumber; }
	public void setRecordNumber(int recordNumber) { this.recordNumber = recordNumber; }
	public int getOccurrenceNumber() { return occurrenceNumber; }
	public void setOccurrenceNumber(int occurrenceNumber) { this.occurrenceNumber = occurrenceNumber; }
	public String getRecordType() { return recordType; }
	public void setRecordType(String recordType) { this.recordType = recordType; }
	public String getExternalReference() { return externalReference; }
	public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
	public String getFingerprint() { return fingerprint; }
	public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
	public byte[] getRawRecord() { return rawRecord != null ? rawRecord.clone() : null; }
	public void setRawRecord(byte[] rawRecord) { this.rawRecord = rawRecord != null ? rawRecord.clone() : null; }
	public StockImportRecordStatus getRecordStatus() { return recordStatus; }
	public void setRecordStatus(StockImportRecordStatus recordStatus) { this.recordStatus = recordStatus; }
	public String getErrorText() { return errorText; }
	public void setErrorText(String errorText) { this.errorText = errorText; }
}
