package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.dao.logic.MnDao;
import de.zft2.gbanking.exception.GBankingException;

class DaoRepositoryCatalogTest {

	@Test
	void shouldProvideTypedRepositoryForEveryPersistedDaoType() throws Exception {
		try (DbSession session = new DbSession(Path.of("repository-test.db"), Mockito.mock(Connection.class))) {
			DaoRepositoryCatalog catalog = session.repositoryCatalog();

			for (Class<? extends Dao> type : persistedTypes()) {
				assertSame(type, catalog.repository(type).type());
			}
			assertSame(session.repositories(), session.repositories());
			assertThrows(GBankingException.class, () -> catalog.repository(ParameterDataBankAccess.class));
			assertThrows(GBankingException.class, () -> catalog.repository(MnDao.class));
		}
	}

	@Test
	void shouldUseSpecializedRepositoriesForAggregateRoots() throws Exception {
		try (DbSession session = new DbSession(Path.of("repository-test.db"), Mockito.mock(Connection.class))) {
			DaoRepositoryCatalog catalog = session.repositoryCatalog();

			assertInstanceOf(BankAccessRepository.class, catalog.repository(BankAccess.class));
			assertInstanceOf(BankAccountRepository.class, catalog.repository(BankAccount.class));
			assertInstanceOf(BookingRepository.class, catalog.repository(Booking.class));
			assertInstanceOf(CategoryRuleRepository.class, catalog.repository(CategoryRule.class));
			assertInstanceOf(MoneyTransferForeignRepository.class, catalog.repository(MoneyTransferForeign.class));
			assertInstanceOf(ReadOnlyDaoRepository.class, catalog.repository(Bpd.class));
			assertInstanceOf(ReadOnlyDaoRepository.class, catalog.repository(ParameterData.class));
			assertInstanceOf(ReadOnlyDaoRepository.class, catalog.repository(Upd.class));
		}
	}

	private static List<Class<? extends Dao>> persistedTypes() {
		return List.of(
				BankAccess.class,
				BankAccount.class,
				BankAccountStatement.class,
				BankMessage.class,
				Booking.class,
				Bpd.class,
				BusinessCase.class,
				Category.class,
				CategoryRule.class,
				ImportHistory.class,
				Institute.class,
				MoneyTransfer.class,
				MoneyTransferForeign.class,
				MoneyTransferProtocol.class,
				ParameterData.class,
				Psd2ClientConfiguration.class,
				Recipient.class,
				Setting.class,
				Upd.class);
	}
}
