package de.zft2.gbanking.gui.panel.institute;

import java.util.ArrayList;
import java.util.List;

import de.zft2.gbanking.db.dao.Institute;

public enum InstituteSource {

	DBB("DBB", "Deutsche Bundesbank", "https://www.bundesbank.de/de/aufgaben/unbarer-zahlungsverkehr/serviceangebot/bankleitzahlen"),
	DK("DK", "Deutsche Kreditwirtschaft", "https://www.fints.org/de/hersteller/bankenliste"),
	EPC("EPC", "European Payments Council",
			"https://www.europeanpaymentscouncil.eu/what-we-do/be-involved/register-participants/registers-participants-sepa-payment-schemes");

	private final String displayName;
	private final String linkText;
	private final String url;

	InstituteSource(String displayName, String linkText, String url) {
		this.displayName = displayName;
		this.linkText = linkText;
		this.url = url;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getUrl() {
		return url;
	}

	public String getLinkText() {
		return linkText;
	}

	public static List<InstituteSource> forInstitute(Institute institute) {
		List<InstituteSource> sources = new ArrayList<>(3);
		if (hasDbbData(institute)) {
			sources.add(DBB);
		}
		if (hasDkData(institute)) {
			sources.add(DK);
		}
		if (hasEpcData(institute)) {
			sources.add(EPC);
		}
		return List.copyOf(sources);
	}

	public static String displayNames(Institute institute) {
		return String.join(", ", forInstitute(institute).stream().map(source -> source.getDisplayName()).toList());
	}

	public static String countryForDisplay(Institute institute, String nonEpcCountry) {
		return hasEpcData(institute) ? institute.getCountry() : nonEpcCountry;
	}

	private static boolean hasDbbData(Institute institute) {
		return hasText(institute.getDatasetNumber()) || hasText(institute.getPostcode()) || hasText(institute.getBankNameShort())
				|| hasText(institute.getPan()) || hasText(institute.getCheckdigitMethod()) || hasText(institute.getBlzSuccession());
	}

	private static boolean hasDkData(Institute institute) {
		return institute.getImportNumber() != 0 || hasText(institute.getDataCenter()) || hasText(institute.getOrganisation())
				|| hasText(institute.getHbciDns()) || hasText(institute.getHbciIp()) || institute.getHbciVersion() != null
				|| hasText(institute.getDdv()) || hasText(institute.getPinUrl()) || hasText(institute.getVersion());
	}

	private static boolean hasEpcData(Institute institute) {
		return hasText(institute.getCountry()) || hasText(institute.getAddress()) || hasText(institute.getReadinessDate())
				|| hasText(institute.getSchemeLeavingDate()) || hasText(institute.getSchemeOptions());
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
