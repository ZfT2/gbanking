package de.zft2.gbanking.service.institute;

import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.file.imp.institute.InstituteFileImport;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportDbb;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportDbbReachable;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportDk;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportEpc;
import de.zft2.gbanking.service.Service;

public class InstituteImportService implements Service {

	private static final List<ImportDefinition> DEFAULT_IMPORTS = List.of(
			new ImportDefinition(InstituteFileImportDk.class, InstituteFileImportDk.DEFAULT_FILENAME),
			new ImportDefinition(InstituteFileImportDbb.class, InstituteFileImportDbb.DEFAULT_FILENAME),
			new ImportDefinition(InstituteFileImportEpc.class, InstituteFileImportEpc.DEFAULT_FILENAME),
			new ImportDefinition(InstituteFileImportDbbReachable.class, InstituteFileImportDbbReachable.DEFAULT_FILENAME));

	public List<ImportDefinition> getDefaultImports() {
		return DEFAULT_IMPORTS;
	}

	public record ImportDefinition(Class<? extends InstituteFileImport> importType, String fileName) {

		public ImportDefinition {
			Objects.requireNonNull(importType, "importType");
			Objects.requireNonNull(fileName, "fileName");
		}
	}
}
