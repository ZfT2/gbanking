package de.zft2.gbanking.file;

import static de.zft2.gbanking.util.FileNames.fileName;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.FileType;

public abstract class BaseFileTask extends BaseWorker {

	private static final Logger log = LogManager.getLogger(BaseFileTask.class);

	protected String fileName;
	protected ExportType exportType;
	protected FileType fileType;

	protected BaseFileTask(String fileName) {
		this.fileName = fileName;
	}

	@Override
	protected abstract Void call() throws ParserConfigurationException, SAXException, IOException;

	@Override
	protected void succeeded() {
		updateProgress(100, 100);
		log.info("File task finished. task={}, type={}, file={}", () -> getClass().getSimpleName(), () -> exportType, this::fileNameOnly);
	}

	@Override
	protected void failed() {
		if (log.isErrorEnabled()) {
			log.error("File task failed. task={}, type={}, file={}", getClass().getSimpleName(), exportType, fileNameOnly(), getException());
		}
	}

	protected String fileNameOnly() {
		return fileName(fileName);
	}
}
