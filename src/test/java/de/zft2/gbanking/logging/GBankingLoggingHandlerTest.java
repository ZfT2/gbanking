package de.zft2.gbanking.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

class GBankingLoggingHandlerTest {

	private static final String LOGGER_NAME = GBankingLoggingHandler.class.getName();

	private LoggerContext loggerContext;
	private TestAppender appender;

	@BeforeEach
	void attachAppender() {
		loggerContext = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = loggerContext.getConfiguration();
		appender = new TestAppender();
		appender.start();
		configuration.addAppender(appender);
		configuration.addLogger(LOGGER_NAME, createLoggerConfig(Level.DEBUG, appender));
		loggerContext.updateLoggers();
	}

	@AfterEach
	void detachAppender() {
		Configuration configuration = loggerContext.getConfiguration();
		configuration.removeLogger(LOGGER_NAME);
		appender.stop();
		loggerContext.updateLoggers();
	}

	@Test
	void logRetrivedBankAccessInfo_shouldMaskSensitivePassportData() {
		HBCIPassport passport = mock(HBCIPassport.class);
		Properties bpd = new Properties();
		Properties upd = new Properties();
		when(passport.getInstName()).thenReturn("Test Bank");
		when(passport.getHost()).thenReturn("fints.example.test");
		when(passport.getPort()).thenReturn(443);
		when(passport.getFilterType()).thenReturn("Base64");
		when(passport.getHBCIVersion()).thenReturn("300");
		when(passport.getSuppVersions()).thenReturn(new String[] { "300" });
		when(passport.getBPDVersion()).thenReturn("1");
		when(passport.getBPD()).thenReturn(bpd);
		when(passport.getUPDVersion()).thenReturn("2");
		when(passport.getUPD()).thenReturn(upd);
		when(passport.getUserId()).thenReturn("user-123456");
		when(passport.getCustomerId()).thenReturn("customer-987654");
		when(passport.getCountry()).thenReturn("DE");
		when(passport.getDefaultLang()).thenReturn("de");

		new GBankingLoggingHandler().logRetrivedBankAccessInfo(passport, true);

		String message = singleMessage();
		assertTrue(message.contains("BPD: present"));
		assertTrue(message.contains("UPD: present"));
		assertTrue(message.contains(SensitiveDataMasker.maskIdentifier("user-123456")));
		assertTrue(message.contains(SensitiveDataMasker.maskIdentifier("customer-987654")));
		assertFalse(message.contains("user-123456"));
		assertFalse(message.contains("customer-987654"));
	}

	@Test
	void logRetrievedAccountInfo_shouldMaskAccountIdentifiers() {
		Konto konto = new Konto();
		konto.customerid = "customer-123456";
		konto.iban = "DE44500105175407324931";
		konto.number = "123456789";
		konto.subnumber = "01";
		konto.bic = "TESTDEFFXXX";
		konto.curr = "EUR";

		new GBankingLoggingHandler().logRetrievedAccountInfo(konto);

		String message = singleMessage();
		assertTrue(message.contains(SensitiveDataMasker.maskIdentifier(konto.customerid)));
		assertTrue(message.contains(SensitiveDataMasker.maskIban(konto.iban)));
		assertTrue(message.contains(SensitiveDataMasker.maskAccountNumber(konto.number)));
		assertFalse(message.contains(konto.customerid));
		assertFalse(message.contains(konto.iban));
		assertFalse(message.contains(konto.number));
	}

	@Test
	void logRetrivedBookingInfo_shouldOnlyDescribeCounterpartyPresence() {
		UmsLine booking = new UmsLine();
		booking.id = "booking-1";
		booking.text = "Ueberweisung";
		booking.usage = List.of("Test purpose");
		booking.value = new Value();
		booking.value.setCurr("EUR");
		booking.value.setValue(new BigDecimal("12.34"));
		booking.other = new Konto();
		booking.other.iban = "DE44500105175407324931";

		new GBankingLoggingHandler().logRetrivedBookingInfo(booking);

		String message = singleMessage();
		assertTrue(message.contains("other present"));
		assertFalse(message.contains(booking.other.iban));
	}

	@Test
	void shouldSkipLoggingWhenRequiredLogLevelIsDisabled() {
		Configuration configuration = loggerContext.getConfiguration();
		configuration.removeLogger(LOGGER_NAME);
		configuration.addLogger(LOGGER_NAME, createLoggerConfig(Level.ERROR, appender));
		loggerContext.updateLoggers();

		GBankingLoggingHandler handler = new GBankingLoggingHandler();
		handler.logRetrivedBankAccessInfo(mock(HBCIPassport.class), true);
		handler.logRetrievedAccountInfo(new Konto());
		handler.logRetrivedBookingInfo(new UmsLine());

		assertTrue(appender.messages().isEmpty());
	}

	private String singleMessage() {
		List<String> messages = appender.messages();
		assertTrue(messages.size() == 1, "Expected exactly one log message but got " + messages.size());
		return messages.get(0);
	}

	private static LoggerConfig createLoggerConfig(Level level, Appender appender) {
		LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, level, false);
		loggerConfig.addAppender(appender, level, null);
		return loggerConfig;
	}

	private static final class TestAppender extends AbstractAppender {

		private final List<LogEvent> events = new ArrayList<>();

		private TestAppender() {
			super("GBankingLoggingHandlerTestAppender", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
		}

		@Override
		public void append(LogEvent event) {
			events.add(event.toImmutable());
		}

		private List<String> messages() {
			return events.stream().map(event -> event.getMessage().getFormattedMessage()).toList();
		}
	}

}
