package de.zft2.gbanking.gui.panel.setting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.file.imp.FileImportSettings;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.EnvironmentOptions;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.hbci.ChipTanUsbSupport;
import de.zft2.gbanking.logging.LoggingSettings;
import de.zft2.gbanking.logging.LogLevelSetting;
import de.zft2.gbanking.service.account.AccountStatementService;
import de.zft2.gbanking.service.account.AccountStatementSettings;
import de.zft2.gbanking.service.importproperties.ImportPropertiesSynchronizationService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class SettingsDialog implements BaseMessages {

	private static final Logger log = LogManager.getLogger(SettingsDialog.class);
	private static final String FX_FONT_WEIGHT_BOLD = "-fx-font-weight: bold;";
	private final Window parentWindow;
	private final DBController dbController;
	private final Map<String, String> environmentOptions;
	private final Runnable environmentOptionsSaver;
	private final Map<Setting, Supplier<String>> valueSupplierMap = new LinkedHashMap<>();
	private final Map<String, Supplier<String>> environmentSupplierMap = new LinkedHashMap<>();

	public SettingsDialog(Window parentWindow) {
		this(parentWindow, new LinkedHashMap<>(), () -> {
		});
	}

	public SettingsDialog(Window parentWindow, Map<String, String> environmentOptions, Runnable environmentOptionsSaver) {
		this.parentWindow = parentWindow;
		this.dbController = DBController.getInstance(".");
		this.environmentOptions = environmentOptions;
		this.environmentOptionsSaver = environmentOptionsSaver;
	}

	public Stage createWindow() {
		AccountStatementSettings.ensureSettingsExist();
		ChipTanUsbSupport.ensureSettingsExist();
		LoggingSettings.ensureSettingsExist();
		log.info("Creating settings dialog.");

		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_PANEL_SETTINGS");

		List<Setting> settings = loadVisibleSettings();

		VBox content = new VBox(10);
		content.setPadding(new Insets(12));

		Label headline = new Label(getText("UI_PANEL_SETTINGS"));

		TabPane tabPane = new TabPane(createProgramSettingsTab(settings), createPatternSettingsTab(settings), createEnvironmentSettingsTab());
		GuiLayoutState.configureTabPane(tabPane, "settings.main");

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

		content.getChildren().addAll(headline, new Separator(), tabPane, buttonBar);
		VBox.setVgrow(tabPane, Priority.ALWAYS);

		dialog.setScene(new Scene(content, 980, 650));
		return dialog;
	}

	private Tab createProgramSettingsTab(List<Setting> settings) {
		return createSettingsTab(settings, false, "UI_TAB_PROGRAM_SETTINGS");
	}

	private Tab createPatternSettingsTab(List<Setting> settings) {
		return createSettingsTab(settings, true, "UI_TAB_PATTERN_SETTINGS");
	}

	private Tab createSettingsTab(List<Setting> settings, boolean patternSettings, String titleKey) {
		GridPane grid = createSettingsGrid();
		int row = 1;
		if (patternSettings) {
			Label help = new Label(getText("UI_PATTERN_SETTINGS_HELP"));
			help.setWrapText(true);
			grid.add(help, 0, row++, 3, 1);
		}
		for (Setting setting : settings) {
			if (ImportPropertiesSynchronizationService.isPatternSetting(setting.getAttribute()) == patternSettings) {
				addSettingRow(grid, setting, row++);
			}
		}
		return createTab(titleKey, grid);
	}

	private Tab createEnvironmentSettingsTab() {
		GridPane grid = createSettingsGrid();
		addEnvironmentSettingRow(grid, 1, EnvironmentOptions.DATA_DIRECTORY, "UI_LABEL_ENVIRONMENT_DATA_DIRECTORY_COMMENT",
				EnvironmentOptions.getDataDirectory(environmentOptions));
		addEnvironmentSettingRow(grid, 2, EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY, "UI_LABEL_ENVIRONMENT_IMPORT_PROPERTIES_DIRECTORY_COMMENT",
				EnvironmentOptions.getImportPropertiesDirectory(environmentOptions));
		addEnvironmentSettingRow(grid, 3, EnvironmentOptions.DEFAULT_DIR_IMPORT, "UI_LABEL_ENVIRONMENT_DEFAULT_DIR_IMPORT_COMMENT",
				EnvironmentOptions.normalizeOptionalDirectory(environmentOptions.get(EnvironmentOptions.DEFAULT_DIR_IMPORT)));
		addEnvironmentSettingRow(grid, 4, EnvironmentOptions.DEFAULT_DIR_EXPORT, "UI_LABEL_ENVIRONMENT_DEFAULT_DIR_EXPORT_COMMENT",
				EnvironmentOptions.normalizeOptionalDirectory(environmentOptions.get(EnvironmentOptions.DEFAULT_DIR_EXPORT)));
		return createTab("UI_TAB_ENVIRONMENT_SETTINGS", grid);
	}

	private GridPane createSettingsGrid() {
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
		return grid;
	}

	private Tab createTab(String titleKey, GridPane grid) {
		ScrollPane scrollPane = new ScrollPane(grid);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportWidth(900);
		scrollPane.setPrefViewportHeight(500);

		Tab tab = new Tab(getText(titleKey), scrollPane);
		tab.setClosable(false);
		return tab;
	}

	private void addHeaderRow(GridPane grid) {
		Label propertyHeader = new Label(getText("UI_LABEL_SETTING_PROPERTY"));
		Label attributeHeader = new Label(getText("UI_LABEL_SETTING_ATTRIBUTE"));
		Label valueHeader = new Label(getText("UI_LABEL_SETTING_VALUE"));

		propertyHeader.setStyle(FX_FONT_WEIGHT_BOLD);
		attributeHeader.setStyle(FX_FONT_WEIGHT_BOLD);
		valueHeader.setStyle(FX_FONT_WEIGHT_BOLD);

		grid.add(propertyHeader, 0, 0);
		grid.add(attributeHeader, 1, 0);
		grid.add(valueHeader, 2, 0);
	}

	private List<Setting> loadVisibleSettings() {
		AccountStatementSettings.ensureSettingsExist();
		ChipTanUsbSupport.ensureSettingsExist();
		FileImportSettings.ensureSettingsExist();
		LoggingSettings.ensureSettingsExist();

		List<Setting> allSettings = dbController.getAll(Setting.class);
		if (allSettings == null) {
			return new ArrayList<>();
		}

		return allSettings.stream().filter(Setting::isVisible).sorted(Comparator.comparing(Setting::getAttribute, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	private void addSettingRow(GridPane grid, Setting setting, int row) {
		String comment = AccountStatementSettings.SETTING_ENCRYPT_FILES.equals(setting.getAttribute()) ? getText("UI_LABEL_ACCOUNT_STATEMENT_FILE_ENCRYPTION")
				: setting.getComment();
		Label propertyLabel = new Label(comment != null ? comment : "");
		propertyLabel.setWrapText(true);

		String displayAttribute = ImportPropertiesSynchronizationService.getPatternDisplayAttribute(setting.getAttribute());
		Label attributeLabel = new Label(displayAttribute != null ? displayAttribute : "");
		attributeLabel.setWrapText(true);

		Node editor = createEditor(setting);

		grid.add(propertyLabel, 0, row);
		grid.add(attributeLabel, 1, row);
		grid.add(editor, 2, row);
	}

	private void addEnvironmentSettingRow(GridPane grid, int row, String optionKey, String commentKey, String value) {
		Label propertyLabel = new Label(getText(commentKey));
		propertyLabel.setWrapText(true);

		Label attributeLabel = new Label(optionKey);
		attributeLabel.setWrapText(true);

		TextField field = new TextField(value);
		field.setMaxWidth(Double.MAX_VALUE);
		environmentSupplierMap.put(optionKey, () -> field.getText());

		grid.add(propertyLabel, 0, row);
		grid.add(attributeLabel, 1, row);
		grid.add(field, 2, row);
	}

	private Node createEditor(Setting setting) {
		if (LoggingSettings.isLogLevelSetting(setting.getAttribute())) {
			return createLogLevelField(setting);
		}

		if (ChipTanUsbSupport.SETTING_READER_NAME.equals(setting.getAttribute())) {
			return createCardReaderField(setting);
		}

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

	private ComboBox<LogLevelSetting> createLogLevelField(Setting setting) {
		ComboBox<LogLevelSetting> comboBox = new ComboBox<>(FXCollections.observableArrayList(LogLevelSetting.values()));
		comboBox.setDisable(!setting.isEditable());
		comboBox.setMaxWidth(Double.MAX_VALUE);
		comboBox.setValue(LoggingSettings.resolveLogLevel(setting.getAttribute(), setting.getValue()));
		valueSupplierMap.put(setting,
				() -> comboBox.getValue() != null ? comboBox.getValue().name() : LoggingSettings.getDefaultLogLevel(setting.getAttribute()).name());
		return comboBox;
	}

	private ComboBox<String> createCardReaderField(Setting setting) {
		ComboBox<String> comboBox = new ComboBox<>();
		comboBox.setEditable(true);
		comboBox.setDisable(!setting.isEditable());
		comboBox.setMaxWidth(Double.MAX_VALUE);
		comboBox.getItems().add("");
		comboBox.getItems().addAll(ChipTanUsbSupport.getAvailableReaders());

		String value = setting.getValue() != null ? setting.getValue() : "";
		if (!value.isBlank() && !comboBox.getItems().contains(value)) {
			comboBox.getItems().add(value);
		}
		comboBox.setValue(value);

		valueSupplierMap.put(setting, () -> {
			String editorValue = comboBox.getEditor().getText();
			if (editorValue != null) {
				return editorValue.trim();
			}
			String selectedValue = comboBox.getValue();
			return selectedValue != null ? selectedValue.trim() : "";
		});
		return comboBox;
	}

	private TextField createCharField(Setting setting) {
		TextField field = new TextField(setting.getValue() != null ? setting.getValue() : "");
		field.setDisable(!setting.isEditable());
		field.setMaxWidth(120);

		field.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().length() <= 1 ? change : null));

		valueSupplierMap.put(setting, field::getText);
		return field;
	}

	private TextField createIntegerField(Setting setting) {
		TextField field = new TextField(setting.getValue() != null ? setting.getValue() : "");
		field.setDisable(!setting.isEditable());
		field.setMaxWidth(180);

		field.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("-?\\d*") ? change : null));

		valueSupplierMap.put(setting, field::getText);
		return field;
	}

	private TextField createDecimalField(Setting setting) {
		TextField field = new TextField(setting.getValue() != null ? setting.getValue() : "");
		field.setDisable(!setting.isEditable());
		field.setMaxWidth(180);

		field.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("-?\\d*([\\.,]\\d*)?") ? change : null));

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
		if (!saveProgramSettings() || !saveEnvironmentSettings()) {
			return false;
		}
		try {
			ServiceRegistry.getService(ImportPropertiesSynchronizationService.class).synchronize();
		} catch (RuntimeException exception) {
			log.error("Could not synchronize booking recognition properties", exception);
			showWarning(getText("ALERT_PATTERN_SETTINGS_SYNC_FAILED"));
			return false;
		}

		log.info("Settings saved successfully.");
		showInfo(getText("UI_INFO_SETTINGS_SAVE_SUCCESS"));
		return true;
	}

	private boolean saveProgramSettings() {
		for (Map.Entry<Setting, Supplier<String>> entry : valueSupplierMap.entrySet()) {
			Setting setting = entry.getKey();
			String newValue = entry.getValue().get();
			if (!isValid(setting, newValue)) {
				log.warn("Invalid program setting value rejected. attribute={}", setting.getAttribute());
				showWarning(getText("ALERT_SETTINGS_INVALID_VALUE", setting.getAttribute()));
				return false;
			}
		}
		if (!updateAccountStatementEncryption()) {
			return false;
		}
		for (Map.Entry<Setting, Supplier<String>> entry : valueSupplierMap.entrySet()) {
			Setting setting = entry.getKey();
			String newValue = entry.getValue().get();
			setting.setValue(newValue);
			dbController.insertOrUpdate(setting);
		}

		log.info("Saved {} program settings.", valueSupplierMap.size());
		LoggingSettings.applyLogLevels();
		return true;
	}

	private boolean updateAccountStatementEncryption() {
		for (Map.Entry<Setting, Supplier<String>> entry : valueSupplierMap.entrySet()) {
			Setting setting = entry.getKey();
			if (!AccountStatementSettings.SETTING_ENCRYPT_FILES.equals(setting.getAttribute())) {
				continue;
			}
			boolean enabled = Boolean.parseBoolean(entry.getValue().get());
			if (enabled == Boolean.parseBoolean(setting.getValue())) {
				return true;
			}
			try {
				ServiceRegistry.getService(AccountStatementService.class).updateFileEncryption(enabled);
				return true;
			} catch (RuntimeException exception) {
				log.error("Could not update account statement file encryption", exception);
				showWarning(getText("ALERT_ACCOUNT_STATEMENT_ENCRYPTION_UPDATE_FAILED"));
				return false;
			}
		}
		return true;
	}

	private boolean saveEnvironmentSettings() {
		for (Map.Entry<String, Supplier<String>> entry : environmentSupplierMap.entrySet()) {
			if (!EnvironmentOptions.isValidPath(entry.getValue().get())) {
				log.warn("Invalid environment setting value rejected. attribute={}", entry.getKey());
				showWarning(getText("ALERT_SETTINGS_INVALID_ENVIRONMENT_DIRECTORY", entry.getKey()));
				return false;
			}
		}

		environmentOptions.put(EnvironmentOptions.DATA_DIRECTORY,
				EnvironmentOptions.normalizeDataDirectory(environmentSupplierMap.get(EnvironmentOptions.DATA_DIRECTORY).get()));
		environmentOptions.put(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY,
				EnvironmentOptions.normalizeImportPropertiesDirectory(environmentSupplierMap.get(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY).get()));
		storeOptionalEnvironmentDirectory(EnvironmentOptions.DEFAULT_DIR_IMPORT);
		storeOptionalEnvironmentDirectory(EnvironmentOptions.DEFAULT_DIR_EXPORT);
		environmentOptionsSaver.run();
		log.info("Saved environment settings.");
		log.debug("Saved environment directory settings: {}",
				() -> environmentOptions.keySet().stream().filter(key -> environmentSupplierMap.containsKey(key)).sorted().toList());
		return true;
	}

	private void storeOptionalEnvironmentDirectory(String optionKey) {
		String value = EnvironmentOptions.normalizeOptionalDirectory(environmentSupplierMap.get(optionKey).get());
		if (value.isBlank()) {
			environmentOptions.remove(optionKey);
		} else {
			environmentOptions.put(optionKey, value);
		}
	}

	private boolean isValid(Setting setting, String value) {
		if (LoggingSettings.isLogLevelSetting(setting.getAttribute())) {
			return LogLevelSetting.isValid(value);
		}

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
}
