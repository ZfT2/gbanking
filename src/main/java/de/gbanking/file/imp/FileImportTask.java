package de.gbanking.file.imp;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.gbanking.file.BaseFileTask;

public class FileImportTask extends BaseFileTask {

	private static Logger log = LogManager.getLogger(FileImportTask.class);

	public FileImportTask(String fileName) {
		super(fileName);
	}

	@Override
	public Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("FileImportTask: call(): START");
		setWorkerProgress(0);
		FileImportBean fileImportBean = new FileImportBean(this);
		fileImportBean.importFile(fileName);
		return null;
	}

}
