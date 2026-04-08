package de.gbanking.gui.panel.setting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import de.gbanking.db.DbExecutor;
import de.gbanking.db.dao.Setting;
import de.gbanking.db.dao.enu.DataType;
import de.gbanking.gui.dialog.DialogWindowSupport;
import de.gbanking.messages.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class SettingsDialog {

	private final Window parentWindow;
	private final DbExecutor dbExecutor;
	private final Map<Setting, Supplier<String>> valueSupplierMap = new LinkedHashMap<>();

	public SettingsDialog(Window parentWindow) {
		this.parentWindow = parentWindow;
		this.dbExecutor = DbExecutor.getInstance();
	}

	public Stage createWindow() {
		Stage dialog = new Stage();
		dialog.initOwner(parentWindow);
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(getText("UI_PANEL_SETTINGS"));

		List<Setting> settings = loadVisibleSettings();

		VBox content = new VBox(10);
		content.setPadding(new Insets(12));

		Label headline = new Label(getText("UI_PANEL_SETTINGS"));

		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(8);
		grid.setPadding(new Insets(4, 0, 4, 0));

		ColumnConstraints propertyColumn = new ColumnConstraints();
		propertyColumn.setMinWidth(260);
		propertyColumn.setPrefWidth(320);

		ColumnConstraints attributeColumn = new ColumnConstraints();
		attributeColumn.setMinWidth(160);
		attributeColumn.setPrefWidth(200);

		ColumnConstraints valueColumn = new ColumnConstraints();
		valueColumn.setMinWidth(240);
		valueColumn.setHgrow(Priority.ALWAYS);

		grid.getColumnConstraints().addAll(propertyColumn, attributeColumn, valueColumn);

		addHeaderRow(grid);

		int row = 1;
		for (Setting setting : settings) {
			addSettingRow(grid, setting, row++);
		}

		ScrollPane scrollPane = new ScrollPane(grid);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportWidth(900);
		scrollPane.setPrefViewportHeight(500);

		Button saveButton = new Button(getText("UI_BUTTON_SETTINGS_SAVE"));
		Button closeButton = new Button(getText("UI_BUTTON_SETTINGS_CLOSE"));

		saveButton.setOnAction(event -> {
			if (saveSettings()) {
				dialog.close();
			}
		});
		closeButton.setOnAction(event -> dialog.close());

		HBox buttonBar = new HBox(10, saveButton, closeButton);
		buttonBar.setAlignment(Pos.CENTER_RIGHT);

		content.getChildren().addAll(headline, new Separator(), scrollPane, buttonBar);

		dialog.setScene(new Scene(content, 980, 650));
		return dialog;
	}

	private void addHeaderRow(GridPane grid) {
		Label propertyHeader = new Label(getText("UI_LABEL_SETTING_PROPERTY"));
		Label attributeHeader = new Label(getText("UI_LABEL_SETTING_ATTRIBUTE"));
		Label valueHeader = new Label(getText("UI_LABEL_SETTING_VALUE"));

		propertyHeader.setStyle("-fx-font-weight: bold;");
		attributeHeader.setStyle("-fx-font-weight: bold;");
		valueHeader.setStyle("-fx-font-weight: bold;");

		grid.add(propertyHeader, 0, 0);
		grid.add(attributeHeader, 1, 0);
		grid.add(valueHeader, 2, 0);
	}

	private List<Setting> loadVisibleSettings() {
		List<Setting> allSettings = dbExecutor.getAll(Setting.class);
		if (allSettings == null) {
			return new ArrayList<>();
		}

		return allSettings.stream().filter(Setting::isVisible).sorted(Comparator.comparing(Setting::getAttribute, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());
	}

	private void addSettingRow(GridPane grid, Setting setting, int row) {
		Label propertyLabel = new Label(setting.getComment() != null ? setting.getComment() : "");
		propertyLabel.setWrapText(true);

		Label attributeLabel = new Label(setting.getAttribute() != null ? setting.getAttribute() : "");
		attributeLabel.setWrapText(true);

		Node editor = createEditor(setting);

		grid.add(propertyLabel, 0, row);
		grid.add(attributeLabel, 1, row);
		grid.add(editor, 2, row);
	}

	private Node createEditor(Setting setting) {
		DataType dataType = setting.getDataType();
		if (dataType == null) {
			return createStringField(setting);
		}

		return switch (dataType) {
		case STRING -> createStringField(setting);
		case CHAR -> createCharField(setting);
		case INT -> createIntegerField(setting);
		case FLOAT, DOUBLE, BIGDECIMAL -> createDecimalField(setting);
		case CALENDAR -> createDateField(setting);
		case ENUM -> createStringField(setting);
		case BOOLEAN -> createBooleanField(setting);
		};
	}

	private TextField createStringField(Setting setting) {
		TextField field = new TextField(setting.getValue() != null ? setting.getValue() : "");
		field.setDisable(!setting.isEditable());
		field.setMaxWidth(Double.MAX_VALUE);
		valueSupplierMap.put(setting, field::getText);
		return field;
	}

	private TextField createCharField(Setting setting) {
		TextField field = new TextField(setting.getValue() != null ? setting.getValue() : "");
		field.setDisable(!setting.isEditable());
		field.setMaxWidth(120);

		field.setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().length() <= 1 ? change : null));

		valueSupplierMap.put(setting, field::getText);
		return field;
	}

	private TextField createIntegerField(Setting setting) {
		TextField field = new TextField(setting.getValue() != null ? setting.getValue() : "");
		field.setDisable(!setting.isEditable());
		field.setMaxWidth(180);

		field.setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().matches("-?\\d*") ? change : null));

		valueSupplierMap.put(setting, field::getText);
		return field;
	}

	private TextField createDecimalField(Setting setting) {
		TextField field = new TextField(setting.getValue() != null ? setting.getValue() : "");
		field.setDisable(!setting.isEditable());
		field.setMaxWidth(180);

		field.setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().matches("-?\\d*([\\.,]\\d*)?") ? change : null));

		valueSupplierMap.put(setting, field::getText);
		return field;
	}

	private DatePicker createDateField(Setting setting) {
		DatePicker datePicker = new DatePicker();
		datePicker.setDisable(!setting.isEditable());

		if (setting.getValue() != null && !setting.getValue().isBlank()) {
			try {
				datePicker.setValue(LocalDate.parse(setting.getValue()));
			} catch (DateTimeParseException e) {
				// Wert bleibt leer
			}
		}

		valueSupplierMap.put(setting, () -> datePicker.getValue() != null ? datePicker.getValue().toString() : "");
		return datePicker;
	}

	private HBox createBooleanField(Setting setting) {
		RadioButton trueButton = new RadioButton(getText("UI_LABEL_BOOLEAN_TRUE"));
		RadioButton falseButton = new RadioButton(getText("UI_LABEL_BOOLEAN_FALSE"));

		ToggleGroup group = new ToggleGroup();
		trueButton.setToggleGroup(group);
		falseButton.setToggleGroup(group);

		boolean selectedValue = Boolean.parseBoolean(setting.getValue());
		if (selectedValue) {
			trueButton.setSelected(true);
		} else {
			falseButton.setSelected(true);
		}

		trueButton.setDisable(!setting.isEditable());
		falseButton.setDisable(!setting.isEditable());

		valueSupplierMap.put(setting, () -> Boolean.toString(trueButton.isSelected()));

		HBox box = new HBox(12, trueButton, falseButton);
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	private boolean saveSettings() {
		for (Map.Entry<Setting, Supplier<String>> entry : valueSupplierMap.entrySet()) {
			Setting setting = entry.getKey();
			String newValue = entry.getValue().get();

			if (!isValid(setting, newValue)) {
				showWarning(getFormattedText("ALERT_SETTINGS_INVALID_VALUE", setting.getAttribute()));
				return false;
			}

			setting.setValue(newValue);
			dbExecutor.insertOrUpdate(setting);
		}

		showInfo(getText("UI_INFO_SETTINGS_SAVE_SUCCESS"));
		return true;
	}

	private boolean isValid(Setting setting, String value) {
		if (value == null || value.isBlank()) {
			return true;
		}

		try {
			switch (setting.getDataType()) {
			case INT -> Integer.parseInt(value);
			case FLOAT -> Float.parseFloat(normalizeDecimal(value));
			case DOUBLE -> Double.parseDouble(normalizeDecimal(value));
			case BIGDECIMAL -> new BigDecimal(normalizeDecimal(value));
			case CALENDAR -> LocalDate.parse(value);
			case CHAR -> {
				if (value.length() > 1) {
					return false;
				}
			}
			case STRING, ENUM, BOOLEAN -> {
				// keine zusätzliche Validierung
			}
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private String normalizeDecimal(String value) {
		return value.replace(',', '.');
	}

	private void showWarning(String text) {
		DialogWindowSupport.showAlert(parentWindow, javafx.scene.control.Alert.AlertType.WARNING, text);
	}

	private void showInfo(String text) {
		DialogWindowSupport.showAlert(parentWindow, javafx.scene.control.Alert.AlertType.INFORMATION, text);
	}

	private String getText(String key) {
		return Messages.getInstance().getMessage(key);
	}

	private String getFormattedText(String key, Object... params) {
		return Messages.getInstance().getFormattedMessage(key, params);
	}
}
