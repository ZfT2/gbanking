package de.zft2.gbanking.hbci;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.GVKUmsAllCamt;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.status.HBCIMsgStatus;

/**
 * Temporary workaround for hbci4java CAMT parsers that require a balance even
 * though FinTS permits unbooked CAMT reports without one.
 */
public final class GBankingKUmsAllCamt extends GVKUmsAllCamt {

	private static final Logger log = LogManager.getLogger(GBankingKUmsAllCamt.class);

	public GBankingKUmsAllCamt(HBCIHandler handler) {
		super(handler);
	}

	@Override
	protected void extractResults(HBCIMsgStatus messageStatus, String header, int index) {
		Properties data = messageStatus.getData();
		String resultKey = header + ".notbooked";
		String originalCamt = data.getProperty(resultKey);
		if (originalCamt == null) {
			super.extractResults(messageStatus, header, index);
			return;
		}

		CamtPendingBalanceWorkaround.NormalizedCamt normalized = CamtPendingBalanceWorkaround.normalize(originalCamt);
		if (normalized.addedBalances() == 0) {
			super.extractResults(messageStatus, header, index);
			return;
		}

		log.info("Applying CAMT parser workaround to {} unbooked report(s) without a balance.", normalized.addedBalances());
		GVRKUms result = (GVRKUms) getJobResult();
		int rawCamtIndex = result.camtNotBooked.size();
		data.setProperty(resultKey, normalized.content());
		try {
			super.extractResults(messageStatus, header, index);
		} finally {
			data.setProperty(resultKey, originalCamt);
			if (result.camtNotBooked.size() > rawCamtIndex) {
				result.camtNotBooked.set(rawCamtIndex, originalCamt);
			}
		}
	}
}
