package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public class CategoryRule extends Dao implements Serializable /* MnRelation */ {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1674957549446339777L;

	public enum JoinType implements IdType, LocalizedEnumValue {

		AND(1),
		OR(2);

		private final int dbStateId;

		private JoinType(int dbStateId) {
			this.dbStateId = dbStateId;
		}

		@Override
		public final String toString() {
			return getDisplayName();
		}

		public String getDescription() {
			return getDisplayName();
		}

		@Override
		public int getDbStateId() {
			return dbStateId;
		}

		public static JoinType forInt(int intValue) {
			return IdType.forId(JoinType.class, intValue);
		}
	}

	private String name;
	private Category category;
	private LocalDate filterDateFrom;
	private LocalDate filterDateTo;
	private BigDecimal filterAmountFrom;
	private BigDecimal filterAmountTo;
	private String filterRecipientName;
	private String filterRecipientIban;
	private String filterRecipientAccountNumber;
	private String filterPurpose;
	private JoinType joinType;
	private boolean filterRecipientIsRegex;
	private boolean filterPurposeIsRegex;
	private List<BankAccount> bankAccountList;

	public CategoryRule() {
		filterPurposeIsRegex = false;
		filterRecipientIsRegex = false;
		joinType = JoinType.OR;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public LocalDate getFilterDateFrom() {
		return filterDateFrom;
	}

	public void setFilterDateFrom(LocalDate filterDateFrom) {
		this.filterDateFrom = filterDateFrom;
	}

	public LocalDate getFilterDateTo() {
		return filterDateTo;
	}

	public void setFilterDateTo(LocalDate filterDateTo) {
		this.filterDateTo = filterDateTo;
	}

	public BigDecimal getFilterAmountFrom() {
		return filterAmountFrom;
	}

	public void setFilterAmountFrom(BigDecimal filterAmountFrom) {
		this.filterAmountFrom = filterAmountFrom;
	}

	public BigDecimal getFilterAmountTo() {
		return filterAmountTo;
	}

	public void setFilterAmountTo(BigDecimal filterAmountTo) {
		this.filterAmountTo = filterAmountTo;
	}

	public String getFilterRecipientName() {
		return filterRecipientName;
	}

	public void setFilterRecipientName(String filterRecipientName) {
		this.filterRecipientName = filterRecipientName;
	}

	public String getFilterRecipientIban() {
		return filterRecipientIban;
	}

	public void setFilterRecipientIban(String filterRecipientIban) {
		this.filterRecipientIban = filterRecipientIban;
	}

	public String getFilterRecipientAccountNumber() {
		return filterRecipientAccountNumber;
	}

	public void setFilterRecipientAccountNumber(String filterRecipientAccountNumber) {
		this.filterRecipientAccountNumber = filterRecipientAccountNumber;
	}

	public String getFilterPurpose() {
		return filterPurpose;
	}

	public void setFilterPurpose(String filterPurpose) {
		this.filterPurpose = filterPurpose;
	}

	public JoinType getJoinType() {
		return joinType;
	}

	public void setJoinType(JoinType joinType) {
		this.joinType = joinType;
	}

	public boolean isFilterRecipientIsRegex() {
		return filterRecipientIsRegex;
	}

	public void setFilterRecipientIsRegex(boolean filterRecipientIsRegex) {
		this.filterRecipientIsRegex = filterRecipientIsRegex;
	}

	public boolean isFilterPurposeIsRegex() {
		return filterPurposeIsRegex;
	}

	public void setFilterPurposeIsRegex(boolean filterPurposeIsRegex) {
		this.filterPurposeIsRegex = filterPurposeIsRegex;
	}

	public List<BankAccount> getBankAccountList() {
		return bankAccountList;
	}

	public void setBankAccountList(List<BankAccount> bankAccountList) {
		this.bankAccountList = bankAccountList;
	}

}
