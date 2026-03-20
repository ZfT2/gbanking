package de.gbanking;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.GBankingGui;
import javafx.application.Application;

public class GBanking {

	private static Logger log = LogManager.getLogger(GBanking.class);

	public static void main(String[] args) {

		log.info("GBanking starting...");
		Application.launch(GBankingGui.class, args);
	}
}