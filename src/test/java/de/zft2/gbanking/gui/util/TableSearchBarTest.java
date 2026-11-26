package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

class TableSearchBarTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			1234.560 | 1234.560 1234,560 1.234,56
			-123.4   | -123.4 -123,4 -123,40
			999.995  | 999.995 999,995 1.000,00
			0.125    | 0.125 0,125 0,12
			-0.001   | -0.001 -0,001 -0,00
			-0.005   | -0.005 -0,005 -0,00
			""")
	void amountSearchTextShouldKeepAllExistingVariants(String amount, String expected) {
		assertEquals(expected, TableSearchBar.text(new BigDecimal(amount)));
	}

	@Test
	void typingShouldBeDebouncedAndEnterShouldCancelThePendingSearch() throws InterruptedException {
		CountDownLatch filtered = new CountDownLatch(1);
		CountDownLatch settled = new CountDownLatch(1);
		AtomicInteger evaluations = new AtomicInteger();
		TableView<String> table = JavaFxTestSupport.callFx(() -> new TableView<>());
		TableSearchBar<String> bar = JavaFxTestSupport.callFx(() -> new TableSearchBar<>(table, row -> {
			evaluations.incrementAndGet();
			return row;
		}));
		JavaFxTestSupport.runFx(() -> {
			bar.setRows(List.of("alpha", "beta"));
			table.getItems().addListener((ListChangeListener<String>) change -> filtered.countDown());
			TextField search = (TextField) bar.getChildren().get(1);
			search.setText("alpha");
			search.setText("beta");
			assertEquals(List.of("alpha", "beta"), table.getItems());
		});
		assertTrue(filtered.await(5, TimeUnit.SECONDS));
		JavaFxTestSupport.runFx(() -> {
			assertEquals(List.of("beta"), table.getItems());
			assertEquals(2, evaluations.get());
			TextField search = (TextField) bar.getChildren().get(1);
			search.setText("alpha");
			search.fireEvent(new ActionEvent());
			assertEquals(List.of("alpha"), table.getItems());
			PauseTransition wait = new PauseTransition(Duration.millis(400));
			wait.setOnFinished(event -> settled.countDown());
			wait.play();
		});
		assertTrue(settled.await(5, TimeUnit.SECONDS));
		JavaFxTestSupport.runFx(() -> assertEquals(4, evaluations.get()));
	}
}
