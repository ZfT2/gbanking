package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropertiesFileSupportTest {

	@TempDir
	Path tempDirectory;

	@Test
	void shouldUpdateValuesAndPreserveCommentsAndLineEndings() throws Exception {
		Path file = tempDirectory.resolve("test.properties");
		String original = "# General comment\nalpha=old\n! Beta comment\nbeta=old\\\n continued\nobsolete=value\n";
		Files.writeString(file, original, StandardCharsets.UTF_8);

		String updated = PropertiesFileSupport.updateContent(file,
				Map.of("alpha", "new", "beta", "new value", "added", "additional"), "Default comment");

		assertEquals("# General comment\nalpha=new\n! Beta comment\nbeta=new value\nadded=additional\n", updated);
		assertFalse(updated.contains("obsolete"));
	}

	@Test
	void shouldAddDefaultCommentToNewFile() throws Exception {
		String content = PropertiesFileSupport.updateContent(tempDirectory.resolve("new.properties"), Map.of("key", "value"),
				"Application properties");

		assertTrue(content.startsWith("# Application properties" + System.lineSeparator()));
		assertTrue(content.contains("key=value" + System.lineSeparator()));
	}
}
