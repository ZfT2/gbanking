package de.zft2.gbanking.service.bankaccess;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.mapper.HbciMapper;
import de.zft2.gbanking.service.AbstractDbService;

public class BankAccountClosureService extends AbstractDbService {

	public List<BankAccount> findMissingAccounts(BankAccess access) {
		List<BankAccount> stored = dbController.getAll(BankAccount.class);
		List<BankAccount> returned = access.getReturnedAccounts() != null ? access.getReturnedAccounts() : List.of();
		List<BankAccount> bankReferences = new ArrayList<>(returned);
		if (access.getAccessType() == BankAccessType.HBCI) {
			BankAccount reference = new BankAccount();
			reference.setBlz(access.getFints().getBlz());
			bankReferences.add(reference);
		}
		if (bankReferences.stream().noneMatch(account -> trimToNull(account.getBlz()) != null || trimToNull(account.getBic()) != null)) {
			stored.stream().filter(account -> access.getId() > 0
					&& Integer.valueOf(access.getId()).equals(account.getBankAccessId())).forEach(bankReferences::add);
		} else {
			bankReferences.addAll(stored.stream()
					.filter(account -> trimToNull(account.getBlz()) != null && bankReferences.stream()
							.anyMatch(reference -> trimToNull(reference.getBlz()) != null && sameBank(account, reference))).toList());
		}
		return stored.stream()
				.filter(account -> bankReferences.stream().anyMatch(reference -> sameBank(account, reference)))
				.filter(account -> returned.stream().noneMatch(remote -> sameAccount(account, remote)))
				.toList();
	}

	public void saveClosureDates(Map<Integer, LocalDate> dates) {
		dbController.executeInTransaction(() -> dates.forEach((id, date) -> {
			BankAccount account = dbController.getById(BankAccount.class, id);
			if (account == null) {
				throw new GBankingException(getText("ALERT_ACCOUNT_SAVE_FAILED"));
			}
			if (!Objects.equals(account.getClosedAt(), date)) {
				account.setClosedAt(date);
				if (dbController.insertOrUpdate(account) == null) {
					throw new GBankingException(getText("ALERT_ACCOUNT_SAVE_FAILED"));
				}
			}
		}));
	}

	static boolean sameBank(BankAccount first, BankAccount second) {
		String firstBlz = trimToNull(first.getBlz());
		String secondBlz = trimToNull(second.getBlz());
		if (firstBlz != null && secondBlz != null) {
			return firstBlz.equals(secondBlz);
		}
		String firstBic = trimToNull(first.getBic());
		String secondBic = trimToNull(second.getBic());
		return firstBic != null && secondBic != null && firstBic.equalsIgnoreCase(secondBic);
	}

	private static boolean sameAccount(BankAccount stored, BankAccount remote) {
		return stored.getId() == remote.getId() && stored.getId() > 0
				|| sameBank(stored, remote) && (BankAccessService.representsSameProviderAccount(stored, remote)
						|| sameAccountNumber(stored, remote));
	}

	private static boolean sameAccountNumber(BankAccount stored, BankAccount remote) {
		String number = HbciMapper.normalizeAccountNumber(stored.getNumber());
		return number != null && number.equals(HbciMapper.normalizeAccountNumber(remote.getNumber()))
				&& Objects.equals(HbciMapper.normalizeAccountNumber(stored.getSubnumber()),
						HbciMapper.normalizeAccountNumber(remote.getSubnumber()));
	}
}
