package de.zft2.gbanking.gui.panel.institute;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessagesBean;
import de.zft2.gbanking.db.dao.Institute;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InstituteDetailPanel extends VBox implements BaseMessagesBean {

	private static final Logger log = LogManager.getLogger(InstituteDetailPanel.class);
	private static final String UI_DETAILS = "UI_PANEL_INSTITUTE_DETAILS";
	private static final String UI_SOURCE_LINK = "UI_LABEL_SOURCE_LINK";
	private static final String EMPTY_VALUE = "\u2013";

	private final TitledPane titledPane = new TitledPane();
	private final TabPane tabs = new TabPane();

	public InstituteDetailPanel() {
		titledPane.setText(getText(UI_DETAILS));
		titledPane.setCollapsible(false);
		titledPane.setContent(tabs);
		titledPane.setMaxWidth(Double.MAX_VALUE);
		tabs.setPrefHeight(215);
		getChildren().setAll(titledPane);
		setFillWidth(true);
		clear();
	}

	public void clear() {
		titledPane.setText(getText(UI_DETAILS));
		tabs.getTabs().setAll(createTab(getText("UI_TAB_INSTITUTE_GENERAL"), new GridPane()));
	}

	public void showInstitute(Institute institute) {
		titledPane.setText(getText(UI_DETAILS) + " - " + text(institute.getBankName()));
		Tab general = createTab(getText("UI_TAB_INSTITUTE_GENERAL"), createGeneralGrid(institute));
		Tab dbb = createTab("DBB", createDbbGrid(institute));
		Tab dbbReachable = createTab("DBB Reachable", createDbbReachableGrid(institute));
		Tab dk = createTab("DK", createDkGrid(institute));
		Tab epc = createTab("EPC", createEpcGrid(institute));
		Tab additional = createTab("Additional", createAdditionalGrid(institute));
		List<InstituteSource> sources = InstituteSource.forInstitute(institute);
		dbb.setDisable(!sources.contains(InstituteSource.DBB));
		dbbReachable.setDisable(!sources.contains(InstituteSource.DBB_REACHABLE));
		dk.setDisable(!sources.contains(InstituteSource.DK));
		epc.setDisable(!sources.contains(InstituteSource.EPC));
		additional.setDisable(!sources.contains(InstituteSource.ADDITIONAL));
		tabs.getTabs().setAll(general, dbb, dbbReachable, dk, epc, additional);
	}

	private GridPane createGeneralGrid(Institute institute) {
		GridPane grid = createGrid();
		add(grid, "UI_LABEL_BANK", institute.getBankName(), 0, 0);
		add(grid, "UI_LABEL_BANK_NAME_SHORT", firstNonBlank(institute.getBankNameShort(), institute.getAdditionalBankNameShort()), 1, 0);
		add(grid, "UI_LABEL_PLACE", institute.getPlace(), 2, 0);
		add(grid, "UI_LABEL_COUNTRY", displayCountry(institute), 3, 0);
		add(grid, "UI_LABEL_POSTCODE", firstNonBlank(institute.getPostcode(), institute.getAdditionalPostcode()), 0, 1);
		add(grid, "UI_LABEL_BLZ", institute.getBlz(), 1, 1);
		add(grid, "UI_LABEL_BIC", institute.getBic(), 2, 1);
		add(grid, "UI_LABEL_INSTITUTE_STATUS", institute.getStateType(), 3, 1);
		add(grid, "UI_LABEL_IMPORT_FILE", institute.getImportFileName(), 0, 2);
		addNode(grid, "UI_LABEL_SOURCE", createSourceLinks(institute), 1, 2);
		add(grid, "UI_LABEL_UPDATED_AT", institute.getUpdatedAt(), 2, 2);
		return grid;
	}

	private String displayCountry(Institute institute) {
		return InstituteSource.countryForDisplay(institute, getText("UI_VALUE_COUNTRY_GERMANY"));
	}

	private GridPane createDbbGrid(Institute institute) {
		GridPane grid = createGrid();
		add(grid, "UI_LABEL_DATASET_NUMBER", institute.getDatasetNumber(), 0, 0);
		add(grid, "UI_LABEL_FEATURE", institute.getFeature(), 1, 0);
		add(grid, "UI_LABEL_PAN", institute.getPan(), 2, 0);
		add(grid, "UI_LABEL_CHECKDIGIT_METHOD", institute.getCheckdigitMethod(), 3, 0);
		add(grid, "UI_LABEL_FEATURE_CHANGE", institute.getFeatureChange() == 0 ? null : institute.getFeatureChange(), 0, 1);
		add(grid, "UI_LABEL_BLZ_DELETION", institute.getBlzDeletion(), 1, 1);
		add(grid, "UI_LABEL_BLZ_SUCCESSION", institute.getBlzSuccession(), 2, 1);
		addNode(grid, UI_SOURCE_LINK, createSourceReference(InstituteSource.DBB), 3, 1);
		return grid;
	}

	private GridPane createDkGrid(Institute institute) {
		GridPane grid = createGrid();
		add(grid, "UI_LABEL_IMPORT_NUMBER", institute.getImportNumber(), 0, 0);
		add(grid, "UI_LABEL_DATA_CENTER", institute.getDataCenter(), 1, 0);
		add(grid, "UI_LABEL_ORGANISATION", institute.getOrganisation(), 2, 0);
		add(grid, "UI_LABEL_HBCI_DNS", institute.getHbciDns(), 3, 0);
		add(grid, "UI_LABEL_HBCI_IP", institute.getHbciIp(), 0, 1);
		add(grid, "UI_LABEL_HBCI_VERSION", institute.getHbciVersion(), 1, 1);
		add(grid, "UI_LABEL_DDV", institute.getDdv(), 2, 1);
		add(grid, "UI_LABEL_PIN_URL", institute.getPinUrl(), 3, 1);
		add(grid, "UI_LABEL_VERSION", institute.getVersion(), 0, 2);
		add(grid, "UI_LABEL_LAST_CHANGED", institute.getLastChanged(), 1, 2);
		add(grid, "UI_LABEL_RDH_1_TO_5", formatRdh(institute, 0, 5), 2, 2);
		add(grid, "UI_LABEL_RDH_6_TO_10", formatRdh(institute, 5, 10), 3, 2);
		addNode(grid, UI_SOURCE_LINK, createSourceReference(InstituteSource.DK), 0, 3);
		return grid;
	}

	private GridPane createDbbReachableGrid(Institute institute) {
		GridPane grid = createGrid();
		add(grid, "UI_LABEL_SERVICE_SCT", institute.getServiceSct(), 0, 0);
		add(grid, "UI_LABEL_SERVICE_COR", institute.getServiceCor(), 1, 0);
		add(grid, "UI_LABEL_SERVICE_COR1", institute.getServiceCor1(), 2, 0);
		add(grid, "UI_LABEL_SERVICE_B2B", institute.getServiceB2b(), 3, 0);
		add(grid, "UI_LABEL_SERVICE_SCC", institute.getServiceScc(), 0, 1);
		addNode(grid, UI_SOURCE_LINK, createSourceReference(InstituteSource.DBB_REACHABLE), 1, 1);
		return grid;
	}

	private GridPane createEpcGrid(Institute institute) {
		GridPane grid = createGrid();
		add(grid, "UI_LABEL_COUNTRY", institute.getCountry(), 0, 0);
		add(grid, "UI_LABEL_ADDRESS", institute.getAddress(), 1, 0);
		add(grid, "UI_LABEL_READINESS_DATE", institute.getReadinessDate(), 2, 0);
		add(grid, "UI_LABEL_SCHEME_LEAVING_DATE", institute.getSchemeLeavingDate(), 3, 0);
		add(grid, "UI_LABEL_SCHEME_OPTIONS", institute.getSchemeOptions(), 0, 1);
		addNode(grid, UI_SOURCE_LINK, createSourceReference(InstituteSource.EPC), 1, 1);
		return grid;
	}

	private GridPane createAdditionalGrid(Institute institute) {
		GridPane grid = createGrid();
		add(grid, "UI_LABEL_BANK_NAME_SHORT", institute.getAdditionalBankNameShort(), 0, 0);
		add(grid, "UI_LABEL_CHECKDIGIT_METHOD", institute.getAdditionalCheckdigitMethod(), 1, 0);
		add(grid, "UI_LABEL_POSTCODE", institute.getAdditionalPostcode(), 2, 0);
		add(grid, "UI_LABEL_DELETION_MARKER", institute.getAdditionalDeletionMarker(), 3, 0);
		add(grid, "UI_LABEL_BLZ_SUCCESSION", institute.getAdditionalBlzSuccession(), 0, 1);
		add(grid, "UI_LABEL_IBAN_RULE", institute.getAdditionalIbanRule(), 1, 1);
		add(grid, "UI_LABEL_IBAN_RULE_VERSION", institute.getAdditionalIbanRuleVersion(), 2, 1);
		addNode(grid, "UI_LABEL_SOURCE", createSourceReference(InstituteSource.ADDITIONAL), 3, 1);
		return grid;
	}

	private HBox createSourceLinks(Institute institute) {
		HBox links = new HBox(10);
		for (InstituteSource source : InstituteSource.forInstitute(institute)) {
			links.getChildren().add(createSourceReference(source));
		}
		if (links.getChildren().isEmpty()) {
			links.getChildren().add(new Label(EMPTY_VALUE));
		}
		return links;
	}

	private Node createSourceReference(InstituteSource source) {
		if (source.getUrl() == null) {
			return new Label(source.getLinkText());
		}
		Hyperlink link = new Hyperlink(source.getLinkText());
		link.setOnAction(event -> openSource(source));
		return link;
	}

	private void openSource(InstituteSource source) {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			log.warn("Opening source links is not supported by this system: {}", source.getUrl());
			return;
		}
		try {
			Desktop.getDesktop().browse(URI.create(source.getUrl()));
		} catch (IOException | IllegalArgumentException e) {
			log.error("Could not open institute source link {}", source.getUrl(), e);
		}
	}

	private static Tab createTab(String title, Node content) {
		Tab tab = new Tab(title, content);
		tab.setClosable(false);
		return tab;
	}

	private static GridPane createGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(18);
		grid.setVgap(6);
		grid.setPadding(new Insets(10));
		for (int column = 0; column < 4; column++) {
			ColumnConstraints constraints = new ColumnConstraints();
			constraints.setPercentWidth(25);
			constraints.setHgrow(Priority.ALWAYS);
			grid.getColumnConstraints().add(constraints);
		}
		return grid;
	}

	private void add(GridPane grid, String key, Object value, int column, int row) {
		addNode(grid, key, new Label(text(value)), column, row);
	}

	private void addNode(GridPane grid, String key, Node value, int column, int row) {
		VBox field = new VBox(2, createFieldLabel(getText(key)), value);
		field.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(field, Priority.ALWAYS);
		grid.add(field, column, row);
	}

	private static Label createFieldLabel(String text) {
		Label label = new Label(text);
		label.setStyle("-fx-font-weight: bold;");
		return label;
	}

	private static String text(Object value) {
		return value == null || value.toString().isBlank() ? EMPTY_VALUE : value.toString();
	}

	private static String firstNonBlank(String preferred, String fallback) {
		return preferred == null || preferred.isBlank() ? fallback : preferred;
	}

	private static String formatRdh(Institute institute, int fromIndex, int toIndex) {
		Boolean[] values = { institute.getRdh1(), institute.getRdh2(), institute.getRdh3(), institute.getRdh4(), institute.getRdh5(),
				institute.getRdh6(), institute.getRdh7(), institute.getRdh8(), institute.getRdh9(), institute.getRdh10() };
		StringBuilder details = new StringBuilder();
		for (int index = fromIndex; index < toIndex; index++) {
			if (!details.isEmpty()) {
				details.append(", ");
			}
			details.append(index + 1).append('=').append(values[index] == null ? EMPTY_VALUE : values[index]);
		}
		return details.toString();
	}
}
