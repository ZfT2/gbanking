package de.zft2.gbanking.service.stock;

import static de.zft2.gbanking.service.stock.StockNumericValues.fromScaled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionMemberRole;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionOperation;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockCorporateAction;
import de.zft2.gbanking.db.dao.stock.StockCorporateActionMember;
import de.zft2.gbanking.db.dao.stock.StockCorporateActionTransaction;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.stock.StockPortfolioService.AcquisitionPriceState;
import de.zft2.gbanking.service.stock.StockPortfolioService.CashComponent;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionKey;

public class StockCorporateActionService extends AbstractDbService {
	private static final BigDecimal ZERO = BigDecimal.ZERO;
	private final StockPortfolioService portfolios = new StockPortfolioService();

	public List<StockCorporateAction> getActions() {
		return dbController.getAll(StockCorporateAction.class);
	}

	public StockCorporateAction createAction(StockCorporateAction action) {
		require(action != null && action.getId() == 0 && action.getActionType() != null
				&& action.getName() != null && !action.getName().isBlank() && action.getEffectiveDate() != null,
				"Bezeichnung, Art und Wirksamkeitsdatum fehlen");
		StockSecurity underlying = security(action.getUnderlyingSecurityId());
		security(action.getTargetSecurityId());
		action.setCurrency(underlying.getDefaultQuoteCurrency());
		for (int id : List.of(action.getTargetSecurityId(),
				action.getRightsSecurityId() != null ? action.getRightsSecurityId() : action.getUnderlyingSecurityId())) {
			require(security(id).getDefaultQuoteCurrency() == action.getCurrency(), "Die Kurswährungen müssen übereinstimmen");
		}
		require(action.getAllocationFromE9() > 0 && action.getAllocationToE9() > 0
				&& action.getRatioFromE9() > 0 && action.getRatioToE9() > 0, "Verhältnisse müssen größer als null sein");
		require(action.getEndDate() == null || !action.getEndDate().isBefore(action.getEffectiveDate()), "Ungültige Bezugsfrist");
		if (action.getActionType() == StockCorporateActionType.RIGHTS_ISSUE) {
			require(action.getRightsSecurityId() != null && action.getRightsSecurityId() != action.getUnderlyingSecurityId()
					&& action.getRightsSecurityId() != action.getTargetSecurityId()
					&& action.getSubscriptionPriceE8() != null && action.getSubscriptionPriceE8() >= 0,
					"Bezugsrecht und Bezugspreis müssen angegeben werden");
		}
		if (action.getActionType() == StockCorporateActionType.NOMINAL_REDUCTION) {
			require(action.getOldNominalE8() != null && action.getNewNominalE8() != null
					&& action.getOldNominalE8() > action.getNewNominalE8() && action.getNewNominalE8() >= 0,
					"Alter und neuer Nennwert müssen eine Herabsetzung beschreiben");
		}
		return dbController.insertOrUpdate(action);
	}

	public void record(PortfolioSummary portfolio, OperationRequest request) {
		dbController.executeInTransaction(() -> {
			StockCorporateAction action = action(request.actionId());
			validateRequest(action, request);
			BankAccount account = dbController.getById(BankAccount.class, portfolio.settlementAccountId());
			require(account != null && (!request.operation().isCashOperation() || account.getBaseCurrency() == action.getCurrency()),
					"Für diese Kapitalmaßnahme ist ein Verrechnungskonto in der Maßnahmenwährung erforderlich");
			StockTransaction transaction = portfolios.createPendingTransaction(portfolio.portfolioId(),
					StockTransactionType.CORPORATE_ACTION, request.date(), request.date());
			List<StockTransactionSecurityLeg> legs = requestedLegs(transaction.getId(), action, request);
			for (StockTransactionSecurityLeg leg : legs) {
				dbController.insertOrUpdate(leg);
			}
			BigDecimal gross = gross(action, request);
			if (request.operation().isCashOperation()) {
				List<CashComponent> components = List.of(
						new CashComponent(StockCashLegRole.TRADE_VALUE, gross, null),
						new CashComponent(StockCashLegRole.FEE, value(request.fees()).negate(), null),
						new CashComponent(StockCashLegRole.TAX, value(request.taxes()).negate(), null));
				if (request.bookingId() == null) {
					portfolios.persistCashComponents(transaction, account, request.date(), components, action.getName());
				} else {
					validateBooking(portfolio, request.bookingId(), gross.subtract(value(request.fees())).subtract(value(request.taxes())));
					int number = 1;
					for (CashComponent component : components) {
						if (component.amount().signum() == 0) { continue; }
						StockTransactionCashLeg cash = new StockTransactionCashLeg();
						cash.setTransactionId(transaction.getId());
						cash.setLegNumber(number++);
						cash.setAccountId(account.getId());
						cash.setBookingId(request.bookingId());
						cash.setLegRole(component.role());
						cash.setAmountMinor(StockNumericValues.toMinorUnits(component.amount(), account.getBaseCurrency()));
						cash.setCurrency(account.getBaseCurrency());
						cash.setValueAt(request.date().atStartOfDay());
						dbController.insertOrUpdate(cash);
					}
				}
			} else {
				require(request.bookingId() == null, "Dieser Vorgang hat keine Geldbewegung");
			}
			transaction.setCashValueAt(request.operation().isCashOperation() ? request.date().atStartOfDay() : null);
			transaction.setSettledAt(request.date().atTime(LocalTime.MAX));
			transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
			dbController.insertOrUpdate(transaction);
			associate(portfolio, action, request, transaction.getId(), null, request.bookingId());
			for (StockTransactionSecurityLeg leg : legs) {
				portfolios.rebalanceReconciliationAdjustment(portfolio.portfolioId(),
						new PositionKey(leg.getSecurityId(), StockQuantityType.UNITS));
			}
			return null;
		});
	}

	public void link(PortfolioSummary portfolio, OperationRequest request, int transactionId, Integer relatedTransactionId) {
		dbController.executeInTransaction(() -> {
			StockCorporateAction action = action(request.actionId());
			validateRequest(action, request);
			StockTransaction primary = transaction(transactionId, portfolio.portfolioId());
			List<StockTransactionSecurityLeg> actual = new ArrayList<>(dbController.getAllByParent(StockTransactionSecurityLeg.class, transactionId));
			if (relatedTransactionId != null) {
				StockTransaction related = transaction(relatedTransactionId, portfolio.portfolioId());
				require(primary.getSettledAt().toLocalDate().equals(related.getSettledAt().toLocalDate()), "Die Wirksamkeitstage müssen übereinstimmen");
				actual.addAll(dbController.getAllByParent(StockTransactionSecurityLeg.class, relatedTransactionId));
			}
			require(primary.getImportRecordId() != null && primary.getSettledAt().toLocalDate().equals(request.date()),
					"Bitte unveränderte importierte Transaktionen mit passendem Datum auswählen");
			require(relatedTransactionId == null || transaction(relatedTransactionId, portfolio.portfolioId()).getImportRecordId() != null,
					"Die Gegenbuchung muss ebenfalls importiert sein");
			List<StockTransactionSecurityLeg> expected = requestedLegs(0, action, request);
			require(actual.stream().allMatch(leg -> leg.getQuantityType() == StockQuantityType.UNITS),
					"Die Originalbewegungen müssen Stückzahlen enthalten");
			require(quantities(actual).equals(quantities(expected)), "Die Originalbewegungen passen nicht zu Art, Wertpapieren oder Mengen");
			associate(portfolio, action, request, transactionId, relatedTransactionId, request.bookingId());
			return null;
		});
	}

	private void associate(PortfolioSummary portfolio, StockCorporateAction action, OperationRequest request,
			int transactionId, Integer relatedId, Integer bookingId) {
		if (bookingId != null) {
			Set<Integer> allowed = new HashSet<>(List.of(transactionId));
			if (relatedId != null) { allowed.add(relatedId); }
			validateBooking(portfolio, bookingId, gross(action, request).subtract(value(request.fees())).subtract(value(request.taxes())), allowed);
		}
		StockCorporateActionTransaction association = new StockCorporateActionTransaction();
		association.setActionId(action.getId());
		association.setBookingId(bookingId);
		association.setOperation(request.operation());
		association.setBasisAmountE8(request.basisAmount() != null ? scaled(request.basisAmount(), 8) : null);
		association.setReferenceQuantityE9(request.operation() == StockCorporateActionOperation.RIGHTS_ALLOCATION
				? scaled(request.quantity(), 9) : 0);
		if (bookingId != null) {
			association.setExternalGrossMinor(StockNumericValues.toMinorUnits(gross(action, request), action.getCurrency()));
			association.setExternalFeesMinor(StockNumericValues.toMinorUnits(value(request.fees()), action.getCurrency()));
			association.setExternalTaxesMinor(StockNumericValues.toMinorUnits(value(request.taxes()), action.getCurrency()));
		}
		dbController.insertOrUpdate(association);
		if (relatedId != null) { addMember(association.getId(), relatedId, StockCorporateActionMemberRole.RELATED); }
		addMember(association.getId(), transactionId, StockCorporateActionMemberRole.PRIMARY);
		List<ActionResult> results = getResults(portfolio.portfolioId());
		ActionResult result = results.stream().filter(row -> row.associationId() == association.getId()).findFirst().orElseThrow();
		require(result.gross().compareTo(gross(action, request)) == 0
				&& result.fees().compareTo(value(request.fees())) == 0 && result.taxes().compareTo(value(request.taxes())) == 0,
				"Geldbewegung, Gebühren oder Steuern stimmen nicht mit dem Vorgang überein");
	}

	private void addMember(int associationId, int transactionId, StockCorporateActionMemberRole role) {
		StockCorporateActionMember member = new StockCorporateActionMember();
		member.setAssociationId(associationId);
		member.setTransactionId(transactionId);
		member.setRole(role);
		dbController.insertOrUpdate(member);
	}

	public void unlink(int associationId) {
		dbController.executeInTransaction(() -> {
			StockCorporateActionTransaction association = dbController.getById(StockCorporateActionTransaction.class, associationId);
			require(association != null, "Zuordnung nicht gefunden");
			int transactionId = dbController.getAllByParent(StockCorporateActionMember.class, associationId).stream()
					.filter(member -> member.getRole() == StockCorporateActionMemberRole.PRIMARY)
					.findFirst().orElseThrow(() -> new GBankingException("Die Haupttransaktion der Kapitalmaßnahme fehlt")).getTransactionId();
			StockTransaction transaction = dbController.getById(StockTransaction.class, transactionId);
			boolean imported = transaction.getImportRecordId() != null;
			dbController.delete(association, StatementType.DELETE);
			if (!imported) {
				List<StockTransactionSecurityLeg> legs = dbController.getAllByParent(StockTransactionSecurityLeg.class, transaction.getId());
				if (association.getBookingId() == null) { portfolios.removeManualSettlementBookings(transaction); }
				transaction.setTransactionStatus(StockTransactionStatus.CANCELLED);
				transaction.setSettledAt(null);
				dbController.insertOrUpdate(transaction);
				if (association.getBookingId() != null) {
					for (StockTransactionCashLeg cash : dbController.getAllByParent(StockTransactionCashLeg.class, transaction.getId())) {
						cash.setBookingId(null);
						dbController.insertOrUpdate(cash);
					}
				}
				for (StockTransactionSecurityLeg leg : legs) {
					portfolios.rebalanceReconciliationAdjustment(transaction.getPortfolioId(),
							new PositionKey(leg.getSecurityId(), StockQuantityType.UNITS));
				}
			}
			portfolios.requireNonNegativeExchangePositions(transaction.getPortfolioId(), securityIds(action(association.getActionId())));
			return null;
		});
	}

	public List<ActionResult> getResults(int portfolioId) {
		List<ActionResult> results = new ArrayList<>();
		portfolios.acquisitionPricesByPosition(portfolioId, results);
		return results;
	}

	Map<Integer, Event> events(List<StockTransaction> transactions) {
		Map<Integer, StockTransaction> byId = transactions.stream().collect(Collectors.toMap(StockTransaction::getId, t -> t));
		Map<Integer, StockCorporateAction> actions = getActions().stream().collect(Collectors.toMap(StockCorporateAction::getId, a -> a));
		Map<Integer, List<StockCorporateActionMember>> members = dbController.getAll(StockCorporateActionMember.class).stream()
				.collect(Collectors.groupingBy(member -> member.getAssociationId()));
		Map<Integer, Event> result = new HashMap<>();
		Map<String, BigDecimal> allocated = new HashMap<>();
		for (StockCorporateActionTransaction association : dbController.getAll(StockCorporateActionTransaction.class)) {
			List<StockTransaction> entries = members.get(association.getId()).stream()
					.map(member -> byId.get(member.getTransactionId())).toList();
			if (entries.stream().allMatch(entry -> entry == null)) { continue; }
			require(entries.stream().noneMatch(entry -> entry == null), "Eine zugeordnete Transaktion fehlt");
			StockTransaction trigger = entries.stream().min(Comparator.comparing(StockTransaction::getSettledAt)
					.thenComparingInt(StockTransaction::getId)).orElseThrow();
			List<StockTransactionSecurityLeg> legs = entries.stream()
					.flatMap(t -> dbController.getAllByParent(StockTransactionSecurityLeg.class, t.getId()).stream()).toList();
			List<StockTransactionCashLeg> cash = entries.stream()
					.flatMap(t -> dbController.getAllByParent(StockTransactionCashLeg.class, t.getId()).stream()).toList();
			Event event = new Event(actions.get(association.getActionId()), association, trigger, legs, cash);
			if (association.getOperation() == StockCorporateActionOperation.RIGHTS_ALLOCATION) {
				BigDecimal amount = fromScaled(association.getReferenceQuantityE9(), 9);
				allocated.merge(trigger.getPortfolioId() + ":" + association.getActionId(), amount, BigDecimal::add);
			}
			entries.forEach(t -> result.put(t.getId(), event));
		}
		result.replaceAll((id, event) -> new Event(event.action(), event.association(), event.trigger(), event.legs(), event.cash(),
				allocated.getOrDefault(event.trigger().getPortfolioId() + ":" + event.action().getId(), ZERO)));
		return result;
	}

	ActionResult apply(Event event, Map<PositionKey, AcquisitionPriceState> states) {
		StockCorporateAction action = event.action();
		StockCorporateActionOperation operation = event.association().getOperation();
		int sourceId = sourceId(action, operation);
		int targetId = targetId(action, operation);
		AcquisitionPriceState source = states.computeIfAbsent(new PositionKey(sourceId, StockQuantityType.UNITS), key -> new AcquisitionPriceState());
		AcquisitionPriceState target = states.computeIfAbsent(new PositionKey(targetId, StockQuantityType.UNITS), key -> new AcquisitionPriceState());
		BigDecimal outgoing = event.legs().stream().filter(leg -> leg.getLegRole() != StockSecurityLegRole.REFERENCE && leg.getQuantityE9() < 0)
				.map(leg -> fromScaled(leg.getQuantityE9(), 9).negate()).reduce(ZERO, BigDecimal::add);
		BigDecimal incoming = event.legs().stream().filter(leg -> leg.getLegRole() != StockSecurityLegRole.REFERENCE && leg.getQuantityE9() > 0)
				.map(leg -> fromScaled(leg.getQuantityE9(), 9)).reduce(ZERO, BigDecimal::add);
		CashAmounts cash = cashAmounts(event);
		BigDecimal gross = cash.gross(), fees = cash.fees(), taxes = cash.taxes();
		BigDecimal basis = event.association().getBasisAmountE8() != null ? fromScaled(event.association().getBasisAmountE8(), 8) : null;
		BigDecimal disposed = null, profit = null;
		require(source.quantity.compareTo(outgoing) >= 0, "Nicht genügend Stücke am Wirksamkeitstag");
		switch (operation) {
		case RIGHTS_ALLOCATION -> {
			disposed = basis;
			BigDecimal eligible = fromScaled(event.association().getReferenceQuantityE9(), 9);
			require(source.quantity.compareTo(eligible) >= 0 && source.quantity.compareTo(event.allocated()) >= 0,
					"Zuteilung übersteigt den zugrunde liegenden Aktienbestand oder ist doppelt erfasst");
			adjustBasis(source, basis == null ? null : basis.negate(), action.getCurrency());
			credit(target, incoming, basis, action.getCurrency());
		}
		case RIGHTS_BUY -> credit(target, incoming, gross.negate().add(fees).add(taxes), action.getCurrency());
		case SUBSCRIPTION, CONSOLIDATION -> {
			if (operation == StockCorporateActionOperation.CONSOLIDATION) {
				require(source.quantity.compareTo(outgoing) == 0, "Die Zusammenlegung muss den gesamten Ausgangsbestand umfassen");
			}
			disposed = cost(source, outgoing, action.getCurrency());
			source.apply(outgoing.negate(), null, null);
			BigDecimal total = disposed == null ? null : disposed.subtract(gross).add(fees).add(taxes);
			credit(target, incoming, total, action.getCurrency());
		}
		case RIGHTS_SALE, FRACTIONAL_SALE, RIGHTS_EXPIRY -> {
			disposed = cost(source, outgoing, action.getCurrency());
			source.apply(outgoing.negate(), null, null);
			profit = disposed == null ? null : gross.subtract(fees).subtract(taxes).subtract(disposed);
		}
		case CANCELLATION, CAPITAL_RETURN -> {
			require(source.quantity.signum() > 0, "Einziehung oder Kapitalrückzahlung benötigt einen Aktienbestand");
			disposed = basis;
			BigDecimal total = cost(source, source.quantity, action.getCurrency());
			BigDecimal remaining = source.quantity.subtract(outgoing);
			if (remaining.signum() == 0 && total != null && basis != null) {
				require(total.setScale(8, RoundingMode.HALF_UP).compareTo(basis) == 0,
						"Bei vollständiger Einziehung muss die gesamte Anschaffungsbasis berücksichtigt werden");
				disposed = total;
			}
			require(total == null || disposed == null || disposed.compareTo(total) <= 0, "Ausgebuchte Basis übersteigt den Anschaffungswert");
			source.quantity = remaining;
			setBasis(source, total == null || disposed == null ? null : total.subtract(disposed), action.getCurrency());
			profit = disposed == null ? null : gross.subtract(fees).subtract(taxes).subtract(disposed);
		}
		case NOMINAL_REDUCTION -> { /* The immutable definition retains both dated nominal values. */ }
		}
		return new ActionResult(event.association().getId(), action.getId(), action.getUnderlyingSecurityId(),
				action.getName(), operation, event.trigger().getSettledAt().toLocalDate(), outgoing, incoming,
				gross, fees, taxes, gross.subtract(fees).subtract(taxes), disposed, profit, action.getCurrency());
	}

	private CashAmounts cashAmounts(Event event) {
		Currency currency = event.action().getCurrency();
		BigDecimal gross = ZERO, fees = ZERO, taxes = ZERO;
		require(event.association().getOperation().isCashOperation() || event.cash().isEmpty(), "Dieser Vorgang darf keine Geldbewegung enthalten");
		for (StockTransactionCashLeg leg : event.cash()) {
			require(leg.getCurrency() == currency, "Fremdwährung erfordert eine explizite Umrechnung vor der Zuordnung");
			BigDecimal amount = fromScaled(leg.getAmountMinor(), currency.getMinorUnitDigits());
			switch (leg.getLegRole()) {
			case FEE -> fees = fees.subtract(amount);
			case TAX -> taxes = taxes.subtract(amount);
			default -> gross = gross.add(amount);
			}
		}
		if (event.association().getBookingId() != null) {
			Booking booking = dbController.getById(Booking.class, event.association().getBookingId());
			require(booking != null, "Zugeordneter Kontoumsatz fehlt");
			if (event.cash().isEmpty()) {
				gross = fromScaled(event.association().getExternalGrossMinor(), currency.getMinorUnitDigits());
				fees = fromScaled(event.association().getExternalFeesMinor(), currency.getMinorUnitDigits());
				taxes = fromScaled(event.association().getExternalTaxesMinor(), currency.getMinorUnitDigits());
			} else {
				require(event.cash().stream().allMatch(leg -> event.association().getBookingId().equals(leg.getBookingId())),
						"Die Geldbewegung ist bereits mit einem anderen Kontoumsatz verbunden");
			}
			require(booking.getAmount().compareTo(gross.subtract(fees).subtract(taxes)) == 0, "Der zugeordnete Kontoumsatz wurde verändert");
		}
		return new CashAmounts(gross, fees, taxes);
	}

	private static BigDecimal cost(AcquisitionPriceState state, BigDecimal quantity, Currency currency) {
		if (quantity.signum() == 0) { return ZERO; }
		if (state.exactBasis != null && state.currency == currency) {
			return state.exactBasis.multiply(quantity).divide(state.quantity, 16, RoundingMode.HALF_UP);
		}
		BigDecimal price = state.priceIn(currency);
		return price != null ? price.multiply(quantity) : null;
	}

	private static void adjustBasis(AcquisitionPriceState state, BigDecimal change, Currency currency) {
		BigDecimal previous = cost(state, state.quantity, currency);
		setBasis(state, previous == null || change == null ? null : previous.add(change), currency);
	}

	private static void setBasis(AcquisitionPriceState state, BigDecimal total, Currency currency) {
		require(total == null || total.signum() >= 0, "Anschaffungsbasis darf nicht negativ werden");
		state.economicBasis = true;
		state.exactBasis = total;
		state.averagePrice = total != null && state.quantity.signum() > 0
				? total.divide(state.quantity, 8, RoundingMode.HALF_UP) : null;
		state.currency = state.averagePrice != null ? currency : null;
	}

	static void credit(AcquisitionPriceState state, BigDecimal quantity, BigDecimal basis, Currency currency) {
		BigDecimal previous = cost(state, state.quantity, currency);
		state.apply(quantity, basis != null ? basis.divide(quantity, 8, RoundingMode.HALF_UP) : null, currency);
		setBasis(state, previous != null && basis != null ? previous.add(basis) : null, currency);
	}

	private List<StockTransactionSecurityLeg> requestedLegs(int transactionId, StockCorporateAction action, OperationRequest request) {
		int source = sourceId(action, request.operation()), target = targetId(action, request.operation());
		BigDecimal quantity = value(request.quantity()), incoming = ZERO, outgoing = ZERO, reference = ZERO;
		switch (request.operation()) {
		case RIGHTS_ALLOCATION -> { reference = quantity; incoming = ratio(quantity, action.getAllocationFromE9(), action.getAllocationToE9()); }
		case RIGHTS_BUY -> incoming = quantity;
		case SUBSCRIPTION, CONSOLIDATION -> { outgoing = quantity; incoming = ratio(quantity, action.getRatioFromE9(), action.getRatioToE9()); }
		case RIGHTS_SALE, RIGHTS_EXPIRY, CANCELLATION, FRACTIONAL_SALE -> outgoing = quantity;
		case CAPITAL_RETURN, NOMINAL_REDUCTION -> reference = ZERO;
		}
		List<StockTransactionSecurityLeg> legs = new ArrayList<>();
		if (outgoing.signum() > 0) { legs.add(leg(transactionId, source, outgoing.negate(), false)); }
		if (incoming.signum() > 0) { legs.add(leg(transactionId, target, incoming, false)); }
		if (legs.isEmpty() || request.operation() == StockCorporateActionOperation.RIGHTS_ALLOCATION) {
			legs.add(leg(transactionId, source, reference, true));
		}
		for (int i = 0; i < legs.size(); i++) { legs.get(i).setLegNumber(i + 1); }
		return legs;
	}

	private StockTransactionSecurityLeg leg(int transactionId, int securityId, BigDecimal quantity, boolean reference) {
		StockTransactionSecurityLeg leg = portfolios.createPositionLeg(transactionId, security(securityId), scaled(quantity, 9), null, null);
		leg.setLegRole(reference ? StockSecurityLegRole.REFERENCE : StockSecurityLegRole.POSITION);
		return leg;
	}

	private static Map<Movement, Long> quantities(List<StockTransactionSecurityLeg> legs) {
		Map<Movement, Long> result = new HashMap<>();
		legs.stream().filter(leg -> leg.getLegRole() != StockSecurityLegRole.REFERENCE)
				.forEach(leg -> result.merge(new Movement(leg.getSecurityId(), leg.getQuantityE9() < 0), leg.getQuantityE9(), Math::addExact));
		return result;
	}

	public static BigDecimal ratio(BigDecimal quantity, long from, long to) {
		try {
			return quantity.multiply(BigDecimal.valueOf(to)).divide(BigDecimal.valueOf(from), 9, RoundingMode.UNNECESSARY).stripTrailingZeros();
		} catch (ArithmeticException exception) {
			throw new GBankingException("Das Verhältnis ergibt keine exakt darstellbare Stückzahl; tatsächliche Teilstücke prüfen", exception);
		}
	}

	private void validateRequest(StockCorporateAction action, OperationRequest request) {
		require(request.operation() != null && request.date() != null && !request.date().isBefore(action.getEffectiveDate()),
				"Art und gültiges Datum des Vorgangs fehlen");
		require(value(request.quantity()).signum() > 0 || request.operation() == StockCorporateActionOperation.CAPITAL_RETURN
				|| request.operation() == StockCorporateActionOperation.NOMINAL_REDUCTION, "Die Stückzahl muss positiv sein");
		require(value(request.fees()).signum() >= 0 && value(request.taxes()).signum() >= 0
				&& (request.basisAmount() == null || request.basisAmount().signum() >= 0), "Kosten und Basis müssen nichtnegativ sein");
		require(request.operation().isPermitted(action.getActionType()), "Der Vorgang passt nicht zur Kapitalmaßnahme");
		require(request.bookingId() == null || request.operation().isCashOperation(), "Dieser Vorgang hat keine Geldbewegung");
		if (request.operation() == StockCorporateActionOperation.SUBSCRIPTION) {
			require(action.getEndDate() == null || !request.date().isAfter(action.getEndDate()), "Die Bezugsfrist ist abgelaufen");
		}
		if (request.operation() == StockCorporateActionOperation.RIGHTS_EXPIRY) {
			require(action.getEndDate() == null || !request.date().isBefore(action.getEndDate()), "Bezugsrechte sind noch nicht verfallen");
		}
		if (request.operation() == StockCorporateActionOperation.RIGHTS_ALLOCATION) {
			require(request.date().equals(action.getEffectiveDate()), "Die Zuteilung muss am Wirksamkeitstag liegen");
		}
		require(request.fees() == null || request.fees().stripTrailingZeros().scale() <= action.getCurrency().getMinorUnitDigits(),
				"Gebühren überschreiten die Währungsgenauigkeit");
		require(request.taxes() == null || request.taxes().stripTrailingZeros().scale() <= action.getCurrency().getMinorUnitDigits(),
				"Steuern überschreiten die Währungsgenauigkeit");
		if (request.operation().isCashOperation() && request.operation() != StockCorporateActionOperation.SUBSCRIPTION) {
			require(request.priceOrAmount() != null && request.priceOrAmount().signum() >= 0, "Preis oder Auszahlungsbetrag fehlt");
		}
		if (!request.operation().isCashOperation()) {
			require(value(request.fees()).signum() == 0 && value(request.taxes()).signum() == 0, "Dieser Vorgang hat keine Geldbewegung");
		}
	}

	private static BigDecimal gross(StockCorporateAction action, OperationRequest request) {
		BigDecimal quantity = value(request.quantity()), price = value(request.priceOrAmount());
		BigDecimal amount = switch (request.operation()) {
		case RIGHTS_BUY -> quantity.multiply(price).negate();
		case RIGHTS_SALE, FRACTIONAL_SALE -> quantity.multiply(price);
		case SUBSCRIPTION -> ratio(quantity, action.getRatioFromE9(), action.getRatioToE9())
				.multiply(fromScaled(action.getSubscriptionPriceE8(), 8)).negate();
		case CANCELLATION, CAPITAL_RETURN -> price;
		default -> ZERO;
		};
		return amount.setScale(action.getCurrency().getMinorUnitDigits(), RoundingMode.HALF_UP);
	}

	private static int sourceId(StockCorporateAction action, StockCorporateActionOperation operation) {
		return switch (operation) {
		case RIGHTS_BUY, RIGHTS_SALE, RIGHTS_EXPIRY, SUBSCRIPTION -> action.getRightsSecurityId();
		case FRACTIONAL_SALE -> action.getTargetSecurityId();
		default -> action.getUnderlyingSecurityId();
		};
	}

	private static int targetId(StockCorporateAction action, StockCorporateActionOperation operation) {
		return operation == StockCorporateActionOperation.RIGHTS_ALLOCATION || operation == StockCorporateActionOperation.RIGHTS_BUY
				? action.getRightsSecurityId() : action.getTargetSecurityId();
	}

	private StockCorporateAction action(int id) {
		StockCorporateAction action = dbController.getById(StockCorporateAction.class, id);
		require(action != null, "Kapitalmaßnahme nicht gefunden");
		return action;
	}

	private static Set<Integer> securityIds(StockCorporateAction action) {
		Set<Integer> ids = new HashSet<>(List.of(action.getUnderlyingSecurityId(), action.getTargetSecurityId()));
		if (action.getRightsSecurityId() != null) { ids.add(action.getRightsSecurityId()); }
		return ids;
	}

	private StockSecurity security(int id) {
		StockSecurity security = dbController.getById(StockSecurity.class, id);
		require(security != null && security.getDefaultQuantityType() == StockQuantityType.UNITS
				&& security.getDefaultQuotationType() == StockQuotationType.ABSOLUTE && security.getDefaultQuoteCurrency() != null,
				"Bitte stücknotierte Wertpapiere mit Kurswährung auswählen");
		return security;
	}

	private StockTransaction transaction(int id, int portfolioId) {
		StockTransaction transaction = dbController.getById(StockTransaction.class, id);
		require(transaction != null && transaction.getPortfolioId() == portfolioId
				&& transaction.getTransactionStatus() == StockTransactionStatus.SETTLED, "Ungültige Depottransaktion");
		return transaction;
	}

	private void validateBooking(PortfolioSummary portfolio, int id, BigDecimal amount) {
		validateBooking(portfolio, id, amount, Set.of());
	}

	private void validateBooking(PortfolioSummary portfolio, int id, BigDecimal amount, Set<Integer> allowedTransactions) {
		Booking booking = dbController.getById(Booking.class, id);
		require(booking != null && booking.getAccountId() == portfolio.settlementAccountId()
				&& booking.getAmount().compareTo(amount) == 0
				&& dbController.getAll(StockTransactionCashLeg.class).stream().noneMatch(leg ->
						Integer.valueOf(id).equals(leg.getBookingId()) && !allowedTransactions.contains(leg.getTransactionId()))
				&& dbController.getAll(StockCorporateActionTransaction.class).stream().noneMatch(a -> Integer.valueOf(id).equals(a.getBookingId())),
				"Kontoumsatz passt nicht oder ist bereits zugeordnet");
	}

	static void require(boolean valid, String message) {
		if (!valid) { throw new GBankingException(message); }
	}

	private static BigDecimal value(BigDecimal value) { return value != null ? value : ZERO; }
	public static long scaled(BigDecimal value, int scale) {
		try { return StockNumericValues.toScaledLong(value, scale, RoundingMode.UNNECESSARY); }
		catch (ArithmeticException exception) { throw new GBankingException("Zahl überschreitet die unterstützte Genauigkeit oder Größe", exception); }
	}

	public record OperationRequest(int actionId, StockCorporateActionOperation operation, LocalDate date,
			BigDecimal quantity, BigDecimal priceOrAmount, BigDecimal fees, BigDecimal taxes,
			BigDecimal basisAmount, Integer bookingId) { }
	private record Movement(int securityId, boolean outgoing) { }
	private record CashAmounts(BigDecimal gross, BigDecimal fees, BigDecimal taxes) { }
	record Event(StockCorporateAction action, StockCorporateActionTransaction association, StockTransaction trigger,
			List<StockTransactionSecurityLeg> legs, List<StockTransactionCashLeg> cash, BigDecimal allocated) {
		Event(StockCorporateAction action, StockCorporateActionTransaction association, StockTransaction trigger,
				List<StockTransactionSecurityLeg> legs, List<StockTransactionCashLeg> cash) {
			this(action, association, trigger, legs, cash, ZERO);
		}
		Set<Integer> securityIds() {
			return StockCorporateActionService.securityIds(action);
		}
	}
	public record ActionResult(int associationId, int actionId, int underlyingSecurityId, String actionName,
			StockCorporateActionOperation operation, LocalDate date, BigDecimal outgoing, BigDecimal incoming,
			BigDecimal gross, BigDecimal fees, BigDecimal taxes, BigDecimal netCash, BigDecimal disposedBasis,
			BigDecimal realizedResult, Currency currency) { }
}
