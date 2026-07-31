package de.zft2.gbanking.file.imp.institute;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.util.AppPaths;

public abstract class InstituteFileImport implements BaseMessages {

	private static final Logger log = LogManager.getLogger(InstituteFileImport.class);

	private static final Path IMPORT_DIR = Paths.get("import");
	private static final Path ARCHIVE_DIR = IMPORT_DIR.resolve("archive");
	private static final Pattern DATED_CSV_SUFFIX = Pattern.compile("_(\\d{8})\\.csv$", Pattern.CASE_INSENSITIVE);

	protected final DBController dbController = DBController.getInstance(".");
	protected BaseWorker worker;

	protected Path baseDirectory = AppPaths.getApplicationBaseDirectory();
	protected Charset charset;

	protected String currentFileName;

	protected InstituteFileImport(String basePath, String fileName, Charset charset, BaseWorker worker) {
		this.baseDirectory = resolveBaseDirectory(basePath);
		this.currentFileName = fileName;
		this.charset = charset;
		this.worker = worker;
	}

	/**
	 * getInstance for JUnit tests without worker support.
	 */
	public static InstituteFileImport getInstance(Class<InstituteFileImportDk> type, String basePath, String fileName) {
		return getInstance(type, basePath, fileName, null, null);
	}

	/**
	 * getInstance for production usage with worker support.
	 */
	static InstituteFileImport getInstance(Class<? extends InstituteFileImport> type, String basePath, String fileName, Charset charset,
			BaseWorker worker) {
		if (type != null) {
			InstituteFileImport importType = null;
			if (type == InstituteFileImportDk.class) {
				importType = new InstituteFileImportDk(basePath, fileName, InstituteFileImportDk.getCharset(charset), worker);
			} else if (type == InstituteFileImportDbb.class) {
				importType = new InstituteFileImportDbb(basePath, fileName, InstituteFileImportDbb.getCharset(charset), worker);
			} else if (type == InstituteFileImportEpc.class) {
				importType = new InstituteFileImportEpc(basePath, fileName, InstituteFileImportEpc.getCharset(charset), worker);
			}
			return importType;
		} else {
			return chooseByDefaultFilename(fileName, basePath, charset, worker);
		}
	}

	private static InstituteFileImport chooseByDefaultFilename(String fileName, String basePath, Charset charset, BaseWorker worker) {
		switch (fileName) {
		case InstituteFileImportDk.DEFAULT_FILENAME:
			return new InstituteFileImportDk(fileName, basePath, InstituteFileImportDk.getCharset(charset), worker);
		case InstituteFileImportDbb.DEFAULT_FILENAME:
			return new InstituteFileImportDbb(fileName, basePath, InstituteFileImportDbb.getCharset(charset), worker);
		case InstituteFileImportEpc.DEFAULT_FILENAME:
			return new InstituteFileImportEpc(fileName, basePath, InstituteFileImportEpc.getCharset(charset), worker);
		default:
			throw new GBankingException("Unknown default file: " + fileName);
		}
	}

	protected void runImport() throws IOException {
		Path archive = baseDirectory.resolve(ARCHIVE_DIR);
		if (!Files.exists(archive)) {
			Files.createDirectories(archive);
		}

		List<Path> importFiles = resolveImportFiles();

		if (importFiles.isEmpty()) {
			log.info("Keine (neue) Bankenliste Datei vorhanden.");
			updateWorkerState(100, "UI_PROGRESS_INSTITUTE_NO_NEW_BANK_LIST");
			return;
		}

		int fileIndex = 0;
		for (Path file : importFiles) {
			fileIndex++;
			updateWorkerState(2, "UI_PROGRESS_INSTITUTE_READ_BANK_LIST", fileIndex, importFiles.size(), file.getFileName());

			log.info("Importiere Bankenliste Datei: {}", file.getFileName());

			processFile(file);

			updateWorkerState(95, "UI_PROGRESS_INSTITUTE_ARCHIVE_FILE", file.getFileName());
			moveToArchive(file);
		}

		updateWorkerState(100, "UI_PROGRESS_INSTITUTE_COMPLETED");
	}

	protected final void processFile(Path file) throws IOException {
		List<Institute> importedInstitutes = parseCsv(file);
		if (importedInstitutes.isEmpty()) {
			updateWorkerState(92, "UI_PROGRESS_INSTITUTE_NO_VALID_ROWS");
			return;
		}

		Integer importHistoryId = createImportHistory(file);
		if (importHistoryId == null) {
			return;
		}
		for (Institute institute : importedInstitutes) {
			institute.setImportFile(importHistoryId);
		}

		updateWorkerState(35, getGroupingMessageKey());
		Map<String, List<Institute>> groupedInstitutes = importedInstitutes.stream().collect(Collectors.groupingBy(this::getGroupKey));
		setStatesForGroups(groupedInstitutes);
		reconcileInstitutes(groupedInstitutes.values().stream().flatMap(List::stream).toList());
	}

	private List<Institute> parseCsv(Path file) throws IOException {
		List<Institute> importedInstitutes = new ArrayList<>();
		long totalRows;
		try (var lineStream = Files.lines(file, charset)) {
			totalRows = Math.max(1, lineStream.skip(1).count());
		}

		try (Reader reader = Files.newBufferedReader(file, charset);
				CSVParser parser = csvFormat().parse(reader)) {
			int rowIndex = 0;
			for (CSVRecord csvRecord : parser) {
				rowIndex++;
				updateWorkerRange(rowIndex, totalRows, 2, 30, "UI_PROGRESS_INSTITUTE_READ_CSV_ROW", rowIndex, totalRows);
				Institute institute = mapRecord(csvRecord);
				if (institute != null) {
					importedInstitutes.add(institute);
				}
			}
		}
		return importedInstitutes;
	}

	private Integer createImportHistory(Path file) {
		Path fileName = file.getFileName();
		if (fileName == null) {
			log.error("FileName is null!");
			return null;
		}
		ImportHistory importHistory = dbController.insertOrUpdate(new ImportHistory(fileName.toString()));
		return importHistory.getId();
	}

	private void setStatesForGroups(Map<String, List<Institute>> groupedInstitutes) {
		for (List<Institute> group : groupedInstitutes.values()) {
			group.sort(groupComparator());
			for (int index = 0; index < group.size(); index++) {
				group.get(index).setStateType(index == 0 ? InstituteStatus.ACTIVE : InstituteStatus.DUPLICATE);
			}
		}
	}

	private void reconcileInstitutes(List<Institute> importedInstitutes) {
		List<Institute> currentInstitutes = dbController.getAll(Institute.class).stream()
				.filter(this::isRelevantCurrentInstitute)
				.filter(this::isCurrentInstitute)
				.toList();
		Set<Integer> matchedInstituteIds = new HashSet<>();
		List<Institute> institutesToInsert = new ArrayList<>();
		List<MatchedInstitute> institutesToUpdate = new ArrayList<>();

		matchInstitutes(importedInstitutes, currentInstitutes, matchedInstituteIds, institutesToInsert, institutesToUpdate);
		prepareMatchedInstitutes(institutesToUpdate);
		archiveUnmatchedInstitutes(currentInstitutes, matchedInstituteIds);
		updateMatchedInstitutes(institutesToUpdate);
		insertNewInstitutes(institutesToInsert);
	}

	private void matchInstitutes(List<Institute> importedInstitutes, List<Institute> currentInstitutes, Set<Integer> matchedInstituteIds,
			List<Institute> institutesToInsert, List<MatchedInstitute> institutesToUpdate) {
		int total = importedInstitutes.size();
		int processed = 0;
		for (Institute imported : importedInstitutes) {
			processed++;
			updateWorkerRange(processed, total, 35, 90, getProcessingMessageKey(), processed, total, getGroupKey(imported));

			Optional<Institute> existingInstitute = findCurrentInstitute(currentInstitutes, matchedInstituteIds, imported);
			if (existingInstitute.isEmpty()) {
				institutesToInsert.add(imported);
				continue;
			}

			Institute existing = existingInstitute.get();
			matchedInstituteIds.add(existing.getId());
			if (needsCurrentImportMetadataUpdate(existing, imported)) {
				institutesToUpdate.add(new MatchedInstitute(existing, imported));
			}
		}
	}

	private Optional<Institute> findCurrentInstitute(List<Institute> currentInstitutes, Set<Integer> matchedInstituteIds, Institute imported) {
		return currentInstitutes.stream()
				.filter(existing -> !matchedInstituteIds.contains(existing.getId()))
				.filter(existing -> isSameInstituteIdentity(existing, imported))
				.findFirst();
	}

	private boolean needsCurrentImportMetadataUpdate(Institute existing, Institute imported) {
		return hasImportMetadataChanged(existing, imported)
				|| existing.getStateType() != imported.getStateType()
				|| !hasSameContent(existing, imported);
	}

	private void archiveUnmatchedInstitutes(List<Institute> currentInstitutes, Set<Integer> matchedInstituteIds) {
		for (Institute existing : currentInstitutes) {
			if (!matchedInstituteIds.contains(existing.getId())) {
				existing.setStateType(InstituteStatus.ARCHIVED);
				dbController.insertOrUpdate(existing);
			}
		}
	}

	private void updateMatchedInstitutes(List<MatchedInstitute> institutesToUpdate) {
		for (MatchedInstitute matchedInstitute : institutesToUpdate) {
			Institute existing = matchedInstitute.existing();
			Institute imported = matchedInstitute.toImport();
			boolean contentChanged = !hasSameContent(existing, imported);

			copyImportedFields(existing, imported);
			existing.setStateType(imported.getStateType());
			if (contentChanged || existing.getImportFile() == null) {
				existing.setImportFile(imported.getImportFile());
			}
			dbController.insertOrUpdate(existing);
		}
	}

	private void insertNewInstitutes(List<Institute> institutesToInsert) {
		if (institutesToInsert.isEmpty()) {
			updateWorkerState(92, "UI_PROGRESS_INSTITUTE_NO_CHANGES");
			return;
		}
		updateWorkerState(92, "UI_PROGRESS_INSTITUTE_WRITE", institutesToInsert.size());
		dbController.insertAll(new HashSet<>(institutesToInsert));
	}

	protected final Institute createImportedInstitute() {
		Institute institute = new Institute();
		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setSource(Source.IMPORT);
		institute.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return institute;
	}

	protected final boolean hasInstituteData(CSVRecord csvRecord, String identifyingColumn) {
		return csvRecord.toMap().entrySet().stream()
				.filter(entry -> !identifyingColumn.equals(entry.getKey()))
				.map(Map.Entry::getValue)
				.anyMatch(value -> value != null && !value.isBlank());
	}

	protected String getGroupKey(Institute institute) {
		return institute.getBlz();
	}

	protected String getGroupingMessageKey() {
		return "UI_PROGRESS_INSTITUTE_GROUP_BY_BLZ";
	}

	protected String getProcessingMessageKey() {
		return "UI_PROGRESS_INSTITUTE_PROCESS_BLZ";
	}

	protected boolean hasImportMetadataChanged(Institute existing, Institute imported) {
		return false;
	}

	protected void prepareMatchedInstitutes(List<MatchedInstitute> matchedInstitutes) {
		// Most import formats need no post-processing before persistence.
	}

	protected abstract CSVFormat csvFormat();

	protected abstract Comparator<Institute> groupComparator();

	protected abstract boolean isRelevantCurrentInstitute(Institute institute);

	protected abstract Institute mapRecord(CSVRecord csvRecord);

	protected abstract boolean isSameInstituteIdentity(Institute existing, Institute imported);

	protected abstract boolean hasSameContent(Institute existing, Institute imported);

	protected abstract void copyImportedFields(Institute existing, Institute imported);

	private List<Path> resolveImportFiles() throws IOException {
		Path importDirectory = baseDirectory.resolve(IMPORT_DIR);
		List<Path> datedImportFiles = findDatedImportFiles(importDirectory);
		if (!datedImportFiles.isEmpty()) {
			return datedImportFiles;
		}

		Path file = importDirectory.resolve(currentFileName);
		return Files.exists(file) ? List.of(file) : List.of();
	}

	private List<Path> findDatedImportFiles(Path importDirectory) throws IOException {
		if (!Files.isDirectory(importDirectory)) {
			return List.of();
		}

		Pattern datedFileNamePattern = Pattern.compile(Pattern.quote(getImportFileNameBase()) + "_(\\d{8})\\.csv", Pattern.CASE_INSENSITIVE);
		try (var files = Files.list(importDirectory)) {
			return files.filter(Files::isRegularFile)
					.map(file -> toDatedImportFile(file, datedFileNamePattern))
					.flatMap(Optional::stream)
					.sorted(Comparator.comparing(DatedImportFile::date).thenComparing(DatedImportFile::fileName))
					.map(DatedImportFile::path)
					.toList();
		}
	}

	private Optional<DatedImportFile> toDatedImportFile(Path file, Pattern datedFileNamePattern) {
		Path fileNameFromFile = file.getFileName();
		if (fileNameFromFile == null)
			return Optional.empty();
		Matcher matcher = datedFileNamePattern.matcher(fileNameFromFile.toString());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new DatedImportFile(file, LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE),
					fileNameFromFile.toString()));
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	private String getImportFileNameBase() {
		return DATED_CSV_SUFFIX.matcher(currentFileName).replaceFirst("").replaceFirst("(?i)\\.csv$", "");
	}


	protected void updateWorkerState(int progress, String messageKey, Object... args) {
		if (worker == null) {
			return;
		}
		worker.setProcessingState(getText(messageKey, args));
		worker.setWorkerProgress(progress);
	}

	protected void updateWorkerRange(long current, long total, int start, int end, String messageKey, Object... args) {
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

		worker.setProcessingState(getText(messageKey, args));
		worker.setWorkerProgress(Math.min(progress, end));
	}



	private void moveToArchive(Path file) throws IOException {
		Path target = baseDirectory.resolve(ARCHIVE_DIR).resolve(file.getFileName());
		Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
	}

	protected Path resolveBaseDirectory(String basePath) {
		if (basePath == null || basePath.isBlank() || ".".equals(basePath.trim())) {
			return AppPaths.getApplicationBaseDirectory();
		}
		return AppPaths.resolveInApplicationDirectory(basePath);
	}

	protected boolean isCurrentInstitute(Institute institute) {
		return institute.getStateType() == InstituteStatus.ACTIVE || institute.getStateType() == InstituteStatus.DUPLICATE;
	}

	record MatchedInstitute(Institute existing, Institute toImport) {
	}

	private record DatedImportFile(Path path, LocalDate date, String fileName) {
	}

}
