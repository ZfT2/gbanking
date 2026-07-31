package de.zft2.gbanking.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.util.AppPaths;

class AssemblyInstituteDatabaseTest {

	private static final Pattern FILE_SET_PATTERN = Pattern.compile("<fileSet>.*?</fileSet>", Pattern.DOTALL);
	private static final String INSTITUTE_INCLUDE = "<include>institute.db</include>";

	@Test
	void allDistributionAssembliesPackageInstituteDatabase() throws IOException {
		Path projectDirectory = AppPaths.getApplicationBaseDirectory();
		Path instituteDatabase = projectDirectory.resolve("data").resolve("institute.db");
		assertTrue(Files.isRegularFile(instituteDatabase), "The bundled institute database must exist");
		assertTrue(Files.size(instituteDatabase) > 0, "The bundled institute database must not be empty");

		for (String platform : List.of("windows", "linux", "mac")) {
			assertAssemblyIncludesInstituteDatabase(projectDirectory, platform);
		}
	}

	private void assertAssemblyIncludesInstituteDatabase(Path projectDirectory, String platform) throws IOException {
		Path descriptor = projectDirectory.resolve("src").resolve("assembly").resolve(platform + ".xml");
		String descriptorXml = Files.readString(descriptor);
		String instituteFileSet = FILE_SET_PATTERN.matcher(descriptorXml).results()
				.map(MatchResult::group)
				.filter(fileSet -> fileSet.contains(INSTITUTE_INCLUDE))
				.findFirst()
				.orElseThrow(() -> new AssertionError(platform + " assembly does not include institute.db"));

		assertTrue(instituteFileSet.contains("<directory>${project.basedir}/data</directory>"),
				platform + " assembly must read institute.db from the project data directory");
		assertTrue(instituteFileSet.contains("<outputDirectory>/data</outputDirectory>"),
				platform + " assembly must package institute.db into the data directory");
	}
}
