package de.zft2.gbanking.file.imp;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface ImportedBankNameCorrectionHandler {

	Map<Integer, String> selectCorrections(List<ImportedBankNameFinding> findings);
}
