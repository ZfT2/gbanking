package org.kapott.hbci.GV;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassportInternal;

class GVKontoauszugUebersichtTest {

	@BeforeEach
	void initHbciUtils() {
		HBCIUtils.initThread(new Properties(), new HBCICallbackConsole());
	}

	@AfterEach
	void closeHbciUtils() {
		HBCIUtils.doneThread();
	}

	@Test
	void getHbciCodeShouldNotDependOnBpdParameterSegment() {
		HBCIHandler handler = mock(HBCIHandler.class);
		HBCIPassportInternal passport = mock(HBCIPassportInternal.class);
		when(handler.getPassport()).thenReturn(passport);

		GVKontoauszugUebersicht job = new GVKontoauszugUebersicht(handler);

		assertEquals("HKKAU", job.getHBCICode());
	}
}
