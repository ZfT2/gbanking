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
import java.util.HashMap;
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
		Layout layout = layout(parse(readText(importFile, definition.getCharset()), definition.getSeparator()), definition);
		return new CsvImportData(nonBlankHeaders(layout.headers()), mapRows(layout.dataRecords(), layout.headers()));
	}

	HeaderReader headerReader(Path importFile) {
		return new HeaderReader(importFile);
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
		return decode(Files.readAllBytes(file), charset);
	}

	private static String decode(byte[] content, Charset charset) {
		if (charset != null) {
			return removeLeadingBom(new String(content, charset));
		}
		try {
			return removeLeadingBom(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString());
		} catch (CharacterCodingException exception) {
			return new String(content, FALLBACK_CHARSET);
		}
	}

	private static List<CSVRecord> parse(String content, char separator) throws IOException {
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(separator).setTrim(true).get();
		try (CSVParser parser = format.parse(new StringReader(content))) {
			return parser.getRecords();
		}
	}

	/** Applies leading/trailing rows and the header of the definition and validates headers and column counts. */
	private Layout layout(List<CSVRecord> allRecords, CsvImportDefinition definition) {
		int firstRecord = Math.min(definition.getLeadingRows(), allRecords.size());
		int lastRecord = Math.max(firstRecord, allRecords.size() - definition.getTrailingRows());
		List<CSVRecord> records = allRecords.subList(firstRecord, lastRecord);
		if (records.isEmpty()) {
			return new Layout(List.of(), List.of());
		}

		boolean configuredHeader = definition.hasConfiguredHeader();
		List<String> headers = configuredHeader ? definition.getExtraHeaders() : values(records.get(0));
		List<CSVRecord> dataRecords = records.subList(configuredHeader ? 0 : 1, records.size());
		validateHeaders(headers, definition);
		for (CSVRecord csvRecord : dataRecords) {
			if (csvRecord.size() > headers.size()) {
				throw new GBankingException("CSV row " + csvRecord.getRecordNumber() + " contains more columns than definition '"
						+ definition.getName() + "'.");
			}
		}
		return new Layout(headers, dataRecords);
	}

	private List<CsvImportData.Row> mapRows(List<CSVRecord> records, List<String> headers) {
		List<CsvImportData.Row> rows = new ArrayList<>(records.size());
		for (CSVRecord csvRecord : records) {
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

	private record Layout(List<String> headers, List<CSVRecord> dataRecords) {
	}

	private record Format(Charset charset, char separator) {
	}

	/**
	 * Inspects one file with many definitions: the file is read once and parsed once per charset and separator,
	 * and data rows are validated like in {@link CsvImportDataReader#read} but not mapped.
	 */
	final class HeaderReader {

		private final Path importFile;
		private final Map<Format, List<CSVRecord>> recordsByFormat = new HashMap<>();
		private byte[] content;

		private HeaderReader(Path importFile) {
			this.importFile = importFile;
		}

		Set<String> headers(CsvImportDefinition definition) throws IOException {
			Format format = new Format(definition.getCharset(), definition.getSeparator());
			List<CSVRecord> records = recordsByFormat.get(format);
			if (records == null) {
				if (content == null) {
					content = Files.readAllBytes(importFile);
				}
				records = parse(decode(content, format.charset()), format.separator());
				recordsByFormat.put(format, records);
			}
			return nonBlankHeaders(layout(records, definition).headers());
		}
	}

}
