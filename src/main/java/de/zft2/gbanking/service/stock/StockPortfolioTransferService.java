package de.zft2.gbanking.service.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockCorporateActionMember;
import de.zft2.gbanking.db.dao.stock.StockSecurityExchangeLink;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionKey;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;

public class StockPortfolioTransferService extends AbstractDbService {

	private final StockPortfolioService portfolios = new StockPortfolioService();

	public record TransferPlan(int sourceId, int targetId, List<Integer> outgoingIds, List<Integer> incomingIds,
			List<LocalDate> dates, List<PositionSummary> positions) {
		public TransferPlan {
			outgoingIds = List.copyOf(outgoingIds);
			incomingIds = List.copyOf(incomingIds);
			dates = List.copyOf(dates);
			positions = List.copyOf(positions);
		}
	}

	public TransferPlan findExistingDeliveries(PortfolioSummary source, PortfolioSummary target, LocalDate preferredDate) {
		List<TransferPlan> plans = transferPlans(source, target);
		List<TransferPlan> preferred = preferredDate == null ? List.of()
				: plans.stream().filter(plan -> plan.dates().contains(preferredDate)).toList();
		List<TransferPlan> candidates = preferred.isEmpty() ? plans : preferred;
		if (candidates.size() > 1) {
			throw new GBankingException("Mehrere vollständige Depotüberträge gefunden. Bitte ein eindeutiges Transaktionsdatum angeben.");
		}
		return candidates.isEmpty() ? null : candidates.get(0);
	}

	public void transferManually(PortfolioSummary source, PortfolioSummary target, LocalDate date, BigDecimal exchangeRate) {
		if (findExistingDeliveries(source, target, date) != null) {
			throw new GBankingException("Inzwischen wurden passende Lieferungen gefunden. Bitte den Depotübertrag erneut prüfen.");
		}
		portfolios.transferPortfolio(source, target, date, exchangeRate);
	}

	public void transferExistingDeliveries(PortfolioSummary source, PortfolioSummary target, TransferPlan plan,
			LocalDate overrideDate, BigDecimal exchangeRate) {
		dbController.executeInTransaction(() -> {
			if (plan == null || !transferPlans(source, target).contains(plan)) {
				throw new GBankingException("Die Lieferungen oder Bestände haben sich geändert. Bitte den Depotübertrag erneut prüfen.");
			}
			LocalDate date = overrideDate != null ? overrideDate : plan.dates().get(0);
			convertDeliveries(plan.outgoingIds(), StockTransactionType.TRANSFER_OUT, overrideDate);
			convertDeliveries(plan.incomingIds(), StockTransactionType.TRANSFER_IN, overrideDate);
			for (PositionSummary position : plan.positions()) {
				if (plan.outgoingIds().isEmpty()) {
					portfolios.persistTransferSide(source, position, position.quantity(), date, true);
				}
				if (plan.incomingIds().isEmpty()) {
					portfolios.persistTransferSide(target, position, position.quantity(), date, false);
				}
			}
			requireNonNegativeHistory(source.portfolioId(), plan.positions());
			requireNonNegativeHistory(target.portfolioId(), plan.positions());
			portfolios.transferSettlementBalance(portfolios.requireSettlementAccount(source),
					portfolios.requireSettlementAccount(target), date, exchangeRate);
			return null;
		});
	}

	private List<TransferPlan> transferPlans(PortfolioSummary source, PortfolioSummary target) {
		if (source == null || target == null || source.portfolioId() == target.portfolioId()) {
			throw new GBankingException("Bitte verschiedene Quell- und Zieldepots auswählen.");
		}
		List<DeliveryDay> outgoing = deliveryDays(source.portfolioId(), StockTransactionType.DELIVERY_OUT);
		List<DeliveryDay> incoming = deliveryDays(target.portfolioId(), StockTransactionType.DELIVERY_IN);
		List<TransferPlan> plans = new ArrayList<>();
		for (int index = 0; index < outgoing.size(); index++) {
			DeliveryDay day = outgoing.get(index);
			List<PositionSummary> positions = portfolios.positionsBeforeTransfer(source, day.transactions().get(0));
			DeliveryDay complete = completeDelivery(outgoing, index, positionQuantities(positions));
			if (complete != null && clearsSourcePortfolio(source, complete)) {
				addPlans(plans, source, target, complete, incoming, positions);
			}
		}
		if (plans.isEmpty()) {
			addPlans(plans, source, target, null, incoming, portfolios.getPositions(source.portfolioId()));
		}
		// A later partial delivery is not a second transfer when it belongs to a complete earlier sequence.
		return plans.stream().filter(plan -> plans.stream().noneMatch(other -> other != plan
				&& other.outgoingIds().size() > plan.outgoingIds().size()
				&& other.outgoingIds().containsAll(plan.outgoingIds()))).toList();
	}

	private boolean clearsSourcePortfolio(PortfolioSummary source, DeliveryDay delivery) {
		StockTransaction last = delivery.transactions().get(delivery.transactions().size() - 1);
		List<StockTransactionSecurityLeg> lastLegs = delivery.legs().stream()
				.filter(leg -> leg.getTransactionId() == last.getId()).toList();
		DeliveryDay lastMovement = new DeliveryDay(last.getSettledAt().toLocalDate(), List.of(last), lastLegs);
		return positionQuantities(portfolios.positionsBeforeTransfer(source, last)).equals(lastMovement.quantities());
	}

	private static void addPlans(List<TransferPlan> plans, PortfolioSummary source, PortfolioSummary target,
			DeliveryDay outgoing, List<DeliveryDay> incoming, List<PositionSummary> positions) {
		Map<PositionKey, Long> quantities = positionQuantities(positions);
		if (quantities.isEmpty() || quantities.values().stream().anyMatch(quantity -> quantity <= 0)) {
			return;
		}
		List<DeliveryDay> matches = new ArrayList<>();
		for (int index = 0; index < incoming.size(); index++) {
			DeliveryDay complete = completeDelivery(incoming, index, quantities);
			if (complete != null) {
				matches.add(complete);
			}
		}
		for (DeliveryDay day : matches) {
			plans.add(plan(source, target, outgoing, day, positions));
		}
		if (outgoing != null && matches.isEmpty()) {
			boolean partial = incoming.stream().anyMatch(day -> day.quantities().keySet().stream().anyMatch(quantities::containsKey));
			if (partial) {
				return;
			}
			plans.add(plan(source, target, outgoing, null, positions));
		}
	}

	private static TransferPlan plan(PortfolioSummary source, PortfolioSummary target, DeliveryDay outgoing,
			DeliveryDay incoming, List<PositionSummary> positions) {
		List<LocalDate> dates = Stream.of(outgoing, incoming).filter(day -> day != null)
				.flatMap(day -> day.transactions().stream()).map(transaction -> transaction.getSettledAt().toLocalDate())
				.distinct().sorted().toList();
		return new TransferPlan(source.portfolioId(), target.portfolioId(),
				outgoing != null ? outgoing.ids() : List.of(), incoming != null ? incoming.ids() : List.of(),
				dates, positions);
	}

	private static DeliveryDay completeDelivery(List<DeliveryDay> days, int start, Map<PositionKey, Long> expected) {
		Map<PositionKey, Long> quantities = new HashMap<>();
		List<StockTransaction> transactions = new ArrayList<>();
		List<StockTransactionSecurityLeg> legs = new ArrayList<>();
		for (int index = start; index < days.size(); index++) {
			DeliveryDay day = days.get(index);
			day.quantities().forEach((key, quantity) -> quantities.merge(key, quantity, Math::addExact));
			if (quantities.entrySet().stream().anyMatch(entry -> entry.getValue() > expected.getOrDefault(entry.getKey(), 0L))) {
				return null;
			}
			transactions.addAll(day.transactions());
			legs.addAll(day.legs());
			if (!expected.isEmpty() && quantities.equals(expected)) {
				return new DeliveryDay(days.get(start).date(), transactions, legs);
			}
		}
		return null;
	}

	private List<DeliveryDay> deliveryDays(int portfolioId, StockTransactionType type) {
		List<StockTransaction> transactions = dbController.getAllByParent(StockTransaction.class, portfolioId);
		Map<Integer, List<StockTransactionSecurityLeg>> legs = dbController.getAllByParent(StockTransactionSecurityLeg.class,
				portfolioId, StockPortfolioService.SQL_SELECT_SECURITY_LEGS_BY_PORTFOLIO).stream()
				.collect(Collectors.groupingBy(leg -> leg.getTransactionId()));
		Set<Integer> blocked = blockedTransactions(transactions);
		Map<LocalDate, List<StockTransaction>> days = transactions.stream()
				.filter(transaction -> transaction.getTransactionType() == type
						&& transaction.getTransactionStatus() == StockTransactionStatus.SETTLED
						&& !blocked.contains(transaction.getId()))
				.filter(transaction -> validLegs(legs.getOrDefault(transaction.getId(), List.of()), type))
				.sorted(Comparator.comparing(StockTransaction::getSettledAt).thenComparingInt(StockTransaction::getId))
				.collect(Collectors.groupingBy(transaction -> transaction.getSettledAt().toLocalDate()));
		return days.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(entry -> new DeliveryDay(entry.getKey(), entry.getValue(), entry.getValue().stream()
						.flatMap(transaction -> legs.get(transaction.getId()).stream()).toList())).toList();
	}

	private Set<Integer> blockedTransactions(List<StockTransaction> transactions) {
		Set<Integer> blocked = dbController.getAll(StockTransactionCashLeg.class).stream()
				.map(leg -> leg.getTransactionId()).collect(Collectors.toCollection(HashSet::new));
		dbController.getAll(StockCorporateActionMember.class).forEach(member -> blocked.add(member.getTransactionId()));
		Set<Integer> exchangeLegs = new HashSet<>();
		for (StockSecurityExchangeLink link : dbController.getAll(StockSecurityExchangeLink.class)) {
			exchangeLegs.add(link.getSourceLegId());
			exchangeLegs.add(link.getTargetLegId());
		}
		dbController.getAll(StockTransactionSecurityLeg.class).stream().filter(leg -> exchangeLegs.contains(leg.getId()))
				.forEach(leg -> blocked.add(leg.getTransactionId()));
		transactions.stream().filter(transaction -> transaction.getReversalOfTransactionId() != null).forEach(transaction -> {
			blocked.add(transaction.getId());
			blocked.add(transaction.getReversalOfTransactionId());
		});
		return blocked;
	}

	private static boolean validLegs(List<StockTransactionSecurityLeg> legs, StockTransactionType type) {
		return !legs.isEmpty() && legs.stream().allMatch(leg -> leg.getLegRole() == StockSecurityLegRole.POSITION
				&& (type == StockTransactionType.DELIVERY_OUT ? leg.getQuantityE9() < 0 : leg.getQuantityE9() > 0));
	}

	private static Map<PositionKey, Long> positionQuantities(List<PositionSummary> positions) {
		Map<PositionKey, Long> quantities = new HashMap<>();
		for (PositionSummary position : positions) {
			if (position.quantity().signum() != 0) {
				quantities.put(new PositionKey(position.securityId(), position.quantityType()),
						StockNumericValues.toScaledLong(position.quantity(), 9, RoundingMode.UNNECESSARY));
			}
		}
		return quantities;
	}

	private void convertDeliveries(List<Integer> ids, StockTransactionType type, LocalDate overrideDate) {
		for (int id : ids) {
			StockTransaction transaction = dbController.getById(StockTransaction.class, id);
			transaction.setTransactionType(type);
			if (overrideDate != null) {
				transaction.setTradeAt(overrideDate.atStartOfDay());
				transaction.setSettledAt(overrideDate.atStartOfDay());
				if (transaction.getSettlementDueAt() != null) {
					transaction.setSettlementDueAt(overrideDate.atStartOfDay());
				}
				if (transaction.getCashValueAt() != null) {
					transaction.setCashValueAt(overrideDate.atStartOfDay());
				}
			}
			dbController.insertOrUpdate(transaction);
		}
	}

	private void requireNonNegativeHistory(int portfolioId, List<PositionSummary> positions) {
		Set<PositionKey> affected = positionQuantities(positions).keySet();
		Map<PositionKey, Long> quantities = new HashMap<>();
		Map<Integer, List<StockTransactionSecurityLeg>> legs = dbController.getAllByParent(StockTransactionSecurityLeg.class,
				portfolioId, StockPortfolioService.SQL_SELECT_SECURITY_LEGS_BY_PORTFOLIO).stream()
				.collect(Collectors.groupingBy(leg -> leg.getTransactionId()));
		List<StockTransaction> transactions = dbController.getAllByParent(StockTransaction.class, portfolioId).stream()
				.filter(transaction -> transaction.getTransactionStatus() == StockTransactionStatus.SETTLED)
				.sorted(Comparator.comparing(StockTransaction::getSettledAt).thenComparingInt(StockTransaction::getId)).toList();
		for (StockTransaction transaction : transactions) {
			for (StockTransactionSecurityLeg leg : legs.getOrDefault(transaction.getId(), List.of())) {
				PositionKey key = new PositionKey(leg.getSecurityId(), leg.getQuantityType());
				if (affected.contains(key) && leg.getLegRole() != StockSecurityLegRole.REFERENCE
						&& quantities.merge(key, leg.getQuantityE9(), Math::addExact) < 0) {
					throw new GBankingException("Das Übertragungsdatum würde einen negativen Depotbestand erzeugen.");
				}
			}
		}
	}

	private record DeliveryDay(LocalDate date, List<StockTransaction> transactions, List<StockTransactionSecurityLeg> legs) {
		private List<Integer> ids() {
			return transactions.stream().map(transaction -> transaction.getId()).toList();
		}

		private Map<PositionKey, Long> quantities() {
			Map<PositionKey, Long> quantities = new HashMap<>();
			for (StockTransactionSecurityLeg leg : legs) {
				quantities.merge(new PositionKey(leg.getSecurityId(), leg.getQuantityType()), Math.abs(leg.getQuantityE9()), Math::addExact);
			}
			return quantities;
		}
	}
}
