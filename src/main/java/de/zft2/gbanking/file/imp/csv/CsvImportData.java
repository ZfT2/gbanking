package de.zft2.gbanking.file.imp.csv;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CsvImportData(Set<String> headers, List<Row> rows) {

	public CsvImportData {
		headers = Set.copyOf(headers);
		rows = List.copyOf(rows);
	}

	public record Row(long lineNumber, Map<String, String> values) {

		public Row {
			values = Map.copyOf(values);
		}

		public String text(String header) {
			return header != null ? trimToNull(values.get(header)) : null;
		}
	}
}
