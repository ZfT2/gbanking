package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;

@StockTable(name = "stockSecurity", createdAt = true, updatedAt = true)
public class StockSecurity extends StockDao {

	private StockSecurityType securityType;
	private String name;
	private String issuer;
	private String domicileCountry;
	private LocalDate maturityDate;
	private StockQuantityType defaultQuantityType;
	private Currency nominalCurrency;
	private Currency defaultQuoteCurrency;
	private StockQuotationType defaultQuotationType;
	private StockPriceBasis defaultPriceBasis;
	private StockSecurityState securityState = StockSecurityState.ACTIVE;

	public StockSecurityType getSecurityType() { return securityType; }
	public void setSecurityType(StockSecurityType securityType) { this.securityType = securityType; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getIssuer() { return issuer; }
	public void setIssuer(String issuer) { this.issuer = issuer; }
	public String getDomicileCountry() { return domicileCountry; }
	public void setDomicileCountry(String domicileCountry) { this.domicileCountry = domicileCountry; }
	public LocalDate getMaturityDate() { return maturityDate; }
	public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
	public StockQuantityType getDefaultQuantityType() { return defaultQuantityType; }
	public void setDefaultQuantityType(StockQuantityType defaultQuantityType) { this.defaultQuantityType = defaultQuantityType; }
	public Currency getNominalCurrency() { return nominalCurrency; }
	public void setNominalCurrency(Currency nominalCurrency) { this.nominalCurrency = nominalCurrency; }
	public Currency getDefaultQuoteCurrency() { return defaultQuoteCurrency; }
	public void setDefaultQuoteCurrency(Currency defaultQuoteCurrency) { this.defaultQuoteCurrency = defaultQuoteCurrency; }
	public StockQuotationType getDefaultQuotationType() { return defaultQuotationType; }
	public void setDefaultQuotationType(StockQuotationType defaultQuotationType) { this.defaultQuotationType = defaultQuotationType; }
	public StockPriceBasis getDefaultPriceBasis() { return defaultPriceBasis; }
	public void setDefaultPriceBasis(StockPriceBasis defaultPriceBasis) { this.defaultPriceBasis = defaultPriceBasis; }
	public StockSecurityState getSecurityState() { return securityState; }
	public void setSecurityState(StockSecurityState securityState) { this.securityState = securityState; }
}
