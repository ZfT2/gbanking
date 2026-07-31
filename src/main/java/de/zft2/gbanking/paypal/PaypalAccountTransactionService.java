package de.zft2.gbanking.paypal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;

public class PaypalAccountTransactionService implements BaseMessagesDb {

	private static final int RETRIEVAL_OVERLAP_DAYS = 1;
	private static final int MAX_HISTORY_YEARS = 3;

	private final PaypalSoapClient client;
	private final AccountTransactionService accountTransactionService;

	public PaypalAccountTransactionService(AccountTransactionService accountTransactionService) {
		this(new PaypalSoapClient(), accountTransactionService);
	}

	PaypalAccountTransactionService(PaypalSoapClient client, AccountTransactionService accountTransactionService) {
		this.client = client;
		this.accountTransactionService = accountTransactionService;
	}

	public AccountTransactionRetrievalResult retrieve(BankAccount bankAccount, char[] apiPassword) {
		try {
			BankAccess bankAccess = getPaypalAccess(bankAccount);
			if (bankAccess == null) {
				return persist(bankAccount, AccountTransactionRetrievalResult.failure(
						getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_NO_BANK_ACCESS")));
			}

			PaypalBalance balance = getBalance(bankAccess, apiPassword, bankAccount.getCurrency());
			Instant end = Instant.now();
			Instant start = resolveStart(bankAccount, end);
			List<PaypalTransaction> transactions = retrieveAll(bankAccess, apiPassword, start, end, bankAccount.getCurrency());
			List<Booking> bookings = distinct(transactions).stream().map(transaction -> mapBooking(bankAccount, transaction)).toList();
			return accountTransactionService.persistExternalAccountData(bankAccount, balance.amount(), bookings);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return persist(bankAccount, AccountTransactionRetrievalResult.cancelled(
					getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_CANCELLED")));
		} catch (PaypalApiException exception) {
			AccountTransactionRetrievalResult result = exception.isAuthenticationFailure()
					? AccountTransactionRetrievalResult.wrongPinFailure(exception.getMessage())
					: AccountTransactionRetrievalResult.failure(exception.getMessage());
			return persist(bankAccount, result);
		} catch (RuntimeException exception) {
			persistSafely(bankAccount, AccountTransactionRetrievalResult.failure(
					getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_STORAGE_FAILED")), exception);
			throw exception;
		} finally {
			HbciSessionRunner.clearSecret(apiPassword);
		}
	}

	private BankAccess getPaypalAccess(BankAccount bankAccount) {
		if (bankAccount == null || bankAccount.getBankAccessId() == null) {
			return null;
		}
		BankAccess bankAccess = dbController.getBankAccessById(bankAccount.getBankAccessId());
		return PaypalSupport.isPaypal(bankAccess) && bankAccess.isActive() ? bankAccess : null;
	}

	private PaypalBalance getBalance(BankAccess bankAccess, char[] apiPassword, String currency) throws InterruptedException {
		return client.getBalances(bankAccess.getPaypalApiUsername(), apiPassword, bankAccess.getPaypalApiSignature()).stream()
				.filter(balance -> balance.currency().equalsIgnoreCase(currency))
				.findFirst()
				.orElseThrow(() -> new PaypalApiException(getText("ERROR_PAYPAL_NO_BALANCES"), false));
	}

	Instant resolveStart(BankAccount bankAccount, Instant end) {
		Instant earliestPaypalDate = end.atZone(ZoneOffset.UTC).minusYears(MAX_HISTORY_YEARS).toInstant();
		LocalDate lastBookingDate = accountTransactionService.getLastOnlineBookingDate(bankAccount);
		if (lastBookingDate == null) {
			return earliestPaypalDate;
		}
		Instant overlappingStart = lastBookingDate.minusDays(RETRIEVAL_OVERLAP_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant();
		return overlappingStart.isBefore(earliestPaypalDate) ? earliestPaypalDate : overlappingStart;
	}

	private List<PaypalTransaction> retrieveAll(BankAccess bankAccess, char[] apiPassword, Instant start, Instant end, String currency)
			throws InterruptedException {
		List<PaypalTransaction> transactions = client.searchTransactions(bankAccess.getPaypalApiUsername(), apiPassword,
				bankAccess.getPaypalApiSignature(), start, end, currency);
		if (transactions.size() < PaypalSoapClient.MAX_TRANSACTION_RESULTS) {
			return transactions;
		}
		long intervalSeconds = Duration.between(start, end).getSeconds();
		if (intervalSeconds < 2) {
			throw new PaypalApiException("PayPal returned at least 100 transactions for one second", false);
		}

		Instant midpoint = start.plusSeconds(intervalSeconds / 2);
		List<PaypalTransaction> result = new ArrayList<>();
		result.addAll(retrieveAll(bankAccess, apiPassword, start, midpoint, currency));
		result.addAll(retrieveAll(bankAccess, apiPassword, midpoint, end, currency));
		return result;
	}

	private List<PaypalTransaction> distinct(List<PaypalTransaction> transactions) {
		Map<String, PaypalTransaction> distinctTransactions = new LinkedHashMap<>();
		for (PaypalTransaction transaction : transactions) {
			distinctTransactions.putIfAbsent(stableReference(transaction), transaction);
		}
		return List.copyOf(distinctTransactions.values());
	}

	Booking mapBooking(BankAccount bankAccount, PaypalTransaction transaction) {
		Booking booking = new Booking();
		LocalDate bookingDate = transaction.timestamp().atZone(ZoneId.systemDefault()).toLocalDate();
		booking.setAccountId(bankAccount.getId());
		booking.setDateBooking(bookingDate);
		booking.setDateValue(bookingDate);
		booking.setPurpose(purpose(transaction));
		booking.setAmount(transaction.netAmount());
		booking.setCurrency(transaction.currency());
		booking.setBookingType(transaction.netAmount().signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT);
		booking.setSource(Source.ONLINE_NEW);
		booking.setAdditionalDetails(additionalDetails(transaction));
		booking.setRecipient(recipient(transaction));
		return booking;
	}

	private BookingAdditionalDetails additionalDetails(PaypalTransaction transaction) {
		BookingAdditionalDetails details = new BookingAdditionalDetails();
		details.setInstref(stableReference(transaction));
		details.setGvcode(transaction.type());
		details.setText(transaction.status());
		details.setChargeValue(transaction.feeAmount());
		return details;
	}

	private Recipient recipient(PaypalTransaction transaction) {
		if (!hasText(transaction.payerEmail()) && !hasText(transaction.payerDisplayName())) {
			return null;
		}
		Recipient recipient = new Recipient();
		recipient.setName(firstText(transaction.payerDisplayName(), transaction.payerEmail()));
		recipient.setAccountNumber(emptyToNull(transaction.payerEmail()));
		recipient.setBank(PaypalSupport.DISPLAY_NAME);
		recipient.setSource(Source.ONLINE);
		return recipient;
	}

	private String purpose(PaypalTransaction transaction) {
		return Arrays.stream(new String[] { transaction.type(), transaction.payerDisplayName(), transaction.payerEmail() })
				.filter(this::hasText)
				.distinct()
				.reduce((left, right) -> left + " - " + right)
				.orElse(PaypalSupport.DISPLAY_NAME);
	}

	private String stableReference(PaypalTransaction transaction) {
		return String.join(":", PaypalSupport.BANK_CODE, transaction.transactionId(), transaction.type(), transaction.currency(),
				transaction.timestamp().toString(), transaction.netAmount().toPlainString());
	}

	private String firstText(String first, String second) {
		return hasText(first) ? first : emptyToNull(second);
	}

	private String emptyToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private AccountTransactionRetrievalResult persist(BankAccount account, AccountTransactionRetrievalResult result) {
		accountTransactionService.persistExternalRetrievalStatus(account, result);
		return result;
	}

	private void persistSafely(BankAccount account, AccountTransactionRetrievalResult result, RuntimeException originalFailure) {
		try {
			persist(account, result);
		} catch (RuntimeException statusFailure) {
			originalFailure.addSuppressed(statusFailure);
		}
	}
}
