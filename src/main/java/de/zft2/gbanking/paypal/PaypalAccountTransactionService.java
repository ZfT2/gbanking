package de.zft2.gbanking.paypal;

import java.math.BigDecimal;
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

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.mapper.BookingCurrencyMapper;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;

public class PaypalAccountTransactionService extends AbstractDbService {

	private static final int RETRIEVAL_OVERLAP_DAYS = 1;
	private static final int MAX_HISTORY_YEARS = 3;

	private final PaypalSoapClient client;
	private final AccountTransactionService accountTransactionService;

	public PaypalAccountTransactionService() {
		this(new PaypalSoapClient(), ServiceRegistry.getService(AccountTransactionService.class));
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

			PaypalBalance balance = getBalance(bankAccess, apiPassword, bankAccount.getBaseCurrency());
			Instant end = Instant.now();
			Instant start = resolveStart(bankAccount, end);
			List<PaypalTransaction> transactions = retrieveAll(bankAccess, apiPassword, start, end);
			List<Booking> bookings = mapBookings(bankAccess, apiPassword, bankAccount, distinct(transactions));
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

	private PaypalBalance getBalance(BankAccess bankAccess, char[] apiPassword, Currency baseCurrency) throws InterruptedException {
		List<PaypalBalance> balances = client.getBalances(bankAccess.getPaypal().getApiUsername(), apiPassword,
				bankAccess.getPaypal().getApiSignature());
		for (PaypalBalance balance : balances) {
			Currency.forCode(balance.currency());
		}
		return balances.stream()
				.filter(balance -> baseCurrency == Currency.forCode(balance.currency()))
				.findFirst()
				.orElseThrow(() -> new PaypalApiException(getText("ERROR_PAYPAL_NO_BALANCES"), false));
	}

	private List<Booking> mapBookings(BankAccess bankAccess, char[] apiPassword, BankAccount bankAccount,
			List<PaypalTransaction> transactions) throws InterruptedException {
		List<Booking> bookings = new ArrayList<>(transactions.size());
		for (PaypalTransaction transaction : transactions) {
			Currency transactionCurrency = Currency.forCode(transaction.currency());
			PaypalTransactionDetails details = transactionCurrency == bankAccount.getBaseCurrency() ? null
					: client.getTransactionDetails(bankAccess.getPaypal().getApiUsername(), apiPassword,
							bankAccess.getPaypal().getApiSignature(), transaction.transactionId());
			bookings.add(mapBooking(bankAccount, transaction, details));
		}
		return bookings;
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

	private List<PaypalTransaction> retrieveAll(BankAccess bankAccess, char[] apiPassword, Instant start, Instant end)
			throws InterruptedException {
		List<PaypalTransaction> transactions = client.searchTransactions(bankAccess.getPaypal().getApiUsername(), apiPassword,
				bankAccess.getPaypal().getApiSignature(), start, end);
		if (transactions.size() < PaypalSoapClient.MAX_TRANSACTION_RESULTS) {
			return transactions;
		}
		long intervalSeconds = Duration.between(start, end).getSeconds();
		if (intervalSeconds < 2) {
			throw new PaypalApiException("PayPal returned at least 100 transactions for one second", false);
		}

		Instant midpoint = start.plusSeconds(intervalSeconds / 2);
		List<PaypalTransaction> result = new ArrayList<>();
		result.addAll(retrieveAll(bankAccess, apiPassword, start, midpoint));
		result.addAll(retrieveAll(bankAccess, apiPassword, midpoint, end));
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
		return mapBooking(bankAccount, transaction, null);
	}

	Booking mapBooking(BankAccount bankAccount, PaypalTransaction transaction, PaypalTransactionDetails transactionDetails) {
		Booking booking = new Booking();
		LocalDate bookingDate = transaction.timestamp().atZone(ZoneId.systemDefault()).toLocalDate();
		booking.setAccountId(bankAccount.getId());
		booking.setDateBooking(bookingDate);
		booking.setDateValue(bookingDate);
		booking.setPurpose(purpose(transaction));
		BigDecimal baseAmount = transactionDetails != null
				? signedAmount(transactionDetails.settleAmount(), transaction.netAmount().signum()) : transaction.netAmount();
		String baseAmountCurrency = transactionDetails != null ? transactionDetails.settleCurrency() : transaction.currency();
		BigDecimal foreignAmount = transactionDetails != null ? transaction.netAmount() : null;
		String foreignCurrency = transactionDetails != null ? transaction.currency() : null;
		BigDecimal exchangeRate = transactionDetails != null ? transactionDetails.exchangeRate() : null;
		BookingCurrencyMapper.mapAmounts(booking, baseAmount, baseAmountCurrency, bankAccount.getBaseCurrency(),
				foreignAmount, foreignCurrency, exchangeRate);
		booking.setFee(BookingCurrencyMapper.createFee(transaction.feeAmount(), transaction.feeCurrency(), bankAccount.getBaseCurrency()));
		booking.setBookingType(booking.getAmount().signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT);
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

	private BigDecimal signedAmount(BigDecimal amount, int sign) {
		return amount == null ? null : sign < 0 ? amount.abs().negate() : amount.abs();
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
