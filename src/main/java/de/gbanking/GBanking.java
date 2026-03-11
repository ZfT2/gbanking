package de.gbanking;

import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.fx.GBankingGui;
import javafx.application.Application;

public class GBanking {

	private static Logger log = LogManager.getLogger(GBanking.class);

	public static void main(String[] args) {

		boolean startFX = true;

		if (startFX) {
			log.info("GBanking starting...");
			Application.launch(GBankingGui.class, args);
		} else {
			final String text = args != null && args.length > 0 ? args[0] : "GBanking";
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					final de.gbanking.gui.swing.GBankingGui window = new de.gbanking.gui.swing.GBankingGui(text);
					window.setVisible(true);
					log.info("GBanking started.");
				}
			});
		}
	}
}