package de.zft2.gbanking.service.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.file.imp.institute.InstituteFileImportDbb;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportDbbReachable;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportDk;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportEpc;
import de.zft2.gbanking.service.institute.InstituteImportService.ImportDefinition;

class InstituteImportServiceTest {

	@Test
	void shouldPlanAllDefaultInstituteImportsInExecutionOrder() {
		InstituteImportService service = new InstituteImportService();

		assertEquals(List.of(
				new ImportDefinition(InstituteFileImportDk.class, InstituteFileImportDk.DEFAULT_FILENAME),
				new ImportDefinition(InstituteFileImportDbb.class, InstituteFileImportDbb.DEFAULT_FILENAME),
				new ImportDefinition(InstituteFileImportEpc.class, InstituteFileImportEpc.DEFAULT_FILENAME),
				new ImportDefinition(InstituteFileImportDbbReachable.class, InstituteFileImportDbbReachable.DEFAULT_FILENAME)),
				service.getDefaultImports());
	}
}
