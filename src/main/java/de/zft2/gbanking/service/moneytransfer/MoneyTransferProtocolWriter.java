package de.zft2.gbanking.service.moneytransfer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Function;

import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DbExecutor;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.logging.HbciLogMessageSanitizer;

/**
 * Central helper for the logging and the persistence of money transfer protocols.
 *
 * <p>
 * Every service that talks to a bank stores a protocol with the technical details of the
 * transfer. The writer takes over the logging of the technical protocol and the creation of the
 * protocol of a failed transfer, so that the services only have to provide their own data.
 * </p>
 */
public final class MoneyTransferProtocolWriter {

	private final DbExecutor dbController;
	private final Logger log;
	private final String label;

	/**
	 * Creates a writer for the protocols of a service.
	 *
	 * @param dbController the executor used to write the protocols
	 * @param log         the logger of the service
	 * @param label       the description of the protocol used for the log messages
	 */
	public MoneyTransferProtocolWriter(DbExecutor dbController, Logger log, String label) {
		this.dbController = dbController;
		this.log = log;
		this.label = label;
	}

	/**
	 * Logs the technical protocol of a money transfer.
	 *
	 * @param moneyTransfer     the money transfer the protocol belongs to
	 * @param technicalProtocol the technical protocol of the transfer
	 */
	public void logTechnicalProtocol(MoneyTransfer moneyTransfer, String technicalProtocol) {
		if (log.isDebugEnabled()) {
			log.debug("Technical {} protocol. transferId={}{}{}", label, moneyTransfer.getId(), System.lineSeparator(),
					HbciLogMessageSanitizer.sanitize(technicalProtocol));
		}
	}

	/**
	 * Creates and persists the protocol of a money transfer that failed with an exception.
	 *
	 * @param moneyTransfer     the money transfer the protocol belongs to
	 * @param start             the start of the failed request
	 * @param exception         the exception that caused the failure
	 * @param data              the additional data of the protocol
	 * @param evaluationFactory the evaluation of the technical protocol
	 */
	public void persistFailureProtocol(MoneyTransfer moneyTransfer, LocalDateTime start, RuntimeException exception,
			MoneyTransferProtocolData data, Function<String, MoneyTransferProtocolEvaluator.Evaluation> evaluationFactory) {
		MoneyTransferProtocol protocol = new MoneyTransferProtocol(moneyTransfer.getId(), moneyTransfer.getMoneytransferStatus(),
				start, LocalDateTime.now(ZoneId.systemDefault()));
		data.applyTo(protocol);
		String technicalProtocol = exception.getClass().getName() + ": " + exception.getMessage();
		logTechnicalProtocol(moneyTransfer, technicalProtocol);
		evaluationFactory.apply(technicalProtocol).applyTo(protocol);
		dbController.insertOrUpdate(protocol);
	}
}
