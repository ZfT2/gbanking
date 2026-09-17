package de.zft2.gbanking.service.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockNumericValueType;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockStatementStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionEditField;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockExchangeRate;
import de.zft2.gbanking.db.dao.stock.StockExchangeRateSource;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioPosition;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatement;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatementPosition;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.db.dao.stock.StockTransactionMetadata;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;

public class StockPortfolioService extends AbstractDbService {

	private static final String MANUAL_SOURCE_CODE = "MANUAL";
	private static final String RECONCILIATION_SOURCE_CODE = "RECONCILIATION";
	private static final int QUANTITY_SCALE = StockNumericValueType.QUANTITY.getScaleDigits();
	private static final int PRICE_SCALE = StockNumericValueType.PRICE.getScaleDigits();
	private static final int EXCHANGE_RATE_SCALE = StockNumericValueType.FACTOR.getScaleDigits();

	public List<PortfolioSummary> getPortfolios() {
		Map<Integer, BankAccount> accounts = dbController.getAll(BankAccount.class).stream()
				.collect(Collectors.toMap(BankAccount::getId, Function.identity()));
		return dbController.getAll(StockPortfolio.class).stream()
				.map(portfolio -> createPortfolioSummary(portfolio, accounts))
				.sorted(Comparator.comparing(PortfolioSummary::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public BankAccount getPortfolioAccount(PortfolioSummary portfolio) {
		if (portfolio == null) {
			return null;
		}
		StockPortfolio storedPortfolio = dbController.getById(StockPortfolio.class, portfolio.portfolioId());
		return storedPortfolio != null ? dbController.getById(BankAccount.class, storedPortfolio.getAccountId()) : null;
	}

	public List<BankAccount> getSettlementAccountCandidates() {
		return dbController.getAll(BankAccount.class).stream()
				.filter(StockPortfolioService::isSettlementAccount)
				.filter(account -> account.getAccountState() == null || account.getAccountState() == AccountState.ACTIVE)
				.sorted(Comparator.comparing(account -> accountName(account, accountNumber(account)),
						String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public void changeSettlementAccount(PortfolioSummary portfolio, BankAccount targetAccount, LocalDate validFrom) {
		requirePortfolio(portfolio);
		if (validFrom == null) {
			throw new GBankingException("Der Gültigkeitsbeginn muss angegeben werden");
		}
		if (targetAccount == null) {
			throw new GBankingException("Es wurde kein gültiges Verrechnungskonto ausgewählt");
		}
		BankAccount storedTarget = requireSettlementAccount(targetAccount.getId());
		StockPortfolio storedPortfolio = dbController.getById(StockPortfolio.class, portfolio.portfolioId());
		StockPortfolioSettlementAccount currentRelation = storedPortfolio != null
				? dbController.getById(StockPortfolioSettlementAccount.class, storedPortfolio.getCurrentSettlementRelationId()) : null;
		validateSettlementAccountChange(storedPortfolio, currentRelation, validFrom);
		if (currentRelation.getAccountId() == storedTarget.getId()) {
			return;
		}

		dbController.executeInTransaction(() -> {
			if (validFrom.equals(currentRelation.getValidFrom())) {
				currentRelation.setAccountId(storedTarget.getId());
				dbController.insertOrUpdate(currentRelation);
				return null;
			}

			storedPortfolio.setCurrentSettlementRelationId(0);
			dbController.insertOrUpdate(storedPortfolio);
			currentRelation.setValidTo(validFrom);
			dbController.insertOrUpdate(currentRelation);

			StockPortfolioSettlementAccount newRelation = new StockPortfolioSettlementAccount();
			newRelation.setPortfolioId(storedPortfolio.getId());
			newRelation.setAccountId(storedTarget.getId());
			newRelation.setValidFrom(validFrom);
			dbController.insertOrUpdate(newRelation);
			storedPortfolio.setCurrentSettlementRelationId(newRelation.getId());
			dbController.insertOrUpdate(storedPortfolio);
			return null;
		});
	}

	public List<PositionSummary> getPositions(int portfolioId) {
		Map<Integer, StockSecurity> securities = securitiesById();
		Map<Integer, SecurityIdentifiers> identifiers = identifiersBySecurity();
		Map<Integer, StockSecurityPrice> prices = latestPricesBySecurity();
		Map<PositionKey, AcquisitionPriceValue> finTsPrices = latestFinTsAcquisitionPrices(portfolioId);
		Map<PositionKey, AcquisitionPriceState> calculatedPrices = acquisitionPricesByPosition(portfolioId);
		return dbController.getAllByParent(StockPortfolioPosition.class, portfolioId).stream()
				.map(position -> createPositionSummary(position, securities.get(position.getSecurityId()),
						identifiers.getOrDefault(position.getSecurityId(), SecurityIdentifiers.EMPTY),
						prices.get(position.getSecurityId()), finTsPrices, calculatedPrices))
				.filter(position -> position.quantity().signum() != 0)
				.sorted(Comparator.comparing(PositionSummary::securityName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public List<TransactionSummary> getTransactions(int portfolioId) {
		Map<Integer, StockSecurity> securities = securitiesById();
		Map<Integer, SecurityIdentifiers> identifiers = identifiersBySecurity();
		Map<Integer, StockDataSourceType> sourceTypes = dbController.getAll(StockDataSource.class).stream()
				.collect(Collectors.toMap(StockDataSource::getId, StockDataSource::getSourceType));
		List<TransactionSummary> result = new ArrayList<>();
		for (StockTransaction transaction : dbController.getAllByParent(StockTransaction.class, portfolioId).stream()
				.filter(candidate -> candidate.getTransactionStatus() == StockTransactionStatus.SETTLED).toList()) {
			for (StockTransactionSecurityLeg leg : dbController.getAllByParent(StockTransactionSecurityLeg.class, transaction.getId())) {
				StockSecurity security = securities.get(leg.getSecurityId());
				if (security != null) {
					SecurityIdentifiers securityIdentifiers = identifiers.getOrDefault(security.getId(), SecurityIdentifiers.EMPTY);
					result.add(createTransactionSummary(transaction, leg, security, securityIdentifiers,
							sourceTypes.get(transaction.getSourceId())));
				}
			}
		}
		result.sort(Comparator.comparing(TransactionSummary::date,
				Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(TransactionSummary::transactionId, Comparator.reverseOrder()));
		return result;
	}

	public List<SecuritySummary> getTransactionSecurities() {
		return dbController.getAll(StockSecurity.class).stream()
				.map(security -> new SecuritySummary(security.getId(), security.getName(),
						security.getDefaultQuantityType(), security.getDefaultQuoteCurrency()))
				.sorted(Comparator.comparing(SecuritySummary::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public List<TransactionPrefill> getReconciliationPrefills(int portfolioId) {
		List<StockTransaction> transactions = dbController.getAllByParent(StockTransaction.class, portfolioId).stream()
				.filter(transaction -> transaction.getTransactionStatus() == StockTransactionStatus.SETTLED).toList();
		Set<PositionKey> reconciledPositions = reconciliationPositionKeys(transactions);
		if (reconciledPositions.isEmpty()) {
			return List.of();
		}

		StockPortfolioStatement latestStatement = latestFinalStatement(portfolioId);
		if (latestStatement == null) {
			return List.of();
		}
		Map<PositionKey, Long> reportedQuantities = dbController
				.getAllByParent(StockPortfolioStatementPosition.class, latestStatement.getId()).stream()
				.collect(Collectors.toMap(StockPortfolioService::positionKey,
						StockPortfolioStatementPosition::getQuantityE9, Math::addExact));
		Map<PositionKey, Long> actualQuantities = actualQuantities(transactions, latestStatement.getStatementAt());
		return reconciledPositions.stream()
				.map(key -> createTransactionPrefill(key, reportedQuantities, actualQuantities))
				.filter(Objects::nonNull)
				.sorted(Comparator.comparingInt(TransactionPrefill::securityId)
						.thenComparing(prefill -> prefill.quantityType().getDbStateId()))
				.toList();
	}

	public TransactionEditData getTransactionEditData(int transactionId) {
		StockTransaction transaction = requireTransaction(transactionId);
		StockTransactionSecurityLeg securityLeg = primarySecurityLeg(transaction);
		StockSecurity security = dbController.getById(StockSecurity.class, securityLeg.getSecurityId());
		if (security == null) {
			throw new GBankingException("Das Wertpapier der Transaktion wurde nicht gefunden");
		}
		List<StockTransactionCashLeg> cashLegs = dbController.getAllByParent(StockTransactionCashLeg.class, transactionId);
		BigDecimal exchangeRate = resolveExchangeRate(cashLegs);
		StockTransactionMetadata metadata = dbController.getById(StockTransactionMetadata.class, transactionId);
		return new TransactionEditData(transactionId, transaction.getEditableFieldMask(), transaction.getTransactionType(),
				toDate(transaction.getTradeAt()), toDate(transaction.getSettledAt()), security.getId(),
				fromScaled(securityLeg.getQuantityE9(), QUANTITY_SCALE).abs(),
				securityLeg.getPriceE8() != null ? fromScaled(securityLeg.getPriceE8(), PRICE_SCALE) : null,
				securityLeg.getPriceCurrency(), exchangeRate,
				cashAmount(cashLegs, StockCashLegRole.FEE, exchangeRate),
				cashAmount(cashLegs, StockCashLegRole.TAX, exchangeRate),
				cashAmount(cashLegs, StockCashLegRole.ACCRUED_INTEREST, exchangeRate),
				metadata != null ? metadata.getNote() : null);
	}

	public void saveTransaction(PortfolioSummary portfolio, TransactionEditRequest request) {
		requirePortfolio(portfolio);
		if (request == null) {
			throw new GBankingException("Die Transaktionsdaten fehlen");
		}
		StockSecurity security = dbController.getById(StockSecurity.class, request.securityId());
		requireSecurity(security);
		TradeRequest trade = request.toTradeRequest();
		validateTradeRequest(trade);
		if (request.transactionId() == null) {
			persistTrade(portfolio, security, trade, request.note());
			return;
		}
		replaceEditableTransaction(portfolio, security, request, trade);
	}

	public void deleteTransaction(int transactionId) {
		StockTransaction transaction = requireTransaction(transactionId);
		if (transaction.getTransactionStatus() != StockTransactionStatus.SETTLED) {
			throw new GBankingException("Die Transaktion wurde bereits gelöscht oder ist nicht abgeschlossen");
		}
		List<PositionKey> affectedPositions = dbController
				.getAllByParent(StockTransactionSecurityLeg.class, transactionId).stream()
				.filter(leg -> leg.getLegRole() != StockSecurityLegRole.REFERENCE)
				.map(StockPortfolioService::positionKey).distinct().toList();
		dbController.executeInTransaction(() -> {
			StockDataSource source = dbController.getById(StockDataSource.class, transaction.getSourceId());
			if (source != null && source.getSourceType() == StockDataSourceType.MANUAL) {
				removeManualSettlementBookings(transaction);
			}
			transaction.setTransactionStatus(StockTransactionStatus.CANCELLED);
			transaction.setSettledAt(null);
			dbController.insertOrUpdate(transaction);
			if (transaction.getTransactionType() != StockTransactionType.RECONCILIATION_ADJUSTMENT) {
				affectedPositions.forEach(key -> rebalanceReconciliationAdjustment(
						transaction.getPortfolioId(), key));
			}
			return null;
		});
	}

	public List<Booking> getAccountTransactions(int accountId) {
		List<Booking> result = new ArrayList<>(dbController.getAllByParentFull(Booking.class, accountId));
		result.sort(Comparator.comparing(Booking::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(Booking::getId, Comparator.reverseOrder()));
		BankAccount account = dbController.getById(BankAccount.class, accountId);
		BigDecimal runningBalance = account != null ? account.getBalance() : null;
		for (Booking booking : result) {
			booking.setBalance(runningBalance);
			if (runningBalance != null) {
				runningBalance = runningBalance.subtract(booking.getAmount());
			}
		}
		return result;
	}

	public List<PriceSummary> getPrices(int securityId) {
		Map<Integer, StockDataSource> sources = dbController.getAll(StockDataSource.class).stream()
				.collect(Collectors.toMap(StockDataSource::getId, Function.identity()));
		List<StockSecurityPriceSource> priceSources = dbController.getAllByParent(StockSecurityPriceSource.class, securityId);
		Map<Integer, StockSecurityPriceSource> priceSourcesById = priceSources.stream()
				.collect(Collectors.toMap(StockSecurityPriceSource::getId, Function.identity()));
		List<StockSecurityPrice> prices = priceSources.stream()
				.flatMap(source -> dbController.getAllByParent(StockSecurityPrice.class, source.getId()).stream()).toList();
		Set<Integer> supersededIds = prices.stream().map(StockSecurityPrice::getSupersedesPriceId)
				.filter(id -> id != null).collect(Collectors.toSet());
		return prices.stream().filter(price -> !price.isDeleted() && !supersededIds.contains(price.getId()))
				.map(price -> createPriceSummary(price, priceSourcesById, sources))
				.sorted(Comparator.comparing(PriceSummary::date).reversed()
						.thenComparing(PriceSummary::priceId, Comparator.reverseOrder()))
				.toList();
	}

	private StockSecurityPrice savePrice(StockSecurity security, StockSecurityPrice correctedPrice,
			LocalDate date, BigDecimal priceValue, Currency currency) {
		requireSecurity(security);
		requirePositive(priceValue, "Der Kurs muss größer als null sein");
		if (date == null || currency == null) {
			throw new GBankingException("Datum und Währung müssen angegeben werden");
		}

		StockSecurityPrice price = new StockSecurityPrice();
		price.setPriceSourceId(getOrCreateManualPriceSource(security.getId()).getId());
		price.setQuotedAt(date.atStartOfDay());
		price.setPriceE8(toScaledLong(priceValue, PRICE_SCALE, "Kurs"));
		price.setQuoteCurrency(currency);
		price.setQuotationType(defaultQuotationType(security));
		price.setPriceBasis(price.getQuotationType() == StockQuotationType.PERCENT_OF_NOMINAL
				? defaultPriceBasis(security) : null);
		price.setPriceType(correctedPrice != null ? correctedPrice.getPriceType() : StockPriceType.CLOSE);
		price.setSupersedesPriceId(correctedPrice != null ? correctedPrice.getId() : null);
		return dbController.insertOrUpdate(price);
	}

	public StockSecurityPrice savePrice(int securityId, Integer correctedPriceId,
			LocalDate date, BigDecimal priceValue, Currency currency) {
		return savePrice(securityId, correctedPriceId, date, priceValue, currency, false);
	}

	public StockSecurityPrice savePrice(int securityId, Integer correctedPriceId,
			LocalDate date, BigDecimal priceValue, Currency currency, boolean protectedChangeConfirmed) {
		StockSecurity security = dbController.getById(StockSecurity.class, securityId);
		StockSecurityPrice correctedPrice = correctedPriceId != null ? requireActivePrice(securityId, correctedPriceId) : null;
		requireProtectedPriceChangeConfirmation(correctedPrice, protectedChangeConfirmed);
		return savePrice(security, correctedPrice, date, priceValue, currency);
	}

	public void deletePrice(int securityId, int priceId, boolean protectedChangeConfirmed) {
		StockSecurityPrice price = requireActivePrice(securityId, priceId);
		requireProtectedPriceChangeConfirmation(price, protectedChangeConfirmed);
		StockSecurityPrice deletion = new StockSecurityPrice();
		deletion.setPriceSourceId(getOrCreateManualPriceSource(securityId).getId());
		deletion.setQuotedAt(price.getQuotedAt());
		deletion.setPriceE8(price.getPriceE8());
		deletion.setQuoteCurrency(price.getQuoteCurrency());
		deletion.setQuotationType(price.getQuotationType());
		deletion.setPriceBasis(price.getPriceBasis());
		deletion.setPriceType(price.getPriceType());
		deletion.setVolumeE9(price.getVolumeE9());
		deletion.setDeleted(true);
		deletion.setSupersedesPriceId(price.getId());
		dbController.insertOrUpdate(deletion);
	}

	public void recordTrade(PortfolioSummary portfolio, PositionSummary position, TradeRequest request) {
		requirePortfolio(portfolio);
		requirePortfolioPosition(portfolio, position);
		StockSecurity security = requireSecurity(position);
		persistTrade(portfolio, security, request, null);
	}

	private void persistTrade(PortfolioSummary portfolio, StockSecurity security, TradeRequest request, String note) {
		validateTradeRequest(request);
		if (request.transactionType() == StockTransactionType.SELL) {
			BigDecimal available = availableForSale(portfolio.portfolioId(), security);
			if (request.quantity().compareTo(available) > 0) {
				throw new GBankingException("Die Verkaufsmenge überschreitet den verfügbaren Bestand");
			}
		}

		dbController.executeInTransaction(() -> {
			BankAccount settlementAccount = requireSettlementAccount(portfolio);
			Currency settlementCurrency = requireCurrency(settlementAccount);
			Integer exchangeRateId = storeExchangeRateIfRequired(request.currency(), settlementCurrency,
					request.exchangeRate(), request.settlementDate());
			BigDecimal conversionRate = conversionRate(request.currency(), settlementCurrency, request.exchangeRate());
			BigDecimal total = calculateValue(request.quantity(), request.unitPrice(), defaultQuotationType(security));
			int direction = request.transactionType() == StockTransactionType.BUY ? -1 : 1;
			List<CashComponent> cashComponents = new ArrayList<>();
			cashComponents.add(new CashComponent(StockCashLegRole.TRADE_VALUE,
					total.multiply(BigDecimal.valueOf(direction)).multiply(conversionRate), exchangeRateId));
			addExpense(cashComponents, StockCashLegRole.FEE, request.fees(), conversionRate, exchangeRateId);
			addExpense(cashComponents, StockCashLegRole.TAX, request.taxes(), conversionRate, exchangeRateId);
			addSigned(cashComponents, StockCashLegRole.ACCRUED_INTEREST, request.accruedInterest(),
					direction, conversionRate, exchangeRateId);

			StockTransaction transaction = createPendingTransaction(portfolio.portfolioId(), request.transactionType(),
					request.tradeDate(), request.settlementDate());
			StockTransactionSecurityLeg leg = createPositionLeg(transaction.getId(), security,
					toScaledLong(request.quantity(), QUANTITY_SCALE, "Anzahl") * -direction,
					toScaledLong(request.unitPrice(), PRICE_SCALE, "Kurs"), request.currency());
			dbController.insertOrUpdate(leg);
			persistCashComponents(transaction, settlementAccount, request.settlementDate(), cashComponents,
					tradePurpose(request.transactionType(), security.getName()));
			persistNote(transaction.getId(), note);
			settle(transaction, request.settlementDate());
			rebalanceReconciliationAdjustment(portfolio.portfolioId(),
					new PositionKey(security.getId(), defaultQuantityType(security)));
			return null;
		});
	}

	public void recordIncome(PortfolioSummary portfolio, PositionSummary position, IncomeRequest request) {
		requirePortfolio(portfolio);
		requirePortfolioPosition(portfolio, position);
		StockSecurity security = requireSecurity(position);
		validateIncomeRequest(request);
		dbController.executeInTransaction(() -> {
			BankAccount settlementAccount = requireSettlementAccount(portfolio);
			Currency settlementCurrency = requireCurrency(settlementAccount);
			Integer exchangeRateId = storeExchangeRateIfRequired(request.currency(), settlementCurrency,
					request.exchangeRate(), request.date());
			BigDecimal conversionRate = conversionRate(request.currency(), settlementCurrency, request.exchangeRate());
			StockTransaction transaction = createPendingTransaction(portfolio.portfolioId(), request.transactionType(),
					request.date(), request.date());
			StockTransactionSecurityLeg reference = new StockTransactionSecurityLeg();
			reference.setTransactionId(transaction.getId());
			reference.setLegNumber(1);
			reference.setSecurityId(security.getId());
			reference.setLegRole(StockSecurityLegRole.REFERENCE);
			reference.setQuantityE9(0);
			reference.setQuantityType(defaultQuantityType(security));
			dbController.insertOrUpdate(reference);

			StockCashLegRole incomeRole = request.transactionType() == StockTransactionType.DIVIDEND
					? StockCashLegRole.DIVIDEND : StockCashLegRole.INTEREST;
			List<CashComponent> cashComponents = new ArrayList<>();
			cashComponents.add(new CashComponent(incomeRole, request.grossAmount().multiply(conversionRate), exchangeRateId));
			addExpense(cashComponents, StockCashLegRole.TAX, request.taxes(), conversionRate, exchangeRateId);
			persistCashComponents(transaction, settlementAccount, request.date(), cashComponents,
					incomePurpose(request.transactionType(), security.getName()));
			settle(transaction, request.date());
			return null;
		});
	}

	public void transferPosition(PortfolioSummary source, PortfolioSummary target, PositionSummary position,
			BigDecimal quantity, LocalDate date) {
		requirePortfolio(source);
		requirePortfolio(target);
		requirePortfolioPosition(source, position);
		if (source.portfolioId() == target.portfolioId()) {
			throw new GBankingException("Quell- und Zieldepot müssen verschieden sein");
		}
		requirePositive(quantity, "Die Übertragungsmenge muss größer als null sein");
		if (quantity.compareTo(currentQuantity(source.portfolioId(), position.securityId())) > 0) {
			throw new GBankingException("Die Übertragungsmenge überschreitet den verfügbaren Bestand");
		}
		if (date == null) {
			throw new GBankingException("Das Übertragungsdatum muss angegeben werden");
		}

		dbController.executeInTransaction(() -> {
			persistPositionTransfer(source, target, requireSecurity(position), position.quantityType(), quantity,
					position.acquisitionPrice(), position.currency(), date);
			return null;
		});
	}

	public void transferPortfolio(PortfolioSummary source, PortfolioSummary target, LocalDate date, BigDecimal exchangeRate) {
		requirePortfolio(source);
		requirePortfolio(target);
		if (source.portfolioId() == target.portfolioId()) {
			throw new GBankingException("Quell- und Zieldepot müssen verschieden sein");
		}
		if (date == null) {
			throw new GBankingException("Das Übertragungsdatum muss angegeben werden");
		}
		dbController.executeInTransaction(() -> {
			for (PositionSummary position : getPositions(source.portfolioId())) {
				if (position.quantity().signum() != 0) {
					persistPositionTransfer(source, target, requireSecurity(position), position.quantityType(),
							position.quantity(), position.acquisitionPrice(), position.currency(), date);
				}
			}
			transferSettlementBalance(requireSettlementAccount(source), requireSettlementAccount(target), date, exchangeRate);
			return null;
		});
	}

	public static LocalDate defaultSettlementDate(LocalDate tradeDate) {
		LocalDate result = tradeDate;
		int businessDays = 0;
		while (businessDays < 2) {
			result = result.plusDays(1);
			if (result.getDayOfWeek() != DayOfWeek.SATURDAY && result.getDayOfWeek() != DayOfWeek.SUNDAY) {
				businessDays++;
			}
		}
		return result;
	}

	private void replaceEditableTransaction(PortfolioSummary portfolio, StockSecurity security,
			TransactionEditRequest request, TradeRequest trade) {
		StockTransaction transaction = requireTransaction(request.transactionId());
		if (transaction.getPortfolioId() != portfolio.portfolioId()
				|| transaction.getTransactionStatus() != StockTransactionStatus.SETTLED) {
			throw new GBankingException("Die ausgewählte Transaktion kann nicht bearbeitet werden");
		}
		TransactionEditData previous = getTransactionEditData(transaction.getId());
		validateEditableChanges(previous, request);
		StockDataSource source = dbController.getById(StockDataSource.class, transaction.getSourceId());
		if (source != null && source.getSourceType() == StockDataSourceType.MANUAL) {
			replaceManualTransaction(portfolio, transaction, security, trade, request.note());
			return;
		}
		applyImportedTransactionEnrichment(portfolio, transaction, security, request, trade);
	}

	private void replaceManualTransaction(PortfolioSummary portfolio, StockTransaction transaction,
			StockSecurity security, TradeRequest trade, String note) {
		dbController.executeInTransaction(() -> {
			removeManualSettlementBookings(transaction);
			transaction.setTransactionStatus(StockTransactionStatus.CANCELLED);
			transaction.setSettledAt(null);
			dbController.insertOrUpdate(transaction);
			persistTrade(portfolio, security, trade, note);
			return null;
		});
	}

	private void applyImportedTransactionEnrichment(PortfolioSummary portfolio, StockTransaction transaction,
			StockSecurity security, TransactionEditRequest request, TradeRequest trade) {
		dbController.executeInTransaction(() -> {
			StockTransactionSecurityLeg securityLeg = primarySecurityLeg(transaction);
			int direction = trade.transactionType() == StockTransactionType.BUY ? 1 : -1;
			if (isEditable(transaction, StockTransactionEditField.TRANSACTION_TYPE)) {
				transaction.setTransactionType(trade.transactionType());
			}
			if (isEditable(transaction, StockTransactionEditField.TRADE_DATE)) {
				transaction.setTradeAt(trade.tradeDate().atStartOfDay());
			}
			if (isEditable(transaction, StockTransactionEditField.SETTLEMENT_DATE)) {
				transaction.setSettlementDueAt(trade.settlementDate().atStartOfDay());
				transaction.setSettledAt(trade.settlementDate().atStartOfDay());
				transaction.setCashValueAt(trade.settlementDate().atStartOfDay());
				transaction.setSettlementDateInferred(false);
			}
			dbController.insertOrUpdate(transaction);

			if (isEditable(transaction, StockTransactionEditField.SECURITY)) {
				securityLeg.setSecurityId(security.getId());
			}
			if (isEditable(transaction, StockTransactionEditField.QUANTITY)) {
				securityLeg.setQuantityE9(toScaledLong(trade.quantity(), QUANTITY_SCALE, "Anzahl") * direction);
				securityLeg.setQuantityType(defaultQuantityType(security));
			}
			if (isEditable(transaction, StockTransactionEditField.PRICE_OR_AMOUNT)) {
				securityLeg.setPriceE8(toScaledLong(trade.unitPrice(), PRICE_SCALE, "Kurs"));
				securityLeg.setQuotationType(defaultQuotationType(security));
				securityLeg.setPriceBasis(securityLeg.getQuotationType() == StockQuotationType.PERCENT_OF_NOMINAL
						? defaultPriceBasis(security) : null);
			}
			if (isEditable(transaction, StockTransactionEditField.CURRENCY)) {
				securityLeg.setPriceCurrency(trade.currency());
			}
			dbController.insertOrUpdate(securityLeg);

			BankAccount account = requireSettlementAccount(portfolio);
			Currency settlementCurrency = requireCurrency(account);
			List<StockTransactionCashLeg> existingCashLegs = dbController.getAllByParent(
					StockTransactionCashLeg.class, transaction.getId());
			Integer exchangeRateId = isEditable(transaction, StockTransactionEditField.EXCHANGE_RATE)
					? storeExchangeRateIfRequired(trade.currency(), settlementCurrency,
							trade.exchangeRate(), trade.settlementDate())
					: existingCashLegs.stream().map(StockTransactionCashLeg::getExchangeRateId)
							.filter(Objects::nonNull).findFirst().orElse(null);
			BigDecimal rate = conversionRate(trade.currency(), settlementCurrency, trade.exchangeRate());
			if (isEditable(transaction, StockTransactionEditField.FEES)) {
				saveSupplementCashLeg(transaction, account, StockCashLegRole.FEE, trade.fees(), rate.negate(),
						exchangeRateId, trade.settlementDate());
			}
			if (isEditable(transaction, StockTransactionEditField.TAXES)) {
				saveSupplementCashLeg(transaction, account, StockCashLegRole.TAX, trade.taxes(), rate.negate(),
						exchangeRateId, trade.settlementDate());
			}
			if (isEditable(transaction, StockTransactionEditField.ACCRUED_INTEREST)) {
				saveSupplementCashLeg(transaction, account, StockCashLegRole.ACCRUED_INTEREST,
						trade.accruedInterest(), rate.multiply(BigDecimal.valueOf(-direction)), exchangeRateId,
						trade.settlementDate());
			}
			if (isEditable(transaction, StockTransactionEditField.NOTE)) {
				persistNote(transaction.getId(), request.note());
			}
			return null;
		});
	}

	private static boolean isEditable(StockTransaction transaction, StockTransactionEditField field) {
		return field.isSet(transaction.getEditableFieldMask());
	}

	private void saveSupplementCashLeg(StockTransaction transaction, BankAccount account, StockCashLegRole role,
			BigDecimal amount, BigDecimal factor, Integer exchangeRateId, LocalDate valueDate) {
		StockTransactionCashLeg cashLeg = dbController.getAllByParent(StockTransactionCashLeg.class,
				transaction.getId()).stream().filter(candidate -> candidate.getLegRole() == role).findFirst().orElse(null);
		if (amount == null || amount.signum() == 0) {
			if (cashLeg != null && cashLeg.getBookingId() == null) {
				dbController.delete(cashLeg, StatementType.DELETE);
			}
			return;
		}
		if (cashLeg == null) {
			cashLeg = new StockTransactionCashLeg();
			cashLeg.setTransactionId(transaction.getId());
			cashLeg.setLegNumber(nextCashLegNumber(transaction.getId()));
			cashLeg.setAccountId(account.getId());
			cashLeg.setLegRole(role);
		}
		cashLeg.setAmountMinor(toMinor(amount.abs().multiply(factor), requireCurrency(account)));
		cashLeg.setCurrency(requireCurrency(account));
		cashLeg.setExchangeRateId(exchangeRateId);
		cashLeg.setValueAt(valueDate.atStartOfDay());
		dbController.insertOrUpdate(cashLeg);
	}

	private int nextCashLegNumber(int transactionId) {
		return dbController.getAllByParent(StockTransactionCashLeg.class, transactionId).stream()
				.mapToInt(StockTransactionCashLeg::getLegNumber).max().orElse(0) + 1;
	}

	private void validateEditableChanges(TransactionEditData previous, TransactionEditRequest request) {
		requireEditable(previous, StockTransactionEditField.TRANSACTION_TYPE,
				previous.transactionType() != request.transactionType());
		requireEditable(previous, StockTransactionEditField.TRADE_DATE,
				!Objects.equals(previous.tradeDate(), request.tradeDate()));
		requireEditable(previous, StockTransactionEditField.SETTLEMENT_DATE,
				!Objects.equals(previous.settlementDate(), request.settlementDate()));
		requireEditable(previous, StockTransactionEditField.SECURITY,
				previous.securityId() != request.securityId());
		requireEditable(previous, StockTransactionEditField.QUANTITY,
				!sameDecimal(previous.quantity(), request.quantity()));
		requireEditable(previous, StockTransactionEditField.PRICE_OR_AMOUNT,
				!sameDecimal(previous.unitPrice(), request.unitPrice()));
		requireEditable(previous, StockTransactionEditField.CURRENCY,
				previous.currency() != request.currency());
		requireEditable(previous, StockTransactionEditField.EXCHANGE_RATE,
				!sameDecimal(previous.exchangeRate(), request.exchangeRate()));
		requireEditable(previous, StockTransactionEditField.FEES, !sameDecimal(previous.fees(), request.fees()));
		requireEditable(previous, StockTransactionEditField.TAXES, !sameDecimal(previous.taxes(), request.taxes()));
		requireEditable(previous, StockTransactionEditField.ACCRUED_INTEREST,
				!sameDecimal(previous.accruedInterest(), request.accruedInterest()));
		requireEditable(previous, StockTransactionEditField.NOTE, !Objects.equals(previous.note(), request.note()));
	}

	private static void requireEditable(TransactionEditData transaction, StockTransactionEditField field,
			boolean changed) {
		if (changed && !field.isSet(transaction.editableFieldMask())) {
			throw new GBankingException("Ein von FinTS oder aus einer Datei gelieferter Wert darf nicht geändert werden");
		}
	}

	private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
		return left == null ? right == null : right != null && left.compareTo(right) == 0;
	}

	private StockTransaction requireTransaction(int transactionId) {
		StockTransaction transaction = dbController.getById(StockTransaction.class, transactionId);
		if (transaction == null) {
			throw new GBankingException("Die ausgewählte Transaktion wurde nicht gefunden");
		}
		return transaction;
	}

	private StockTransactionSecurityLeg primarySecurityLeg(StockTransaction transaction) {
		return dbController.getAllByParent(StockTransactionSecurityLeg.class, transaction.getId()).stream()
				.filter(leg -> leg.getLegRole() == StockSecurityLegRole.POSITION)
				.findFirst().orElseGet(() -> dbController.getAllByParent(StockTransactionSecurityLeg.class,
						transaction.getId()).stream().findFirst()
						.orElseThrow(() -> new GBankingException("Die Transaktion enthält kein Wertpapier")));
	}

	private BigDecimal resolveExchangeRate(List<StockTransactionCashLeg> cashLegs) {
		return cashLegs.stream().map(StockTransactionCashLeg::getExchangeRateId).filter(Objects::nonNull)
				.map(id -> dbController.getById(StockExchangeRate.class, id)).filter(Objects::nonNull)
				.map(rate -> fromScaled(rate.getRateE12(), EXCHANGE_RATE_SCALE)).findFirst().orElse(BigDecimal.ONE);
	}

	private static BigDecimal cashAmount(List<StockTransactionCashLeg> cashLegs, StockCashLegRole role,
			BigDecimal exchangeRate) {
		return cashLegs.stream().filter(leg -> leg.getLegRole() == role).findFirst()
				.map(leg -> BigDecimal.valueOf(leg.getAmountMinor(), leg.getCurrency().getMinorUnitDigits()).abs()
						.divide(exchangeRate, PRICE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros())
				.orElse(null);
	}

	private void persistNote(int transactionId, String note) {
		String normalized = note != null && !note.isBlank() ? note.trim() : null;
		StockTransactionMetadata metadata = dbController.getById(StockTransactionMetadata.class, transactionId);
		if (normalized == null && metadata == null) {
			return;
		}
		if (metadata == null) {
			metadata = new StockTransactionMetadata();
			metadata.setTransactionId(transactionId);
		}
		metadata.setNote(normalized);
		dbController.insertOrUpdate(metadata);
	}

	private void removeManualSettlementBookings(StockTransaction transaction) {
		Set<Integer> bookingIds = dbController.getAllByParent(StockTransactionCashLeg.class, transaction.getId()).stream()
				.map(StockTransactionCashLeg::getBookingId).filter(Objects::nonNull).collect(Collectors.toSet());
		for (Integer bookingId : bookingIds) {
			Booking booking = dbController.getById(Booking.class, bookingId);
			if (booking == null) {
				continue;
			}
			BankAccount account = dbController.getById(BankAccount.class, booking.getAccountId());
			if (account != null && account.getBalance() != null && booking.getAmount() != null) {
				account.setBalance(account.getBalance().subtract(booking.getAmount()));
				dbController.insertOrUpdate(account);
			}
			dbController.delete(booking, StatementType.DELETE);
		}
	}

	private static LocalDate toDate(java.time.LocalDateTime value) {
		return value != null ? value.toLocalDate() : null;
	}

	private PortfolioSummary createPortfolioSummary(StockPortfolio portfolio, Map<Integer, BankAccount> accounts) {
		StockPortfolioSettlementAccount relation = dbController.getById(StockPortfolioSettlementAccount.class,
				portfolio.getCurrentSettlementRelationId());
		BankAccount portfolioAccount = accounts.get(portfolio.getAccountId());
		BankAccount settlementAccount = relation != null ? accounts.get(relation.getAccountId()) : null;
		return new PortfolioSummary(portfolio.getId(), accountName(portfolioAccount, "Depot " + portfolio.getId()),
				text(portfolioAccount, BankAccount::getBankName), accountNumber(portfolioAccount), portfolio.getOpenedAt(),
				settlementAccount != null ? settlementAccount.getId() : 0, accountName(settlementAccount, ""),
				text(settlementAccount, BankAccount::getBankName), text(settlementAccount, BankAccount::getIban),
				settlementAccount != null ? settlementAccount.getBaseCurrency() : null,
				settlementAccount != null ? settlementAccount.getBalance() : null);
	}

	private static String accountName(BankAccount account, String fallback) {
		String name = text(account, BankAccount::getAccountName);
		return !name.isBlank() ? name : fallback;
	}

	private static String accountNumber(BankAccount account) {
		String iban = text(account, BankAccount::getIban);
		return !iban.isBlank() ? iban : text(account, BankAccount::getNumber);
	}

	private static boolean isSettlementAccount(BankAccount account) {
		return account != null && (account.getAccountType() == AccountType.CURRENT_ACCOUNT
				|| account.getAccountType() == AccountType.DEPOT_ACCOUNT);
	}

	private static void validateSettlementAccountChange(StockPortfolio portfolio,
			StockPortfolioSettlementAccount currentRelation, LocalDate validFrom) {
		if (portfolio == null || currentRelation == null || currentRelation.getPortfolioId() != portfolio.getId()
				|| currentRelation.getValidFrom() == null || currentRelation.getValidTo() != null) {
			throw new GBankingException("Das Depot hat keine gültige aktuelle Verrechnungskonto-Zuordnung");
		}
		if (validFrom.isBefore(currentRelation.getValidFrom())) {
			throw new GBankingException("Der Gültigkeitsbeginn darf nicht vor der aktuellen Zuordnung liegen");
		}
		if (portfolio.getClosedAt() != null && !validFrom.isBefore(portfolio.getClosedAt())) {
			throw new GBankingException("Der Gültigkeitsbeginn muss vor der Schließung des Depots liegen");
		}
	}

	private static String text(BankAccount account, Function<BankAccount, String> provider) {
		String value = account != null ? provider.apply(account) : null;
		return value != null ? value : "";
	}

	private PositionSummary createPositionSummary(StockPortfolioPosition position, StockSecurity security,
			SecurityIdentifiers identifiers, StockSecurityPrice price,
			Map<PositionKey, AcquisitionPriceValue> finTsPrices,
			Map<PositionKey, AcquisitionPriceState> calculatedPrices) {
		if (security == null) {
			throw new GBankingException("Wertpapier zum Depotbestand wurde nicht gefunden");
		}
		BigDecimal quantity = fromScaled(position.getQuantityE9(), QUANTITY_SCALE);
		BigDecimal unitPrice = price != null ? fromScaled(price.getPriceE8(), PRICE_SCALE) : null;
		BigDecimal totalValue = price != null ? calculateValue(quantity, unitPrice, price.getQuotationType()) : null;
		Currency currency = price != null ? price.getQuoteCurrency() : security.getDefaultQuoteCurrency();
		PositionKey key = new PositionKey(position.getSecurityId(), position.getQuantityType());
		AcquisitionPriceValue finTsPrice = finTsPrices.get(key);
		BigDecimal acquisitionPrice = resolveAcquisitionPrice(currency, finTsPrice, calculatedPrices.get(key));
		BigDecimal acquisitionValue = acquisitionPrice != null
				? calculateValue(quantity, acquisitionPrice, security.getDefaultQuotationType()) : null;
		return new PositionSummary(position.getPortfolioId(), security.getId(), security.getName(), identifiers.isin(),
				identifiers.wkn(), position.getQuantityType(), quantity, acquisitionPrice,
				acquisitionValue, unitPrice, totalValue, currency, calculatePerformance(unitPrice, acquisitionPrice));
	}

	private static BigDecimal resolveAcquisitionPrice(Currency currency, AcquisitionPriceValue finTsPrice,
			AcquisitionPriceState calculatedPrice) {
		if (finTsPrice != null) {
			return finTsPrice.priceIn(currency);
		}
		return calculatedPrice != null ? calculatedPrice.priceIn(currency) : null;
	}

	private Map<PositionKey, AcquisitionPriceValue> latestFinTsAcquisitionPrices(int portfolioId) {
		Set<Integer> sourceIds = dbController.getAll(StockDataSource.class).stream()
				.filter(source -> source.getSourceType() == StockDataSourceType.FINTS)
				.map(StockDataSource::getId).collect(Collectors.toSet());
		StockPortfolioStatement latestStatement = dbController.getAllByParent(StockPortfolioStatement.class, portfolioId).stream()
				.filter(statement -> sourceIds.contains(statement.getSourceId()))
				.filter(statement -> statement.getStatementStatus() == StockStatementStatus.FINAL)
				.max(Comparator.comparing(StockPortfolioStatement::getStatementAt,
						Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingInt(StockPortfolioStatement::getId))
				.orElse(null);
		if (latestStatement == null) {
			return Map.of();
		}
		Map<PositionKey, AcquisitionPriceValue> result = new HashMap<>();
		for (StockPortfolioStatementPosition position : dbController.getAllByParent(
				StockPortfolioStatementPosition.class, latestStatement.getId())) {
			if (position.getReportedAcquisitionPriceE8() != null) {
				result.put(new PositionKey(position.getSecurityId(), position.getQuantityType()),
						new AcquisitionPriceValue(fromScaled(position.getReportedAcquisitionPriceE8(), PRICE_SCALE),
								position.getAcquisitionPriceCurrency()));
			}
		}
		return result;
	}

	private Map<PositionKey, AcquisitionPriceState> acquisitionPricesByPosition(int portfolioId) {
		Map<PositionKey, AcquisitionPriceState> result = new HashMap<>();
		List<StockTransaction> transactions = dbController.getAllByParent(StockTransaction.class, portfolioId).stream()
				.filter(transaction -> transaction.getTransactionStatus() == StockTransactionStatus.SETTLED)
				.filter(transaction -> transaction.getTransactionType() != StockTransactionType.RECONCILIATION_ADJUSTMENT)
				.sorted(Comparator.comparing(StockTransaction::getSettledAt,
						Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(StockTransaction::getId))
				.toList();
		for (StockTransaction transaction : transactions) {
			for (StockTransactionSecurityLeg leg : dbController.getAllByParent(StockTransactionSecurityLeg.class,
					transaction.getId())) {
				if (leg.getLegRole() != StockSecurityLegRole.REFERENCE) {
					PositionKey key = new PositionKey(leg.getSecurityId(), leg.getQuantityType());
					BigDecimal price = isAcquisitionTransaction(transaction.getTransactionType()) && leg.getPriceE8() != null
							? fromScaled(leg.getPriceE8(), PRICE_SCALE) : null;
					result.computeIfAbsent(key, ignored -> new AcquisitionPriceState())
							.apply(fromScaled(leg.getQuantityE9(), QUANTITY_SCALE), price, leg.getPriceCurrency());
				}
			}
		}
		return result;
	}

	private static boolean isAcquisitionTransaction(StockTransactionType type) {
		return type == StockTransactionType.BUY || type == StockTransactionType.TRANSFER_IN
				|| type == StockTransactionType.DELIVERY_IN || type == StockTransactionType.OPENING_BALANCE;
	}

	private TransactionSummary createTransactionSummary(StockTransaction transaction, StockTransactionSecurityLeg leg,
			StockSecurity security, SecurityIdentifiers identifiers, StockDataSourceType sourceType) {
		BigDecimal quantity = fromScaled(leg.getQuantityE9(), QUANTITY_SCALE).abs();
		BigDecimal unitPrice = leg.getPriceE8() != null ? fromScaled(leg.getPriceE8(), PRICE_SCALE) : null;
		BigDecimal totalPrice = unitPrice != null ? calculateValue(quantity, unitPrice, leg.getQuotationType()) : null;
		return new TransactionSummary(transaction.getId(), transaction.getTransactionType(), security.getId(), security.getName(),
				identifiers.isin(), identifiers.wkn(),
				transaction.getTradeAt() != null ? transaction.getTradeAt().toLocalDate() : null,
				quantity, unitPrice, totalPrice, leg.getPriceCurrency(), transaction.getEditableFieldMask(),
				sourceType != null && sourceType != StockDataSourceType.MANUAL);
	}

	private Map<Integer, StockSecurity> securitiesById() {
		return dbController.getAll(StockSecurity.class).stream()
				.collect(Collectors.toMap(StockSecurity::getId, Function.identity()));
	}

	private Map<Integer, SecurityIdentifiers> identifiersBySecurity() {
		Map<Integer, SecurityIdentifiers> result = new HashMap<>();
		for (StockSecurityIdentifier identifier : dbController.getAll(StockSecurityIdentifier.class)) {
			SecurityIdentifiers current = result.getOrDefault(identifier.getSecurityId(), SecurityIdentifiers.EMPTY);
			if (identifier.getIdentifierType() == StockIdentifierType.ISIN && current.isin() == null) {
				result.put(identifier.getSecurityId(), new SecurityIdentifiers(identifier.getIdentifierValue(), current.wkn()));
			} else if (identifier.getIdentifierType() == StockIdentifierType.WKN && current.wkn() == null) {
				result.put(identifier.getSecurityId(), new SecurityIdentifiers(current.isin(), identifier.getIdentifierValue()));
			}
		}
		return result;
	}

	private Map<Integer, StockSecurityPrice> latestPricesBySecurity() {
		Map<Integer, StockSecurityPrice> result = new HashMap<>();
		for (StockSecurity security : dbController.getAll(StockSecurity.class)) {
			List<StockSecurityPriceSource> sources = dbController.getAllByParent(StockSecurityPriceSource.class, security.getId()).stream()
					.filter(StockSecurityPriceSource::isEnabled).toList();
			Map<Integer, Integer> priorities = sources.stream().collect(
					Collectors.toMap(StockSecurityPriceSource::getId, StockSecurityPriceSource::getPriority));
			List<StockSecurityPrice> prices = sources.stream()
					.flatMap(source -> dbController.getAllByParent(StockSecurityPrice.class, source.getId()).stream()).toList();
			Set<Integer> supersededIds = prices.stream().map(StockSecurityPrice::getSupersedesPriceId)
					.filter(id -> id != null).collect(Collectors.toSet());
			prices.stream().filter(price -> !price.isDeleted() && !supersededIds.contains(price.getId()))
					.max(Comparator.comparing(StockSecurityPrice::getQuotedAt)
							.thenComparingInt(price -> -priorities.get(price.getPriceSourceId()))
							.thenComparingInt(StockSecurityPrice::getId))
					.ifPresent(price -> result.put(security.getId(), price));
		}
		return result;
	}

	private PriceSummary createPriceSummary(StockSecurityPrice price, Map<Integer, StockSecurityPriceSource> priceSources,
			Map<Integer, StockDataSource> sources) {
		StockSecurityPriceSource priceSource = priceSources.get(price.getPriceSourceId());
		StockDataSource source = priceSource != null ? sources.get(priceSource.getSourceId()) : null;
		return new PriceSummary(price.getId(), price.getQuotedAt().toLocalDate(),
				fromScaled(price.getPriceE8(), PRICE_SCALE), price.getQuoteCurrency(), price.getPriceType(),
				source != null ? source.getSourceName() : "", source != null ? source.getSourceType() : null);
	}

	private StockSecurityPrice requireActivePrice(int securityId, int priceId) {
		StockSecurityPrice price = dbController.getById(StockSecurityPrice.class, priceId);
		if (price == null) {
			throw new GBankingException("Der Wertpapierkurs wurde nicht gefunden oder bereits ersetzt");
		}
		StockSecurityPriceSource source = dbController.getById(StockSecurityPriceSource.class, price.getPriceSourceId());
		boolean inactive = price.isDeleted() || dbController.getAll(StockSecurityPrice.class).stream()
				.anyMatch(candidate -> Objects.equals(candidate.getSupersedesPriceId(), priceId));
		if (source == null || source.getSecurityId() != securityId || inactive) {
			throw new GBankingException("Der Wertpapierkurs wurde nicht gefunden oder bereits ersetzt");
		}
		return price;
	}

	private void requireProtectedPriceChangeConfirmation(StockSecurityPrice price, boolean confirmed) {
		if (price == null || confirmed) {
			return;
		}
		StockSecurityPriceSource priceSource = dbController.getById(StockSecurityPriceSource.class,
				price.getPriceSourceId());
		StockDataSource source = priceSource != null
				? dbController.getById(StockDataSource.class, priceSource.getSourceId()) : null;
		if (source != null && isProtectedPriceSource(source.getSourceType())) {
			throw new GBankingException(
					"Extern gelieferte Kurse dürfen nur nach Bestätigung geändert oder gelöscht werden");
		}
	}

	private static boolean isProtectedPriceSource(StockDataSourceType sourceType) {
		return sourceType == StockDataSourceType.FINTS || sourceType == StockDataSourceType.FILE
				|| sourceType == StockDataSourceType.MARKET_DATA;
	}

	private StockDataSource getOrCreateManualSource() {
		return dbController.getAll(StockDataSource.class).stream()
				.filter(source -> MANUAL_SOURCE_CODE.equalsIgnoreCase(source.getSourceCode()))
				.findFirst().orElseGet(() -> {
					StockDataSource source = new StockDataSource();
					source.setSourceCode(MANUAL_SOURCE_CODE);
					source.setSourceName("Manuelle Eingabe");
					source.setSourceType(StockDataSourceType.MANUAL);
					source.setDefaultPriority(0);
					return dbController.insertOrUpdate(source);
				});
	}

	private StockSecurityPriceSource getOrCreateManualPriceSource(int securityId) {
		StockDataSource source = getOrCreateManualSource();
		return dbController.getAllByParent(StockSecurityPriceSource.class, securityId).stream()
				.filter(priceSource -> priceSource.getSourceId() == source.getId()
						&& priceSource.getProviderSymbol() == null && priceSource.getMarketIdentifierCode() == null)
				.findFirst().orElseGet(() -> {
					StockSecurityPriceSource priceSource = new StockSecurityPriceSource();
					priceSource.setSecurityId(securityId);
					priceSource.setSourceId(source.getId());
					priceSource.setPriority(0);
					return dbController.insertOrUpdate(priceSource);
				});
	}

	private StockTransaction createPendingTransaction(int portfolioId, StockTransactionType type,
			LocalDate tradeDate, LocalDate settlementDate) {
		StockTransaction transaction = new StockTransaction();
		transaction.setPortfolioId(portfolioId);
		transaction.setSourceId(getOrCreateManualSource().getId());
		transaction.setTransactionType(type);
		transaction.setTransactionStatus(StockTransactionStatus.PENDING);
		transaction.setEditableFieldMask(StockTransactionEditField.ALL_FIELDS_MASK);
		transaction.setTradeAt(tradeDate.atStartOfDay());
		transaction.setSettlementDueAt(settlementDate.atStartOfDay());
		transaction.setCashValueAt(settlementDate.atStartOfDay());
		return dbController.insertOrUpdate(transaction);
	}

	private StockTransactionSecurityLeg createPositionLeg(int transactionId, StockSecurity security, long quantityE9,
			Long priceE8, Currency priceCurrency) {
		StockTransactionSecurityLeg leg = new StockTransactionSecurityLeg();
		leg.setTransactionId(transactionId);
		leg.setLegNumber(1);
		leg.setSecurityId(security.getId());
		leg.setLegRole(StockSecurityLegRole.POSITION);
		leg.setQuantityE9(quantityE9);
		leg.setQuantityType(defaultQuantityType(security));
		leg.setPriceE8(priceE8);
		leg.setPriceCurrency(priceE8 != null ? priceCurrency : null);
		leg.setQuotationType(priceE8 != null ? defaultQuotationType(security) : null);
		leg.setPriceBasis(leg.getQuotationType() == StockQuotationType.PERCENT_OF_NOMINAL
				? defaultPriceBasis(security) : null);
		return leg;
	}

	private void persistCashComponents(StockTransaction transaction, BankAccount account, LocalDate date,
			List<CashComponent> cashComponents, String purpose) {
		if (account == null) {
			throw new GBankingException("Das Depot hat kein gültiges Verrechnungskonto");
		}
		Currency currency = requireCurrency(account);
		BigDecimal netAmount = cashComponents.stream().map(CashComponent::amount)
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(currency.getMinorUnitDigits(), RoundingMode.HALF_UP);
		Booking booking = netAmount.signum() != 0 ? createBooking(account, date, purpose, netAmount) : null;
		int legNumber = 1;
		for (CashComponent component : cashComponents) {
			long amountMinor = toMinor(component.amount(), currency);
			if (amountMinor == 0) {
				continue;
			}
			StockTransactionCashLeg cashLeg = new StockTransactionCashLeg();
			cashLeg.setTransactionId(transaction.getId());
			cashLeg.setLegNumber(legNumber++);
			cashLeg.setAccountId(account.getId());
			cashLeg.setBookingId(booking != null ? booking.getId() : null);
			cashLeg.setLegRole(component.role());
			cashLeg.setAmountMinor(amountMinor);
			cashLeg.setCurrency(currency);
			cashLeg.setExchangeRateId(component.exchangeRateId());
			cashLeg.setValueAt(date.atStartOfDay());
			dbController.insertOrUpdate(cashLeg);
		}
	}

	private Booking createBooking(BankAccount account, LocalDate date, String purpose, BigDecimal amount) {
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setPurpose(purpose);
		booking.setAmount(amount);
		booking.setBookingType(BookingType.fromAmount(amount));
		booking.setSource(Source.MANUELL_NEW);
		if (account.getBalance() != null) {
			account.setBalance(account.getBalance().add(amount));
			booking.setBalance(account.getBalance());
			dbController.insertOrUpdate(account);
		}
		return dbController.insertOrUpdate(booking);
	}

	private void settle(StockTransaction transaction, LocalDate settlementDate) {
		transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
		transaction.setSettledAt(settlementDate.atStartOfDay());
		dbController.insertOrUpdate(transaction);
	}

	private void persistPositionTransfer(PortfolioSummary source, PortfolioSummary target, StockSecurity security,
			StockQuantityType quantityType, BigDecimal quantity, BigDecimal acquisitionPrice,
			Currency acquisitionCurrency, LocalDate date) {
		long quantityE9 = toScaledLong(quantity, QUANTITY_SCALE, "Anzahl");
		Long priceE8 = acquisitionPrice != null ? toScaledLong(acquisitionPrice, PRICE_SCALE, "Einstandskurs") : null;
		StockTransaction outgoing = createPendingTransaction(source.portfolioId(), StockTransactionType.TRANSFER_OUT, date, date);
		StockTransactionSecurityLeg outgoingLeg = createPositionLeg(outgoing.getId(), security, -quantityE9, priceE8,
				acquisitionCurrency);
		outgoingLeg.setQuantityType(quantityType);
		dbController.insertOrUpdate(outgoingLeg);
		settle(outgoing, date);

		StockTransaction incoming = createPendingTransaction(target.portfolioId(), StockTransactionType.TRANSFER_IN, date, date);
		StockTransactionSecurityLeg incomingLeg = createPositionLeg(incoming.getId(), security, quantityE9, priceE8,
				acquisitionCurrency);
		incomingLeg.setQuantityType(quantityType);
		dbController.insertOrUpdate(incomingLeg);
		settle(incoming, date);
	}

	private void transferSettlementBalance(BankAccount source, BankAccount target, LocalDate date, BigDecimal exchangeRate) {
		if (source.getId() == target.getId()) {
			return;
		}
		if (source.getBalance() == null || target.getBalance() == null) {
			throw new GBankingException("Der Saldo beider Verrechnungskonten muss bekannt sein");
		}
		if (source.getBalance().signum() == 0) {
			return;
		}
		Currency sourceCurrency = requireCurrency(source);
		Currency targetCurrency = requireCurrency(target);
		BigDecimal rate = conversionRate(sourceCurrency, targetCurrency, exchangeRate);
		storeExchangeRateIfRequired(sourceCurrency, targetCurrency, rate, date);
		BigDecimal sourceBalance = source.getBalance();
		BigDecimal targetAmount = sourceBalance.multiply(rate)
				.setScale(targetCurrency.getMinorUnitDigits(), RoundingMode.HALF_UP);
		String purpose = "Saldoübertrag Wertpapierdepot";

		Booking sourceBooking = createRebooking(source, target, date, purpose, sourceBalance.negate(), null);
		Booking targetBooking = createRebooking(target, source, date, purpose, targetAmount, sourceBooking.getId());
		BigDecimal sourceNewBalance = BigDecimal.ZERO.setScale(sourceCurrency.getMinorUnitDigits());
		BigDecimal targetNewBalance = target.getBalance().add(targetAmount);
		if (sourceCurrency != targetCurrency) {
			BookingForeignCurrencyDetails foreignDetails = new BookingForeignCurrencyDetails();
			foreignDetails.setForeignAmount(sourceBalance);
			foreignDetails.setForeignCurrency(sourceCurrency);
			foreignDetails.setExchangeRateToBaseCurrency(rate);
			targetBooking.setForeignCurrencyDetails(foreignDetails);
		}
		sourceBooking.setCrossBookingId(targetBooking.getId());
		dbController.insertOrUpdate(sourceBooking);
		dbController.insertOrUpdate(targetBooking);
		source.setBalance(sourceNewBalance);
		target.setBalance(targetNewBalance);
		dbController.insertOrUpdate(source);
		dbController.insertOrUpdate(target);
	}

	private Booking createRebooking(BankAccount account, BankAccount crossAccount, LocalDate date, String purpose,
			BigDecimal amount, Integer crossBookingId) {
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setPurpose(purpose);
		booking.setAmount(amount);
		booking.setBookingType(BookingType.rebookingFromAmount(amount));
		booking.setCrossAccountId(crossAccount.getId());
		booking.setCrossBookingId(crossBookingId);
		booking.setSource(Source.MANUELL_NEW);
		return dbController.insertOrUpdate(booking);
	}

	private Integer storeExchangeRateIfRequired(Currency sourceCurrency, Currency targetCurrency,
			BigDecimal rate, LocalDate date) {
		if (sourceCurrency == targetCurrency) {
			return null;
		}
		BigDecimal validatedRate = requirePositive(rate, "Für unterschiedliche Währungen muss ein Wechselkurs angegeben werden");
		StockDataSource source = getOrCreateManualSource();
		StockExchangeRateSource rateSource = dbController.getAll(StockExchangeRateSource.class).stream()
				.filter(candidate -> candidate.getSourceId() == source.getId()
						&& candidate.getBaseCurrency() == sourceCurrency && candidate.getQuoteCurrency() == targetCurrency)
				.findFirst().orElseGet(() -> {
					StockExchangeRateSource created = new StockExchangeRateSource();
					created.setSourceId(source.getId());
					created.setBaseCurrency(sourceCurrency);
					created.setQuoteCurrency(targetCurrency);
					created.setPriority(0);
					return dbController.insertOrUpdate(created);
				});
		StockExchangeRate exchangeRate = new StockExchangeRate();
		exchangeRate.setExchangeRateSourceId(rateSource.getId());
		exchangeRate.setQuotedAt(date.atStartOfDay());
		exchangeRate.setRateE12(toScaledLong(validatedRate, EXCHANGE_RATE_SCALE, "Wechselkurs"));
		return dbController.insertOrUpdate(exchangeRate).getId();
	}

	private static BigDecimal conversionRate(Currency sourceCurrency, Currency targetCurrency, BigDecimal rate) {
		return sourceCurrency == targetCurrency ? BigDecimal.ONE
				: requirePositive(rate, "Für unterschiedliche Währungen muss ein Wechselkurs angegeben werden");
	}

	private static void addExpense(List<CashComponent> components, StockCashLegRole role, BigDecimal amount,
			BigDecimal conversionRate, Integer exchangeRateId) {
		if (amount != null && amount.signum() != 0) {
			components.add(new CashComponent(role, amount.abs().negate().multiply(conversionRate), exchangeRateId));
		}
	}

	private static void addSigned(List<CashComponent> components, StockCashLegRole role, BigDecimal amount,
			int direction, BigDecimal conversionRate, Integer exchangeRateId) {
		if (amount != null && amount.signum() != 0) {
			components.add(new CashComponent(role, amount.abs().multiply(BigDecimal.valueOf(direction))
					.multiply(conversionRate), exchangeRateId));
		}
	}

	private static BigDecimal calculateValue(BigDecimal quantity, BigDecimal price, StockQuotationType quotationType) {
		BigDecimal value = quantity.multiply(price);
		return quotationType == StockQuotationType.PERCENT_OF_NOMINAL ? value.movePointLeft(2) : value;
	}

	private static BigDecimal calculatePerformance(BigDecimal currentPrice, BigDecimal acquisitionPrice) {
		if (currentPrice == null || acquisitionPrice == null || acquisitionPrice.signum() == 0) {
			return null;
		}
		return currentPrice.subtract(acquisitionPrice).movePointRight(2)
				.divide(acquisitionPrice, 4, RoundingMode.HALF_UP).stripTrailingZeros();
	}

	private StockPortfolioStatement latestFinalStatement(int portfolioId) {
		return dbController.getAllByParent(StockPortfolioStatement.class, portfolioId).stream()
				.filter(statement -> statement.getStatementStatus() == StockStatementStatus.FINAL)
				.max(Comparator.comparing(StockPortfolioStatement::getStatementAt,
						Comparator.nullsFirst(Comparator.naturalOrder()))
						.thenComparingInt(StockPortfolioStatement::getId))
				.orElse(null);
	}

	private Set<PositionKey> reconciliationPositionKeys(List<StockTransaction> transactions) {
		return transactions.stream()
				.filter(transaction -> transaction.getTransactionType() == StockTransactionType.RECONCILIATION_ADJUSTMENT)
				.flatMap(transaction -> dbController
						.getAllByParent(StockTransactionSecurityLeg.class, transaction.getId()).stream())
				.filter(leg -> leg.getLegRole() != StockSecurityLegRole.REFERENCE)
				.map(StockPortfolioService::positionKey)
				.collect(Collectors.toSet());
	}

	private Map<PositionKey, Long> actualQuantities(List<StockTransaction> transactions,
			LocalDateTime statementAt) {
		Map<PositionKey, Long> result = new HashMap<>();
		transactions.stream()
				.filter(transaction -> transaction.getTransactionType() != StockTransactionType.RECONCILIATION_ADJUSTMENT)
				.filter(transaction -> statementAt == null || !transaction.getSettledAt().isAfter(statementAt))
				.flatMap(transaction -> dbController
						.getAllByParent(StockTransactionSecurityLeg.class, transaction.getId()).stream())
				.filter(leg -> leg.getLegRole() != StockSecurityLegRole.REFERENCE)
				.forEach(leg -> result.merge(positionKey(leg), leg.getQuantityE9(), Math::addExact));
		return result;
	}

	private StockDataSource getOrCreateReconciliationSource() {
		return dbController.getAll(StockDataSource.class).stream()
				.filter(source -> RECONCILIATION_SOURCE_CODE.equalsIgnoreCase(source.getSourceCode()))
				.findFirst().orElseGet(() -> {
					StockDataSource source = new StockDataSource();
					source.setSourceCode(RECONCILIATION_SOURCE_CODE);
					source.setSourceName("Bestandsabgleich");
					source.setSourceType(StockDataSourceType.MANUAL);
					source.setDefaultPriority(100);
					return dbController.insertOrUpdate(source);
				});
	}

	private void rebalanceReconciliationAdjustment(int portfolioId, PositionKey key) {
		StockPortfolioStatement statement = latestFinalStatement(portfolioId);
		if (statement == null) {
			return;
		}
		List<StockTransaction> transactions = dbController.getAllByParent(StockTransaction.class, portfolioId).stream()
				.filter(transaction -> transaction.getTransactionStatus() == StockTransactionStatus.SETTLED).toList();
		List<StockTransaction> adjustments = matchingReconciliationTransactions(transactions, key);
		if (adjustments.isEmpty()) {
			return;
		}
		StockPortfolioStatementPosition reportedPosition = dbController
				.getAllByParent(StockPortfolioStatementPosition.class, statement.getId()).stream()
				.filter(position -> positionKey(position).equals(key)).findFirst().orElse(null);
		long reportedQuantity = reportedPosition != null ? reportedPosition.getQuantityE9() : 0L;
		long actualQuantity = actualQuantities(transactions, statement.getStatementAt()).getOrDefault(key, 0L);
		long requiredAdjustment = Math.subtractExact(reportedQuantity, actualQuantity);
		long currentAdjustment = reconciliationQuantity(adjustments, key);
		if (requiredAdjustment == currentAdjustment) {
			return;
		}

		for (StockTransaction adjustment : adjustments) {
			adjustment.setTransactionStatus(StockTransactionStatus.CANCELLED);
			adjustment.setSettledAt(null);
			dbController.insertOrUpdate(adjustment);
		}
		if (requiredAdjustment != 0) {
			createReconciliationAdjustment(portfolioId, statement, reportedPosition, key, requiredAdjustment);
		}
	}

	private List<StockTransaction> matchingReconciliationTransactions(List<StockTransaction> transactions,
			PositionKey key) {
		return transactions.stream()
				.filter(transaction -> transaction.getTransactionType() == StockTransactionType.RECONCILIATION_ADJUSTMENT)
				.filter(transaction -> dbController
						.getAllByParent(StockTransactionSecurityLeg.class, transaction.getId()).stream()
						.anyMatch(leg -> positionKey(leg).equals(key)))
				.toList();
	}

	private long reconciliationQuantity(List<StockTransaction> adjustments, PositionKey key) {
		long result = 0L;
		for (StockTransaction adjustment : adjustments) {
			for (StockTransactionSecurityLeg leg : dbController
					.getAllByParent(StockTransactionSecurityLeg.class, adjustment.getId())) {
				if (positionKey(leg).equals(key)) {
					result = Math.addExact(result, leg.getQuantityE9());
				}
			}
		}
		return result;
	}

	private void createReconciliationAdjustment(int portfolioId, StockPortfolioStatement statement,
			StockPortfolioStatementPosition reportedPosition, PositionKey key, long quantityE9) {
		LocalDateTime statementAt = Objects.requireNonNullElseGet(statement.getStatementAt(), LocalDateTime::now);
		StockTransaction transaction = new StockTransaction();
		transaction.setPortfolioId(portfolioId);
		transaction.setSourceId(getOrCreateReconciliationSource().getId());
		transaction.setTransactionType(StockTransactionType.RECONCILIATION_ADJUSTMENT);
		transaction.setTransactionStatus(StockTransactionStatus.PENDING);
		transaction.setTradeAt(statementAt);
		transaction.setSettlementDueAt(statementAt);
		transaction.setProviderBookedAt(statementAt);
		transaction.setReconciliationStatementId(statement.getId());
		dbController.insertOrUpdate(transaction);

		StockTransactionSecurityLeg leg = new StockTransactionSecurityLeg();
		leg.setTransactionId(transaction.getId());
		leg.setLegNumber(1);
		leg.setSecurityId(key.securityId());
		leg.setLegRole(StockSecurityLegRole.POSITION);
		leg.setQuantityE9(quantityE9);
		leg.setQuantityType(key.quantityType());
		if (reportedPosition != null && reportedPosition.getReportedPriceE8() != null) {
			leg.setPriceE8(reportedPosition.getReportedPriceE8());
			leg.setPriceCurrency(reportedPosition.getPriceCurrency());
			leg.setQuotationType(reportedPosition.getQuotationType());
			leg.setPriceBasis(reportedPosition.getPriceBasis());
		}
		dbController.insertOrUpdate(leg);
		transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
		transaction.setSettledAt(statementAt);
		dbController.insertOrUpdate(transaction);
	}

	private static TransactionPrefill createTransactionPrefill(PositionKey key,
			Map<PositionKey, Long> reportedQuantities, Map<PositionKey, Long> actualQuantities) {
		long difference = Math.subtractExact(reportedQuantities.getOrDefault(key, 0L),
				actualQuantities.getOrDefault(key, 0L));
		if (difference == 0) {
			return null;
		}
		StockTransactionType type = difference > 0 ? StockTransactionType.BUY : StockTransactionType.SELL;
		return new TransactionPrefill(key.securityId(), key.quantityType(), type,
				fromScaled(difference, QUANTITY_SCALE).abs());
	}

	private static PositionKey positionKey(StockPortfolioStatementPosition position) {
		return new PositionKey(position.getSecurityId(), position.getQuantityType());
	}

	private static PositionKey positionKey(StockTransactionSecurityLeg leg) {
		return new PositionKey(leg.getSecurityId(), leg.getQuantityType());
	}

	private boolean hasCurrentPosition(PositionSummary position) {
		return dbController.getAllByParent(StockPortfolioPosition.class, position.portfolioId()).stream()
				.anyMatch(candidate -> candidate.getSecurityId() == position.securityId()
						&& candidate.getQuantityType() == position.quantityType()
						&& candidate.getQuantityE9() != 0);
	}

	private static long toScaledLong(BigDecimal value, int scale, String fieldName) {
		try {
			return value.setScale(scale, RoundingMode.UNNECESSARY).movePointRight(scale).longValueExact();
		} catch (ArithmeticException exception) {
			throw new GBankingException(fieldName + " hat zu viele Nachkommastellen oder ist zu groß", exception);
		}
	}

	private static long toMinor(BigDecimal value, Currency currency) {
		return value.setScale(currency.getMinorUnitDigits(), RoundingMode.HALF_UP)
				.movePointRight(currency.getMinorUnitDigits()).longValueExact();
	}

	private static BigDecimal fromScaled(long value, int scale) {
		return BigDecimal.valueOf(value, scale).stripTrailingZeros();
	}

	private static StockQuantityType defaultQuantityType(StockSecurity security) {
		return security.getDefaultQuantityType() != null ? security.getDefaultQuantityType() : StockQuantityType.UNITS;
	}

	private static StockQuotationType defaultQuotationType(StockSecurity security) {
		return security.getDefaultQuotationType() != null ? security.getDefaultQuotationType() : StockQuotationType.ABSOLUTE;
	}

	private static StockPriceBasis defaultPriceBasis(StockSecurity security) {
		return security.getDefaultPriceBasis() != null ? security.getDefaultPriceBasis() : StockPriceBasis.CLEAN;
	}

	private static String tradePurpose(StockTransactionType type, String securityName) {
		return (type == StockTransactionType.BUY ? "Wertpapierkauf " : "Wertpapierverkauf ") + securityName;
	}

	private static String incomePurpose(StockTransactionType type, String securityName) {
		return (type == StockTransactionType.DIVIDEND ? "Dividende " : "Zinsgutschrift ") + securityName;
	}

	private static void validateTradeRequest(TradeRequest request) {
		if (request == null || request.tradeDate() == null || request.settlementDate() == null || request.currency() == null
				|| request.transactionType() != StockTransactionType.BUY && request.transactionType() != StockTransactionType.SELL) {
			throw new GBankingException("Die Handelsdaten sind unvollständig");
		}
		if (request.settlementDate().isBefore(request.tradeDate())) {
			throw new GBankingException("Der Valutatag darf nicht vor dem Handelstag liegen");
		}
		requirePositive(request.quantity(), "Die Anzahl muss größer als null sein");
		requirePositive(request.unitPrice(), "Der Kurs muss größer als null sein");
		requireNonNegative(request.fees(), "Gebühren dürfen nicht negativ sein");
		requireNonNegative(request.taxes(), "Steuern dürfen nicht negativ sein");
		requireNonNegative(request.accruedInterest(), "Stückzinsen dürfen nicht negativ sein");
	}

	private static void validateIncomeRequest(IncomeRequest request) {
		if (request == null || request.date() == null || request.currency() == null
				|| request.transactionType() != StockTransactionType.DIVIDEND
						&& request.transactionType() != StockTransactionType.INTEREST) {
			throw new GBankingException("Die Ertragsdaten sind unvollständig");
		}
		requirePositive(request.grossAmount(), "Der Bruttoertrag muss größer als null sein");
		requireNonNegative(request.taxes(), "Steuern dürfen nicht negativ sein");
	}

	private static void requirePortfolio(PortfolioSummary portfolio) {
		if (portfolio == null || portfolio.portfolioId() <= 0 || portfolio.settlementAccountId() <= 0) {
			throw new GBankingException("Es wurde kein gültiges Depot ausgewählt");
		}
	}

	private static void requirePortfolioPosition(PortfolioSummary portfolio, PositionSummary position) {
		if (position == null || position.portfolioId() != portfolio.portfolioId()) {
			throw new GBankingException("Die Depotposition gehört nicht zum ausgewählten Depot");
		}
	}

	private BigDecimal currentQuantity(int portfolioId, int securityId) {
		return dbController.getAllByParent(StockPortfolioPosition.class, portfolioId).stream()
				.filter(position -> position.getSecurityId() == securityId)
				.map(position -> fromScaled(position.getQuantityE9(), QUANTITY_SCALE))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal availableForSale(int portfolioId, StockSecurity security) {
		BigDecimal current = currentQuantity(portfolioId, security.getId());
		return getReconciliationPrefills(portfolioId).stream()
				.filter(prefill -> prefill.securityId() == security.getId()
						&& prefill.quantityType() == security.getDefaultQuantityType()
						&& prefill.transactionType() == StockTransactionType.SELL)
				.map(TransactionPrefill::quantity).findFirst().map(current::max).orElse(current);
	}

	private StockSecurity requireSecurity(PositionSummary position) {
		StockSecurity security = position != null ? dbController.getById(StockSecurity.class, position.securityId()) : null;
		requireSecurity(security);
		return security;
	}

	private static void requireSecurity(StockSecurity security) {
		if (security == null || security.getId() <= 0) {
			throw new GBankingException("Es wurde kein gültiges Wertpapier ausgewählt");
		}
	}

	private static BigDecimal requirePositive(BigDecimal value, String message) {
		if (value == null || value.signum() <= 0) {
			throw new GBankingException(message);
		}
		return value;
	}

	private static void requireNonNegative(BigDecimal value, String message) {
		if (value != null && value.signum() < 0) {
			throw new GBankingException(message);
		}
	}

	private BankAccount requireSettlementAccount(PortfolioSummary portfolio) {
		return requireSettlementAccount(portfolio.settlementAccountId());
	}

	private BankAccount requireSettlementAccount(int accountId) {
		BankAccount account = dbController.getById(BankAccount.class, accountId);
		if (!isSettlementAccount(account)) {
			throw new GBankingException("Das Depot hat kein gültiges Verrechnungskonto");
		}
		return account;
	}

	private static Currency requireCurrency(BankAccount account) {
		if (account == null || account.getBaseCurrency() == null) {
			throw new GBankingException("Das Verrechnungskonto hat keine Währung");
		}
		return account.getBaseCurrency();
	}

	public record PortfolioSummary(int portfolioId, String displayName, String bankName, String accountNumber,
			LocalDate openedAt, int settlementAccountId, String settlementAccountName, String settlementBankName,
			String settlementIban, Currency settlementCurrency, BigDecimal settlementBalance) {
		@Override
		public String toString() {
			return displayName();
		}
	}

	public record PositionSummary(int portfolioId, int securityId, String securityName, String isin, String wkn,
			StockQuantityType quantityType, BigDecimal quantity, BigDecimal acquisitionPrice,
			BigDecimal acquisitionValue, BigDecimal unitPrice, BigDecimal totalValue, Currency currency,
			BigDecimal performancePercent) {
	}

	public record TransactionPrefill(int securityId, StockQuantityType quantityType,
			StockTransactionType transactionType, BigDecimal quantity) {
	}

	public record TransactionSummary(int transactionId, StockTransactionType transactionType, int securityId,
			String securityName, String isin, String wkn, LocalDate date, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal totalPrice, Currency currency, int editableFieldMask,
			boolean externalSource) {
		public boolean editable() {
			return editableFieldMask != 0;
		}
	}

	public record SecuritySummary(int securityId, String name, StockQuantityType quantityType, Currency currency) {
		@Override
		public String toString() {
			return name;
		}
	}

	public record TransactionEditData(int transactionId, int editableFieldMask,
			StockTransactionType transactionType, LocalDate tradeDate, LocalDate settlementDate, int securityId,
			BigDecimal quantity, BigDecimal unitPrice, Currency currency, BigDecimal exchangeRate,
			BigDecimal fees, BigDecimal taxes, BigDecimal accruedInterest, String note) {
		public boolean isEditable(StockTransactionEditField field) {
			return field.isSet(editableFieldMask);
		}
	}

	public record TransactionEditRequest(Integer transactionId, StockTransactionType transactionType,
			LocalDate tradeDate, LocalDate settlementDate, int securityId, BigDecimal quantity,
			BigDecimal unitPrice, Currency currency, BigDecimal exchangeRate, BigDecimal fees,
			BigDecimal taxes, BigDecimal accruedInterest, String note) {
		private TradeRequest toTradeRequest() {
			return new TradeRequest(transactionType, tradeDate, settlementDate, quantity, unitPrice, currency,
					exchangeRate, fees, taxes, accruedInterest);
		}
	}

	public record PriceSummary(int priceId, LocalDate date, BigDecimal price, Currency currency,
			StockPriceType priceType, String sourceName, StockDataSourceType sourceType) {
		public boolean requiresExplicitConfirmation() {
			return isProtectedPriceSource(sourceType);
		}
	}

	public record TradeRequest(StockTransactionType transactionType, LocalDate tradeDate, LocalDate settlementDate,
			BigDecimal quantity, BigDecimal unitPrice, Currency currency, BigDecimal exchangeRate,
			BigDecimal fees, BigDecimal taxes, BigDecimal accruedInterest) {
	}

	public record IncomeRequest(StockTransactionType transactionType, LocalDate date, BigDecimal grossAmount,
			BigDecimal taxes, Currency currency, BigDecimal exchangeRate) {
	}

	private record SecurityIdentifiers(String isin, String wkn) {

		private static final SecurityIdentifiers EMPTY = new SecurityIdentifiers(null, null);
	}

	private record CashComponent(StockCashLegRole role, BigDecimal amount, Integer exchangeRateId) {
	}

	private record PositionKey(int securityId, StockQuantityType quantityType) {
	}

	private record AcquisitionPriceValue(BigDecimal price, Currency currency) {

		private BigDecimal priceIn(Currency expectedCurrency) {
			return currency == expectedCurrency ? price : null;
		}
	}

	private static final class AcquisitionPriceState {
		private BigDecimal quantity = BigDecimal.ZERO;
		private BigDecimal averagePrice;
		private Currency currency;

		private void apply(BigDecimal quantityChange, BigDecimal price, Currency priceCurrency) {
			BigDecimal newQuantity = quantity.add(quantityChange);
			if (quantityChange.signum() > 0 && price != null) {
				if (quantity.signum() == 0) {
					averagePrice = price;
					currency = priceCurrency;
				} else if (averagePrice != null) {
					if (currency == priceCurrency) {
						averagePrice = averagePrice.multiply(quantity).add(price.multiply(quantityChange))
								.divide(newQuantity, PRICE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
					} else {
						averagePrice = null;
						currency = null;
					}
				}
			}
			quantity = newQuantity;
			if (quantity.signum() <= 0) {
				averagePrice = null;
				currency = null;
			}
		}

		private BigDecimal priceIn(Currency expectedCurrency) {
			return currency == expectedCurrency ? averagePrice : null;
		}
	}
}
