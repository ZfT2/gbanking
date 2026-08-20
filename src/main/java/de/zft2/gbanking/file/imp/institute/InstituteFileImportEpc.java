package de.zft2.gbanking.file.imp.institute;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.gui.BaseWorker;

/**
 * https://www.europeanpaymentscouncil.eu/what-we-do/be-involved/register-participants/registers-participants-sepa-payment-schemes
 */
public class InstituteFileImportEpc extends InstituteFileImport {

	public static final String DEFAULT_FILENAME = "sct.csv";

	protected InstituteFileImportEpc(String basePath, String fileName, Charset charset, BaseWorker worker) {
		super(basePath, fileName, charset, worker);
	}

	protected InstituteFileImportEpc(String basePath, Charset charset) {
		this(basePath, null, charset, null);
	}

	@Override
	protected CSVFormat csvFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(',').setQuote('\"').setHeader().setSkipHeaderRecord(true).get();
	}

	@Override
	protected Comparator<Institute> groupComparator() {
		return Comparator.comparing(Institute::getBic);
	}

	@Override
	protected String getGroupKey(Institute institute) {
		return institute.getBic();
	}

	@Override
	protected String getGroupingMessageKey() {
		return "UI_PROGRESS_INSTITUTE_GROUP_BY_BIC";
	}

	@Override
	protected String getProcessingMessageKey() {
		return "UI_PROGRESS_INSTITUTE_PROCESS_BIC";
	}

	@Override
	protected boolean isRelevantCurrentInstitute(Institute institute) {
		return hasText(institute.getCountry()) || hasText(institute.getAddress()) || hasText(institute.getReadinessDate())
				|| hasText(institute.getSchemeLeavingDate()) || hasText(institute.getSchemeOptions());
	}

	@Override
	protected void copyImportedFields(Institute existing, Institute imported) {
		existing.setBic(imported.getBic());
		existing.setBankName(imported.getBankName());
		existing.setPlace(imported.getPlace());
		existing.setCountry(imported.getCountry());
		existing.setAddress(imported.getAddress());
		existing.setReadinessDate(imported.getReadinessDate());
		existing.setSchemeLeavingDate(imported.getSchemeLeavingDate());
		existing.setSchemeOptions(imported.getSchemeOptions());
	}

	@Override
	protected Institute mapRecord(CSVRecord csvRecord) {
		Institute institute = createImportedInstitute();
		String bic = csvRecord.get("BIC");

		if (bic == null || bic.isBlank() || !hasInstituteData(csvRecord, "BIC")) {
			return null;
		}

		institute.setBic(bic);
		institute.setBankName(csvRecord.get("ParticipantName"));
		institute.setPlace(csvRecord.get("City"));

		institute.setCountry(csvRecord.get("Country"));
		institute.setAddress(csvRecord.get("Address"));
		institute.setReadinessDate(csvRecord.get("Readiness Date"));
		institute.setSchemeLeavingDate(csvRecord.get("Scheme Leaving Date"));
		institute.setSchemeOptions(csvRecord.get("Scheme Options"));

		return institute;
	}

	@Override
	protected boolean isSameInstituteIdentity(Institute a, Institute b) {
		return (a.getCountry() != null && b.getCountry() != null) && Objects.equals(a.getBic(), b.getBic());
	}

	@Override
	protected boolean hasSameContent(Institute a, Institute b) {
		return Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getBankName(), b.getBankName()) && Objects.equals(a.getPlace(), b.getPlace())
				&& Objects.equals(a.getCountry(), b.getCountry()) && Objects.equals(a.getAddress(), b.getAddress())
				&& Objects.equals(a.getReadinessDate(), b.getReadinessDate()) && Objects.equals(a.getSchemeLeavingDate(), b.getSchemeLeavingDate())
				&& Objects.equals(a.getSchemeOptions(), b.getSchemeOptions());
	}

	static Charset getCharset(Charset charset) {
		return charset != null ? charset : StandardCharsets.UTF_8;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
