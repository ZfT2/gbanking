package de.zft2.gbanking.file.imp.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.zft2.gbanking.exception.GBankingException;

public class CsvImportAnalyzer {

	private final CsvImportDefinitionRepository repository;
	private final CsvImportDataReader dataReader;

	public CsvImportAnalyzer() {
		this(new CsvImportDefinitionRepository(), new CsvImportDataReader());
	}

	public CsvImportAnalyzer(CsvImportDefinitionRepository repository) {
		this(repository, new CsvImportDataReader());
	}

	CsvImportAnalyzer(CsvImportDefinitionRepository repository, CsvImportDataReader dataReader) {
		this.repository = repository;
		this.dataReader = dataReader;
	}

	public Analysis analyze(Path importFile) throws IOException {
		List<Match> inspected = new ArrayList<>();
		for (CsvImportDefinition definition : repository.load()) {
			try {
				CsvImportData data = dataReader.read(importFile, definition);
				inspected.add(toMatch(definition, data.headers()));
			} catch (GBankingException | UncheckedIOException exception) {
				// A definition using another separator or a fixed header may not be able to parse this file.
			}
		}

		List<Match> compatible = inspected.stream().filter(Match::hasRequiredHeaders).filter(match -> match.matchingHeaderCount() > 0).toList();
		if (!compatible.isEmpty()) {
			return new Analysis(bestMatches(compatible), null, repository.getDefinitionFile());
		}

		List<Match> likelyMatches = bestLikelyMatches(inspected);
		return !likelyMatches.isEmpty()
				? new Analysis(likelyMatches, Problem.MISSING_REQUIRED_FIELDS, repository.getDefinitionFile())
				: new Analysis(List.of(), Problem.UNKNOWN_DEFINITION, repository.getDefinitionFile());
	}

	public Match match(Path importFile, String definitionName) throws IOException {
		for (CsvImportDefinition definition : repository.load()) {
			if (definition.getName().equals(definitionName)) {
				return toMatch(definition, dataReader.read(importFile, definition).headers());
			}
		}
		return null;
	}

	public CsvImportData read(Path importFile, CsvImportDefinition definition) throws IOException {
		return dataReader.read(importFile, definition);
	}

	private Match toMatch(CsvImportDefinition definition, Set<String> actualHeaders) {
		Set<String> missingRequired = difference(definition.getRequiredHeaders(), actualHeaders);
		Set<String> optionalHeaders = difference(definition.getDefinedHeaders(), definition.getRequiredHeaders());
		Set<String> missingOptional = difference(optionalHeaders, actualHeaders);
		Set<String> unknown = difference(actualHeaders, definition.getDefinedHeaders());
		Set<String> matching = new LinkedHashSet<>(actualHeaders);
		matching.retainAll(definition.getDefinedHeaders());
		return new Match(definition, actualHeaders, missingRequired, missingOptional, unknown, matching.size());
	}

	private List<Match> bestMatches(List<Match> matches) {
		Comparator<Match> comparator = Comparator.comparingInt(Match::differenceCount)
				.thenComparing(Match::headerless)
				.thenComparing(Comparator.comparingInt(Match::matchingHeaderCount).reversed());
		Match best = matches.stream().min(comparator).orElseThrow();
		return matches.stream().filter(match -> comparator.compare(match, best) == 0).toList();
	}

	private List<Match> bestLikelyMatches(List<Match> matches) {
		int bestOverlap = matches.stream().mapToInt(Match::matchingHeaderCount).max().orElse(0);
		if (bestOverlap == 0) {
			return List.of();
		}
		int fewestMissing = matches.stream().filter(match -> match.matchingHeaderCount() == bestOverlap)
				.mapToInt(match -> match.missingRequiredHeaders().size()).min().orElse(Integer.MAX_VALUE);
		return matches.stream().filter(match -> match.matchingHeaderCount() == bestOverlap)
				.filter(match -> match.missingRequiredHeaders().size() == fewestMissing).toList();
	}

	private Set<String> difference(Set<String> left, Set<String> right) {
		Set<String> result = new LinkedHashSet<>(left);
		result.removeAll(right);
		return Set.copyOf(result);
	}

	public enum Problem {
		UNKNOWN_DEFINITION,
		MISSING_REQUIRED_FIELDS
	}

	public record Analysis(List<Match> matches, Problem problem, Path definitionFile) {

		public Analysis {
			matches = List.copyOf(matches);
		}
	}

	public record Match(CsvImportDefinition definition, Set<String> actualHeaders, Set<String> missingRequiredHeaders,
			Set<String> missingOptionalHeaders, Set<String> unknownHeaders, int matchingHeaderCount) {

		public Match {
			actualHeaders = Set.copyOf(actualHeaders);
			missingRequiredHeaders = Set.copyOf(missingRequiredHeaders);
			missingOptionalHeaders = Set.copyOf(missingOptionalHeaders);
			unknownHeaders = Set.copyOf(unknownHeaders);
		}

		public boolean hasRequiredHeaders() {
			return missingRequiredHeaders.isEmpty();
		}

		public int differenceCount() {
			return missingOptionalHeaders.size() + unknownHeaders.size();
		}

		public boolean headerless() {
			return definition.hasConfiguredHeader();
		}

		@Override
		public String toString() {
			return definition.getName();
		}
	}
}
