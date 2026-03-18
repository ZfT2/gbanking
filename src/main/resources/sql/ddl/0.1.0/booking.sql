[SQL_SETUP_DROP_BOOKING]
DROP TABLE IF EXISTS booking;

;

[SQL_SETUP_DROP_BOOKING_CATEGORY]
DROP TABLE IF EXISTS booking_category;

;

[SQL_SETUP_CREATE_BOOKING]
CREATE TABLE booking (
   id INTEGER PRIMARY KEY,
   account_id INTEGER NOT NULL,
   dateBooking TEXT,
   dateValue TEXT,
   purpose TEXT,
   amount REAL,
   currency TEXT,
   sepaCustomerRef,
   sepaCreditorId,
   sepaEndToEnd,
   sepaMandate,
   sepaPersonId,
   sepaPurpose,
   sepaTyp,
   bookingType TEXT,
   bookingSource TEXT,
   crossAccount_id INTEGER,
   recipient_id INTEGER,
   category_id INTEGER,
   crossBooking_id INTEGER,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
   FOREIGN KEY(crossAccount_id) REFERENCES bankAccount(id) ON DELETE SET NULL,
   FOREIGN KEY(recipient_id) REFERENCES recipient(id),
   FOREIGN KEY(category_id) REFERENCES category(id),
   CHECK (bookingType IN ("DEPOSIT", "REMOVAL",  "INTEREST", "INTEREST_CHARGE", "REBOOKING_OUT", "REBOOKING_IN")),
   CHECK (bookingSource IN ("ONLINE", "MANUELL", "IMPORT", "IMPORT_INITIAL", "ONLINE_NEW", "MANUELL_NEW", "IMPORT_NEW", "IMPORT_INITIAL_NEW")));

;

[SQL_SETUP_CREATE_BOOKING_CATEGORY]
CREATE TABLE booking_category (
  id INTEGER PRIMARY KEY,
  booking_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  categoryRuleMode INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE,
  FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE CASCADE);
;
