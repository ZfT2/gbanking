package de.zft2.gbanking.file.exp;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BaseFileTask;
import de.zft2.gbanking.gui.enu.ExportType;

public class FileExportTask extends BaseFileTask {

	private static Logger log = LogManager.getLogger(FileExportTask.class);

	private List<BankAccount> exportAccountList;

	public FileExportTask(String fileName, ExportType exportType, List<BankAccount> exportAccountList) {
		super(fileName);
		this.exportType = exportType;
		this.exportAccountList = exportAccountList;
	}

	@Override
	public Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("Starting file export. type={}, file={}, accounts={}", () -> exportType, this::fileNameOnly,
				() -> exportAccountList != null ? exportAccountList.size() : 0);
		log.debug("File export path: {}", fileName);
		setWorkerProgress(0);

		FileExportBean fileExportBean = switch (exportType) {
		case BOOKINGS_XML -> new FileExportXMLBean(this);
		case BOOKINGS_CSV -> new FileExportCSVBean(this);
		case BOOKINGS_FP3 -> new FileExportFP3Bean(this);
		case BOOKINGS_MT940 -> new FileExportMT940Bean(this);
		case MONEYTRANSFERS_CSV -> new FileExportOrdersCSVBean(this);
		case MONEYTRANSFERS_SEPA_XML -> new FileExportOrdersSepaBean(this);
		default -> {
			log.error("Unknown Export type: {}", exportType);
			throw new GBankingException("Unknown Export type: {}", exportType);
		}
		};

		fileExportBean.exportFileFromDatatbase(exportAccountList, fileName);
		return null;
	}

}
