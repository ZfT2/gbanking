package de.gbanking.file;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.gbanking.gui.BaseWorker;
import de.gbanking.gui.enu.FileType;

public abstract class BaseFileTask extends BaseWorker {

	private static final Logger log = LogManager.getLogger(BaseFileTask.class);

	protected String fileName;
	protected FileType fileType;

	protected BaseFileTask(String fileName) {
		this.fileName = fileName;
	}

	@Override
	protected abstract Void call() throws ParserConfigurationException, SAXException, IOException;

	@Override
	protected void succeeded() {
		updateProgress(100, 100);
		log.info("FileTask: call(): DONE");
	}

	@Override
	protected void failed() {
		log.error("FileTask failed", getException());
	}
}