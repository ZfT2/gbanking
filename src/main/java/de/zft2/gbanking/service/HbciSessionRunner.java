package de.zft2.gbanking.service;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.hbci.HbciStatusMessageExtractor;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.bankaccess.BankMessageService;

public final class HbciSessionRunner {

	private static final Logger log = LogManager.getLogger(HbciSessionRunner.class);
	private static final Object HBCI_RUNTIME_LOCK = new Object();

	private final Function<BankAccess, GBankingHBCICallback> callbackFactory;

	public HbciSessionRunner() {
		this(bankAccess -> new GBankingHBCICallback(bankAccess));
	}

	HbciSessionRunner(Function<BankAccess, GBankingHBCICallback> callbackFactory) {
		this.callbackFactory = callbackFactory;
	}

	public <T> T run(BankAccess bankAccess, char[] secret, HbciOperation<T> operation) throws InterruptedException {
		synchronized (HBCI_RUNTIME_LOCK) {
			return runLocked(bankAccess, secret, operation);
		}
	}

	/**
	 * Runs an HBCI operation and converts the well known failures into a result of the calling
	 * service.
	 *
	 * <p>
	 * An interrupted operation and a wrong PIN reported by the bank are the two failures that every
	 * retrieval has to handle in the same way. The method takes over this handling, so that the
	 * calling service only has to provide the results for these failures.
	 * </p>
	 *
	 * @param activity          the description of the running activity used for the log messages
	 * @param entityId          the id of the affected entity used for the log messages
	 * @param bankAccess        the bank access used for the session
	 * @param secret            the secret of the bank access, it is cleared after the session
	 * @param operation         the operation to execute within the session
	 * @param wrongPinResult    the result used if the bank reported invalid PIN credentials
	 * @param interruptedResult the result used if the operation was interrupted
	 * @return the result of the operation or the result of the detected failure
	 */
	public <T> T runGuarded(String activity, Object entityId, BankAccess bankAccess, char[] secret,
			HbciOperation<T> operation, Function<HBCI_Exception, T> wrongPinResult, Supplier<T> interruptedResult) {
		try {
			return run(bankAccess, secret, operation);
		} catch (InterruptedException exception) {
			log.error("Error handling HBCI {} {}", activity, entityId, exception);
			Thread.currentThread().interrupt();
			return interruptedResult.get();
		} catch (HBCI_Exception exception) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(exception)) {
				log.warn("Stopping HBCI {} {} because the bank reported invalid PIN credentials.", activity, entityId);
				return wrongPinResult.apply(exception);
			}
			throw exception;
		}
	}

	private <T> T runLocked(BankAccess bankAccess, char[] secret, HbciOperation<T> operation) throws InterruptedException {
		GBankingHBCICallback hbciCallback = callbackFactory.apply(bankAccess);
		HBCIPassport passport = null;
		hbciCallback.startStatusDialog();

		try {
			resetHbciThreadContextIfInitialized();
			BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
			passport = hbciSupport.initBankConnection(bankAccess, hbciCallback);
			try (HBCIHandler handle = hbciSupport.createHBCIHandler(BaseMessagesDb.getVersion().getId(), passport)) {
				return operation.execute(new HbciSession(hbciCallback, passport, handle));
			}
		} catch (Exception e) {
			hbciCallback.handleException(e);
			throw e;
		} finally {
			clearSecret(secret);
			try {
				if (passport != null) {
					passport.close();
				}
			} finally {
				resetHbciThreadContextIfInitialized();
				ServiceRegistry.getService(BankMessageService.class).saveInstitutionMessages(bankAccess,
						hbciCallback.drainInstitutionMessages());
				hbciCallback.finishStatusDialog();
			}
		}
	}

	private void resetHbciThreadContextIfInitialized() {
		if (HBCIUtils.getParams() != null) {
			HBCIUtils.doneThread();
		}
	}

	public static void clearSecret(char[] secret) {
		if (secret != null) {
			Arrays.fill(secret, '\0');
		}
	}

	public record HbciSession(GBankingHBCICallback callback, HBCIPassport passport, HBCIHandler handler) {
	}

	@FunctionalInterface
	public interface HbciOperation<T> {
		T execute(HbciSession session) throws InterruptedException;
	}
}
