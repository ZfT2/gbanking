package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.StockImportStatus;

@StockTable(name = "stockImportBatch")
public class StockImportBatch extends StockDao {

	private int sourceId;
	private String importerKey;
	private String formatType;
	private String formatVersion;
	private String importerVersion;
	private String fileName;
	private String hashAlgorithm = "SHA-256";
	private String contentHash;
	private byte[] rawContent;
	private String rawContentEncoding;
	private boolean rawContentEncrypted;
	private StockImportStatus importStatus;
	private LocalDateTime startedAt;
	private LocalDateTime completedAt;
	private String errorText;

	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public String getImporterKey() { return importerKey; }
	public void setImporterKey(String importerKey) { this.importerKey = importerKey; }
	public String getFormatType() { return formatType; }
	public void setFormatType(String formatType) { this.formatType = formatType; }
	public String getFormatVersion() { return formatVersion; }
	public void setFormatVersion(String formatVersion) { this.formatVersion = formatVersion; }
	public String getImporterVersion() { return importerVersion; }
	public void setImporterVersion(String importerVersion) { this.importerVersion = importerVersion; }
	public String getFileName() { return fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }
	public String getHashAlgorithm() { return hashAlgorithm; }
	public void setHashAlgorithm(String hashAlgorithm) { this.hashAlgorithm = hashAlgorithm; }
	public String getContentHash() { return contentHash; }
	public void setContentHash(String contentHash) { this.contentHash = contentHash; }
	public byte[] getRawContent() { return rawContent != null ? rawContent.clone() : null; }
	public void setRawContent(byte[] rawContent) { this.rawContent = rawContent != null ? rawContent.clone() : null; }
	public String getRawContentEncoding() { return rawContentEncoding; }
	public void setRawContentEncoding(String rawContentEncoding) { this.rawContentEncoding = rawContentEncoding; }
	public boolean isRawContentEncrypted() { return rawContentEncrypted; }
	public void setRawContentEncrypted(boolean rawContentEncrypted) { this.rawContentEncrypted = rawContentEncrypted; }
	public StockImportStatus getImportStatus() { return importStatus; }
	public void setImportStatus(StockImportStatus importStatus) { this.importStatus = importStatus; }
	public LocalDateTime getStartedAt() { return startedAt; }
	public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
	public LocalDateTime getCompletedAt() { return completedAt; }
	public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
	public String getErrorText() { return errorText; }
	public void setErrorText(String errorText) { this.errorText = errorText; }
}
