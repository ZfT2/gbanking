package de.zft2.gbanking.hbci;

import java.util.Properties;

import de.zft2.gbanking.logging.LoggingSettings;

public final class HbciProperties {

	public static final String LOG_LEVEL_PARAM = "log.loglevel.default";
	public static final String PINTAN_INIT_PARAM = "client.passport.PinTan.init";
	public static final String PRODUCT_KEY_PARAM = "client.product.name";

	private HbciProperties() {
	}

	public static Properties createBaseProperties() {
		Properties properties = new Properties();
		properties.setProperty(PINTAN_INIT_PARAM, "1");
		properties.setProperty(LOG_LEVEL_PARAM, Integer.toString(LoggingSettings.getHbciLogLevel().toHbciLogLevel()));
		return properties;
	}
}
