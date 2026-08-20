package de.zft2.gbanking.file.imp.institute;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.gui.BaseWorker;

public class InstituteFileImportDbbReachable extends InstituteFileImport {

	public static final String DEFAULT_FILENAME = "verzeichnis-erreichbare-zahlungsdienstleister-data.csv";

	protected InstituteFileImportDbbReachable(String basePath, String fileName, Charset charset, BaseWorker worker) {
		super(basePath, fileName, charset, worker);
	}

	protected InstituteFileImportDbbReachable(String basePath, Charset charset) {
		this(basePath, null, charset, null);
	}

	@Override
	protected CSVFormat csvFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get();
	}

	@Override
	protected Comparator<Institute> groupComparator() {
		return Comparator.comparing(Institute::getBic, String.CASE_INSENSITIVE_ORDER);
	}

	@Override
	protected boolean isRelevantCurrentInstitute(Institute institute) {
		return institute.getServiceSct() != null || institute.getServiceCor() != null || institute.getServiceCor1() != null
				|| institute.getServiceB2b() != null || institute.getServiceScc() != null;
	}

	@Override
	protected void copyImportedFields(Institute existing, Institute imported) {
		existing.setBic(imported.getBic());
		existing.setBankName(imported.getBankName());
		existing.setServiceSct(imported.getServiceSct());
		existing.setServiceCor(imported.getServiceCor());
		existing.setServiceCor1(imported.getServiceCor1());
		existing.setServiceB2b(imported.getServiceB2b());
		existing.setServiceScc(imported.getServiceScc());
	}

	@Override
	protected Institute mapRecord(CSVRecord csvRecord) {
		Institute institute = createImportedInstitute();
		String bic = csvRecord.get("BIC");

		if (bic == null || bic.isBlank() || !hasInstituteData(csvRecord, "BIC")) {
			return null;
		}

		institute.setBic(bic.trim());
		institute.setBankName(csvRecord.get("Name").trim());
		institute.setServiceSct(Integer.parseInt(csvRecord.get("SERVICE SCT")));
		institute.setServiceCor(Integer.parseInt(csvRecord.get("SERVICE COR")));
		institute.setServiceCor1(Integer.parseInt(csvRecord.get("SERVICE COR1")));
		institute.setServiceB2b(Integer.parseInt(csvRecord.get("SERVICE B2B")));
		institute.setServiceScc(Integer.parseInt(csvRecord.get("SERVICE SCC")));

		return institute;
	}

	@Override
	protected boolean isSameInstituteIdentity(Institute a, Institute b) {
		return a.getBic() != null && b.getBic() != null && a.getBic().equalsIgnoreCase(b.getBic());
	}

	@Override
	protected boolean hasSameContent(Institute a, Institute b) {
		return Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getBankName(), b.getBankName())
				&& Objects.equals(a.getServiceSct(), b.getServiceSct()) && Objects.equals(a.getServiceCor(), b.getServiceCor())
				&& Objects.equals(a.getServiceCor1(), b.getServiceCor1()) && Objects.equals(a.getServiceB2b(), b.getServiceB2b())
				&& Objects.equals(a.getServiceScc(), b.getServiceScc());
	}

	static Charset getCharset(Charset charset) {
		return charset != null ? charset : StandardCharsets.UTF_8;
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
	protected int getLinesToSkip() {
		return 2;
	}
}
