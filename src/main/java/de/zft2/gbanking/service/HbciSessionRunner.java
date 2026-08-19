package de.zft2.gbanking.service;

import java.util.Arrays;
import java.util.function.Function;

import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.bankaccess.BankMessageService;

public final class HbciSessionRunner {

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
