package de.zft2.gbanking.demo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DbScriptExecutor;
import de.zft2.gbanking.db.SqlTemplateRepository;

public final class DemoDataInstaller {

	private static final Logger log = LogManager.getLogger(DemoDataInstaller.class);

	public void install() {
		log.info("Installing demo data");
		DbScriptExecutor.execute(SqlTemplateRepository.getDemoStatements());
		log.info("Demo data installed successfully");
	}
}
