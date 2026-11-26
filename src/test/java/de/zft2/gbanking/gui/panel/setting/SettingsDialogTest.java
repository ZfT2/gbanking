package de.zft2.gbanking.gui.panel.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.scene.control.TextField;

class SettingsDialogTest {

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	@ParameterizedTest
	@CsvSource(value = {
			"INT;true;NULL;-12;1,2",
			"FLOAT;false;7;-12.5;1.2.3",
			"DOUBLE;true;7;-12,5;12a",
			"BIGDECIMAL;false;7;-,5;1-2"
	}, delimiter = ';', nullValues = "NULL")
	void numericEditorsShouldKeepTheirInputRulesAndConfiguration(DataType type, boolean editable,
			String initial, String accepted, String rejected) {
		JavaFxTestSupport.callFx(() -> {
			Setting setting = new Setting();
			setting.setAttribute("test.number");
			setting.setDataType(type);
			setting.setEditable(editable);
			setting.setValue(initial);
			SettingsDialog dialog = new SettingsDialog(null);
			Method createEditor = SettingsDialog.class.getDeclaredMethod("createEditor", Setting.class);
			createEditor.setAccessible(true);
			TextField field = TextField.class.cast(createEditor.invoke(dialog, setting));
			assertEquals(initial != null ? initial : "", field.getText());
			assertEquals(!editable, field.isDisable());
			assertEquals(180, field.getMaxWidth());

			field.setText(accepted);
			assertEquals(accepted, field.getText());
			field.setText(rejected);
			assertEquals(accepted, field.getText());
			for (String intermediate : type == DataType.INT ? List.of("-", "") : List.of("-", ".", ",", "-.", "-,", "")) {
				field.setText(intermediate);
				assertEquals(intermediate, field.getText());
			}
			return null;
		});
	}
}
