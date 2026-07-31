package de.zft2.gbanking.update;

import java.util.Locale;

public enum OperatingSystem {
	WINDOWS("windows", "gbanking.bat"),
	LINUX("linux", "gbanking.sh"),
	MAC("mac", "gbanking.command");

	private final String releaseClassifier;
	private final String launcherName;

	OperatingSystem(String releaseClassifier, String launcherName) {
		this.releaseClassifier = releaseClassifier;
		this.launcherName = launcherName;
	}

	public static OperatingSystem current() {
		String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (osName.contains("win")) {
			return WINDOWS;
		}
		if (osName.contains("mac")) {
			return MAC;
		}
		return LINUX;
	}

	public String assetSuffix() {
		return "-" + releaseClassifier + ".zip";
	}

	public String launcherName() {
		return launcherName;
	}

	public boolean isWindows() {
		return this == WINDOWS;
	}
}
