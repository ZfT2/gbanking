package de.zft2.gbanking.enu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.CategoryRule.JoinType;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.ParameterDataType;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.gui.enu.ButtonContext;
import de.zft2.gbanking.gui.enu.FileType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.messages.Messages;

class LocalizedEnumValueTest {

	private final Locale previousLocale = Messages.getLocale();

	@AfterEach
	void restoreLocale() {
		Messages.setLocale(previousLocale);
	}

	@Test
	void allLocalizedEnumsShouldHaveGermanAndEnglishValues() {
		assertLocalized(AccountState.values());
		assertLocalized(AccountType.values());
		assertLocalized(BookingType.values());
		assertLocalized(ForeignChargeBearer.values());
		assertLocalized(InstituteStatus.values());
		assertLocalized(MoneyTransferStatus.values());
		assertLocalized(OrderType.values());
		assertLocalized(ParameterDataType.values());
		assertLocalized(SepaType.values());
		assertLocalized(Source.values());
		assertLocalized(StandingorderMode.values());
		assertLocalized(TanProcedure.values());
		assertLocalized(PageContext.values());
		assertLocalized(ButtonContext.values());
		assertLocalized(FileType.values());
		assertLocalized(JoinType.values());

		for (OrderType orderType : OrderType.values()) {
			assertNotEquals(orderType.getMessageKey() + "_PLURAL", orderType.getPlural());
		}
		for (ButtonContext buttonContext : ButtonContext.values()) {
			assertNotEquals(buttonContext.getMessageKey() + "_HEADLINE", buttonContext.getHeadline());
			assertNotEquals(buttonContext.getMessageKey() + "_DESCRIPTION", buttonContext.getDescription());
		}
	}

	@Test
	void displayAndParsingShouldFollowLocaleWithoutLosingLegacyGermanValues() {
		Messages.setLocale(Locale.ENGLISH);

		assertEquals("SEPA transfer", OrderType.TRANSFER.toString());
		assertEquals("SEPA transfers", OrderType.TRANSFER.getPlural());
		assertSame(OrderType.TRANSFER, OrderType.forString("SEPA-Überweisung"));
		assertSame(OrderType.TRANSFER, OrderType.forString("SEPA transfer"));
	}

	private void assertLocalized(LocalizedEnumValue[] values) {
		for (LocalizedEnumValue value : values) {
			assertNotEquals(value.getMessageKey(), value.getGermanName(), value.getMessageKey() + " is missing in German");
			assertNotEquals(value.getMessageKey(), value.getEnglishName(), value.getMessageKey() + " is missing in English");
		}
	}
}
