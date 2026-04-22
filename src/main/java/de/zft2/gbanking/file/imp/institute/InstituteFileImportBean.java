package de.zft2.gbanking.file.imp.institute;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
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

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.util.TypeConverter;

public class InstituteFileImportBean {

	private static final Logger log = LogManager.getLogger(InstituteFileImportBean.class);

	private static final Path IMPORT_DIR = Paths.get("import");
	private static final Path ARCHIVE_DIR = Paths.get("import/archive");

	private final DBController dbController = DBController.getInstance(".");
	private final BaseWorker worker;

	private String basePath = ".";
	private String importFileName = "fints_institute NEU mit BIC Master.csv";
	private Charset charset = StandardCharsets.ISO_8859_1;

	/**
	 * Constructor for production usage with worker support.
	 */
	public InstituteFileImportBean(String basePath, String fileName, Charset charset, BaseWorker worker) {
		this.basePath = basePath;
		this.importFileName = fileName;
		this.charset = charset;
		this.worker = worker;
	}

	/**
	 * Constructor for usage without progress tracking (e.g. tests).
	 */
	public InstituteFileImportBean(String basePath, String fileName, Charset charset) {
		this(basePath, fileName, charset, null);
	}

	/**
	 * Constructor for legacy/test usage with default charset.
	 */
	public InstituteFileImportBean(String basePath, String fileName) {
		this(basePath, fileName, StandardCharsets.ISO_8859_1, null);
	}

	/**
	 * Default constructor.
	 */
	public InstituteFileImportBean() {
		this.worker = null;
	}

	public void runImport() throws IOException {
		Path archive = Paths.get(basePath, ARCHIVE_DIR.toString());
		if (!Files.exists(archive)) {
			Files.createDirectories(archive);
		}

		Path file = Paths.get(basePath, IMPORT_DIR.toString(), importFileName);

		if (!Files.exists(file)) {
			log.info("Keine (neue) Bankenliste Datei vorhanden.");
			updateWorkerState(100, "Keine neue Bankenliste gefunden");
			return;
		}

		updateWorkerState(2, "Reading bank list: %s", file.getFileName());

		log.info("Importiere Bankenliste Datei: {}", file.getFileName());
		processFile(file);

		updateWorkerState(95, "Archiving file: %s", file.getFileName());
		moveToArchive(file);

		updateWorkerState(100, "Bank list import completed");
	}

	private void processFile(Path file) throws IOException {
		List<Institute> csvInstituteList = parseCsv(file);

		updateWorkerState(35, "Grouping institutes by BLZ");

		Map<String, List<Institute>> institutesGroupedByBlz = csvInstituteList.stream().collect(Collectors.groupingBy(Institute::getBlz));

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

		List<Institute> institutesDbList = dbController.getAll(Institute.class);
		List<Institute> institutesToInsertList = new ArrayList<>();

		int total = institutesGroupedList.size();
		int processed = 0;

		for (Institute newInst : institutesGroupedList) {
			processed++;
			updateWorkerRange(processed, total, 35, 90, "Processing institute %d of %d (BLZ: %s)", processed, total, newInst.getBlz());

			Optional<Institute> existingOpt = institutesDbList.stream().filter(e -> isSameBusinessKey(e, newInst)).findFirst();

			if (existingOpt.isEmpty()) {
				institutesToInsertList.add(newInst);
			} else {
				Institute existing = existingOpt.get();
				if (!equalsInstitute(existing, newInst)) {
					existing.setStateType(InstituteStatus.ARCHIVED);
					dbController.insertOrUpdate(existing);
					institutesToInsertList.add(newInst);
				}
			}
		}

		if (!institutesToInsertList.isEmpty()) {
			updateWorkerState(92, "Writing %d institutes to database", institutesToInsertList.size());
			dbController.insertAll(new HashSet<>(institutesToInsertList));
		} else {
			updateWorkerState(92, "No institute changes detected");
		}
	}

	private List<Institute> parseCsv(Path file) throws IOException {
		List<Institute> instituteCsvList = new ArrayList<>();

		long totalRows;
		try (var lineStream = Files.lines(file, charset)) {
			totalRows = Math.max(1, lineStream.skip(1).count());
		}

		try (Reader reader = Files.newBufferedReader(file, charset);
				CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get().parse(reader)) {

			int rowIndex = 0;
			for (CSVRecord csvRecord : parser) {
				rowIndex++;
				updateWorkerRange(rowIndex, totalRows, 2, 30, "Reading CSV row %d of %d", rowIndex, totalRows);

				Institute instituteToAdd = mapRecord(csvRecord);
				if (instituteToAdd != null) {
					instituteCsvList.add(instituteToAdd);
				}
			}
		}

		return instituteCsvList;
	}

	private void updateWorkerState(int progress, String message, Object... args) {
		if (worker == null) {
			return;
		}
		worker.setProcessingState(String.format(message, args));
		worker.setWorkerProgress(progress);
	}

	private void updateWorkerRange(long current, long total, int start, int end, String message, Object... args) {
		if (worker == null) {
			return;
		}

		int progress;
		if (total <= 0) {
			progress = start;
		} else {
			double fraction = current / (double) total;
			progress = start + (int) Math.round((end - start) * fraction);
		}

		worker.setProcessingState(String.format(message, args));
		worker.setWorkerProgress(Math.min(progress, end));
	}

	private boolean isSameBusinessKey(Institute a, Institute b) {
		return Objects.equals(a.getBlz(), b.getBlz()) && Objects.equals(a.getImportNumber(), b.getImportNumber());
	}

	private Institute mapRecord(CSVRecord csvRecord) {
		Institute institute = new Institute();
		String nr = csvRecord.get("Nr.");

		if (nr == null || nr.isBlank()) {
			return null;
		}

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
			institute.setLastChanged(TypeConverter.toLocalDateFromDateStr(date));
		}

		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setSource(Source.IMPORT);
		institute.setUpdatedAt(LocalDate.now());

		return institute;
	}

	private boolean equalsInstitute(Institute a, Institute b) {
		return Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getBankName(), b.getBankName()) && Objects.equals(a.getPlace(), b.getPlace())
				&& Objects.equals(a.getHbciVersion(), b.getHbciVersion()) && Objects.equals(a.getPinUrl(), b.getPinUrl())
				&& Objects.equals(a.getVersion(), b.getVersion()) && Objects.equals(getTime(a.getLastChanged()), getTime(b.getLastChanged()));
	}

	private Long getTime(LocalDate cal) {
		return cal == null ? null : cal.toEpochDay();
	}

	private void moveToArchive(Path file) throws IOException {
		Path target = Paths.get(basePath, ARCHIVE_DIR.resolve(file.getFileName()).toString());
		Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
	}
}