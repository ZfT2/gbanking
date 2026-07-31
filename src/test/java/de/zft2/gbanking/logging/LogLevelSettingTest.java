package de.zft2.gbanking.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.manager.HBCIUtils;

class LogLevelSettingTest {

	@Test
	void fromValueShouldTrimIgnoreCaseAndFallbackForBlankOrUnknownValues() {
		assertSame(LogLevelSetting.DEBUG, LogLevelSetting.fromValue(" debug ", LogLevelSetting.INFO));
		assertSame(LogLevelSetting.TRACE, LogLevelSetting.fromValue("TRACE", LogLevelSetting.INFO));
		assertSame(LogLevelSetting.WARN, LogLevelSetting.fromValue(null, LogLevelSetting.WARN));
		assertSame(LogLevelSetting.WARN, LogLevelSetting.fromValue("   ", LogLevelSetting.WARN));
		assertSame(LogLevelSetting.WARN, LogLevelSetting.fromValue("verbose", LogLevelSetting.WARN));
	}

	@Test
	void isValidShouldAcceptOnlyKnownEnumValuesIgnoringCaseAndWhitespace() {
		assertTrue(LogLevelSetting.isValid("none"));
		assertTrue(LogLevelSetting.isValid(" ERROR "));
		assertTrue(LogLevelSetting.isValid("trace"));
		assertFalse(LogLevelSetting.isValid(null));
		assertFalse(LogLevelSetting.isValid(""));
		assertFalse(LogLevelSetting.isValid("verbose"));
	}

	@Test
	void valuesShouldExposeExpectedLog4jAndHbciLevels() {
		assertEquals(Level.OFF, LogLevelSetting.NONE.toLog4jLevel());
		assertEquals(HBCIUtils.LOG_NONE, LogLevelSetting.NONE.toHbciLogLevel());
		assertEquals(Level.ERROR, LogLevelSetting.ERROR.toLog4jLevel());
		assertEquals(HBCIUtils.LOG_ERR, LogLevelSetting.ERROR.toHbciLogLevel());
		assertEquals(Level.WARN, LogLevelSetting.WARN.toLog4jLevel());
		assertEquals(HBCIUtils.LOG_WARN, LogLevelSetting.WARN.toHbciLogLevel());
		assertEquals(Level.INFO, LogLevelSetting.INFO.toLog4jLevel());
		assertEquals(HBCIUtils.LOG_INFO, LogLevelSetting.INFO.toHbciLogLevel());
		assertEquals(Level.DEBUG, LogLevelSetting.DEBUG.toLog4jLevel());
		assertEquals(HBCIUtils.LOG_DEBUG, LogLevelSetting.DEBUG.toHbciLogLevel());
		assertEquals(Level.TRACE, LogLevelSetting.TRACE.toLog4jLevel());
		assertEquals(HBCIUtils.LOG_DEBUG2, LogLevelSetting.TRACE.toHbciLogLevel());
	}
}
