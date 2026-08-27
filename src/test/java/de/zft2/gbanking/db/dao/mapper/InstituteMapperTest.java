package de.zft2.gbanking.db.dao.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Institute;

class InstituteMapperTest {

	private final InstituteMapper mapper = new InstituteMapper();

	@Test
	void hasDkDataShouldRecognizeEveryPersistedField() {
		assertFalse(mapper.hasDkData(new Institute()));
		assertEveryValueDetected(List.of(
				institute -> institute.setImportNumber(1),
				institute -> institute.setDataCenter("data-center"),
				institute -> institute.setOrganisation("organisation"),
				institute -> institute.setHbciDns("hbci.example"),
				institute -> institute.setHbciIp("127.0.0.1"),
				institute -> institute.setHbciVersion(3.0),
				institute -> institute.setDdv("DDV"),
				institute -> institute.setRdh1(Boolean.FALSE),
				institute -> institute.setRdh2(Boolean.FALSE),
				institute -> institute.setRdh3(Boolean.FALSE),
				institute -> institute.setRdh4(Boolean.FALSE),
				institute -> institute.setRdh5(Boolean.FALSE),
				institute -> institute.setRdh6(Boolean.FALSE),
				institute -> institute.setRdh7(Boolean.FALSE),
				institute -> institute.setRdh8(Boolean.FALSE),
				institute -> institute.setRdh9(Boolean.FALSE),
				institute -> institute.setRdh10(Boolean.FALSE),
				institute -> institute.setPinUrl("https://pin.example"),
				institute -> institute.setVersion("1.0"),
				institute -> institute.setLastChanged(LocalDate.of(2026, 7, 29))),
				institute -> mapper.hasDkData(institute));
	}

	@Test
	void hasDbbDataShouldRecognizeEveryPersistedField() {
		assertFalse(mapper.hasDbbData(new Institute()));
		assertEveryValueDetected(List.of(
				institute -> institute.setDatasetNumber("dataset"),
				institute -> institute.setFeature(1),
				institute -> institute.setPostcode("10115"),
				institute -> institute.setBankNameShort("Bank"),
				institute -> institute.setPan("PAN"),
				institute -> institute.setCheckdigitMethod("00"),
				institute -> institute.setFeatureChange('A'),
				institute -> institute.setBlzDeletion(1),
				institute -> institute.setBlzSuccession("10020030")),
				institute -> mapper.hasDbbData(institute));
	}

	@Test
	void hasEpcDataShouldRecognizeEveryPersistedField() {
		assertFalse(mapper.hasEpcData(new Institute()));
		assertEveryValueDetected(List.of(
				institute -> institute.setCountry("DE"),
				institute -> institute.setAddress("Address"),
				institute -> institute.setReadinessDate("2026-07-29"),
				institute -> institute.setSchemeLeavingDate("2026-08-01"),
				institute -> institute.setSchemeOptions("SCT")),
				institute -> mapper.hasEpcData(institute));
	}

	private static void assertEveryValueDetected(List<Consumer<Institute>> values,
			Predicate<Institute> hasData) {
		for (int index = 0; index < values.size(); index++) {
			Institute institute = new Institute();
			values.get(index).accept(institute);
			assertTrue(hasData.test(institute), "Persisted field at index " + index + " was not detected");
		}
	}
}
