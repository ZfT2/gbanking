package de.gbanking.file.swing;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.gbanking.swing.BaseWorker;
import de.gbanking.gui.swing.enu.FileType;

public abstract class BaseFileTask extends BaseWorker {

	private static Logger log = LogManager.getLogger(BaseFileTask.class);

	protected String fileName;
	protected FileType fileType;

	protected BaseFileTask(String fileName) {
		this.fileName = fileName;
	}

	@Override
	public abstract Void doInBackground() throws ParserConfigurationException, SAXException, IOException;

	@Override
	public void done() {
		setProgress(100);
		log.info("File/*Import*/Task: doInBackground(): DONE");
	}

}
