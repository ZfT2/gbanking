package de.zft2.gbanking.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipPackageExtractor {

	public Path extract(Path zipFile, Path targetDirectory) throws IOException, UpdateException {
		Path normalizedTargetDirectory = targetDirectory.toAbsolutePath().normalize();
		Files.createDirectories(normalizedTargetDirectory);

		try (InputStream inputStream = Files.newInputStream(zipFile); ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
			ZipEntry entry;
			while ((entry = zipInputStream.getNextEntry()) != null) {
				Path outputPath = normalizedTargetDirectory.resolve(entry.getName()).normalize();
				if (!outputPath.startsWith(normalizedTargetDirectory)) {
					throw new UpdateException("Update archive contains an invalid path: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(outputPath);
				} else {
					Path parentDirectory = outputPath.getParent();
					if (parentDirectory != null) {
						Files.createDirectories(parentDirectory);
					}
					Files.copy(zipInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
				}
				zipInputStream.closeEntry();
			}
		}

		return resolveDistributionRoot(normalizedTargetDirectory);
	}

	private Path resolveDistributionRoot(Path extractionDirectory) throws IOException, UpdateException {
		if (isDistributionRoot(extractionDirectory)) {
			return extractionDirectory;
		}

		try (Stream<Path> children = Files.list(extractionDirectory)) {
			List<Path> directories = children.filter(Files::isDirectory).toList();
			if (directories.size() == 1 && isDistributionRoot(directories.get(0))) {
				return directories.get(0);
			}
		}

		throw new UpdateException("Update archive does not contain a valid GBanking distribution");
	}

	private boolean isDistributionRoot(Path path) {
		return Files.isDirectory(path.resolve("bin")) && Files.isDirectory(path.resolve("lib"));
	}
}
