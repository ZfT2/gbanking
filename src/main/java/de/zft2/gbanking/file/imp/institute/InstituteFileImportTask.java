package de.zft2.gbanking.file.imp.institute;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.file.BaseFileTask;

public class InstituteFileImportTask extends BaseFileTask {

	private static final Logger log = LogManager.getLogger(InstituteFileImportTask.class);

	private final Class<? extends InstituteFileImport> instituteFileImportType;
	private final String basePath;
	private final Charset charset;

	public InstituteFileImportTask(Class<? extends InstituteFileImport> instituteFileImportType, String basePath, String fileName, Charset charset) {
		super(fileName);
		this.instituteFileImportType = instituteFileImportType;
		this.basePath = basePath;
		this.charset = charset;
	}

	@Override
	protected Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("InstituteFileImportTask: call(): START for {}", instituteFileImportType.getName());
		setWorkerProgress(0);

		InstituteFileImport bean = InstituteFileImport.getInstance(instituteFileImportType, basePath, fileName, charset, this);
		bean.runImport();

		return null;
	}
}
