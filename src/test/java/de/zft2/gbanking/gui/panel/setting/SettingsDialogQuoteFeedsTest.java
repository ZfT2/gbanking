package de.zft2.gbanking.gui.panel.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockQuoteFeed;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.service.stock.quote.GenericQuoteFeed;
import de.zft2.gbanking.service.stock.quote.QuoteProviderSettings;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

class SettingsDialogQuoteFeedsTest {

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	@Test
	void shouldLoadAndSaveFeedsWithoutAJsonSetting() {
		DBController db = database.controller();
		GenericQuoteFeed feed = GenericQuoteFeed.create("Draft feed");
		QuoteProviderSettings.saveGenericFeeds(List.of(feed));
		int sourceId = db.getAll(StockQuoteFeed.class).get(0).getSourceId();

		JavaFxTestSupport.callFx(() -> {
			SettingsDialog dialog = new SettingsDialog(null);
			Stage window = dialog.createWindow();
			try {
				TextField name = field(dialog, "genericFeedNameField");
				TextField url = field(dialog, "genericFeedUrlField");
				assertEquals(feed.name(), name.getText());
				name.setText("Renamed feed");
				url.setText("https://example.test/{symbol}");
				Method save = SettingsDialog.class.getDeclaredMethod("saveProgramSettings");
				save.setAccessible(true);
				assertEquals(true, save.invoke(dialog));
			} finally {
				window.close();
			}
			return null;
		});

		GenericQuoteFeed saved = QuoteProviderSettings.getConfiguration().genericFeeds().get(0);
		assertEquals(feed.id(), saved.id());
		assertEquals("Renamed feed", saved.name());
		assertEquals("https://example.test/{symbol}", saved.urlTemplate());
		assertEquals("Renamed feed", db.getById(StockDataSource.class, sourceId).getSourceName());
	}

	private static TextField field(SettingsDialog dialog, String name) throws ReflectiveOperationException {
		Field field = SettingsDialog.class.getDeclaredField(name);
		field.setAccessible(true);
		return TextField.class.cast(field.get(dialog));
	}
}
