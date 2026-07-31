package org.kapott.hbci.GV;

import java.util.Properties;

import org.kapott.hbci.GV_Result.HBCIJobResultImpl;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.LogFilter;

/**
 * HBCI4Java does not ship a highlevel job for HKKAU yet. This small adapter adds
 * the missing FinTS syntax nodes at runtime and exposes the job under the usual
 * HBCI4Java naming scheme.
 */
public class GVKontoauszugUebersicht extends HBCIJobImpl<HBCIJobResultImpl> {

	public static final String LOWLEVEL_NAME = "KontoauszugUebersicht";
	private static final String HBCI_CODE = "HKKAU";
	private static final String DEFAULT_SEGMENT_VERSION = "2";

	public static String getLowlevelName() {
		return LOWLEVEL_NAME;
	}

	public static boolean ensureSyntax(HBCIHandler handler) {
		return KontoauszugUebersichtSyntax.ensureAvailable(handler);
	}

	public static String resolveSegmentVersion(HBCIHandler handler) {
		if (handler == null) {
			return DEFAULT_SEGMENT_VERSION;
		}
		Properties supportedJobs = handler.getSupportedLowlevelJobs();
		String version = supportedJobs != null ? supportedJobs.getProperty(LOWLEVEL_NAME) : null;
		if ("1".equals(version) || "2".equals(version) || "3".equals(version)) {
			return version;
		}
		return DEFAULT_SEGMENT_VERSION;
	}

	public GVKontoauszugUebersicht(HBCIHandler handler) {
		super(handler, getLowlevelName(), new HBCIJobResultImpl());
		ensureSyntax(handler);
		setSegVersion(resolveSegmentVersion(handler));

		addConstraint("my.bic", "My.bic", "", LogFilter.FILTER_MOST);
		addConstraint("my.iban", "My.iban", "", LogFilter.FILTER_IDS);
		addConstraint("my.country", "My.KIK.country", "DE", LogFilter.FILTER_NONE);
		addConstraint("my.blz", "My.KIK.blz", "", LogFilter.FILTER_MOST);
		addConstraint("my.number", "My.number", "", LogFilter.FILTER_IDS);
		addConstraint("my.subnumber", "My.subnumber", "", LogFilter.FILTER_MOST);
		addConstraint("maxentries", "maxentries", "", LogFilter.FILTER_NONE);
		addConstraint("offset", "offset", "", LogFilter.FILTER_NONE);
	}

	@Override
	public String getHBCICode() {
		return HBCI_CODE;
	}

	@Override
	protected boolean skipBPDCheck() {
		return true;
	}

	@Override
	protected boolean redoAllowed() {
		return true;
	}
}
