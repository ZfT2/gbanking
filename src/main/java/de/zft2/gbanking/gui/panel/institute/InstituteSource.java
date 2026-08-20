package de.zft2.gbanking.gui.panel.institute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.zft2.gbanking.db.dao.Institute;

public enum InstituteSource {

	DBB("DBB", "Deutsche Bundesbank", "https://www.bundesbank.de/de/aufgaben/unbarer-zahlungsverkehr/serviceangebot/bankleitzahlen"),
	DBB_REACHABLE("DBB Reachable", "Deutsche Bundesbank",
			"https://www.bundesbank.de/de/startseite/verzeichnis-der-erreichbaren-zahlungsdienstleister-list-of-reachable-payment-service-providers-602880"),
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
		List<InstituteSource> sources = new ArrayList<>(4);
		if (hasDbbData(institute)) {
			sources.add(DBB);
		}
		if (hasDbbReachableData(institute)) {
			sources.add(DBB_REACHABLE);
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
		if (hasEpcData(institute)) {
			return institute.getCountry();
		}
		String reachableCountry = reachableCountryCode(institute);
		return reachableCountry != null ? reachableCountry : nonEpcCountry;
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

	private static boolean hasDbbReachableData(Institute institute) {
		return institute.getServiceSct() != null || institute.getServiceCor() != null || institute.getServiceCor1() != null
				|| institute.getServiceB2b() != null || institute.getServiceScc() != null;
	}

	private static boolean hasEpcData(Institute institute) {
		return hasText(institute.getCountry()) || hasText(institute.getAddress()) || hasText(institute.getReadinessDate())
				|| hasText(institute.getSchemeLeavingDate()) || hasText(institute.getSchemeOptions());
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String reachableCountryCode(Institute institute) {
		String bic = institute.getBic();
		return hasDbbReachableData(institute) && bic != null && bic.length() >= 6 ? bic.substring(4, 6).toUpperCase(Locale.ROOT) : null;
	}
}
