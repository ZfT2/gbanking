package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.StockDayCountConvention;
import de.zft2.gbanking.db.dao.enu.StockInterestType;

@StockTable(name = "stockSecurityInterestTerms", idColumn = "security_id", parentColumn = "security_id",
		generatedId = false, createdAt = true, updatedAt = true)
public class StockSecurityInterestTerms extends StockDao {

	private int sourceId;
	private StockInterestType interestType;
	private Long fixedRateE9;
	private String referenceRateName;
	private Long spreadRateE9;
	private Long floorRateE9;
	private Long capRateE9;
	private Integer paymentFrequencyPerYear;
	private StockDayCountConvention dayCountConvention;
	private int exCouponDays;

	public int getSecurityId() { return getId(); }
	public void setSecurityId(int securityId) { setId(securityId); }
	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public StockInterestType getInterestType() { return interestType; }
	public void setInterestType(StockInterestType interestType) { this.interestType = interestType; }
	public Long getFixedRateE9() { return fixedRateE9; }
	public void setFixedRateE9(Long fixedRateE9) { this.fixedRateE9 = fixedRateE9; }
	public String getReferenceRateName() { return referenceRateName; }
	public void setReferenceRateName(String referenceRateName) { this.referenceRateName = referenceRateName; }
	public Long getSpreadRateE9() { return spreadRateE9; }
	public void setSpreadRateE9(Long spreadRateE9) { this.spreadRateE9 = spreadRateE9; }
	public Long getFloorRateE9() { return floorRateE9; }
	public void setFloorRateE9(Long floorRateE9) { this.floorRateE9 = floorRateE9; }
	public Long getCapRateE9() { return capRateE9; }
	public void setCapRateE9(Long capRateE9) { this.capRateE9 = capRateE9; }
	public Integer getPaymentFrequencyPerYear() { return paymentFrequencyPerYear; }
	public void setPaymentFrequencyPerYear(Integer paymentFrequencyPerYear) { this.paymentFrequencyPerYear = paymentFrequencyPerYear; }
	public StockDayCountConvention getDayCountConvention() { return dayCountConvention; }
	public void setDayCountConvention(StockDayCountConvention dayCountConvention) { this.dayCountConvention = dayCountConvention; }
	public int getExCouponDays() { return exCouponDays; }
	public void setExCouponDays(int exCouponDays) { this.exCouponDays = exCouponDays; }
}
