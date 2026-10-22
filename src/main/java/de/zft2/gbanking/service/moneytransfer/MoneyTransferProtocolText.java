package de.zft2.gbanking.service.moneytransfer;

/**
 * Builder for the technical protocol text of a money transfer.
 *
 * <p>
 * The technical protocol collects the results of the HBCI dialog. The services use different
 * labels for the same kind of information, so the labels are passed by the caller.
 * </p>
 */
public final class MoneyTransferProtocolText {

	private final StringBuilder text = new StringBuilder();

	private MoneyTransferProtocolText() {
	}

	/**
	 * Starts a protocol text with the given first line.
	 *
	 * @param label the label of the first line
	 * @param value the value of the first line
	 * @return the started protocol text
	 */
	public static MoneyTransferProtocolText of(String label, Object value) {
		return new MoneyTransferProtocolText().append(label, value);
	}

	/**
	 * Starts an empty protocol text.
	 *
	 * @return the started protocol text
	 */
	public static MoneyTransferProtocolText empty() {
		return new MoneyTransferProtocolText();
	}

	/**
	 * Appends a line with the given label, even if the value is missing.
	 *
	 * @param label the label of the line
	 * @param value the value of the line
	 * @return the protocol text
	 */
	public MoneyTransferProtocolText append(String label, Object value) {
		text.append(label).append(": ").append(value).append(System.lineSeparator());
		return this;
	}

	/**
	 * Appends a line with the given label if the value is not null.
	 *
	 * @param label the label of the line
	 * @param value the value of the line
	 * @return the protocol text
	 */
	public MoneyTransferProtocolText appendNonNull(String label, Object value) {
		return value == null ? this : append(label, value);
	}

	/**
	 * Appends a line with the given label if the value contains text.
	 *
	 * @param label the label of the line
	 * @param value the value of the line
	 * @return the protocol text
	 */
	public MoneyTransferProtocolText appendNonBlank(String label, Object value) {
		return value == null || value.toString().isBlank() ? this : append(label, value);
	}

	/**
	 * Returns the protocol text without the trailing line separator.
	 *
	 * @return the protocol text
	 */
	public String build() {
		return text.toString().trim();
	}
}
