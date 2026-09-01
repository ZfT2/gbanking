package de.zft2.gbanking.hbci;

import java.util.Properties;

import de.zft2.gbanking.db.BuildInfo;
import de.zft2.gbanking.logging.LoggingSettings;

public final class HbciProperties {

	public static final String LOG_LEVEL_PARAM = "log.loglevel.default";
	public static final String LOG_FILTER_PARAM = "log.filter";
	public static final String PINTAN_INIT_PARAM = "client.passport.PinTan.init";
	public static final String PRODUCT_KEY_PARAM = "client.product.name";
	public static final String PRODUCT_VERSION_PARAM = "client.product.version";
	private static final int PRODUCT_VERSION_MAX_LENGTH = 5;

	private HbciProperties() {
	}

	public static Properties createBaseProperties() {
		Properties properties = new Properties();
		properties.setProperty(PINTAN_INIT_PARAM, "1");
		properties.setProperty(LOG_LEVEL_PARAM, Integer.toString(LoggingSettings.getHbciLogLevel().toHbciLogLevel()));
		properties.setProperty(LOG_FILTER_PARAM, Integer.toString(LoggingSettings.getHbciLogFilterLevel()));
		properties.setProperty(PRODUCT_VERSION_PARAM, toFinTsProductVersion(BuildInfo.getProgramVersion()));
		return properties;
	}

	static String toFinTsProductVersion(String programVersion) {
		int qualifierIndex = programVersion.indexOf('-');
		int releaseVersionLength = qualifierIndex >= 0 ? qualifierIndex : programVersion.length();
		int productVersionLength = Math.min(releaseVersionLength, PRODUCT_VERSION_MAX_LENGTH);
		String productVersion = programVersion.substring(0, productVersionLength);
		return productVersion.endsWith(".") ? productVersion.substring(0, productVersionLength - 1) : productVersion;
	}
}
