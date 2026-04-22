package de.zft2.gbanking.db.dao.logic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;

public class StatementsLogicBooking extends StatementsLogicDefault<Booking, Void> implements StatementsLogic<Booking, Void> {

	private static Logger log = LogManager.getLogger(StatementsLogicBooking.class);
	
	@Override
	public SqlParameter getSqlParameter(Booking bK) {
		return new SqlParameter(null, null, false, true);
	}
	
	@Override
	public void addOneToOneRelations(Booking booking) {
		
		log.debug("addOneToOneRelations()");
		
		booking.setRecipient(getById(Recipient.class, booking.getRecipientId()));
		booking.setCategory(getById(Category.class, booking.getCategoryId()));
	}
}
