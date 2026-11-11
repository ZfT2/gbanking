package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.StockCorporateActionMemberRole;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionOperation;

@StockTable(name = "stockCorporateActionTransaction", parentColumn = "action_id", createdAt = true)
public class StockCorporateActionTransaction extends StockDao {
	private int actionId;
	private StockCorporateActionMemberRole primaryRole = StockCorporateActionMemberRole.PRIMARY;
	private Integer bookingId;
	private StockCorporateActionOperation operation;
	private Long basisAmountE8;
	private long referenceQuantityE9;
	private Long externalGrossMinor;
	private Long externalFeesMinor;
	private Long externalTaxesMinor;

	public int getActionId() { return actionId; }
	public void setActionId(int id) { actionId = id; }
	public StockCorporateActionMemberRole getPrimaryRole() { return primaryRole; }
	public Integer getBookingId() { return bookingId; }
	public void setBookingId(Integer id) { bookingId = id; }
	public StockCorporateActionOperation getOperation() { return operation; }
	public void setOperation(StockCorporateActionOperation operation) { this.operation = operation; }
	public Long getBasisAmountE8() { return basisAmountE8; }
	public void setBasisAmountE8(Long value) { basisAmountE8 = value; }
	public long getReferenceQuantityE9() { return referenceQuantityE9; }
	public void setReferenceQuantityE9(long value) { referenceQuantityE9 = value; }
	public Long getExternalGrossMinor() { return externalGrossMinor; }
	public void setExternalGrossMinor(Long value) { externalGrossMinor = value; }
	public Long getExternalFeesMinor() { return externalFeesMinor; }
	public void setExternalFeesMinor(Long value) { externalFeesMinor = value; }
	public Long getExternalTaxesMinor() { return externalTaxesMinor; }
	public void setExternalTaxesMinor(Long value) { externalTaxesMinor = value; }
}
