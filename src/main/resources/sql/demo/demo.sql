# Demo data for a freshly initialized tenant database.
# Enum values are stored using their stable database IDs.

[SQL_DEMO_INSERT_BANK_ACCESS]
INSERT INTO bankAccess (
    id, bankName, active, accessType, updatedAt
) VALUES (
    900000, 'DemoBank - keine Online-Verbindung', 1, 1, '2026-01-01 09:00:00.000'
);

[SQL_DEMO_INSERT_BANK_ACCESS_FINTS]
INSERT INTO bankAccessFints (
    bankAccess_id, country, blz, hbciURL, port, userId, customerId, sysId,
    tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType
) VALUES (
    900000, 'DE', '99999999', 'https://demo.invalid/fints', 443, 'demo.user',
    'demo.customer', 'demo.system', 6, '940', '300', '1', '1', 2
);

[SQL_DEMO_INSERT_BANK_ACCOUNTS]
INSERT INTO bankAccount (
    id, bankAccess_id, parentAccount_id, providerAccountId, accountName, baseCurrency, accountType, accountSource,
    iban, bic, number, subNumber, bankName, blz, hbciAccountType, accountLimit,
    customerId, ownerName, ownerName2, country, creditorId, isSEPAAccount,
    isOfflineAccount, accountState, balance, createdAt, updatedAt
) VALUES
    (900001, 900000, NULL, NULL, 'Girokonto Demo', 1, 1, 1,
     'DE93999999990000000001', 'DEMODEFFXXX', '0000000001', '00', 'DemoBank', '99999999', 1, '1000.00',
     'demo.customer', 'Max Mustermann', NULL, 'DE', NULL, 1, 0, 1, 4860.35,
     '2026-01-01 09:00:00.000', '2026-06-30 18:00:00.000'),
    (900002, 900000, NULL, NULL, 'Tagesgeld Reserve', 1, 2, 1,
     'DE66999999990000000002', 'DEMODEFFXXX', '0000000002', '00', 'DemoBank', '99999999', 10, '0.00',
     'demo.customer', 'Max Mustermann', NULL, 'DE', NULL, 1, 0, 1, 6354.75,
     '2026-01-01 09:00:00.000', '2026-06-30 18:00:00.000'),
    (900003, 900000, 900001, NULL, 'Kreditkarte Demo', 1, 10, 1,
     'DE39999999990000000003', 'DEMODEFFXXX', '0000000003', '00', 'DemoBank', '99999999', 30, '2500.00',
     'demo.customer', 'Max Mustermann', NULL, 'DE', NULL, 0, 0, 1, -642.40,
     '2026-01-01 09:00:00.000', '2026-06-30 18:00:00.000'),
    (900004, NULL, NULL, NULL, 'Haushaltskasse', 1, 13, 5,
     NULL, NULL, 'BAR-DEMO', NULL, NULL, NULL, 0, '0.00',
     NULL, 'Max Mustermann', NULL, 'DE', NULL, 0, 1, 1, 250.00,
     '2026-01-01 09:00:00.000', '2026-06-30 18:00:00.000');

[SQL_DEMO_INSERT_BANK_ACCOUNT_IDENTIFIERS]
INSERT INTO bankAccountIdentifiers (id, account_id, propertyType, value) VALUES
    (900011, 900001, 2, 'DE93999999990000000001'),
    (900012, 900001, 2, '0000000001'),
    (900013, 900002, 2, 'DE66999999990000000002'),
    (900014, 900002, 2, '0000000002'),
    (900015, 900003, 1, 'DE39999999990000000003'),
    (900016, 900003, 1, '0000000003');

[SQL_DEMO_INSERT_RECIPIENTS]
INSERT INTO recipient (
    id, name, iban, bic, accountnumber, blz, bank, source, note, isDefault, updatedAt
) VALUES
    (930001, 'Vermieter Muster GmbH', 'DE12999999990000000101', 'DEMODEFFXXX', NULL, NULL,
     'Musterbank', 1, 'Monatliche Warmmiete', 1, '2026-06-30 18:00:00.000'),
    (930002, 'Stadtwerke Musterstadt', 'DE82999999990000000102', 'DEMODEFFXXX', NULL, NULL,
     'Kommunalbank', 7, 'Strom und Wasser', 0, '2026-06-30 18:00:00.000'),
    (930003, 'Demo Supermarkt', 'DE55999999990000000103', 'DEMODEFFXXX', NULL, NULL,
     'Handelsbank', 1, 'Lebensmittel', 0, '2026-06-30 18:00:00.000'),
    (930004, 'Reisebuero Sonnenschein', 'DE28999999990000000104', 'DEMODEFFXXX', NULL, NULL,
     'Reisebank', 5, 'Urlaubsreisen', 0, '2026-06-30 18:00:00.000'),
    (930005, 'Apotheke am Markt', 'DE98999999990000000105', 'DEMODEFFXXX', NULL, NULL,
     'Gesundheitsbank', 7, NULL, 0, '2026-06-30 18:00:00.000'),
    (930006, 'International Demo Supplies', 'DE71999999990000000106', 'DEMODEFFXXX', NULL, NULL,
     'Demo Foreign Bank', 5, 'Lieferant fuer Auslandsauftrag', 0, '2026-06-30 18:00:00.000');

[SQL_DEMO_INSERT_CATEGORIES]
INSERT INTO category (id, name, parent_id, updatedAt) VALUES
    (920001, 'Einnahmen', NULL, '2026-01-01 09:00:00.000'),
    (920002, 'Gehalt', 920001, '2026-01-01 09:00:00.000'),
    (920003, 'Zinsen', 920001, '2026-01-01 09:00:00.000'),
    (920010, 'Wohnen', NULL, '2026-01-01 09:00:00.000'),
    (920011, 'Miete', 920010, '2026-01-01 09:00:00.000'),
    (920012, 'Energie', 920010, '2026-01-01 09:00:00.000'),
    (920020, 'Haushalt', NULL, '2026-01-01 09:00:00.000'),
    (920021, 'Lebensmittel', 920020, '2026-01-01 09:00:00.000'),
    (920022, 'Gesundheit', 920020, '2026-01-01 09:00:00.000'),
    (920030, 'Freizeit', NULL, '2026-01-01 09:00:00.000'),
    (920031, 'Reisen', 920030, '2026-01-01 09:00:00.000'),
    (920040, 'Sparen', NULL, '2026-01-01 09:00:00.000'),
    (920041, 'Ruecklagen', 920040, '2026-01-01 09:00:00.000'),
    (920050, 'Mobilitaet', NULL, '2026-01-01 09:00:00.000'),
    (920051, 'Versicherungen', NULL, '2026-01-01 09:00:00.000'),
    (920052, 'Kommunikation', NULL, '2026-01-01 09:00:00.000'),
    (920053, 'Bildung', NULL, '2026-01-01 09:00:00.000'),
    (920054, 'Spenden', NULL, '2026-01-01 09:00:00.000');

[SQL_DEMO_INSERT_CATEGORY_RULES]
INSERT INTO categoryRule (
    id, name, category_id, filterDateFrom, filterDateTo, filterAmountFrom, filterAmountTo,
    filterRecipientName, filterRecipientIban, filterRecipientAccountNumber, filterPurpose,
    filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt
) VALUES
    (921001, 'Supermarkt automatisch', 920021, NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, 'Supermarkt', 0, 0, 1, '2026-01-01 09:00:00.000'),
    (921002, 'Monatliche Miete', 920011, NULL, NULL, -1300.00, -1000.00,
     'Vermieter Muster GmbH', NULL, NULL, NULL, 0, 0, 1, '2026-01-01 09:00:00.000');

[SQL_DEMO_LINK_CATEGORY_RULES]
INSERT INTO categoryRule_bankAccount (id, categoryRule_id, account_id, updatedAt) VALUES
    (922001, 921001, 900001, '2026-01-01 09:00:00.000'),
    (922002, 921001, 900003, '2026-01-01 09:00:00.000'),
    (922003, 921002, 900001, '2026-01-01 09:00:00.000');

[SQL_DEMO_INSERT_BOOKINGS]
INSERT INTO booking (
    id, account_id, parentBooking_id, dateBooking, dateValue, purpose, amount,
    bookingType, bookingSource, crossAccount_id, recipient_id, category_id,
    categoryRule_id, crossBooking_id, updatedAt
) VALUES
    (910001, 900001, NULL, '2026-01-03 00:00:00.000', '2026-01-03 00:00:00.000',
     'Gehalt Januar Demo GmbH', 3200.00, 1, 1, NULL, NULL, 920002, NULL, NULL, '2026-01-03 08:00:00.000'),
    (910002, 900001, NULL, '2026-01-05 00:00:00.000', '2026-01-05 00:00:00.000',
     'Miete Januar', -1200.00, 2, 1, NULL, 930001, 920011, 921002, NULL, '2026-01-05 08:00:00.000'),
    (910003, 900001, NULL, '2026-01-09 00:00:00.000', '2026-01-09 00:00:00.000',
     'Demo Supermarkt Wocheneinkauf', -86.40, 2, 1, NULL, 930003, 920021, 921001, NULL, '2026-01-09 18:00:00.000'),
    (910004, 900001, NULL, '2026-02-10 00:00:00.000', '2026-02-10 00:00:00.000',
     'Stromabschlag Februar', -78.90, 2, 7, NULL, 930002, 920012, NULL, NULL, '2026-02-10 08:00:00.000'),
    (910005, 900001, NULL, '2026-02-20 00:00:00.000', '2026-02-20 00:00:00.000',
     'Erstattung Versicherung', 75.00, 1, 7, NULL, NULL, 920001, NULL, NULL, '2026-02-20 08:00:00.000'),
    (910006, 900003, NULL, '2026-02-27 00:00:00.000', '2026-02-27 00:00:00.000',
     'Vorgemerkt: Hotelreservierung', -240.00, 2, 2, NULL, 930004, 920031, NULL, NULL, '2026-02-27 18:00:00.000'),
    (910007, 900001, NULL, '2026-03-03 00:00:00.000', '2026-03-03 00:00:00.000',
     'Gehalt Maerz Demo GmbH', 3200.00, 1, 1, NULL, NULL, 920002, NULL, NULL, '2026-03-03 08:00:00.000'),
    (910008, 900001, NULL, '2026-03-05 00:00:00.000', '2026-03-05 00:00:00.000',
     'Miete Maerz', -1200.00, 2, 1, NULL, 930001, 920011, 921002, NULL, '2026-03-05 08:00:00.000'),
    (910009, 900001, NULL, '2026-03-12 00:00:00.000', '2026-03-12 00:00:00.000',
     'Demo Supermarkt Familieneinkauf', -112.35, 2, 1, NULL, 930003, 920021, 921001, NULL, '2026-03-12 18:00:00.000'),
    (910012, 900003, NULL, '2026-04-14 00:00:00.000', '2026-04-14 00:00:00.000',
     'Hotel Sonnenschein', -380.00, 2, 1, NULL, 930004, 920031, NULL, NULL, '2026-04-14 18:00:00.000'),
    (910013, 900002, NULL, '2026-04-30 00:00:00.000', '2026-04-30 00:00:00.000',
     'Zinsgutschrift April', 4.75, 3, 1, NULL, NULL, 920003, NULL, NULL, '2026-04-30 18:00:00.000'),
    (910014, 900001, NULL, '2026-05-18 00:00:00.000', '2026-05-18 00:00:00.000',
     'Restaurant mit Freunden', -62.30, 2, 5, NULL, NULL, NULL, NULL, NULL, '2026-05-18 22:00:00.000'),
    (910015, 900003, NULL, '2026-05-20 00:00:00.000', '2026-05-20 00:00:00.000',
     'Stornierung Online-Einkauf', 80.00, 7, 1, NULL, NULL, 920020, NULL, NULL, '2026-05-20 12:00:00.000'),
    (910016, 900002, NULL, '2026-05-31 00:00:00.000', '2026-05-31 00:00:00.000',
     'Entgelt Verwahrung', -1.50, 4, 1, NULL, NULL, 920040, NULL, NULL, '2026-05-31 18:00:00.000'),
    (910020, 900001, NULL, '2026-06-08 00:00:00.000', '2026-06-08 00:00:00.000',
     'Drogerie und Haushalt', -120.00, 2, 1, NULL, NULL, NULL, NULL, NULL, '2026-06-08 18:00:00.000'),
    (910021, 900001, 910020, '2026-06-08 00:00:00.000', '2026-06-08 00:00:00.000',
     'Haushaltswaren', -70.00, 2, 5, NULL, 930003, 920021, NULL, NULL, '2026-06-08 18:00:00.000'),
    (910022, 900001, 910020, '2026-06-08 00:00:00.000', '2026-06-08 00:00:00.000',
     'Apothekenartikel', -50.00, 2, 5, NULL, 930005, 920022, NULL, NULL, '2026-06-08 18:00:00.000'),
    (910023, 900004, NULL, '2026-06-12 00:00:00.000', '2026-06-12 00:00:00.000',
     'Bargeld fuer Haushaltskasse', 100.00, 1, 5, NULL, NULL, 920020, NULL, NULL, '2026-06-12 18:00:00.000'),
    (910024, 900001, NULL, '2026-06-15 00:00:00.000', '2026-06-15 00:00:00.000',
     'Ausgefuehrter Reiseauftrag', -350.00, 2, 6, NULL, 930004, 920031, NULL, NULL, '2026-06-15 18:00:00.000'),
    (910025, 900001, NULL, '2026-06-30 00:00:00.000', '2026-06-30 00:00:00.000',
     'Vorgemerkt: Kartenzahlung Demo Cafe', -35.00, 2, 2, NULL, NULL, 920030, NULL, NULL, '2026-06-30 18:00:00.000');

[SQL_DEMO_INSERT_ADDITIONAL_BOOKINGS]
WITH RECURSIVE demoBooking(sequenceNumber) AS (
    SELECT 1
    UNION ALL
    SELECT sequenceNumber + 1
    FROM demoBooking
    WHERE sequenceNumber < 200
)
INSERT INTO booking (
    id, account_id, parentBooking_id, dateBooking, dateValue, purpose, amount,
    bookingType, bookingSource, crossAccount_id, recipient_id, category_id,
    categoryRule_id, crossBooking_id, updatedAt
)
SELECT
    950000 + sequenceNumber,
    CASE
        WHEN sequenceNumber % 10 = 0 THEN 900004
        WHEN sequenceNumber % 4 = 0 THEN 900003
        ELSE 900001
    END,
    NULL,
    strftime('%Y-%m-%d 00:00:00.000', '2024-03-01', printf('+%d days', (sequenceNumber - 1) * 4)),
    strftime('%Y-%m-%d 00:00:00.000', '2024-03-01', printf('+%d days', (sequenceNumber - 1) * 4)),
    CASE (sequenceNumber - 1) % 5
        WHEN 0 THEN 'Fahrtkosten und Monatskarte'
        WHEN 1 THEN 'Versicherungsbeitrag'
        WHEN 2 THEN 'Telefon und Internet'
        WHEN 3 THEN 'Kurs und Fachliteratur'
        ELSE 'Spende an gemeinnuetzige Organisation'
    END || printf(' #%03d', sequenceNumber),
    -ROUND(CASE (sequenceNumber - 1) % 5
        WHEN 0 THEN 18.00 + (sequenceNumber % 75)
        WHEN 1 THEN 45.00 + (sequenceNumber % 120)
        WHEN 2 THEN 20.00 + (sequenceNumber % 35)
        WHEN 3 THEN 12.00 + (sequenceNumber % 90)
        ELSE 5.00 + (sequenceNumber % 45)
    END + (sequenceNumber % 100) / 100.0, 2),
    2,
    CASE WHEN sequenceNumber % 10 = 0 THEN 5 ELSE 1 END,
    NULL,
    NULL,
    920050 + ((sequenceNumber - 1) % 5),
    NULL,
    NULL,
    strftime('%Y-%m-%d 12:00:00.000', '2024-03-01', printf('+%d days', (sequenceNumber - 1) * 4))
FROM demoBooking;

[SQL_DEMO_INSERT_CROSS_BOOKINGS]
INSERT INTO booking (
    id, account_id, parentBooking_id, dateBooking, dateValue, purpose, amount,
    bookingType, bookingSource, crossAccount_id, recipient_id, category_id,
    categoryRule_id, crossBooking_id, updatedAt
) VALUES
    (910010, 900001, NULL, '2026-03-20 00:00:00.000', '2026-03-20 00:00:00.000',
     'Umbuchung auf Tagesgeld', -500.00, 5, 5, 900002, NULL, 920041, NULL, NULL, '2026-03-20 18:00:00.000'),
    (910011, 900002, NULL, '2026-03-20 00:00:00.000', '2026-03-20 00:00:00.000',
     'Umbuchung vom Girokonto', 500.00, 6, 5, 900001, NULL, 920041, NULL, 910010, '2026-03-20 18:00:00.000');

[SQL_DEMO_COMPLETE_CROSS_BOOKING]
UPDATE booking
SET crossBooking_id = 910011
WHERE id = 910010;

[SQL_DEMO_INSERT_BOOKING_SEPA_DETAILS]
INSERT INTO bookingAdditionalSepa (
    id, booking_id, sepa_customer_ref, sepa_creditor_id, sepa_end_to_end,
    sepa_mandate, sepa_person_id, sepa_purpose, sepa_typ, updatedAt
) VALUES
    (911001, 910002, 'MREF-2026-01', 'DE98ZZZ09999999999', 'E2E-RENT-2026-01',
     'MANDAT-MIETE-01', NULL, 'Miete', 4, '2026-01-05 08:00:00.000'),
    (911002, 910024, 'CREF-REISE-2026', NULL, 'E2E-REISE-2026',
     NULL, NULL, 'Reise', 2, '2026-06-15 18:00:00.000');

[SQL_DEMO_INSERT_BOOKING_NOTES]
INSERT INTO bookingAdditionalNote (id, booking_id, note, review_required, updatedAt) VALUES
    (912001, 910014, 'Beleg und Aufteilung noch pruefen.', 1, '2026-05-18 22:05:00.000'),
    (912002, 910024, 'Auftrag wurde aus der Auftragsverwaltung erzeugt.', 0, '2026-06-15 18:05:00.000');

[SQL_DEMO_INSERT_BOOKING_ADDITIONAL_DETAILS]
INSERT INTO bookingAdditional (
    id, booking_id, add_instref, add_gvcode, add_text, add_primanota, add_key,
    add_is_storno, add_raw_data, add_is_sepa,
    add_is_camt, add_bank_saldo, updatedAt
) VALUES
    (913001, 910004, 'DEMO-INST-20260210', '105', 'SEPA-BASISLASTSCHRIFT', '4711', '05',
     0, 'Demo-Rohdaten fuer die Detailansicht', 1, 1, 4781.45, '2026-02-10 08:00:00.000');

[SQL_DEMO_INSERT_CREDITCARD_DETAILS]
INSERT INTO bookingAdditionalCreditcard (
    id, booking_id, creditcard_transaction_date, creditcard_type,
    creditcard_merchant_area, creditcard_merchant_category, updatedAt
) VALUES
    (914001, 910012, '2026-04-12 00:00:00.000', 'VISA',
     'New York', 'Hotel', '2026-04-14 18:00:00.000');

[SQL_DEMO_INSERT_FOREIGNCURRENCY_DETAILS]
INSERT INTO bookingAdditionalForeigncurrency (
    id, booking_id, foreignAmount, foreignCurrency, exchangeRateToBaseCurrency, updatedAt
) VALUES
    (915001, 910012, -410.00, 2, 0.9268292683, '2026-04-14 18:00:00.000');

[SQL_DEMO_INSERT_BOOKING_FEE]
INSERT INTO bookingFee (id, booking_id, amount, currency, updatedAt) VALUES
    (916001, 910012, 5.00, 1, '2026-04-14 18:00:00.000');

[SQL_DEMO_INSERT_MONEY_TRANSFER_HISTORY]
INSERT INTO moneytransfer (
	 id, account_id, moneytransferType, recipient_id, purpose, purposeCode, amount,
	 executionDate, executionDay, moneytransferStatus, standingorderMode,
	 historyorder_id, updatedAt
) VALUES
	 (940006, 900001, 4, 930002, 'Alter Stromabschlag', 'SUPP', 75.00,
	  '10.01.26', 10, 8, 1, NULL, '2026-02-01 10:00:00.000');

[SQL_DEMO_INSERT_MONEY_TRANSFERS]
INSERT INTO moneytransfer (
	 id, account_id, moneytransferType, recipient_id, purpose, purposeCode, amount,
	 executionDate, executionDay, moneytransferStatus, standingorderMode,
	 historyorder_id, updatedAt
) VALUES
	 (940001, 900001, 1, 930004, 'Restzahlung Sommerurlaub', 'OTHR', 650.00,
	  NULL, NULL, 1, NULL, NULL, '2026-06-25 10:00:00.000'),
	 (940002, 900001, 2, 930005, 'Sofortige Kostenerstattung', 'GDDS', 42.50,
	  NULL, NULL, 2, NULL, NULL, '2026-06-20 10:00:00.000'),
	 (940003, 900001, 6, 930006, 'Dringende Ersatzteillieferung', 'GDSV', 185.75,
	  NULL, NULL, 4, NULL, NULL, '2026-06-21 10:00:00.000'),
	 (940004, 900001, 3, 930004, 'Anzahlung Winterurlaub', 'OTHR', 300.00,
	  '15.09.26', NULL, 5, NULL, NULL, '2026-06-22 10:00:00.000'),
	 (940005, 900001, 4, 930002, 'Stromabschlag aktuell', 'SUPP', 82.00,
	  '10.03.26', 10, 5, 1, 940006, '2026-06-23 10:00:00.000'),
	 (940007, 900001, 5, 930006, 'Internationale Warenlieferung', 'GDDS', 275.00,
	  NULL, NULL, 1, NULL, NULL, '2026-06-24 10:00:00.000');

[SQL_DEMO_INSERT_FOREIGN_TRANSFER_DETAILS]
INSERT INTO moneytransferForeign (
    id, moneytransfer_id, currency, recipientCountry, recipientAccountNumber,
    recipientBankCode, recipientSubAccount, recipientAddressLine1, recipientAddressLine2,
    recipientBankCountry, recipientBankAddressLine1, recipientBankAddressLine2,
    chargeBearer, regulatoryReporting, endToEndReference, updatedAt
) VALUES
    (941001, 940007, 'USD', 'US', 'DEMO-ACCOUNT-4711', 'DEMOUS33', NULL,
     '100 Demo Street', 'Demo City', 'US', '1 Banking Avenue', 'Demo City',
     1, 'Demo-Warenlieferung', 'E2E-FOREIGN-DEMO-001', '2026-06-24 10:00:00.000');

[SQL_DEMO_INSERT_MONEY_TRANSFER_PROTOCOLS]
INSERT INTO moneytransferProtocol (
	 id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, bankOrderId,
	 sepaOrderStatus, sepaCancellationCode, protocolText, updatedAt
) VALUES
	 (942001, 940001, 1, '2026-06-25T10:00:00', '2026-06-25T10:00:01', NULL, NULL, NULL,
	  'Demo-Auftrag wurde lokal gespeichert.', '2026-06-25 10:00:01.000'),
	 (942002, 940002, 2, '2026-06-20T10:00:00', '2026-06-20T10:00:02', 'DEMO-INSTANT-001', 7, NULL,
	  'Demo-Bankmeldung: Auftrag angenommen.', '2026-06-20 10:00:02.000'),
	 (942003, 940003, 4, '2026-06-21T10:00:00', '2026-06-21T10:00:03', NULL, NULL, NULL,
	  'Demo-Bankmeldung: Auftrag konnte nicht ausgefuehrt werden.', '2026-06-21 10:00:03.000'),
	 (942004, 940005, 5, '2026-06-23T10:00:00', '2026-06-23T10:00:04', 'DEMO-STANDING-001', NULL, NULL,
	  'Demo-Bestand: Dauerauftrag wurde abgeglichen.', '2026-06-23 10:00:04.000'),
	 (942005, 940006, 8, '2026-02-01T10:00:00', '2026-02-01T10:00:00', 'DEMO-STANDING-001', NULL, NULL,
	  'Demo-Bestand: Historischer Dauerauftrag.', '2026-02-01 10:00:00.000'),
	 (942006, 940004, 5, '2026-06-22T10:00:00', '2026-06-22T10:00:00', 'DEMO-SCHEDULED-001', NULL, NULL,
	  'Demo-Bestand: Terminauftrag wurde abgeglichen.', '2026-06-22 10:00:00.000');

[SQL_DEMO_INSERT_PARAMETER_DATA]
INSERT INTO parameterData (id, pdKey, pdType, updatedAt) VALUES
    (960001, 'Demo.AllowedGV', 1, '2026-01-01 09:00:00.000'),
    (960002, 'KInfo.AllowedGV_1.code', 2, '2026-01-01 09:00:00.000');

[SQL_DEMO_LINK_PARAMETER_DATA]
INSERT INTO bankAccess_parameterData (id, bankAccess_id, parameterData_id, pdValue, updatedAt) VALUES
    (961001, 900000, 960001,
     'HKCCS,HKIPZ,HKEIL,HKCSE,HKCDE,HKCSA,HKCSL,HKCDN,HKCDL,HKAUB,HKCSB,HKCDB,HKCAZ,HKEKA,HKSAL',
     '2026-01-01 09:00:00.000'),
    (961002, 900000, 960002,
     'HKCCS,HKIPZ,HKEIL,HKCSE,HKCDE,HKCSA,HKCSL,HKCDN,HKCDL,HKAUB,HKCSB,HKCDB,HKCAZ,HKEKA,HKSAL',
     '2026-01-01 09:00:00.000');

[SQL_DEMO_INSERT_BUSINESS_CASE]
INSERT INTO businessCase (id, caseValue, updatedAt) VALUES
    (970001,
     'HKCCS;HKIPZ;HKEIL;HKCSE;HKCDE;HKCSA;HKCSL;HKCDN;HKCDL;HKAUB;HKCSB;HKCDB;HKCAZ;HKEKA;HKSAL',
     '2026-01-01 09:00:00.000');

[SQL_DEMO_LINK_BUSINESS_CASES]
INSERT INTO bankAccount_businessCase (id, account_id, businessCase_id, updatedAt) VALUES
    (971001, 900001, 970001, '2026-01-01 09:00:00.000'),
    (971002, 900002, 970001, '2026-01-01 09:00:00.000'),
    (971003, 900003, 970001, '2026-01-01 09:00:00.000');

[SQL_DEMO_INSERT_RETRIEVAL_STATUS]
INSERT INTO bankAccountRetrievalStatus (
    bankAccount_id, retrievedAt, result, newBookingCount, pendingBookingCount, lastError
) VALUES
    (900001, '2026-06-30 18:00:00.000', 1, 4, 1, NULL),
    (900002, '2026-06-30 18:01:00.000', 2, 0, 0, 'Demo: Online-Abruf nicht verfuegbar'),
    (900003, '2026-06-30 18:02:00.000', 1, 2, 1, NULL);
