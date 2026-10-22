package de.zft2.gbanking.service.moneytransfer;

import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.SepaCancellationCode;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;

/**
 * The additional data of a money transfer protocol.
 *
 * <p>
 * The services fill the protocol with different data. The data is collected in this record so
 * that the protocol can be created and persisted by a shared code path.
 * </p>
 *
 * @param bankOrderId         the order id returned by the bank
 * @param hbciJobId           the id of the HBCI job that executed the transfer
 * @param sepaOrderStatus     the SEPA order status returned by the bank
 * @param sepaCancellationCode the SEPA cancellation code returned by the bank
 */
public record MoneyTransferProtocolData(String bankOrderId, String hbciJobId, SepaOrderStatus sepaOrderStatus,
		SepaCancellationCode sepaCancellationCode) {

	public static final MoneyTransferProtocolData EMPTY = new MoneyTransferProtocolData(null, null, null, null);

	/**
	 * Fills the given protocol with the data.
	 *
	 * @param protocol the protocol to fill
	 */
	public void applyTo(MoneyTransferProtocol protocol) {
		protocol.setBankOrderId(bankOrderId);
		protocol.setHbciJobId(hbciJobId);
		protocol.setSepaOrderStatus(sepaOrderStatus);
		protocol.setSepaCancellationCode(sepaCancellationCode);
	}
}
