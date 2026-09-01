package de.zft2.gbanking.file.imp.institute;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.gui.BaseWorker;

public class InstituteFileImportAdditional extends InstituteFileImport {

	public static final String DEFAULT_FILENAME = "blz_additional_gbanking.csv";

	private static final String COLUMN_BLZ = "BLZ";
	private static final String COLUMN_BANK_NAME = "Institutsname";
	private static final String COLUMN_PLACE = "Ort";
	private static final String COLUMN_BANK_NAME_SHORT = "Kurzbezeichnung";
	private static final String COLUMN_CHECKDIGIT_METHOD = "Prüfziffermethode";
	private static final String COLUMN_BIC = "BIC";
	private static final String COLUMN_POSTCODE = "PLZ";
	private static final String COLUMN_DELETION_MARKER = "Löschmarker";
	private static final String COLUMN_BLZ_SUCCESSION = "Nachfolge-BLZ";
	private static final String COLUMN_IBAN_RULE = "IBAN-Regel";
	private static final String COLUMN_IBAN_RULE_VERSION = "IBAN-Regel-Version";

	protected InstituteFileImportAdditional(String basePath, String fileName, Charset charset, BaseWorker worker) {
		super(basePath, fileName, charset, worker);
	}

	@Override
	protected CSVFormat csvFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get();
	}

	@Override
	protected Comparator<Institute> groupComparator() {
		return Comparator.comparing((Institute institute) -> institute.getBankName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
				.thenComparing(institute -> institute.getBic(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
	}

	@Override
	protected boolean isRelevantCurrentInstitute(Institute institute) {
		return hasText(institute.getAdditionalBankNameShort()) || hasText(institute.getAdditionalCheckdigitMethod())
				|| hasText(institute.getAdditionalPostcode()) || hasText(institute.getAdditionalDeletionMarker())
				|| hasText(institute.getAdditionalBlzSuccession()) || hasText(institute.getAdditionalIbanRule())
				|| hasText(institute.getAdditionalIbanRuleVersion());
	}

	@Override
	protected Institute mapRecord(CSVRecord csvRecord) {
		String blz = value(csvRecord, COLUMN_BLZ);
		String bankName = value(csvRecord, COLUMN_BANK_NAME);
		if (blz == null || bankName == null) {
			return null;
		}

		Institute institute = createImportedInstitute();
		institute.setBlz(blz);
		institute.setBankName(bankName);
		institute.setPlace(value(csvRecord, COLUMN_PLACE));
		institute.setBic(value(csvRecord, COLUMN_BIC));
		institute.setAdditionalBankNameShort(value(csvRecord, COLUMN_BANK_NAME_SHORT));
		institute.setAdditionalCheckdigitMethod(value(csvRecord, COLUMN_CHECKDIGIT_METHOD));
		institute.setAdditionalPostcode(value(csvRecord, COLUMN_POSTCODE));
		institute.setAdditionalDeletionMarker(value(csvRecord, COLUMN_DELETION_MARKER));
		institute.setAdditionalBlzSuccession(value(csvRecord, COLUMN_BLZ_SUCCESSION));
		institute.setAdditionalIbanRule(value(csvRecord, COLUMN_IBAN_RULE));
		institute.setAdditionalIbanRuleVersion(value(csvRecord, COLUMN_IBAN_RULE_VERSION));
		return institute;
	}

	@Override
	protected boolean isSameInstituteIdentity(Institute existing, Institute imported) {
		return Objects.equals(existing.getBlz(), imported.getBlz())
				&& equalsIgnoreCase(existing.getBankName(), imported.getBankName())
				&& equalsIgnoreCase(existing.getPlace(), imported.getPlace());
	}

	@Override
	protected boolean hasSameContent(Institute existing, Institute imported) {
		return Objects.equals(existing.getBlz(), imported.getBlz()) && Objects.equals(existing.getBic(), imported.getBic())
				&& Objects.equals(existing.getBankName(), imported.getBankName()) && Objects.equals(existing.getPlace(), imported.getPlace())
				&& Objects.equals(existing.getAdditionalBankNameShort(), imported.getAdditionalBankNameShort())
				&& Objects.equals(existing.getAdditionalCheckdigitMethod(), imported.getAdditionalCheckdigitMethod())
				&& Objects.equals(existing.getAdditionalPostcode(), imported.getAdditionalPostcode())
				&& Objects.equals(existing.getAdditionalDeletionMarker(), imported.getAdditionalDeletionMarker())
				&& Objects.equals(existing.getAdditionalBlzSuccession(), imported.getAdditionalBlzSuccession())
				&& Objects.equals(existing.getAdditionalIbanRule(), imported.getAdditionalIbanRule())
				&& Objects.equals(existing.getAdditionalIbanRuleVersion(), imported.getAdditionalIbanRuleVersion());
	}

	@Override
	protected void copyImportedFields(Institute existing, Institute imported) {
		existing.setBlz(imported.getBlz());
		existing.setBic(imported.getBic());
		existing.setBankName(imported.getBankName());
		existing.setPlace(imported.getPlace());
		existing.setAdditionalBankNameShort(imported.getAdditionalBankNameShort());
		existing.setAdditionalCheckdigitMethod(imported.getAdditionalCheckdigitMethod());
		existing.setAdditionalPostcode(imported.getAdditionalPostcode());
		existing.setAdditionalDeletionMarker(imported.getAdditionalDeletionMarker());
		existing.setAdditionalBlzSuccession(imported.getAdditionalBlzSuccession());
		existing.setAdditionalIbanRule(imported.getAdditionalIbanRule());
		existing.setAdditionalIbanRuleVersion(imported.getAdditionalIbanRuleVersion());
	}

	private static String value(CSVRecord csvRecord, String column) {
		if (!csvRecord.isMapped(column) || !csvRecord.isSet(column)) {
			return null;
		}
		String value = csvRecord.get(column).trim();
		return value.isEmpty() ? null : value;
	}

	private static boolean equalsIgnoreCase(String first, String second) {
		return first == null ? second == null : second != null && first.equalsIgnoreCase(second);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	static Charset getCharset(Charset charset) {
		return charset != null ? charset : StandardCharsets.ISO_8859_1;
	}
}
