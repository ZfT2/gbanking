package de.gbanking.fileexport.swing;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.file.swing.BaseFileTask;
import de.gbanking.gui.swing.enu.FileType;

public class FileExportTask extends BaseFileTask {

	private static Logger log = LogManager.getLogger(FileExportTask.class);	
	
	private List<BankAccount> exportAccountList;

	public FileExportTask(String fileName, FileType fileType, List<BankAccount> exportAccountList) {
		super(fileName);
		this.fileType = fileType;
		this.exportAccountList = exportAccountList;
	}

	@Override
	public Void doInBackground() throws ParserConfigurationException, SAXException, IOException {
		log.info("FileExportTask: doInBackground(): START");
		setProgress(0);
		FileExportBean fileExportBean = fileType == FileType.CSV ?  new FileExportCSVBean(this) : new FileExportXMLBean(this);
		fileExportBean.exportFileFromDatatbase(exportAccountList, fileName);
		return null;
	}

//	@Override
//	public void done() {
//		setProgress(100);
//		log.info("FileImportTask: doInBackground(): DONE");
//	}

}
