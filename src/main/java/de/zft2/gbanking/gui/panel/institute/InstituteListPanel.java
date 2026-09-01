package de.zft2.gbanking.gui.panel.institute;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.overview.InstituteOverviewPanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class InstituteListPanel extends AbstractFilterableTablePanel<Institute> {

	private final InstituteOverviewPanel parentPanel;

	public InstituteListPanel(InstituteOverviewPanel parentPanel) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parentPanel;
		setColumns(createColumns());
		configureTableLayout("institutes");
		onSelection(institute -> parentPanel.getDetailPanel().showInstitute(institute));
		reload();
	}

	private List<TableColumn<Institute, ?>> createColumns() {
		TableColumn<Institute, String> name = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), institute -> institute.getBankName(), 220, 300);
		TableColumn<Institute, String> place = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_PLACE"), institute -> institute.getPlace(), 160);
		TableColumn<Institute, String> country = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_COUNTRY"),
				institute -> displayCountry(institute), 90);
		TableColumn<Institute, String> blz = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_BLZ"), institute -> institute.getBlz(), 95);
		TableColumn<Institute, String> bic = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_BIC"), institute -> institute.getBic(), 115);
		TableColumn<Institute, String> validFrom = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_VALID_FROM"),
				institute -> institute.getReadinessDate(), 90);
		TableColumn<Institute, String> validUntil = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_VALID_UNTIL"),
				institute -> institute.getSchemeLeavingDate(), 90);
		TableColumn<Institute, LocalDate> sourceDataAsOf = TableColumnFactory.createCalendarDateColumn(getText("UI_TABLE_SOURCE_DATA_AS_OF"),
				institute -> institute.getLastChanged(), 100);
		TableColumn<Institute, LocalDate> dataAsOf = TableColumnFactory.createCalendarDateColumn(getText("UI_TABLE_DATA_AS_OF"),
				institute -> institute.getUpdatedAt(), 90);
		TableColumn<Institute, String> source = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_SOURCE"),
				institute -> InstituteSource.displayNames(institute), 110);
		return List.of(name, place, country, blz, bic, validFrom, validUntil, sourceDataAsOf, dataAsOf, source);
	}

	@Override
	protected boolean matchesFilter(Institute institute, String filter) {
		return matchesAny(filter, institute.getBankName(), institute.getBankNameShort(), institute.getPlace(), institute.getPostcode(),
				institute.getBlz(), institute.getBic(), displayCountry(institute), institute.getReadinessDate(), institute.getSchemeLeavingDate(),
				institute.getAdditionalBankNameShort(), institute.getAdditionalPostcode(), institute.getAdditionalCheckdigitMethod(),
				institute.getAdditionalDeletionMarker(), institute.getAdditionalBlzSuccession(), institute.getAdditionalIbanRule(),
				institute.getAdditionalIbanRuleVersion(),
				institute.getLastChanged() != null ? institute.getLastChanged().toString() : null,
				institute.getUpdatedAt() != null ? institute.getUpdatedAt().toString() : null, InstituteSource.displayNames(institute));
	}

	private String displayCountry(Institute institute) {
		return InstituteSource.countryForDisplay(institute, getText("UI_VALUE_COUNTRY_GERMANY"));
	}

	public void reload() {
		List<Institute> institutes = dbController.getAll(Institute.class);
		institutes.sort(Comparator.comparing((Institute institute) -> institute.getBankName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
				.thenComparing(institute -> institute.getBlz(), Comparator.nullsLast(Comparator.naturalOrder())));
		replaceItems(institutes);
		setPanelTitle(getText("UI_PANEL_INSTITUTES_LIST") + " (" + institutes.size() + ")");
		parentPanel.getDetailPanel().clear();
	}
}
