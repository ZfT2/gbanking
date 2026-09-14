package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.StockCouponRateStatus;

@StockTable(name = "stockCouponPeriod", parentColumn = "security_id", createdAt = true)
public class StockCouponPeriod extends StockDao {

	private int securityId;
	private int sourceId;
	private Integer importRecordId;
	private LocalDate accrualStart;
	private LocalDate accrualEnd;
	private LocalDate paymentDate;
	private long rateE9;
	private StockCouponRateStatus rateStatus;

	public int getSecurityId() { return securityId; }
	public void setSecurityId(int securityId) { this.securityId = securityId; }
	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public Integer getImportRecordId() { return importRecordId; }
	public void setImportRecordId(Integer importRecordId) { this.importRecordId = importRecordId; }
	public LocalDate getAccrualStart() { return accrualStart; }
	public void setAccrualStart(LocalDate accrualStart) { this.accrualStart = accrualStart; }
	public LocalDate getAccrualEnd() { return accrualEnd; }
	public void setAccrualEnd(LocalDate accrualEnd) { this.accrualEnd = accrualEnd; }
	public LocalDate getPaymentDate() { return paymentDate; }
	public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
	public long getRateE9() { return rateE9; }
	public void setRateE9(long rateE9) { this.rateE9 = rateE9; }
	public StockCouponRateStatus getRateStatus() { return rateStatus; }
	public void setRateStatus(StockCouponRateStatus rateStatus) { this.rateStatus = rateStatus; }
}
