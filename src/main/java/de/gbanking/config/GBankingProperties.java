package de.gbanking.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.fp32xmlextract.exception.ConfigurationException;

public class GBankingProperties extends Properties {

	private static Logger log = LogManager.getLogger(GBankingProperties.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 1735959545579391865L;

	private static String baseDir;
	private static final String SUB_PATH = "properties/";

	private static Collection<GBankingProperties> instances = new ArrayList<>();
	private String fileName;

	private GBankingProperties() {
	}

	private static void initBaseDir(String propertiesFile) {
		
		URL divergentBase = GBankingProperties.class.getResource("/basePath.properties");
		if (divergentBase != null) {
			Properties basePathProperties = new Properties();
			try {
				basePathProperties.load(GBankingProperties.class.getResourceAsStream("/basePath.properties"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			baseDir = basePathProperties.getProperty("basePath");
		}
		
		if (baseDir == null) {
			if (new File(SUB_PATH + propertiesFile).exists()) { // für laden aus Eclipse Run
				baseDir = new File(SUB_PATH + propertiesFile).getAbsoluteFile().getParentFile().getParent() + "/";
			} else { // für laden aus Jar File
				baseDir = new File(".").getAbsoluteFile().getParentFile().getParent() + "/";
			}
		}
	}
	
	public String getBaseDir() {
		return baseDir;
	}

	public static GBankingProperties getInstance(String propertiesFile, boolean intern) throws ConfigurationException {
		initBaseDir(propertiesFile);
		log.log(Level.INFO, "Properties base dir: {0}", baseDir);

		GBankingProperties instance = getInstanceForFile(propertiesFile);
		String subPath = SUB_PATH;
		if (instance == null) {
			if (intern) {
				copyFile(baseDir + subPath + propertiesFile);
				subPath = SUB_PATH + "intern/";
			}
			try (InputStream is = new FileInputStream(baseDir + subPath + propertiesFile);) {
				instance = new GBankingProperties();
				instance.load(new InputStreamReader(is, StandardCharsets.UTF_8));
				instance.setFileName(propertiesFile);
				instances.add(instance);
			} catch (FileNotFoundException fnfe) {
				log.error("FileNotFoundException: ", fnfe);
				throw new ConfigurationException(propertiesFile, fnfe);
			} catch (IOException e) {
				log.error("IOException: ", e);
			}
		}
		return instance;
	}

	public String getProp(String key) {
		return this.getProperty(key);
	}

	private static GBankingProperties getInstanceForFile(String propertiesFile) {
		for (GBankingProperties instance : instances) {
			if (instance.getFileName().equalsIgnoreCase(propertiesFile)) {
				return instance;
			}
		}
		return null;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	private static void copyFile(String propertiesFileSrc) {
		String propertiesFileDest = new File(propertiesFileSrc).getParent() + "/intern/"
				+ new File(propertiesFileSrc).getName();

		try (BufferedReader br = new BufferedReader(new FileReader(propertiesFileSrc));
				OutputStream os = new FileOutputStream(propertiesFileDest, false);
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));) {

			pw.println("# file automatically created at: "
					+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())
					+ " DO NOT MODIFY!\n");
			String line;
			while ((line = br.readLine()) != null) {
				final int posEqSign = line.indexOf("=");
				if (posEqSign > -1) {
					pw.println(line.substring(0, posEqSign).replace(" ", "\\u0020") + line.substring(posEqSign));
				} else {
					pw.println(line);
				}
				pw.flush();
			}
		} catch (FileNotFoundException e) {
			log.error("FileNotFoundException (copyFile): ", e);
		} catch (IOException e) {
			log.error("IOException (copyFile): ", e);
		}
	}

	@Override
	public synchronized boolean equals(Object other) {
		return super.equals(other);
	}
	
	@Override
	public synchronized int hashCode() {
		return entrySet().hashCode();
	}
}
