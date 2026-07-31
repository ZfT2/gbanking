package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OperationDurationLabelTest {

	@Test
	void shouldFormatDurationsInSecondsAndMinutes() {
		assertAll(
				() -> assertEquals("0s", OperationDurationLabel.formatSeconds(-1L)),
				() -> assertEquals("42s", OperationDurationLabel.formatSeconds(42L)),
				() -> assertEquals("59s", OperationDurationLabel.formatSeconds(59L)),
				() -> assertEquals("1m 0s", OperationDurationLabel.formatSeconds(60L)),
				() -> assertEquals("2m 4s", OperationDurationLabel.formatSeconds(124L)));
	}
}
