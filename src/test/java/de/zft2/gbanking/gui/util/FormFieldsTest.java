package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.util.FormFields;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

class FormFieldsTest {

	@Test
	void textFactoriesShouldUseExpectedWidths() {
		TextField xs = JavaFxTestSupport.callFx(FormFields::textXs);
		TextField l = JavaFxTestSupport.callFx(FormFields::textL);

		assertEquals(FieldWidth.XS.getPrefWidth(), xs.getPrefWidth());
		assertEquals(FieldWidth.L.getPrefWidth(), l.getPrefWidth());
	}

	@Test
	void textAreaLargeShouldUseDefaultAndCustomRowCount() {
		TextArea defaultArea = JavaFxTestSupport.callFx(FormFields::textAreaLarge);
		TextArea customArea = JavaFxTestSupport.callFx(() -> FormFields.textAreaLarge(6));

		assertEquals(3, defaultArea.getPrefRowCount());
		assertEquals(6, customArea.getPrefRowCount());
	}

	@Test
	void comboFactoryShouldApplyWidthAndItems() {
		ComboBox<String> combo = JavaFxTestSupport.callFx(() -> FormFields.comboM(FXCollections.observableArrayList("EUR", "USD")));

		assertEquals(FieldWidth.M.getPrefWidth(), combo.getPrefWidth());
		assertEquals(2, combo.getItems().size());
	}
}
