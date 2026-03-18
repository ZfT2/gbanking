package de.gbanking.db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class BuildInfo {

    private static final String UNKNOWN_VERSION = "0.0.0";

    private BuildInfo() {
    }

    static String getVersion() {
        Package pkg = BuildInfo.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
            return pkg.getImplementationVersion();
        }

        Properties properties = new Properties();
        try (InputStream in = BuildInfo.class.getClassLoader().getResourceAsStream("build-info.properties")) {
            if (in != null) {
                properties.load(in);
                String value = properties.getProperty("app.version");
                if (value != null && !value.isBlank() && !value.contains("@project.version@")) {
                    return value.trim();
                }
            }
        } catch (IOException e) {
            // ignore and use fallback below
        }
        return UNKNOWN_VERSION;
    }
}
