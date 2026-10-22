package de.zft2.gbanking.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProgressCalculationTest {

	@Test
	void calculate_shouldMapTheProcessedItemsToThePhaseInterval() {
		assertEquals(0, ProgressCalculation.calculate(0, 4, 0, 10));
		assertEquals(3, ProgressCalculation.calculate(1, 4, 0, 10));
		assertEquals(5, ProgressCalculation.calculate(2, 4, 0, 10));
		assertEquals(10, ProgressCalculation.calculate(4, 4, 0, 10));
	}

	@Test
	void calculate_shouldUseTheStartOfTheSecondPhase() {
		assertEquals(10, ProgressCalculation.calculate(0, 4, 10, 90));
		assertEquals(30, ProgressCalculation.calculate(1, 4, 10, 90));
		assertEquals(50, ProgressCalculation.calculate(2, 4, 10, 90));
		assertEquals(90, ProgressCalculation.calculate(4, 4, 10, 90));
	}

	@Test
	void calculate_shouldLimitTheProcessedItemsToTheTotalCount() {
		assertEquals(10, ProgressCalculation.calculate(7, 4, 0, 10));
		assertEquals(10, ProgressCalculation.calculate(-3, 4, 10, 90));
	}

	@Test
	void calculate_shouldFinishThePhaseWithoutItems() {
		assertEquals(10, ProgressCalculation.calculate(0, 0, 0, 10));
		assertEquals(90, ProgressCalculation.calculate(0, 0, 10, 90));
	}
}
