package de.zft2.gbanking.file.imp.institute;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteValidityDateType;
import de.zft2.gbanking.gui.BaseWorker;

public class InstituteFileImportAdditional extends InstituteFileImport {

	public static final String DEFAULT_FILENAME = "blz_additional_gbanking.csv";
	private static final Logger log = LogManager.getLogger(InstituteFileImportAdditional.class);
	private static final Pattern HISTORICAL_FILE_NAME = Pattern.compile("^blz(\\d{2})(0[1-9]|1[0-2])\\.txt$",
			Pattern.CASE_INSENSITIVE);
	private static final DateTimeFormatter SOURCE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu");
	private static final String METADATA_MARKER = "********";

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
	private static final String[] HISTORICAL_COLUMNS = {
			COLUMN_BLZ, COLUMN_BANK_NAME, COLUMN_PLACE, COLUMN_BANK_NAME_SHORT, COLUMN_CHECKDIGIT_METHOD, COLUMN_BIC,
			COLUMN_POSTCODE, COLUMN_DELETION_MARKER, COLUMN_BLZ_SUCCESSION, COLUMN_IBAN_RULE, COLUMN_IBAN_RULE_VERSION
	};

	protected InstituteFileImportAdditional(String basePath, String fileName, Charset charset, BaseWorker worker) {
		super(basePath, fileName, charset, worker);
	}

	@Override
	protected CSVFormat csvFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get();
	}

	@Override
	protected CSVFormat csvFormat(Path file) {
		if (isHistoricalFile(file)) {
			return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(HISTORICAL_COLUMNS).get();
		}
		return super.csvFormat(file);
	}

	@Override
	protected List<Path> findSnapshotFiles(Path importDirectory) throws IOException {
		if (Files.isDirectory(importDirectory)) {
			try (var files = Files.list(importDirectory)) {
				List<Path> historicalFiles = files.filter(Files::isRegularFile)
						.filter(this::isHistoricalFile)
						.sorted(Comparator.comparing(this::getFileMonth).thenComparing(path -> path.getFileName().toString()))
						.toList();
				if (!historicalFiles.isEmpty()) {
					return historicalFiles;
				}
			}
		}
		return super.findSnapshotFiles(importDirectory);
	}

	@Override
	protected int getPreambleLinesToSkip(Path file) throws IOException {
		if (!isHistoricalFile(file)) {
			return super.getPreambleLinesToSkip(file);
		}
		try (var reader = Files.newBufferedReader(file, charset)) {
			String firstLine = reader.readLine();
			return firstLine != null && firstLine.startsWith(METADATA_MARKER + ";") ? 1 : 0;
		}
	}

	@Override
	protected Snapshot getSnapshot(Path file) {
		if (!isHistoricalFile(file)) {
			return super.getSnapshot(file);
		}
		try (var reader = Files.newBufferedReader(file, charset)) {
			String firstLine = reader.readLine();
			if (firstLine != null && firstLine.startsWith(METADATA_MARKER + ";")) {
				String[] header = firstLine.split(";", -1);
				if (header.length >= 3) {
					return new Snapshot(LocalDate.parse(header[2], SOURCE_DATE_FORMAT), InstituteValidityDateType.SOURCE_DATE);
				}
			}
		} catch (IOException | DateTimeParseException exception) {
			log.warn("Could not parse Additional validity date from {}", file.getFileName(), exception);
		}
		return new Snapshot(getFileMonth(file), InstituteValidityDateType.FILE_MONTH);
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
		if (blz == null || bankName == null || METADATA_MARKER.equals(blz)) {
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

	private boolean isHistoricalFile(Path file) {
		Path fileName = file.getFileName();
		return fileName != null && HISTORICAL_FILE_NAME.matcher(fileName.toString()).matches();
	}

	private LocalDate getFileMonth(Path file) {
		Path fileName = file.getFileName();
		if (fileName == null) {
			return super.getSnapshot(file).date();
		}
		Matcher matcher = HISTORICAL_FILE_NAME.matcher(fileName.toString());
		if (matcher.matches()) {
			try {
				return LocalDate.of(2000 + Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), 1);
			} catch (DateTimeException exception) {
				log.warn("Could not parse Additional snapshot month from {}", fileName, exception);
			}
		}
		return super.getSnapshot(file).date();
	}
}
