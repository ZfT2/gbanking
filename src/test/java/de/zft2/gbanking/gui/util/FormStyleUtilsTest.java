package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

class FormStyleUtilsTest {

	@Test
	void applyWidthShouldSetMinAndPreferredWidth() {
		TextField textField = JavaFxTestSupport.callFx(() -> FormStyleUtils.applyWidth(new TextField(), FieldWidth.M));

		assertEquals(FieldWidth.M.getPrefWidth(), textField.getPrefWidth());
		assertEquals(FieldWidth.M.getPrefWidth(), textField.getMinWidth());
	}

	@Test
	void prepareLargeTextAreaShouldConfigureWrappingAndSizing() {
		TextArea textArea = JavaFxTestSupport.callFx(() -> FormStyleUtils.prepareLargeTextArea(new TextArea(), 5));

		assertTrue(textArea.isWrapText());
		assertEquals(5, textArea.getPrefRowCount());
		assertEquals(FieldWidth.L.getPrefWidth(), textArea.getPrefWidth());
		assertEquals(Double.MAX_VALUE, textArea.getMaxWidth());
	}

	@Test
	void createButtonBarShouldStyleButtonsAndBar() {
		HBox buttonBar = JavaFxTestSupport.callFx(() -> {
			Button save = new Button("save");
			Button cancel = new Button("cancel");
			return FormStyleUtils.createButtonBar(save, cancel);
		});

		assertTrue(buttonBar.getStyleClass().contains("gbanking-button-bar"));
		assertEquals(2, buttonBar.getChildren().size());
		buttonBar.getChildren().forEach(node -> {
			Button button = (Button) node;
			assertTrue(button.getStyleClass().contains("gbanking-form-button"));
			assertEquals(120.0, button.getPrefWidth());
		});
	}

	@Test
	void styleFormAndTitledPaneShouldAddStyleClasses() {
		VBox panel = JavaFxTestSupport.callFx(VBox::new);
		Region titledPane = JavaFxTestSupport.callFx(Region::new);

		JavaFxTestSupport.runFx(() -> {
			FormStyleUtils.styleFormPanel(panel);
			FormStyleUtils.styleTitledPane(titledPane);
		});

		assertTrue(panel.getStyleClass().contains("gbanking-form-panel"));
		assertTrue(titledPane.getStyleClass().contains("gbanking-form-titled-pane"));
	}

	@Test
	void setEditableShouldToggleControlsAndReadonlyStyle() {
		TextField textField = JavaFxTestSupport.callFx(TextField::new);
		TextArea textArea = JavaFxTestSupport.callFx(TextArea::new);
		ComboBox<String> comboBox = JavaFxTestSupport.callFx(ComboBox::new);
		CheckBox checkBox = JavaFxTestSupport.callFx(CheckBox::new);

		JavaFxTestSupport.runFx(() -> FormStyleUtils.setEditable(false, textField, textArea, comboBox, checkBox));

		assertFalse(textField.isEditable());
		assertTrue(textField.isDisable());
		assertTrue(textField.getStyleClass().contains("gbanking-readonly-control"));
		assertFalse(textArea.isEditable());
		assertTrue(textArea.isDisable());
		assertTrue(comboBox.isDisable());
		assertTrue(checkBox.isDisable());

		JavaFxTestSupport.runFx(() -> FormStyleUtils.setEditable(true, textField, textArea, comboBox, checkBox));

		assertTrue(textField.isEditable());
		assertFalse(textField.isDisable());
		assertFalse(textField.getStyleClass().contains("gbanking-readonly-control"));
		assertTrue(textArea.isEditable());
		assertFalse(textArea.isDisable());
		assertFalse(comboBox.isDisable());
		assertFalse(checkBox.isDisable());
	}

	@Test
	void isUserEditableShouldReflectSourceRules() {
		assertTrue(FormStyleUtils.isUserEditable(null));
		assertTrue(FormStyleUtils.isUserEditable(Source.MANUELL));
		assertTrue(FormStyleUtils.isUserEditable(Source.IMPORT_INITIAL));
		assertFalse(FormStyleUtils.isUserEditable(Source.ONLINE));
		assertFalse(FormStyleUtils.isUserEditable(Source.AUTO_PRENO));
		assertTrue(FormStyleUtils.isUserEditable(Source.MANUELL_NEW));
		assertFalse(FormStyleUtils.isUserEditable(Source.ONLINE_NEW));
	}

	@Test
	void controlsShouldReturnListInSameOrder() {
		TextField first = JavaFxTestSupport.callFx(TextField::new);
		TextField second = JavaFxTestSupport.callFx(TextField::new);

		assertEquals(first, FormStyleUtils.controls(first, second).get(0));
		assertEquals(second, FormStyleUtils.controls(first, second).get(1));
	}
}
