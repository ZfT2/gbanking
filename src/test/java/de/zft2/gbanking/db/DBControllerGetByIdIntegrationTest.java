package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.testdata.TestDataFactory;

class DBControllerGetByIdIntegrationTest extends DBControllerIntegrationBaseTest {

	@Test
	void getByIdShouldMapMoneyTransferRecipientAndForeignDetails() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678"));
		BankAccount account = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		Recipient recipient = db.insertOrUpdate(TestDataFactory.createSampleRecipient01());

		MoneyTransferForeign foreignDetails = new MoneyTransferForeign();
		foreignDetails.setCurrency("USD");
		foreignDetails.setRecipientCountry("US");
		foreignDetails.setRecipientAccountNumber("123456789");
		foreignDetails.setRecipientBankCode("BOFAUS3N");
		foreignDetails.setChargeBearer(ForeignChargeBearer.SHARED);
		foreignDetails.setEndToEndReference("E2E-PRIMARY-ID");

		MoneyTransfer transfer = new MoneyTransfer();
		transfer.setAccountId(account.getId());
		transfer.setOrderType(OrderType.FOREIGN_TRANSFER);
		transfer.setRecipientId(recipient.getId());
		transfer.setPurpose("Foreign transfer");
		transfer.setAmount(new BigDecimal("123.45"));
		transfer.setMoneytransferStatus(MoneyTransferStatus.NEW);
		transfer.setForeignTransfer(foreignDetails);
		db.insertOrUpdate(transfer);

		MoneyTransfer storedTransfer = db.getById(MoneyTransfer.class, transfer.getId());

		assertEquals(transfer.getId(), storedTransfer.getId());
		assertEquals(new BigDecimal("123.45"), storedTransfer.getAmount());
		assertNotNull(storedTransfer.getRecipient());
		assertEquals(recipient.getId(), storedTransfer.getRecipient().getId());
		assertEquals(recipient.getName(), storedTransfer.getRecipient().getName());
		assertNotNull(storedTransfer.getForeignTransfer());
		assertEquals("USD", storedTransfer.getForeignTransfer().getCurrency());
		assertEquals("US", storedTransfer.getForeignTransfer().getRecipientCountry());
		assertEquals("E2E-PRIMARY-ID", storedTransfer.getForeignTransfer().getEndToEndReference());

		transfer.setOrderType(OrderType.TRANSFER);
		db.insertOrUpdate(transfer);

		MoneyTransfer convertedTransfer = db.getById(MoneyTransfer.class, transfer.getId());
		assertEquals(OrderType.TRANSFER, convertedTransfer.getOrderType());
		assertNull(convertedTransfer.getForeignTransfer());
	}

	@Test
	void getByIdShouldMapAllInstituteDetailsAndImportHistory() {
		ImportHistory importHistory = db.insertOrUpdate(new ImportHistory("institute-primary-id.csv"));
		Institute institute = new Institute();
		institute.setBlz("50010517");
		institute.setBic("INGDDEFFXXX");
		institute.setBankName("Test Institute");
		institute.setPlace("Frankfurt");
		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setImportFile(importHistory.getId());
		institute.setImportNumber(7);
		institute.setDataCenter("Test Data Center");
		institute.setHbciVersion(3.0);
		institute.setLastChanged(LocalDate.of(2026, 7, 29));
		institute.setDatasetNumber("DBB-42");
		institute.setPostcode("60311");
		institute.setCountry("DE");
		institute.setAddress("Teststrasse 1");
		institute.setServiceSct(1);
		institute.setServiceCor(0);
		institute.setServiceCor1(1);
		institute.setServiceB2b(0);
		institute.setServiceScc(1);
		db.insertOrUpdate(institute);

		Institute storedInstitute = db.getById(Institute.class, institute.getId());

		assertEquals(institute.getId(), storedInstitute.getId());
		assertEquals("Test Institute", storedInstitute.getBankName());
		assertEquals("institute-primary-id.csv", storedInstitute.getImportFileName());
		assertEquals(7, storedInstitute.getImportNumber());
		assertEquals("Test Data Center", storedInstitute.getDataCenter());
		assertEquals("DBB-42", storedInstitute.getDatasetNumber());
		assertEquals("60311", storedInstitute.getPostcode());
		assertEquals("DE", storedInstitute.getCountry());
		assertEquals("Teststrasse 1", storedInstitute.getAddress());
		assertEquals(1, storedInstitute.getServiceSct());
		assertEquals(0, storedInstitute.getServiceCor());
		assertEquals(1, storedInstitute.getServiceCor1());
		assertEquals(0, storedInstitute.getServiceB2b());
		assertEquals(1, storedInstitute.getServiceScc());
	}
}
