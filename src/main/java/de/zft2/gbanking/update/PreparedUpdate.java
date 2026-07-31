package de.zft2.gbanking.update;

import java.nio.file.Path;
import java.util.Objects;

public record PreparedUpdate(String version, Path installDirectory, Path sourceDirectory, Path workDirectory) {

	public PreparedUpdate {
		Objects.requireNonNull(version, "version must not be null");
		Objects.requireNonNull(installDirectory, "installDirectory must not be null");
		Objects.requireNonNull(sourceDirectory, "sourceDirectory must not be null");
		Objects.requireNonNull(workDirectory, "workDirectory must not be null");
	}
}
