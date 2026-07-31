package de.zft2.gbanking.update;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class VersionComparator {

	private VersionComparator() {
	}

	public static boolean isNewer(String candidateVersion, String currentVersion) {
		ParsedVersion candidate = parse(candidateVersion);
		ParsedVersion current = parse(currentVersion);

		for (int i = 0; i < Math.max(candidate.numbers().length, current.numbers().length); i++) {
			int candidatePart = part(candidate.numbers(), i);
			int currentPart = part(current.numbers(), i);
			if (candidatePart != currentPart) {
				return candidatePart > currentPart;
			}
		}

		return candidate.qualifier() == null && current.qualifier() != null;
	}

	public static String normalize(String version) {
		if (version == null || version.isBlank()) {
			return "0.0.0";
		}
		String normalized = version.trim();
		if (normalized.startsWith("v") || normalized.startsWith("V")) {
			normalized = normalized.substring(1);
		}
		int buildMetadataStart = normalized.indexOf('+');
		if (buildMetadataStart >= 0) {
			normalized = normalized.substring(0, buildMetadataStart);
		}
		return normalized;
	}

	private static ParsedVersion parse(String version) {
		String normalized = normalize(version);
		String[] versionAndQualifier = normalized.split("-", 2);
		int[] numbers = Arrays.stream(versionAndQualifier[0].split("\\.")).mapToInt(VersionComparator::parseIntPart).toArray();
		String qualifier = versionAndQualifier.length > 1 ? versionAndQualifier[1].toLowerCase(Locale.ROOT) : null;
		return new ParsedVersion(numbers, qualifier);
	}

	private static int parseIntPart(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		int end = 0;
		while (end < value.length() && Character.isDigit(value.charAt(end))) {
			end++;
		}
		if (end == 0) {
			return 0;
		}
		return Integer.parseInt(value.substring(0, end));
	}

	private static int part(int[] numbers, int index) {
		return index < numbers.length ? numbers[index] : 0;
	}

	private record ParsedVersion(int[] numbers, String qualifier) {

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ParsedVersion other = (ParsedVersion) obj;
			return Arrays.equals(numbers, other.numbers) && Objects.equals(qualifier, other.qualifier);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(numbers);
			result = prime * result + Objects.hash(qualifier);
			return result;
		}

		@Override
		public String toString() {
			return "ParsedVersion [numbers=" + Arrays.toString(numbers) + ", qualifier=" + qualifier + "]";
		}
	}
}
