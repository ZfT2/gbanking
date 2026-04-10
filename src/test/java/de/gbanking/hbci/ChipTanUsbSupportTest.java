package de.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.kapott.hbci.smartcardio.ChipTanCardService;
import org.kapott.hbci.smartcardio.SmartCardService;

import de.gbanking.db.DBController;
import de.gbanking.db.DBControllerTestUtil;
import de.gbanking.db.DbExecutor;
import de.gbanking.db.dao.Setting;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChipTanUsbSupportTest {

	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void shouldCreateChipTanSettingsOnlyOnce() {
		ChipTanUsbSupport.ensureSettingsExist();
		ChipTanUsbSupport.ensureSettingsExist();

		long enabledCount = DbExecutor.getInstance().getAll(Setting.class).stream()
				.filter(setting -> ChipTanUsbSupport.SETTING_ENABLED.equals(setting.getAttribute()))
				.count();
		long readerCount = DbExecutor.getInstance().getAll(Setting.class).stream()
				.filter(setting -> ChipTanUsbSupport.SETTING_READER_NAME.equals(setting.getAttribute()))
				.count();

		assertEquals(1, enabledCount);
		assertEquals(1, readerCount);
	}

	@Test
	void shouldReadConfiguredSettings() {
		ChipTanUsbSupport.ensureSettingsExist();
		Setting enabled = DbExecutor.getInstance().getAll(Setting.class).stream()
				.filter(setting -> ChipTanUsbSupport.SETTING_ENABLED.equals(setting.getAttribute()))
				.findFirst()
				.orElseThrow();
		Setting readerName = DbExecutor.getInstance().getAll(Setting.class).stream()
				.filter(setting -> ChipTanUsbSupport.SETTING_READER_NAME.equals(setting.getAttribute()))
				.findFirst()
				.orElseThrow();

		enabled.setValue("true");
		readerName.setValue("Reader 01");
		DbExecutor.getInstance().insertOrUpdate(enabled);
		DbExecutor.getInstance().insertOrUpdate(readerName);

		assertTrue(ChipTanUsbSupport.isEnabled());
		assertEquals("Reader 01", ChipTanUsbSupport.getConfiguredReaderName());
	}

	@Test
	void shouldRecognizeValidChipTanPayloads() {
		assertTrue(ChipTanUsbSupport.isChipTanPayload("0FA1BC"));
		assertTrue(ChipTanUsbSupport.isChipTanPayload(" 0fa1bc "));
		assertFalse(ChipTanUsbSupport.isChipTanPayload("12345"));
		assertFalse(ChipTanUsbSupport.isChipTanPayload("GG"));
		assertFalse(ChipTanUsbSupport.isChipTanPayload(null));
	}

	@Test
	void shouldDelegateTanRequestToSmartCardServiceUsingConfiguredReader() {
		ChipTanUsbSupport.ensureSettingsExist();
		Setting readerName = DbExecutor.getInstance().getAll(Setting.class).stream()
				.filter(setting -> ChipTanUsbSupport.SETTING_READER_NAME.equals(setting.getAttribute()))
				.findFirst()
				.orElseThrow();
		readerName.setValue("Reader 01");
		DbExecutor.getInstance().insertOrUpdate(readerName);

		ChipTanCardService cardService = mock(ChipTanCardService.class);
		when(cardService.getTan("0FA1BC")).thenReturn("123456");

		try (MockedStatic<SmartCardService> mockedStatic = Mockito.mockStatic(SmartCardService.class)) {
			mockedStatic.when(() -> SmartCardService.createInstance(ChipTanCardService.class, "Reader 01")).thenReturn(cardService);

			assertEquals("123456", ChipTanUsbSupport.requestTan("0FA1BC"));
		}
	}

	@Test
	void shouldUseFirstAvailableReaderWhenNoReaderNameConfigured() {
		ChipTanUsbSupport.ensureSettingsExist();

		ChipTanCardService cardService = mock(ChipTanCardService.class);
		when(cardService.getTan("0FA1BC")).thenReturn("654321");

		try (MockedStatic<SmartCardService> mockedStatic = Mockito.mockStatic(SmartCardService.class)) {
			mockedStatic.when(() -> SmartCardService.createInstance(eq(ChipTanCardService.class), isNull())).thenReturn(cardService);

			assertEquals("654321", ChipTanUsbSupport.requestTan("0FA1BC"));
		}
	}

	@Test
	void shouldExposeReaderSettingMetadata() {
		ChipTanUsbSupport.ensureSettingsExist();

		Setting enabled = DbExecutor.getInstance().getAll(Setting.class).stream()
				.filter(setting -> ChipTanUsbSupport.SETTING_ENABLED.equals(setting.getAttribute()))
				.findFirst()
				.orElseThrow();
		Setting readerName = DbExecutor.getInstance().getAll(Setting.class).stream()
				.filter(setting -> ChipTanUsbSupport.SETTING_READER_NAME.equals(setting.getAttribute()))
				.findFirst()
				.orElseThrow();

		assertNotNull(enabled.getComment());
		assertNotNull(readerName.getComment());
		assertTrue(enabled.isVisible());
		assertTrue(readerName.isVisible());
	}
}
