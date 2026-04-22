package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CategoryRule extends Dao implements Serializable /* MnRelation */ {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1674957549446339777L;

	public enum JoinType {
		AND("Alle Bedingungen müssen zutreffen"), OR("Eine Bedingung muss zutreffen");

		private final String description;

		private JoinType(String description) {
			this.description = description;
		}

		@Override
		public final String toString() {
			return description;
		}
	}

	private Category category;
	private LocalDate filterDateFrom;
	private LocalDate filterDateTo;
	private BigDecimal filterAmountFrom;
	private BigDecimal filterAmountTo;
	private String filterRecipient;
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

	public String getFilterRecipient() {
		return filterRecipient;
	}

	public void setFilterRecipient(String filterRecipient) {
		this.filterRecipient = filterRecipient;
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
