package de.zft2.gbanking.file.imp.institute;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.gui.BaseWorker;

public class InstituteFileImportDbb extends InstituteFileImport {

	public static final String DEFAULT_FILENAME = "blz-aktuell-csv-data.csv";

	protected InstituteFileImportDbb(String basePath, String fileName, Charset charset, BaseWorker worker) {
		super(basePath, fileName, charset, worker);
	}

	protected InstituteFileImportDbb(String basePath, Charset charset) {
		this(basePath, null, charset, null);
	}

	@Override
	protected CSVFormat csvFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get();
	}

	@Override
	protected Comparator<Institute> groupComparator() {
		return Comparator.comparing(Institute::getDatasetNumber);
	}

	@Override
	protected boolean isRelevantCurrentInstitute(Institute institute) {
		return institute.getDatasetNumber() != null;
	}

	@Override
	protected boolean hasImportMetadataChanged(Institute existing, Institute imported) {
		return !existing.getDatasetNumber().equalsIgnoreCase(imported.getDatasetNumber());
	}

	@Override
	protected void copyImportedFields(Institute existing, Institute imported) {
		existing.setDatasetNumber(imported.getDatasetNumber());
		existing.setBic(imported.getBic());
		existing.setBankName(imported.getBankName());
		existing.setPlace(imported.getPlace());
		existing.setFeature(imported.getFeature());
		existing.setPostcode(imported.getPostcode());
		existing.setBankNameShort(imported.getBankNameShort());
		existing.setPan(imported.getPan());
		existing.setCheckdigitMethod(imported.getCheckdigitMethod());
		existing.setFeatureChange(imported.getFeatureChange());
		existing.setBlzDeletion(imported.getBlzDeletion());
		existing.setBlzSuccession(imported.getBlzSuccession());
	}

	@Override
	protected Institute mapRecord(CSVRecord csvRecord) {
		Institute institute = createImportedInstitute();
		String blz = csvRecord.get("Bankleitzahl");

		if (blz == null || blz.isBlank() || !hasInstituteData(csvRecord, "Datensatznummer")) {
			return null;
		}

		institute.setBlz(blz);
		institute.setFeature(Integer.parseInt(csvRecord.get("Merkmal")));
		institute.setBankName(csvRecord.get("Bezeichnung"));
		institute.setPostcode(csvRecord.get("PLZ"));
		institute.setPlace(csvRecord.get("Ort"));
		institute.setBankNameShort(csvRecord.get("Kurzbezeichnung"));
		institute.setPan(csvRecord.get("PAN"));
		institute.setBic(csvRecord.get("BIC"));
		institute.setCheckdigitMethod(csvRecord.get("Prüfzifferberechnungsmethode"));
		institute.setDatasetNumber(csvRecord.get("Datensatznummer"));
		institute.setFeatureChange(csvRecord.get("Änderungskennzeichen").charAt(0));
		institute.setBlzDeletion(Integer.parseInt(csvRecord.get("Bankleitzahllöschung")));
		institute.setBlzSuccession(csvRecord.get("Nachfolge-Bankleitzahl"));

		return institute;
	}

	@Override
	protected boolean isSameInstituteIdentity(Institute a, Institute b) {
		return Objects.equals(a.getBlz(), b.getBlz()) && Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getDatasetNumber(), b.getDatasetNumber());
	}

	@Override
	protected boolean hasSameContent(Institute a, Institute b) {
		return Objects.equals(a.getBlz(), b.getBlz()) && Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getBankName(), b.getBankName())
				&& Objects.equals(a.getPlace(), b.getPlace()) && Objects.equals(a.getDatasetNumber(), b.getDatasetNumber())
				&& Objects.equals(a.getFeature(), b.getFeature()) && Objects.equals(a.getPostcode(), b.getPostcode())
				&& Objects.equals(a.getBankNameShort(), b.getBankNameShort()) && Objects.equals(a.getPan(), b.getPan())
				&& Objects.equals(a.getCheckdigitMethod(), b.getCheckdigitMethod()) && Objects.equals(a.getFeatureChange(), b.getFeatureChange())
				&& Objects.equals(a.getBlzDeletion(), b.getBlzDeletion()) && Objects.equals(a.getBlzSuccession(), b.getBlzSuccession());
	}

	static Charset getCharset(Charset charset) {
		return charset != null ? charset : StandardCharsets.ISO_8859_1;
	}

}
