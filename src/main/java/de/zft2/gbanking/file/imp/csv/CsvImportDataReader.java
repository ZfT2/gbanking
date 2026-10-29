package de.zft2.gbanking.file.imp.csv;

import static de.zft2.gbanking.util.TextValues.removeLeadingBom;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.exception.GBankingException;

public class CsvImportDataReader {

	private static final Charset FALLBACK_CHARSET = Charset.forName("windows-1252");

	public CsvImportData read(Path importFile, CsvImportDefinition definition) throws IOException {
		String content = readText(importFile, definition.getCharset());
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(definition.getSeparator()).setTrim(true).get();
		try (CSVParser parser = format.parse(new StringReader(content))) {
			return mapRecords(parser.getRecords(), definition);
		}
	}

	/**
	 * Reads a CSV file as text and removes a leading byte order mark. Without a configured charset the
	 * file is decoded as UTF-8 and falls back to Windows-1252, which many bank exports still use.
	 *
	 * @param file    the CSV file
	 * @param charset the configured charset or {@code null} for automatic detection
	 * @return the file content
	 * @throws IOException if the file cannot be read
	 */
	public static String readText(Path file, Charset charset) throws IOException {
		byte[] content = Files.readAllBytes(file);
		if (charset != null) {
			return removeLeadingBom(new String(content, charset));
		}
		try {
			return removeLeadingBom(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString());
		} catch (CharacterCodingException exception) {
			return new String(content, FALLBACK_CHARSET);
		}
	}

	private CsvImportData mapRecords(List<CSVRecord> allRecords, CsvImportDefinition definition) {
		int firstRecord = Math.min(definition.getLeadingRows(), allRecords.size());
		int lastRecord = Math.max(firstRecord, allRecords.size() - definition.getTrailingRows());
		List<CSVRecord> records = allRecords.subList(firstRecord, lastRecord);
		if (records.isEmpty()) {
			return new CsvImportData(Set.of(), List.of());
		}

		List<String> headers;
		int firstDataRecord;
		if (definition.hasConfiguredHeader()) {
			headers = definition.getExtraHeaders();
			firstDataRecord = 0;
		} else {
			headers = values(records.get(0));
			firstDataRecord = 1;
		}
		validateHeaders(headers, definition);
		return new CsvImportData(nonBlankHeaders(headers), mapRows(records.subList(firstDataRecord, records.size()), headers, definition));
	}

	private List<CsvImportData.Row> mapRows(List<CSVRecord> records, List<String> headers, CsvImportDefinition definition) {
		List<CsvImportData.Row> rows = new ArrayList<>(records.size());
		for (CSVRecord csvRecord : records) {
			if (csvRecord.size() > headers.size()) {
				throw new GBankingException("CSV row " + csvRecord.getRecordNumber() + " contains more columns than definition '"
						+ definition.getName() + "'.");
			}
			Map<String, String> values = new LinkedHashMap<>();
			for (int index = 0; index < headers.size(); index++) {
				String header = headers.get(index);
				if (!header.isBlank() && index < csvRecord.size()) {
					values.put(header, csvRecord.get(index));
				}
			}
			rows.add(new CsvImportData.Row(csvRecord.getRecordNumber(), values));
		}
		return List.copyOf(rows);
	}

	private void validateHeaders(List<String> headers, CsvImportDefinition definition) {
		Set<String> uniqueHeaders = new LinkedHashSet<>();
		for (String header : headers) {
			if (!header.isBlank() && !uniqueHeaders.add(header)) {
				throw new GBankingException("Duplicate CSV column '" + header + "' for definition '" + definition.getName() + "'.");
			}
		}
	}

	private Set<String> nonBlankHeaders(List<String> headers) {
		Set<String> result = new LinkedHashSet<>();
		headers.stream().filter(header -> !header.isBlank()).forEach(result::add);
		return Set.copyOf(result);
	}

	private List<String> values(CSVRecord csvRecord) {
		List<String> values = new ArrayList<>(csvRecord.size());
		for (String value : csvRecord) {
			values.add(value.trim());
		}
		return List.copyOf(values);
	}

}
