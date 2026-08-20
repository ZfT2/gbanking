package de.zft2.gbanking.gui.panel.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Institute;

class InstituteSourceTest {

	@Test
	void countryForDisplayShouldUseGermanFallbackWithoutEpcData() {
		Institute institute = new Institute();
		institute.setBlz("50010517");

		assertEquals("Deutschland", InstituteSource.countryForDisplay(institute, "Deutschland"));
	}

	@Test
	void countryForDisplayShouldPreserveEpcCountry() {
		Institute institute = new Institute();
		institute.setCountry("AUSTRIA");

		assertEquals("AUSTRIA", InstituteSource.countryForDisplay(institute, "Deutschland"));
	}

	@Test
	void shouldIdentifyReachableSourceAndCountryFromBic() {
		Institute institute = new Institute();
		institute.setBic("AAAARSBG");
		institute.setServiceSct(1);

		assertEquals(List.of(InstituteSource.DBB_REACHABLE), InstituteSource.forInstitute(institute));
		assertEquals("RS", InstituteSource.countryForDisplay(institute, "Deutschland"));
	}
}
