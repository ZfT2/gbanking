package de.zft2.gbanking.service.account;

import java.util.List;
import java.util.Set;

import de.zft2.gbanking.db.dao.Booking;

@FunctionalInterface
public interface BookingDuplicateReviewHandler {

	Set<Booking> selectImports(List<Match> matches);

	record Match(Booking existing, Booking incoming) {
	}
}
