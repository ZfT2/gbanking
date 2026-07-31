package de.zft2.gbanking.file.imp;

import de.zft2.core.exception.ConfigurationException;
import de.zft2.core.process.BookingProcessor;
import de.zft2.gbanking.file.imp.dto.ImportBankAccount;
import de.zft2.gbanking.file.imp.dto.ImportBooking;

class ImportBookingProcessor extends BookingProcessor<ImportBooking, ImportBankAccount> {

	ImportBookingProcessor() throws ConfigurationException {
		super();
	}
}
