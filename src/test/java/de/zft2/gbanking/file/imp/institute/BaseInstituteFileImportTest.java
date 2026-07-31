package de.zft2.gbanking.file.imp.institute;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import de.zft2.gbanking.db.DBController;

abstract class BaseInstituteFileImportTest {

	// ---------------------------------------------------------
	// Helper methods
	// ---------------------------------------------------------

	/**
	 * Moves the archived CSV file back to the import directory. This allows
	 * repeated execution of tests.
	 */
	void restoreFile(Path importDir, Path archiveDir, String fileName) throws Exception {

		Path archivedFile = archiveDir.resolve(fileName);
		Path importFile = importDir.resolve(fileName);

		if (Files.exists(archivedFile)) {
			Files.move(archivedFile, importFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	List<String> selectImportFileNames() throws SQLException {
		List<String> names = new ArrayList<>();
		try (Statement statement = DBController.getConnection().createStatement();
				var rs = statement.executeQuery("SELECT importFileName FROM institute_db.importHistory ORDER BY id")) {
			while (rs.next()) {
				names.add(rs.getString("importFileName"));
			}
		}
		return names;
	}

}
