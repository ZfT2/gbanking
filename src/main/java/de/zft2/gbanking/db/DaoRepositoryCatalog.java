package de.zft2.gbanking.db;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.repository.BankAccessRepository;
import de.zft2.gbanking.db.repository.BankAccountRepository;
import de.zft2.gbanking.db.repository.BookingRepository;
import de.zft2.gbanking.db.repository.CategoryRuleRepository;
import de.zft2.gbanking.db.repository.JdbcDaoRepository;
import de.zft2.gbanking.db.repository.MoneyTransferForeignRepository;
import de.zft2.gbanking.db.repository.ReadOnlyDaoRepository;
import de.zft2.gbanking.exception.GBankingException;

final class DaoRepositoryCatalog {

	private final DbSession session;
	private final Map<Class<? extends Dao>, DaoRepository<?>> repositories = new HashMap<>();

	DaoRepositoryCatalog(DbSession session) {
		this.session = Objects.requireNonNull(session, "session");
		BankAccountRepository bankAccountRepository = new BankAccountRepository(session);
		register(new BankAccessRepository(session, bankAccountRepository));
		register(bankAccountRepository);
		register(new BookingRepository(session));
		register(new CategoryRuleRepository(session, bankAccountRepository));
		registerSimple(BankAccountStatement.class);
		registerSimple(BankMessage.class);
		registerReadOnly(Bpd.class);
		registerSimple(BusinessCase.class);
		registerSimple(Category.class);
		registerSimple(ImportHistory.class);
		registerSimple(Institute.class);
		registerSimple(MoneyTransfer.class);
		register(new MoneyTransferForeignRepository(session));
		registerSimple(MoneyTransferProtocol.class);
		registerReadOnly(ParameterData.class);
		registerSimple(Psd2ClientConfiguration.class);
		registerSimple(Recipient.class);
		registerSimple(Setting.class);
		registerReadOnly(Upd.class);
	}

	@SuppressWarnings("unchecked")
	<T extends Dao> DaoRepository<T> repository(Class<T> type) {
		DaoRepository<?> repository = repositories.get(Objects.requireNonNull(type, "type"));
		if (repository == null) {
			throw new GBankingException("No repository configured for DAO type: " + type.getName());
		}
		return (DaoRepository<T>) repository;
	}

	private <T extends Dao> void registerSimple(Class<T> type) {
		register(new JdbcDaoRepository<>(type, session));
	}

	private <T extends Dao> void registerReadOnly(Class<T> type) {
		register(new ReadOnlyDaoRepository<>(type, session));
	}

	private <T extends Dao> void register(DaoRepository<T> repository) {
		DaoRepository<?> previous = repositories.put(repository.type(), repository);
		if (previous != null) {
			throw new IllegalStateException("Duplicate repository for DAO type: " + repository.type().getName());
		}
	}
}
