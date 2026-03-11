package de.gbanking.fileimport.institute;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.DBController;
import de.gbanking.db.dao.Institute;
import de.gbanking.db.dao.enu.InstituteStatus;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.util.TypeConverter;

public class InstituteFileImportBean {

	private static Logger log = LogManager.getLogger(InstituteFileImportBean.class);

	private String basePath = ".";
	private String importFileName = "fints_institute NEU mit BIC Master.csv";
	private Charset charset = StandardCharsets.ISO_8859_1;
	
	private static final Path IMPORT_DIR = Paths.get("import");
	private static final Path ARCHIVE_DIR = Paths.get("import/archive");

	private final DBController dbController = DBController.getInstance(".");

	public InstituteFileImportBean(String basePath, String fileName, Charset charset) {
		this.basePath = basePath;
		this.importFileName = fileName;
		this.charset = charset;
	}

	public InstituteFileImportBean() {
	}

	public void runImport() throws IOException {
		
		Path archive = Paths.get(basePath, ARCHIVE_DIR.toString()); 
		if (!Files.exists(archive)) {
			Files.createDirectories(archive);
		}

		Path file = Paths.get(basePath, IMPORT_DIR.toString(), importFileName);
		if (!Files.exists(file)) {
			log.info("Keine (neue) Bankenliste Datei vorhanden.");
			return;
		}
		
		log.info("Importiere Bankenliste Datei: {}", file.getFileName());
		processFile(file);
		moveToArchive(file);

	}

	private void processFile(Path file) throws IOException {

		List<Institute> csvInstituteList = parseCsv(file);

		// Gruppieren nach BLZ
		Map<String, List<Institute>> institutesGroupedByBlz = csvInstituteList.stream().collect(Collectors.groupingBy(Institute::getBlz));

		// Status setzen (ACTIVE / DUPLICATE)
		for (List<Institute> group : institutesGroupedByBlz.values()) {

			group.sort(Comparator.comparing(Institute::getImportNumber));

			for (int i = 0; i < group.size(); i++) {
				if (i == 0) {
					group.get(i).setStateType(InstituteStatus.ACTIVE);
				} else {
					group.get(i).setStateType(InstituteStatus.DUPLICATE);
				}
			}
		}

		List<Institute> institutesGroupedList = institutesGroupedByBlz.values().stream().flatMap(List::stream).toList();

		// Bestehende Datensätze laden (ACTIVE + DUPLICATE)
		List<Institute> institutesDbList = dbController.getAll(Institute.class);
		List<Institute> institutesToInsertList = new ArrayList<>();

		for (Institute newInst : institutesGroupedList) {

			Optional<Institute> existingOpt = institutesDbList.stream().filter(e -> isSameBusinessKey(e, newInst)).findFirst();

			if (existingOpt.isEmpty()) {
				// komplett neuer Datensatz
				institutesToInsertList.add(newInst);
			} else {
				Institute existing = existingOpt.get();

				if (!equalsInstitute(existing, newInst)) {
					// alte Version archivieren
					existing.setStateType(InstituteStatus.ARCHIVED);
					dbController.insertOrUpdate(existing);

					institutesToInsertList.add(newInst);
				}
			}
		}

		if (!institutesToInsertList.isEmpty()) {
			dbController.insertAll(new HashSet<>(institutesToInsertList));
		}
	}

	private boolean isSameBusinessKey(Institute a, Institute b) {
		return Objects.equals(a.getBlz(), b.getBlz()) && Objects.equals(a.getImportNumber(), b.getImportNumber());
	}

	private List<Institute> parseCsv(Path file) throws IOException {

		List<Institute> instituteCsvList = new ArrayList<>();

		try (Reader reader = Files.newBufferedReader(file, charset);
				CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get().parse(reader)) {

			for (CSVRecord csvRecord : parser) {
				Institute instituteToAdd = mapRecord(csvRecord);
				if (instituteToAdd != null)
					instituteCsvList.add(mapRecord(csvRecord));
			}
		}

		return instituteCsvList;
	}

	private Institute mapRecord(CSVRecord csvRecord) {
		Institute institute = new Institute();
		
		String nr = csvRecord.get("Nr.");
		if (nr == null || "".equalsIgnoreCase(nr))
			return null;

		institute.setImportNumber(Integer.valueOf(nr));
		institute.setBlz(csvRecord.get("BLZ"));
		institute.setBic(csvRecord.get("BIC"));
		institute.setBankName(csvRecord.get("Institut"));
		institute.setPlace(csvRecord.get("Ort"));
		institute.setDataCenter(csvRecord.get("RZ"));
		institute.setOrganisation(csvRecord.get("Organisation"));
		institute.setHbciDns(csvRecord.get("HBCI-Zugang DNS"));
		institute.setHbciIp(csvRecord.get("HBCI- Zugang     IP-Adresse"));

		String hbciVersion = csvRecord.get("HBCI-Version");
		if (!hbciVersion.isEmpty()) {
			institute.setHbciVersion(Double.valueOf(hbciVersion));
		}

		institute.setDdv(csvRecord.get("DDV"));
		institute.setRdh1(TypeConverter.toBoolean(csvRecord.get("RDH-1")));
		institute.setRdh2(TypeConverter.toBoolean(csvRecord.get("RDH-2")));
		institute.setRdh3(TypeConverter.toBoolean(csvRecord.get("RDH-3")));
		institute.setRdh4(TypeConverter.toBoolean(csvRecord.get("RDH-4")));
		institute.setRdh5(TypeConverter.toBoolean(csvRecord.get("RDH-5")));
		institute.setRdh6(TypeConverter.toBoolean(csvRecord.get("RDH-6")));
		institute.setRdh7(TypeConverter.toBoolean(csvRecord.get("RDH-7")));
		institute.setRdh8(TypeConverter.toBoolean(csvRecord.get("RDH-8")));
		institute.setRdh9(TypeConverter.toBoolean(csvRecord.get("RDH-9")));
		institute.setRdh10(TypeConverter.toBoolean(csvRecord.get("RDH-10")));

		institute.setPinUrl(csvRecord.get("PIN/TAN-Zugang URL"));
		institute.setVersion(csvRecord.get("Version"));

		String date = csvRecord.get("Datum letzte Änderung");
		if (!date.isEmpty()) {
			institute.setLastChanged(TypeConverter.toCalendarFromDateStr(date));
		}

		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setSource(Source.IMPORT);
		institute.setUpdatedAt(Calendar.getInstance());

		return institute;
	}

	private boolean equalsInstitute(Institute a, Institute b) {
		return Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getBankName(), b.getBankName()) && Objects.equals(a.getPlace(), b.getPlace())
				&& Objects.equals(a.getHbciVersion(), b.getHbciVersion()) && Objects.equals(a.getPinUrl(), b.getPinUrl())
				&& Objects.equals(a.getVersion(), b.getVersion()) && Objects.equals(getTime(a.getLastChanged()), getTime(b.getLastChanged()));
	}

	private Long getTime(Calendar cal) {
		return cal == null ? null : cal.getTimeInMillis();
	}

	private void moveToArchive(Path file) throws IOException {
		Path target = Paths.get(basePath, ARCHIVE_DIR.resolve(file.getFileName()).toString());
		Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
	}
}
