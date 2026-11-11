package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionType;

@StockTable(name = "stockCorporateAction", createdAt = true)
public class StockCorporateAction extends StockDao {
	private String name;
	private StockCorporateActionType actionType;
	private int underlyingSecurityId;
	private Integer rightsSecurityId;
	private int targetSecurityId;
	private long allocationFromE9;
	private long allocationToE9;
	private long ratioFromE9;
	private long ratioToE9;
	private Long subscriptionPriceE8;
	private Currency currency;
	private LocalDate effectiveDate;
	private LocalDate endDate;
	private Long oldNominalE8;
	private Long newNominalE8;

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public StockCorporateActionType getActionType() { return actionType; }
	public void setActionType(StockCorporateActionType actionType) { this.actionType = actionType; }
	public int getUnderlyingSecurityId() { return underlyingSecurityId; }
	public void setUnderlyingSecurityId(int id) { underlyingSecurityId = id; }
	public Integer getRightsSecurityId() { return rightsSecurityId; }
	public void setRightsSecurityId(Integer id) { rightsSecurityId = id; }
	public int getTargetSecurityId() { return targetSecurityId; }
	public void setTargetSecurityId(int id) { targetSecurityId = id; }
	public long getAllocationFromE9() { return allocationFromE9; }
	public void setAllocationFromE9(long value) { allocationFromE9 = value; }
	public long getAllocationToE9() { return allocationToE9; }
	public void setAllocationToE9(long value) { allocationToE9 = value; }
	public long getRatioFromE9() { return ratioFromE9; }
	public void setRatioFromE9(long value) { ratioFromE9 = value; }
	public long getRatioToE9() { return ratioToE9; }
	public void setRatioToE9(long value) { ratioToE9 = value; }
	public Long getSubscriptionPriceE8() { return subscriptionPriceE8; }
	public void setSubscriptionPriceE8(Long value) { subscriptionPriceE8 = value; }
	public Currency getCurrency() { return currency; }
	public void setCurrency(Currency value) { currency = value; }
	public LocalDate getEffectiveDate() { return effectiveDate; }
	public void setEffectiveDate(LocalDate value) { effectiveDate = value; }
	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate value) { endDate = value; }
	public Long getOldNominalE8() { return oldNominalE8; }
	public void setOldNominalE8(Long value) { oldNominalE8 = value; }
	public Long getNewNominalE8() { return newNominalE8; }
	public void setNewNominalE8(Long value) { newNominalE8 = value; }
	@Override public String toString() { return name; }
}
