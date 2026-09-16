package de.zft2.gbanking.service.stock;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;

public class StockPortfolioAdministrationService extends AbstractDbService {

	public List<PortfolioDetails> getPortfolios() {
		Map<Integer, BankAccount> accounts = dbController.getAll(BankAccount.class).stream()
				.collect(Collectors.toMap(BankAccount::getId, Function.identity()));
		return dbController.getAll(StockPortfolio.class).stream()
				.map(portfolio -> toDetails(portfolio, accounts))
				.filter(details -> details != null)
				.sorted(Comparator.comparing(PortfolioDetails::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public List<SettlementAccountOption> getSettlementAccountOptions(Integer includedAccountId) {
		return dbController.getAll(BankAccount.class).stream()
				.filter(StockPortfolioAdministrationService::isSettlementAccount)
				.filter(account -> account.getId() == value(includedAccountId)
						|| account.getAccountState() == null || account.getAccountState() == AccountState.ACTIVE)
				.map(account -> new SettlementAccountOption(account.getId(), settlementAccountName(account),
						account.getBaseCurrency()))
				.sorted(Comparator.comparing(SettlementAccountOption::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public PortfolioDetails save(PortfolioSaveRequest request) {
		validate(request);
		int portfolioId = dbController.executeInTransaction(() -> request.portfolioId() == null
				? createPortfolio(request) : updatePortfolio(request));
		return getPortfolios().stream().filter(portfolio -> portfolio.portfolioId() == portfolioId).findFirst()
				.orElseThrow(() -> new GBankingException("Das gespeicherte Depot wurde nicht gefunden"));
	}

	private int createPortfolio(PortfolioSaveRequest request) {
		BankAccount account = new BankAccount();
		account.setAccountType(AccountType.DEPOT);
		account.setSource(Source.MANUELL);
		account.setOfflineAccount(true);
		applyAccountValues(account, request);
		account.setOfflineAccount(true);
		dbController.insertOrUpdate(account);

		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setAccountId(account.getId());
		portfolio.setCurrentSettlementRelationId(0);
		portfolio.setOpenedAt(request.openedAt());
		portfolio.setClosedAt(request.closedAt());
		dbController.insertOrUpdate(portfolio);

		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setPortfolioId(portfolio.getId());
		relation.setAccountId(request.settlementAccountId());
		relation.setValidFrom(request.settlementValidFrom());
		dbController.insertOrUpdate(relation);

		portfolio.setCurrentSettlementRelationId(relation.getId());
		dbController.insertOrUpdate(portfolio);
		return portfolio.getId();
	}

	private int updatePortfolio(PortfolioSaveRequest request) {
		StockPortfolio portfolio = requirePortfolio(request.portfolioId());
		BankAccount account = requireDepotAccount(portfolio.getAccountId());
		StockPortfolioSettlementAccount relation = requireCurrentRelation(portfolio);

		applyAccountValues(account, request);
		dbController.insertOrUpdate(account);
		portfolio.setOpenedAt(request.openedAt());
		portfolio.setClosedAt(request.closedAt());
		dbController.insertOrUpdate(portfolio);

		if (relation.getAccountId() != request.settlementAccountId()) {
			changeSettlementAccount(portfolio, relation, request);
		}
		return portfolio.getId();
	}

	private void changeSettlementAccount(StockPortfolio portfolio, StockPortfolioSettlementAccount currentRelation,
			PortfolioSaveRequest request) {
		LocalDate validFrom = request.settlementValidFrom();
		if (validFrom.isBefore(currentRelation.getValidFrom())) {
			throw new GBankingException("Der Gültigkeitsbeginn darf nicht vor der aktuellen Zuordnung liegen");
		}
		if (validFrom.equals(currentRelation.getValidFrom())) {
			currentRelation.setAccountId(request.settlementAccountId());
			dbController.insertOrUpdate(currentRelation);
			return;
		}

		portfolio.setCurrentSettlementRelationId(0);
		dbController.insertOrUpdate(portfolio);
		currentRelation.setValidTo(validFrom);
		dbController.insertOrUpdate(currentRelation);

		StockPortfolioSettlementAccount newRelation = new StockPortfolioSettlementAccount();
		newRelation.setPortfolioId(portfolio.getId());
		newRelation.setAccountId(request.settlementAccountId());
		newRelation.setValidFrom(validFrom);
		dbController.insertOrUpdate(newRelation);
		portfolio.setCurrentSettlementRelationId(newRelation.getId());
		dbController.insertOrUpdate(portfolio);
	}

	private static void applyAccountValues(BankAccount account, PortfolioSaveRequest request) {
		account.setAccountName(trimToNull(request.name()));
		account.setOwnerName(trimToNull(request.ownerName()));
		account.setOwnerName2(trimToNull(request.ownerName2()));
		account.setIban(trimToNull(request.iban()));
		account.setNumber(trimToNull(request.accountNumber()));
		account.setSubnumber(trimToNull(request.subnumber()));
		account.setBankName(trimToNull(request.bankName()));
		account.setBic(trimToNull(request.bic()));
		account.setBlz(trimToNull(request.blz()));
		account.setBaseCurrency(request.currency());
		account.setAccountState(request.accountState());
		account.setOfflineAccount(request.offline());
	}

	private void validate(PortfolioSaveRequest request) {
		if (request == null) {
			throw new GBankingException("Die Depotdaten fehlen");
		}
		if (trimToNull(request.name()) == null) {
			throw new GBankingException("Die Depotbezeichnung muss angegeben werden");
		}
		if (request.currency() == null || request.accountState() == null || request.openedAt() == null) {
			throw new GBankingException("Währung, Status und Eröffnungsdatum müssen angegeben werden");
		}
		if (request.closedAt() != null && !request.openedAt().isBefore(request.closedAt())) {
			throw new GBankingException("Das Schließungsdatum muss nach dem Eröffnungsdatum liegen");
		}
		BankAccount settlementAccount = dbController.getById(BankAccount.class, request.settlementAccountId());
		if (!isSettlementAccount(settlementAccount)) {
			throw new GBankingException("Es wurde kein gültiges Verrechnungskonto ausgewählt");
		}
		if (request.settlementValidFrom() == null || request.settlementValidFrom().isBefore(request.openedAt())) {
			throw new GBankingException("Der Gültigkeitsbeginn des Verrechnungskontos darf nicht vor der Depoteröffnung liegen");
		}
		if (request.closedAt() != null && !request.settlementValidFrom().isBefore(request.closedAt())) {
			throw new GBankingException("Der Gültigkeitsbeginn des Verrechnungskontos muss vor der Depotschließung liegen");
		}
	}

	private StockPortfolio requirePortfolio(Integer portfolioId) {
		StockPortfolio portfolio = portfolioId != null ? dbController.getById(StockPortfolio.class, portfolioId) : null;
		if (portfolio == null) {
			throw new GBankingException("Das Depot wurde nicht gefunden");
		}
		return portfolio;
	}

	private BankAccount requireDepotAccount(int accountId) {
		BankAccount account = dbController.getById(BankAccount.class, accountId);
		if (account == null || account.getAccountType() != AccountType.DEPOT) {
			throw new GBankingException("Das Depotkonto wurde nicht gefunden");
		}
		return account;
	}

	private StockPortfolioSettlementAccount requireCurrentRelation(StockPortfolio portfolio) {
		StockPortfolioSettlementAccount relation = dbController.getById(StockPortfolioSettlementAccount.class,
				portfolio.getCurrentSettlementRelationId());
		if (relation == null || relation.getPortfolioId() != portfolio.getId() || relation.getValidTo() != null) {
			throw new GBankingException("Das Depot hat keine gültige aktuelle Verrechnungskonto-Zuordnung");
		}
		return relation;
	}

	private PortfolioDetails toDetails(StockPortfolio portfolio, Map<Integer, BankAccount> accounts) {
		BankAccount account = accounts.get(portfolio.getAccountId());
		StockPortfolioSettlementAccount relation = dbController.getById(StockPortfolioSettlementAccount.class,
				portfolio.getCurrentSettlementRelationId());
		BankAccount settlementAccount = relation != null ? accounts.get(relation.getAccountId()) : null;
		if (account == null || relation == null || settlementAccount == null) {
			return null;
		}
		return new PortfolioDetails(portfolio.getId(), account.getId(), value(account.getAccountName()),
				value(account.getOwnerName()), value(account.getOwnerName2()), value(account.getIban()),
				value(account.getNumber()), value(account.getSubnumber()), value(account.getBankName()),
				value(account.getBic()), value(account.getBlz()), account.getBaseCurrency(), account.getAccountState(),
				account.isOfflineAccount(), account.getBankAccessId(), bankAccessName(account.getBankAccessId()), portfolio.getOpenedAt(),
				portfolio.getClosedAt(), relation.getAccountId(), settlementAccountName(settlementAccount),
				relation.getValidFrom(), account.getCreatedAt(), account.getUpdatedAt());
	}

	private String bankAccessName(Integer bankAccessId) {
		if (bankAccessId == null || bankAccessId <= 0) {
			return "";
		}
		BankAccess access = dbController.getBankAccessById(bankAccessId);
		return access != null ? value(access.getBankName()) : "";
	}

	private static boolean isSettlementAccount(BankAccount account) {
		return account != null && (account.getAccountType() == AccountType.CURRENT_ACCOUNT
				|| account.getAccountType() == AccountType.DEPOT_ACCOUNT);
	}

	private static String settlementAccountName(BankAccount account) {
		String name = trimToNull(account.getAccountName());
		String identifier = trimToNull(account.getIban()) != null ? account.getIban() : trimToNull(account.getNumber());
		if (name == null) {
			return value(identifier);
		}
		return identifier == null ? name : name + " (" + identifier + ")";
	}

	private static String value(String value) {
		return value != null ? value : "";
	}

	private static int value(Integer value) {
		return value != null ? value : 0;
	}

	public record PortfolioDetails(int portfolioId, int accountId, String name, String ownerName,
			String ownerName2, String iban, String accountNumber, String subnumber, String bankName,
			String bic, String blz, Currency currency, AccountState accountState, boolean offline,
			Integer bankAccessId, String bankAccessName, LocalDate openedAt, LocalDate closedAt, int settlementAccountId,
			String settlementAccountName, LocalDate settlementValidFrom, LocalDate createdAt,
			LocalDate updatedAt) {
	}

	public record SettlementAccountOption(int accountId, String displayName, Currency currency) {
		@Override
		public String toString() {
			return displayName;
		}
	}

	public record PortfolioSaveRequest(Integer portfolioId, String name, String ownerName, String ownerName2,
			String iban, String accountNumber, String subnumber, String bankName, String bic, String blz,
			Currency currency, AccountState accountState, boolean offline, LocalDate openedAt,
			LocalDate closedAt, int settlementAccountId, LocalDate settlementValidFrom) {
	}
}
