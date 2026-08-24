package de.zft2.gbanking.file.imp.csv;

import java.io.IOException;
import java.io.StringReader;
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

	public CsvImportData read(Path importFile, CsvImportDefinition definition) throws IOException {
		String content = stripBom(Files.readString(importFile, StandardCharsets.UTF_8));
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(definition.getSeparator()).setTrim(true).get();
		try (CSVParser parser = format.parse(new StringReader(content))) {
			return mapRecords(parser.getRecords(), definition);
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
		for (CSVRecord record : records) {
			if (record.size() > headers.size()) {
				throw new GBankingException("CSV row " + record.getRecordNumber() + " contains more columns than definition '"
						+ definition.getName() + "'.");
			}
			Map<String, String> values = new LinkedHashMap<>();
			for (int index = 0; index < headers.size(); index++) {
				String header = headers.get(index);
				if (!header.isBlank() && index < record.size()) {
					values.put(header, record.get(index));
				}
			}
			rows.add(new CsvImportData.Row(record.getRecordNumber(), values));
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

	private List<String> values(CSVRecord record) {
		List<String> values = new ArrayList<>(record.size());
		for (String value : record) {
			values.add(value.trim());
		}
		return List.copyOf(values);
	}

	private String stripBom(String value) {
		return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
	}
}
