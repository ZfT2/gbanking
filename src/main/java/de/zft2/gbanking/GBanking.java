package de.zft2.gbanking;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.gui.GBankingGui;
import de.zft2.gbanking.util.AppPaths;
import javafx.application.Application;

public class GBanking {

	static {
		System.setProperty("gbanking.baseDir", AppPaths.getApplicationBaseDirectory().toString());
	}

	private static Logger log = LogManager.getLogger(GBanking.class);

	public static void main(String[] args) {

		log.info("GBanking starting...");
		Application.launch(GBankingGui.class, args);
	}
}
