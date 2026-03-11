package de.gbanking.fileimport.swing;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.gbanking.file.swing.BaseFileTask;

public class FileImportTask extends BaseFileTask {

	private static Logger log = LogManager.getLogger(FileImportTask.class);

	public FileImportTask(String fileName) {
		super(fileName);
	}

	@Override
	public Void doInBackground() throws ParserConfigurationException, SAXException, IOException {
		log.info("FileImportTask: doInBackground(): START");
		setProgress(0);
		FileImportBean fileImportBean = new FileImportBean(this);
		fileImportBean.importFile(fileName);
		return null;
	}

//	@Override
//	public void done() {
//		setProgress(100);
//		log.info("FileImportTask: doInBackground(): DONE");
//	}

}
