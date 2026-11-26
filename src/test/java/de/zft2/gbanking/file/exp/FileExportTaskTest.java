package de.zft2.gbanking.file.exp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.ExportException;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.testdata.TestDataFactory;
import javafx.concurrent.Worker.State;

class FileExportTaskTest {

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	@ParameterizedTest
	@EnumSource(value = ExportType.class, names = {
			"BOOKINGS_XML", "BOOKINGS_CSV", "BOOKINGS_FP3", "BOOKINGS_MT940",
			"MONEYTRANSFERS_CSV", "MONEYTRANSFERS_SEPA_XML"
	})
	void writeFailureShouldFailTheTaskAndKeepTheCause(ExportType type) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setIban("DE30120300000018884074");
		account.setOwnerName("Export test");
		account = database.controller().insertOrUpdate(account);
		Recipient recipient = database.controller().resolveRecipient(
				new Recipient("Recipient", "DE73700202700005716977", null, null, null, null, Source.MONEYTRANSFER));
		MoneyTransfer transfer = new MoneyTransfer();
		transfer.setAccountId(account.getId());
		transfer.setRecipientId(recipient.getId());
		transfer.setOrderType(OrderType.TRANSFER);
		transfer.setAmount(BigDecimal.ONE);
		transfer.setMoneytransferStatus(MoneyTransferStatus.NEW);
		database.controller().insertOrUpdate(transfer);
		FileExportTask task = new FileExportTask(database.directory().toString(), type, List.of(account));

		JavaFxTestSupport.runFx(task);

		JavaFxTestSupport.runFx(() -> {
			assertEquals(State.FAILED, task.getState());
			ExportException exception = assertInstanceOf(ExportException.class, task.getException());
			assertNotNull(exception.getCause());
			assertTrue(task.getWorkerProgress() < 100);
		});
	}
}
