package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.fp3xmlextract.data.Fp3XmlBooking;

class Fp3BookingDateReaderTest {

	@TempDir
	Path tempDir;

	@Test
	void applyDates_shouldReadFourDigitYearAndValueDateWithoutYear() throws Exception {
		Path fp3File = writeFp3File("""
				<?xml version="1.0" encoding="utf-8"?>
				<preparedreport>
					<b2>
						<m21 u="Zinsen" />
						<m27 u="25.07.2024" />
					</b2>
					<b2>
						<m21 u="Sparplan" />
						<m27 u="26.07.2021&#10;25.07." />
					</b2>
				</preparedreport>
				""");

		List<Fp3XmlBooking> bookings = List.of(booking("Zinsen"), booking("Sparplan"));

		Fp3BookingDateReader.applyDates(fp3File, bookings);

		assertEquals(LocalDate.of(2024, 7, 25), bookings.get(0).getDateBooking());
		assertNull(bookings.get(0).getDateValue());
		assertEquals(LocalDate.of(2021, 7, 26), bookings.get(1).getDateBooking());
		assertEquals(LocalDate.of(2021, 7, 25), bookings.get(1).getDateValue());
	}

	@Test
	void applyDates_shouldReadTwoDigitYear() throws Exception {
		Path fp3File = writeFp3File("""
				<?xml version="1.0" encoding="utf-8"?>
				<preparedreport>
					<b2>
						<m21 u="Zinsen" />
						<m27 u="25.07.24" />
					</b2>
				</preparedreport>
				""");

		List<Fp3XmlBooking> bookings = List.of(booking("Zinsen"));

		Fp3BookingDateReader.applyDates(fp3File, bookings);

		assertEquals(LocalDate.of(2024, 7, 25), bookings.get(0).getDateBooking());
	}

	@Test
	void applyDates_shouldNotAssignDatesOfAnotherBooking() throws Exception {
		Path fp3File = writeFp3File("""
				<?xml version="1.0" encoding="utf-8"?>
				<preparedreport>
					<b2>
						<m21 u="Zinsen" />
						<m27 u="25.07.2024" />
					</b2>
				</preparedreport>
				""");

		List<Fp3XmlBooking> bookings = List.of(booking("Anderer Verwendungszweck"));

		Fp3BookingDateReader.applyDates(fp3File, bookings);

		assertNull(bookings.get(0).getDateBooking());
		assertNull(bookings.get(0).getDateValue());
	}

	private Fp3XmlBooking booking(String purpose) {
		return new Fp3XmlBooking(null, null, purpose, null, null, null, "");
	}

	private Path writeFp3File(String content) throws Exception {
		Path fp3File = tempDir.resolve("bookings.fp3");
		Files.writeString(fp3File, content, StandardCharsets.UTF_8);
		return fp3File;
	}
}
