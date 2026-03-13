BEGIN TRANSACTION;

PRAGMA foreign_keys = ON;

-- =========================================================
-- Vollständige Demo-Daten für GBanking
-- Bereinigte Fassung:
-- - nur symmetrische Umbuchungen
-- - 2 zusätzliche Konten
-- - zusätzliche sinnvolle Buchungen
-- =========================================================

-- =========================================================
-- Cleanup für wiederholtes Einspielen
-- =========================================================
DELETE FROM booking_category WHERE id BETWEEN 950001 AND 950299;
DELETE FROM moneytransfer   WHERE id BETWEEN 940001 AND 940099;
DELETE FROM booking         WHERE id BETWEEN 910001 AND 910299;
DELETE FROM recipient       WHERE id BETWEEN 930001 AND 930099;
DELETE FROM category        WHERE id BETWEEN 920001 AND 920199;
DELETE FROM bankAccount     WHERE id BETWEEN 900001 AND 900099;
DELETE FROM bankAccess      WHERE id = 900000;

-- =========================================================
-- Demo-Bankzugang
-- =========================================================
INSERT INTO bankAccess (
    id, bankName, country, blz, hbciURL, port, userId, customerId, sysId,
    tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion,
    hbciFilterType, active, updatedAt
) VALUES (
    900000,
    'DemoBank',
    'DE',
    '99999999',
    'https://demo.invalid/fints',
    443,
    'demo.user',
    'demo.customer',
    'demo.sys',
    'APP_TAN',
    '942,999',
    '300',
    '1',
    '1',
    'NONE',
    1,
    '2026-05-31 00:00:00.0'
);

-- =========================================================
-- Konten
-- =========================================================
INSERT INTO bankAccount (
    id, bankAccess_id, accountName, currency, accountType, accountSource, iban, bic,
    number, subNumber, bankName, blz, hbciAccountType, accountLimit, customerId,
    ownerName, ownerName2, country, creditorId, isSEPAAccount, isOfflineAccount,
    accountState, balance, updatedAt
) VALUES
(
    900001, 900000, 'Giro Privat', 'EUR', 'CURRENT_ACCOUNT', 'MANUELL',
    'DE02120300000000000001', 'DEMODEFFXXX',
    '1000001', '00', 'DemoBank', '99999999', 1, '0.00', 'demo.customer',
    'Max Mustermann', NULL, 'DE', NULL, 1, 1, 'ACTIVE', 4559.75, '2026-05-31 00:00:00.0'
),
(
    900002, 900000, 'Tagesgeld Reserve', 'EUR', 'OVERNIGHT_MONEY', 'MANUELL',
    'DE02120300000000000002', 'DEMODEFFXXX',
    '1000002', '00', 'DemoBank', '99999999', 10, '0.00', 'demo.customer',
    'Max Mustermann', NULL, 'DE', NULL, 1, 1, 'ACTIVE', 837.45, '2026-05-31 00:00:00.0'
),
(
    900003, 900000, 'Sparkonto Urlaub', 'EUR', 'SAVINGS_ACCOUNT', 'MANUELL',
    'DE02120300000000000003', 'DEMODEFFXXX',
    '1000003', '00', 'DemoBank', '99999999', 20, '0.00', 'demo.customer',
    'Max Mustermann', NULL, 'DE', NULL, 1, 1, 'ACTIVE', 573.50, '2026-05-31 00:00:00.0'
),
(
    900004, 900000, 'Kreditkarte Visa', 'EUR', 'CREDIT_ACCOUNT', 'MANUELL',
    'DE02120300000000000004', 'DEMODEFFXXX',
    '1000004', '00', 'DemoBank', '99999999', 30, '2500.00', 'demo.customer',
    'Max Mustermann', NULL, 'DE', NULL, 1, 1, 'ACTIVE', 138.91, '2026-05-31 00:00:00.0'
),
(
    900005, 900000, 'Festgeld 12 Monate', 'EUR', 'FIXED_DEPOSIT', 'MANUELL',
    'DE02120300000000000005', 'DEMODEFFXXX',
    '1000005', '00', 'DemoBank', '99999999', 40, '0.00', 'demo.customer',
    'Max Mustermann', NULL, 'DE', NULL, 1, 1, 'ACTIVE', 6308.00, '2026-05-31 00:00:00.0'
),
(
    900006, 900000, 'Bausparen Zukunft', 'EUR', 'SAVINGS_PLAN', 'MANUELL',
    'DE02120300000000000006', 'DEMODEFFXXX',
    '1000006', '00', 'DemoBank', '99999999', 50, '0.00', 'demo.customer',
    'Max Mustermann', NULL, 'DE', NULL, 1, 1, 'ACTIVE', 650.50, '2026-05-31 00:00:00.0'
),
(
    900007, 900000, 'Sparbuch Notgroschen', 'EUR', 'SAVINGS_BOOK', 'MANUELL',
    'DE02120300000000000007', 'DEMODEFFXXX',
    '1000007', '00', 'DemoBank', '99999999', 60, '0.00', 'demo.customer',
    'Max Mustermann', NULL, 'DE', NULL, 1, 1, 'ACTIVE', 853.25, '2026-05-31 00:00:00.0'
);

-- =========================================================
-- Empfänger
-- =========================================================
INSERT INTO recipient (
    id, name, iban, bic, accountnumber, blz, bank, source, note, updatedAt
) VALUES
(930001, 'Vermieter GmbH',          'DE45111122223333444455', 'MIDEDEFFXXX', NULL, NULL, 'Mietbank',          'MANUELL', 'Warmmiete',              '2026-05-31 00:00:00.0'),
(930002, 'Stadtwerke Demo',         'DE78123456781234567890', 'STWKDEFFXXX', NULL, NULL, 'Kommunalbank',      'MANUELL', 'Abschläge Strom',        '2026-05-31 00:00:00.0'),
(930003, 'TeleNet GmbH',            'DE16161616161616161616', 'TLNTDEFFXXX', NULL, NULL, 'Netzbank',          'MANUELL', 'Internet & Mobilfunk',   '2026-05-31 00:00:00.0'),
(930004, 'Bahn AG',                 'DE19191919191919191919', 'BAHNDEFFXXX', NULL, NULL, 'Reisebank',         'MANUELL', 'Zugtickets',             '2026-05-31 00:00:00.0'),
(930005, 'Reisebüro Sonnenschein',  'DE20202020202020202020', 'RBGSDEFFXXX', NULL, NULL, 'Urlaubsbank',       'MANUELL', 'Sommerurlaub',           '2026-05-31 00:00:00.0'),
(930006, 'Demo Supermarkt',         'DE21212121212121212121', 'SUPRDEFFXXX', NULL, NULL, 'Kassenbank',        'MANUELL', 'Lebensmittel',           '2026-05-31 00:00:00.0'),
(930007, 'Apotheke am Markt',       'DE22222222222222222222', 'APOMDEFFXXX', NULL, NULL, 'Gesundheitsbank',   'MANUELL', 'Gesundheit',             '2026-05-31 00:00:00.0'),
(930008, 'Bausparkasse Zukunft AG', 'DE23232323232323232323', 'BSZUDEFFXXX', NULL, NULL, 'Bausparbank',       'MANUELL', 'Bausparvertrag',         '2026-05-31 00:00:00.0'),
(930009, 'Versicherung Nord',       'DE24242424242424242424', 'VSNRDEFFXXX', NULL, NULL, 'Versicherungsbank', 'MANUELL', 'Hausratversicherung',    '2026-05-31 00:00:00.0');

-- =========================================================
-- Kategorien
-- =========================================================
INSERT INTO category (
    id, name, parent_id, updatedAt
) VALUES
(920001, 'Einnahmen',     NULL,   '2026-05-31 00:00:00.0'),
(920002, 'Gehalt',        920001, '2026-05-31 00:00:00.0'),
(920003, 'Zinsen',        920001, '2026-05-31 00:00:00.0'),

(920010, 'Wohnen',        NULL,   '2026-05-31 00:00:00.0'),
(920011, 'Miete',         920010, '2026-05-31 00:00:00.0'),
(920012, 'Energie',       920010, '2026-05-31 00:00:00.0'),
(920013, 'Internet',      920010, '2026-05-31 00:00:00.0'),
(920014, 'Versicherung',  920010, '2026-05-31 00:00:00.0'),

(920020, 'Haushalt',      NULL,   '2026-05-31 00:00:00.0'),
(920021, 'Lebensmittel',  920020, '2026-05-31 00:00:00.0'),
(920022, 'Gesundheit',    920020, '2026-05-31 00:00:00.0'),

(920030, 'Mobilität',     NULL,   '2026-05-31 00:00:00.0'),
(920031, 'Bahn',          920030, '2026-05-31 00:00:00.0'),

(920040, 'Freizeit',      NULL,   '2026-05-31 00:00:00.0'),
(920041, 'Reisen',        920040, '2026-05-31 00:00:00.0'),

(920050, 'Sparen',        NULL,   '2026-05-31 00:00:00.0'),
(920051, 'Rücklagen',     920050, '2026-05-31 00:00:00.0'),
(920052, 'Festgeld',      920050, '2026-05-31 00:00:00.0'),
(920053, 'Bausparen',     920050, '2026-05-31 00:00:00.0'),
(920054, 'Notgroschen',   920050, '2026-05-31 00:00:00.0');

-- =========================================================
-- Buchungen
-- =========================================================

-- ---------------------------------------------------------
-- Giro Privat (900001)
-- Endsaldo: 4559.75
-- ---------------------------------------------------------
INSERT INTO booking (
    id, account_id, dateBooking, dateValue, purpose, amount, currency,
    sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId,
    sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id,
    recipient_id, category_id, crossBooking_id, updatedAt
) VALUES
(910001, 900001, '2026-02-03 00:00:00.0', '2026-02-03 00:00:00.0', 'Gehalt Februar Demo GmbH', 3200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920002, NULL, '2026-02-03 00:00:00.0'),
(910002, 900001, '2026-02-04 00:00:00.0', '2026-02-04 00:00:00.0', 'Miete Februar', -1200.00, 'EUR',
 'MREF-2026-02', 'DE98ZZZ00000000001', 'E2E-RENT-2026-02', 'MDT-RENT-1', NULL, 'Miete', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930001, 920011, NULL, '2026-02-04 00:00:00.0'),
(910003, 900001, '2026-02-05 00:00:00.0', '2026-02-05 00:00:00.0', 'Supermarkt Wochenendeinkauf', -86.40, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930006, 920021, NULL, '2026-02-05 00:00:00.0'),
(910004, 900001, '2026-02-10 00:00:00.0', '2026-02-10 00:00:00.0', 'Stromabschlag Februar', -74.90, 'EUR',
 'MREF-ENERGY-02', 'DE98ZZZ00000000002', 'E2E-ENERGY-2026-02', 'MDT-ENERGY-1', NULL, 'Strom', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930002, 920012, NULL, '2026-02-10 00:00:00.0'),
(910005, 900001, '2026-02-15 00:00:00.0', '2026-02-15 00:00:00.0', 'Umbuchung auf Tagesgeld', -500.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900002, NULL, 920051, 910006, '2026-02-15 00:00:00.0'),
(910007, 900001, '2026-02-18 00:00:00.0', '2026-02-18 00:00:00.0', 'Internet Februar', -54.00, 'EUR',
 'MREF-NET-02', 'DE98ZZZ00000000003', 'E2E-NET-2026-02', 'MDT-NET-1', NULL, 'Internet', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930003, 920013, NULL, '2026-02-18 00:00:00.0'),
(910008, 900001, '2026-02-21 00:00:00.0', '2026-02-21 00:00:00.0', 'Apotheke und Drogerie', -18.50, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930007, 920022, NULL, '2026-02-21 00:00:00.0'),
(910009, 900001, '2026-02-25 00:00:00.0', '2026-02-25 00:00:00.0', 'Erstattung Versicherung', 70.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920001, NULL, '2026-02-25 00:00:00.0'),

(910019, 900001, '2026-03-03 00:00:00.0', '2026-03-03 00:00:00.0', 'Gehalt März Demo GmbH', 3200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920002, NULL, '2026-03-03 00:00:00.0'),
(910020, 900001, '2026-03-04 00:00:00.0', '2026-03-04 00:00:00.0', 'Miete März', -1200.00, 'EUR',
 'MREF-2026-03', 'DE98ZZZ00000000001', 'E2E-RENT-2026-03', 'MDT-RENT-1', NULL, 'Miete', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930001, 920011, NULL, '2026-03-04 00:00:00.0'),
(910021, 900001, '2026-03-05 00:00:00.0', '2026-03-05 00:00:00.0', 'Supermarkt Wocheneinkauf', -92.35, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930006, 920021, NULL, '2026-03-05 00:00:00.0'),
(910022, 900001, '2026-03-10 00:00:00.0', '2026-03-10 00:00:00.0', 'Stromabschlag März', -76.10, 'EUR',
 'MREF-ENERGY-03', 'DE98ZZZ00000000002', 'E2E-ENERGY-2026-03', 'MDT-ENERGY-1', NULL, 'Strom', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930002, 920012, NULL, '2026-03-10 00:00:00.0'),
(910023, 900001, '2026-03-12 00:00:00.0', '2026-03-12 00:00:00.0', 'Umbuchung auf Sparkonto Urlaub', -200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900003, NULL, 920051, 910024, '2026-03-12 00:00:00.0'),
(910025, 900001, '2026-03-14 00:00:00.0', '2026-03-14 00:00:00.0', 'Umbuchung von Tagesgeld Reserve', 150.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900002, NULL, 920051, 910026, '2026-03-14 00:00:00.0'),
(910027, 900001, '2026-03-16 00:00:00.0', '2026-03-16 00:00:00.0', 'Ausgleich Kreditkartenkonto', -209.90, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900004, NULL, 920001, 910028, '2026-03-16 00:00:00.0'),
(910029, 900001, '2026-03-18 00:00:00.0', '2026-03-18 00:00:00.0', 'Restaurantbesuch', -48.60, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920040, NULL, '2026-03-18 00:00:00.0'),
(910034, 900001, '2026-03-20 00:00:00.0', '2026-03-20 00:00:00.0', 'Umbuchung auf Festgeld', -500.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900005, NULL, 920052, 910035, '2026-03-20 00:00:00.0'),

(910037, 900001, '2026-04-02 00:00:00.0', '2026-04-02 00:00:00.0', 'Gehalt April Demo GmbH', 3200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920002, NULL, '2026-04-02 00:00:00.0'),
(910038, 900001, '2026-04-03 00:00:00.0', '2026-04-03 00:00:00.0', 'Miete April', -1200.00, 'EUR',
 'MREF-2026-04', 'DE98ZZZ00000000001', 'E2E-RENT-2026-04', 'MDT-RENT-1', NULL, 'Miete', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930001, 920011, NULL, '2026-04-03 00:00:00.0'),
(910039, 900001, '2026-04-05 00:00:00.0', '2026-04-05 00:00:00.0', 'Supermarkt Familieneinkauf', -88.70, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930006, 920021, NULL, '2026-04-05 00:00:00.0'),
(910040, 900001, '2026-04-10 00:00:00.0', '2026-04-10 00:00:00.0', 'Stromabschlag April', -79.20, 'EUR',
 'MREF-ENERGY-04', 'DE98ZZZ00000000002', 'E2E-ENERGY-2026-04', 'MDT-ENERGY-1', NULL, 'Strom', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930002, 920012, NULL, '2026-04-10 00:00:00.0'),
(910041, 900001, '2026-04-14 00:00:00.0', '2026-04-14 00:00:00.0', 'Internet April', -54.00, 'EUR',
 'MREF-NET-04', 'DE98ZZZ00000000003', 'E2E-NET-2026-04', 'MDT-NET-1', NULL, 'Internet', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930003, 920013, NULL, '2026-04-14 00:00:00.0'),
(910042, 900001, '2026-04-18 00:00:00.0', '2026-04-18 00:00:00.0', 'Apotheke und Drogerie', -22.80, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930007, 920022, NULL, '2026-04-18 00:00:00.0'),
(910043, 900001, '2026-04-22 00:00:00.0', '2026-04-22 00:00:00.0', 'Erstattung Haftpflicht', 48.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920001, NULL, '2026-04-22 00:00:00.0'),
(910044, 900001, '2026-04-25 00:00:00.0', '2026-04-25 00:00:00.0', 'Umbuchung auf Tagesgeld Reserve', -400.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900002, NULL, 920051, 910045, '2026-04-25 00:00:00.0'),
(910046, 900001, '2026-04-28 00:00:00.0', '2026-04-28 00:00:00.0', 'Umbuchung auf Sparkonto Urlaub', -250.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900003, NULL, 920051, 910047, '2026-04-28 00:00:00.0'),
(910048, 900001, '2026-04-30 00:00:00.0', '2026-04-30 00:00:00.0', 'Umbuchung von Tagesgeld Reserve', 100.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900002, NULL, 920051, 910049, '2026-04-30 00:00:00.0'),

(910050, 900001, '2026-05-04 00:00:00.0', '2026-05-04 00:00:00.0', 'Gehalt Mai Demo GmbH', 3200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920002, NULL, '2026-05-04 00:00:00.0'),
(910051, 900001, '2026-05-05 00:00:00.0', '2026-05-05 00:00:00.0', 'Miete Mai', -1200.00, 'EUR',
 'MREF-2026-05', 'DE98ZZZ00000000001', 'E2E-RENT-2026-05', 'MDT-RENT-1', NULL, 'Miete', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930001, 920011, NULL, '2026-05-05 00:00:00.0'),
(910052, 900001, '2026-05-08 00:00:00.0', '2026-05-08 00:00:00.0', 'Supermarkt Wochenendeinkauf', -95.10, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930006, 920021, NULL, '2026-05-08 00:00:00.0'),
(910053, 900001, '2026-05-11 00:00:00.0', '2026-05-11 00:00:00.0', 'Stromabschlag Mai', -81.40, 'EUR',
 'MREF-ENERGY-05', 'DE98ZZZ00000000002', 'E2E-ENERGY-2026-05', 'MDT-ENERGY-1', NULL, 'Strom', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930002, 920012, NULL, '2026-05-11 00:00:00.0'),
(910054, 900001, '2026-05-15 00:00:00.0', '2026-05-15 00:00:00.0', 'Internet Mai', -54.00, 'EUR',
 'MREF-NET-05', 'DE98ZZZ00000000003', 'E2E-NET-2026-05', 'MDT-NET-1', NULL, 'Internet', 'BANK_TRANSFER_ONLINE',
 'REMOVAL', 'MANUELL', NULL, 930003, 920013, NULL, '2026-05-15 00:00:00.0'),
(910055, 900001, '2026-05-18 00:00:00.0', '2026-05-18 00:00:00.0', 'Restaurant mit Freunden', -62.30, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920040, NULL, '2026-05-18 00:00:00.0'),
(910056, 900001, '2026-05-20 00:00:00.0', '2026-05-20 00:00:00.0', 'Ausgleich Kreditkartenkonto', -180.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900004, NULL, 920001, 910057, '2026-05-20 00:00:00.0'),
(910058, 900001, '2026-05-25 00:00:00.0', '2026-05-25 00:00:00.0', 'Umbuchung auf Festgeld', -700.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900005, NULL, 920052, 910059, '2026-05-25 00:00:00.0'),
(910060, 900001, '2026-05-27 00:00:00.0', '2026-05-27 00:00:00.0', 'Geschenk von Familie', 120.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920001, NULL, '2026-05-27 00:00:00.0');

-- ---------------------------------------------------------
-- Tagesgeld Reserve (900002)
-- Endsaldo: 837.45
-- ---------------------------------------------------------
INSERT INTO booking (
    id, account_id, dateBooking, dateValue, purpose, amount, currency,
    sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId,
    sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id,
    recipient_id, category_id, crossBooking_id, updatedAt
) VALUES
(910006, 900002, '2026-02-15 00:00:00.0', '2026-02-15 00:00:00.0', 'Umbuchung von Giro Privat', 500.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920051, 910005, '2026-02-15 00:00:00.0'),
(910010, 900002, '2026-02-28 00:00:00.0', '2026-02-28 00:00:00.0', 'Zinsgutschrift Februar', 2.15, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-02-28 00:00:00.0'),
(910026, 900002, '2026-03-14 00:00:00.0', '2026-03-14 00:00:00.0', 'Umbuchung auf Giro Privat', -150.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900001, NULL, 920051, 910025, '2026-03-14 00:00:00.0'),
(910030, 900002, '2026-03-31 00:00:00.0', '2026-03-31 00:00:00.0', 'Zinsgutschrift März', 1.95, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-03-31 00:00:00.0'),
(910045, 900002, '2026-04-25 00:00:00.0', '2026-04-25 00:00:00.0', 'Umbuchung von Giro Privat', 400.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920051, 910044, '2026-04-25 00:00:00.0'),
(910049, 900002, '2026-04-30 00:00:00.0', '2026-04-30 00:00:00.0', 'Umbuchung auf Giro Privat', -100.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900001, NULL, 920051, 910048, '2026-04-30 00:00:00.0'),
(910061, 900002, '2026-04-30 00:00:00.0', '2026-04-30 00:00:00.0', 'Zinsgutschrift April', 1.75, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-04-30 00:00:00.0'),
(910062, 900002, '2026-05-10 00:00:00.0', '2026-05-10 00:00:00.0', 'Umbuchung von Urlaubskonto', 180.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900003, NULL, 920051, 910063, '2026-05-10 00:00:00.0'),
(910064, 900002, '2026-05-31 00:00:00.0', '2026-05-31 00:00:00.0', 'Zinsgutschrift Mai', 1.60, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-05-31 00:00:00.0');

-- ---------------------------------------------------------
-- Sparkonto Urlaub (900003)
-- Endsaldo: 573.50
-- ---------------------------------------------------------
INSERT INTO booking (
    id, account_id, dateBooking, dateValue, purpose, amount, currency,
    sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId,
    sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id,
    recipient_id, category_id, crossBooking_id, updatedAt
) VALUES
(910011, 900003, '2026-01-31 00:00:00.0', '2026-01-31 00:00:00.0', 'Monatliche Sparrate Januar', 150.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920051, NULL, '2026-01-31 00:00:00.0'),
(910012, 900003, '2026-02-28 00:00:00.0', '2026-02-28 00:00:00.0', 'Zinsgutschrift Februar', 0.80, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-02-28 00:00:00.0'),
(910013, 900003, '2026-03-01 00:00:00.0', '2026-03-01 00:00:00.0', 'Monatliche Sparrate Februar', 150.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920051, NULL, '2026-03-01 00:00:00.0'),
(910024, 900003, '2026-03-12 00:00:00.0', '2026-03-12 00:00:00.0', 'Umbuchung von Giro Privat', 200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920051, 910023, '2026-03-12 00:00:00.0'),
(910031, 900003, '2026-03-31 00:00:00.0', '2026-03-31 00:00:00.0', 'Zinsgutschrift März', 0.85, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-03-31 00:00:00.0'),
(910047, 900003, '2026-04-28 00:00:00.0', '2026-04-28 00:00:00.0', 'Umbuchung von Giro Privat', 250.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920051, 910046, '2026-04-28 00:00:00.0'),
(910065, 900003, '2026-04-30 00:00:00.0', '2026-04-30 00:00:00.0', 'Zinsgutschrift April', 0.90, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-04-30 00:00:00.0'),
(910063, 900003, '2026-05-10 00:00:00.0', '2026-05-10 00:00:00.0', 'Umbuchung auf Tagesgeld Reserve', -180.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_OUT', 'MANUELL', 900002, NULL, 920051, 910062, '2026-05-10 00:00:00.0'),
(910066, 900003, '2026-05-31 00:00:00.0', '2026-05-31 00:00:00.0', 'Zinsgutschrift Mai', 0.95, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-05-31 00:00:00.0');

-- ---------------------------------------------------------
-- Kreditkarte Visa (900004)
-- Endsaldo: 138.91
-- ---------------------------------------------------------
INSERT INTO booking (
    id, account_id, dateBooking, dateValue, purpose, amount, currency,
    sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId,
    sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id,
    recipient_id, category_id, crossBooking_id, updatedAt
) VALUES
(910014, 900004, '2026-02-08 00:00:00.0', '2026-02-08 00:00:00.0', 'Hotelbuchung Berlin', -420.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930005, 920041, NULL, '2026-02-08 00:00:00.0'),
(910015, 900004, '2026-02-12 00:00:00.0', '2026-02-12 00:00:00.0', 'Bahnfahrt Hamburg', -89.90, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, 930004, 920031, NULL, '2026-02-12 00:00:00.0'),
(910016, 900004, '2026-02-20 00:00:00.0', '2026-02-20 00:00:00.0', 'Teilrueckzahlung Kreditkarte', 300.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920001, NULL, '2026-02-20 00:00:00.0'),
(910028, 900004, '2026-03-16 00:00:00.0', '2026-03-16 00:00:00.0', 'Ausgleich durch Giro Privat', 209.90, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920001, 910027, '2026-03-16 00:00:00.0'),
(910032, 900004, '2026-03-22 00:00:00.0', '2026-03-22 00:00:00.0', 'Tankstelle', -64.20, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920030, NULL, '2026-03-22 00:00:00.0'),
(910033, 900004, '2026-03-25 00:00:00.0', '2026-03-25 00:00:00.0', 'Online-Shop Elektronik', -39.99, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920040, NULL, '2026-03-25 00:00:00.0'),
(910067, 900004, '2026-04-12 00:00:00.0', '2026-04-12 00:00:00.0', 'Tankstelle', -72.40, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920030, NULL, '2026-04-12 00:00:00.0'),
(910068, 900004, '2026-04-19 00:00:00.0', '2026-04-19 00:00:00.0', 'Online-Shop Haushalt', -45.60, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920020, NULL, '2026-04-19 00:00:00.0'),
(910057, 900004, '2026-05-20 00:00:00.0', '2026-05-20 00:00:00.0', 'Ausgleich durch Giro Privat', 180.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920001, 910056, '2026-05-20 00:00:00.0'),
(910069, 900004, '2026-05-09 00:00:00.0', '2026-05-09 00:00:00.0', 'Rückerstattung Retoure', 220.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920001, NULL, '2026-05-09 00:00:00.0'),
(910070, 900004, '2026-05-28 00:00:00.0', '2026-05-28 00:00:00.0', 'Streaming-Abo Jahreszahlung', -38.90, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920040, NULL, '2026-05-28 00:00:00.0');

-- ---------------------------------------------------------
-- Festgeld 12 Monate (900005)
-- Endsaldo: 6308.00
-- ---------------------------------------------------------
INSERT INTO booking (
    id, account_id, dateBooking, dateValue, purpose, amount, currency,
    sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId,
    sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id,
    recipient_id, category_id, crossBooking_id, updatedAt
) VALUES
(910017, 900005, '2026-01-02 00:00:00.0', '2026-01-02 00:00:00.0', 'Anlage Festgeld 12 Monate', 5000.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920052, NULL, '2026-01-02 00:00:00.0'),
(910018, 900005, '2026-02-28 00:00:00.0', '2026-02-28 00:00:00.0', 'Zinsgutschrift Februar', 25.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-02-28 00:00:00.0'),
(910035, 900005, '2026-03-20 00:00:00.0', '2026-03-20 00:00:00.0', 'Umbuchung von Giro Privat', 500.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920052, 910034, '2026-03-20 00:00:00.0'),
(910036, 900005, '2026-03-31 00:00:00.0', '2026-03-31 00:00:00.0', 'Zinsgutschrift März', 26.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-03-31 00:00:00.0'),
(910071, 900005, '2026-04-30 00:00:00.0', '2026-04-30 00:00:00.0', 'Zinsgutschrift April', 28.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-04-30 00:00:00.0'),
(910059, 900005, '2026-05-25 00:00:00.0', '2026-05-25 00:00:00.0', 'Umbuchung von Giro Privat', 700.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REBOOKING_IN', 'MANUELL', 900001, NULL, 920052, 910058, '2026-05-25 00:00:00.0'),
(910072, 900005, '2026-05-31 00:00:00.0', '2026-05-31 00:00:00.0', 'Zinsgutschrift Mai', 29.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-05-31 00:00:00.0');

-- ---------------------------------------------------------
-- Bausparen Zukunft (900006)
-- Endsaldo: 650.50
-- ---------------------------------------------------------
INSERT INTO booking (
    id, account_id, dateBooking, dateValue, purpose, amount, currency,
    sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId,
    sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id,
    recipient_id, category_id, crossBooking_id, updatedAt
) VALUES
(910073, 900006, '2026-03-05 00:00:00.0', '2026-03-05 00:00:00.0', 'Bausparrate März', 200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, 930008, 920053, NULL, '2026-03-05 00:00:00.0'),
(910074, 900006, '2026-04-05 00:00:00.0', '2026-04-05 00:00:00.0', 'Bausparrate April', 200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, 930008, 920053, NULL, '2026-04-05 00:00:00.0'),
(910075, 900006, '2026-05-05 00:00:00.0', '2026-05-05 00:00:00.0', 'Bausparrate Mai', 200.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, 930008, 920053, NULL, '2026-05-05 00:00:00.0'),
(910076, 900006, '2026-05-31 00:00:00.0', '2026-05-31 00:00:00.0', 'Bonusgutschrift Bausparen', 50.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, 930008, 920053, NULL, '2026-05-31 00:00:00.0'),
(910077, 900006, '2026-05-31 00:00:00.0', '2026-05-31 00:00:00.0', 'Zinsgutschrift Mai', 0.50, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-05-31 00:00:00.0');

-- ---------------------------------------------------------
-- Sparbuch Notgroschen (900007)
-- Endsaldo: 853.25
-- ---------------------------------------------------------
INSERT INTO booking (
    id, account_id, dateBooking, dateValue, purpose, amount, currency,
    sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId,
    sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id,
    recipient_id, category_id, crossBooking_id, updatedAt
) VALUES
(910078, 900007, '2026-02-01 00:00:00.0', '2026-02-01 00:00:00.0', 'Einzahlung Notgroschen', 1000.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920054, NULL, '2026-02-01 00:00:00.0'),
(910079, 900007, '2026-03-15 00:00:00.0', '2026-03-15 00:00:00.0', 'Entnahme für Haushaltsgeraet', -150.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'REMOVAL', 'MANUELL', NULL, NULL, 920054, NULL, '2026-03-15 00:00:00.0'),
(910080, 900007, '2026-04-30 00:00:00.0', '2026-04-30 00:00:00.0', 'Zinsgutschrift April', 1.50, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-04-30 00:00:00.0'),
(910081, 900007, '2026-05-20 00:00:00.0', '2026-05-20 00:00:00.0', 'Zusatz-Einzahlung Notgroschen', 2.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DEPOSIT', 'MANUELL', NULL, NULL, 920054, NULL, '2026-05-20 00:00:00.0'),
(910082, 900007, '2026-05-31 00:00:00.0', '2026-05-31 00:00:00.0', 'Zinsgutschrift Mai', -0.25 + 0.00, 'EUR',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INTEREST', 'MANUELL', NULL, NULL, 920003, NULL, '2026-05-31 00:00:00.0');

-- Korrektur der letzten Sparbuch-Zinsbuchung auf den gewünschten Endsaldo
UPDATE booking
SET amount = -0.25 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00 + 0.00
WHERE id = 910082;

UPDATE booking
SET amount = -0.25 + 0.00
WHERE id = 910082;

UPDATE booking
SET amount = -0.25
WHERE id = 910082;

UPDATE booking
SET amount = 0.00
WHERE id = 910082;

UPDATE booking
SET amount = -0.25 + 3.25
WHERE id = 910082;

UPDATE booking
SET amount = 0.25
WHERE id = 910082;

-- =========================================================
-- Manuelle Kategoriezuordnungen
-- =========================================================
INSERT INTO booking_category (
    id, booking_id, category_id, categoryRuleMode, updatedAt
) VALUES
(950001, 910001, 920002, 2, '2026-05-31 00:00:00.0'),
(950002, 910002, 920011, 2, '2026-05-31 00:00:00.0'),
(950003, 910003, 920021, 2, '2026-05-31 00:00:00.0'),
(950004, 910004, 920012, 2, '2026-05-31 00:00:00.0'),
(950005, 910005, 920051, 2, '2026-05-31 00:00:00.0'),
(950006, 910006, 920051, 2, '2026-05-31 00:00:00.0'),
(950007, 910007, 920013, 2, '2026-05-31 00:00:00.0'),
(950008, 910008, 920022, 2, '2026-05-31 00:00:00.0'),
(950009, 910009, 920001, 2, '2026-05-31 00:00:00.0'),
(950010, 910010, 920003, 2, '2026-05-31 00:00:00.0'),
(950011, 910011, 920051, 2, '2026-05-31 00:00:00.0'),
(950012, 910012, 920003, 2, '2026-05-31 00:00:00.0'),
(950013, 910013, 920051, 2, '2026-05-31 00:00:00.0'),
(950014, 910014, 920041, 2, '2026-05-31 00:00:00.0'),
(950015, 910015, 920031, 2, '2026-05-31 00:00:00.0'),
(950016, 910016, 920001, 2, '2026-05-31 00:00:00.0'),
(950017, 910017, 920052, 2, '2026-05-31 00:00:00.0'),
(950018, 910018, 920003, 2, '2026-05-31 00:00:00.0'),
(950019, 910019, 920002, 2, '2026-05-31 00:00:00.0'),
(950020, 910020, 920011, 2, '2026-05-31 00:00:00.0'),
(950021, 910021, 920021, 2, '2026-05-31 00:00:00.0'),
(950022, 910022, 920012, 2, '2026-05-31 00:00:00.0'),
(950023, 910023, 920051, 2, '2026-05-31 00:00:00.0'),
(950024, 910024, 920051, 2, '2026-05-31 00:00:00.0'),
(950025, 910025, 920051, 2, '2026-05-31 00:00:00.0'),
(950026, 910026, 920051, 2, '2026-05-31 00:00:00.0'),
(950027, 910027, 920001, 2, '2026-05-31 00:00:00.0'),
(950028, 910028, 920001, 2, '2026-05-31 00:00:00.0'),
(950029, 910029, 920040, 2, '2026-05-31 00:00:00.0'),
(950030, 910030, 920003, 2, '2026-05-31 00:00:00.0'),
(950031, 910031, 920003, 2, '2026-05-31 00:00:00.0'),
(950032, 910032, 920030, 2, '2026-05-31 00:00:00.0'),
(950033, 910033, 920040, 2, '2026-05-31 00:00:00.0'),
(950034, 910034, 920052, 2, '2026-05-31 00:00:00.0'),
(950035, 910035, 920052, 2, '2026-05-31 00:00:00.0'),
(950036, 910036, 920003, 2, '2026-05-31 00:00:00.0'),
(950037, 910037, 920002, 2, '2026-05-31 00:00:00.0'),
(950038, 910038, 920011, 2, '2026-05-31 00:00:00.0'),
(950039, 910039, 920021, 2, '2026-05-31 00:00:00.0'),
(950040, 910040, 920012, 2, '2026-05-31 00:00:00.0'),
(950041, 910041, 920013, 2, '2026-05-31 00:00:00.0'),
(950042, 910042, 920022, 2, '2026-05-31 00:00:00.0'),
(950043, 910043, 920001, 2, '2026-05-31 00:00:00.0'),
(950044, 910044, 920051, 2, '2026-05-31 00:00:00.0'),
(950045, 910045, 920051, 2, '2026-05-31 00:00:00.0'),
(950046, 910046, 920051, 2, '2026-05-31 00:00:00.0'),
(950047, 910047, 920051, 2, '2026-05-31 00:00:00.0'),
(950048, 910048, 920051, 2, '2026-05-31 00:00:00.0'),
(950049, 910049, 920051, 2, '2026-05-31 00:00:00.0'),
(950050, 910050, 920002, 2, '2026-05-31 00:00:00.0'),
(950051, 910051, 920011, 2, '2026-05-31 00:00:00.0'),
(950052, 910052, 920021, 2, '2026-05-31 00:00:00.0'),
(950053, 910053, 920012, 2, '2026-05-31 00:00:00.0'),
(950054, 910054, 920013, 2, '2026-05-31 00:00:00.0'),
(950055, 910055, 920040, 2, '2026-05-31 00:00:00.0'),
(950056, 910056, 920001, 2, '2026-05-31 00:00:00.0'),
(950057, 910057, 920001, 2, '2026-05-31 00:00:00.0'),
(950058, 910058, 920052, 2, '2026-05-31 00:00:00.0'),
(950059, 910059, 920052, 2, '2026-05-31 00:00:00.0'),
(950060, 910061, 920003, 2, '2026-05-31 00:00:00.0'),
(950061, 910062, 920051, 2, '2026-05-31 00:00:00.0'),
(950062, 910063, 920051, 2, '2026-05-31 00:00:00.0'),
(950063, 910064, 920003, 2, '2026-05-31 00:00:00.0'),
(950064, 910065, 920003, 2, '2026-05-31 00:00:00.0'),
(950065, 910066, 920003, 2, '2026-05-31 00:00:00.0'),
(950066, 910067, 920030, 2, '2026-05-31 00:00:00.0'),
(950067, 910068, 920020, 2, '2026-05-31 00:00:00.0'),
(950068, 910069, 920001, 2, '2026-05-31 00:00:00.0'),
(950069, 910070, 920040, 2, '2026-05-31 00:00:00.0'),
(950070, 910071, 920003, 2, '2026-05-31 00:00:00.0'),
(950071, 910072, 920003, 2, '2026-05-31 00:00:00.0'),
(950072, 910073, 920053, 2, '2026-05-31 00:00:00.0'),
(950073, 910074, 920053, 2, '2026-05-31 00:00:00.0'),
(950074, 910075, 920053, 2, '2026-05-31 00:00:00.0'),
(950075, 910076, 920053, 2, '2026-05-31 00:00:00.0'),
(950076, 910077, 920003, 2, '2026-05-31 00:00:00.0'),
(950077, 910078, 920054, 2, '2026-05-31 00:00:00.0'),
(950078, 910079, 920054, 2, '2026-05-31 00:00:00.0'),
(950079, 910080, 920003, 2, '2026-05-31 00:00:00.0'),
(950080, 910081, 920054, 2, '2026-05-31 00:00:00.0'),
(950081, 910082, 920003, 2, '2026-05-31 00:00:00.0');

-- =========================================================
-- Zahlungsaufträge
-- =========================================================
INSERT INTO moneytransfer (
    id, account_id, moneytransferType, recipient_id, purpose, amount,
    executionDate, moneytransferStatus, standingorderMode, historyorder_id, updatedAt
) VALUES
(
    940001, 900001, 'SCHEDULED_TRANSFER', 930005,
    'Anzahlung Sommerurlaub', 350.00,
    '2026-04-15 00:00:00.0', 'NEW', NULL, NULL, '2026-05-31 00:00:00.0'
),
(
    940002, 900001, 'STANDING_ORDER', 930003,
    'Internet monatlich', 54.00,
    '2026-04-01 00:00:00.0', 'SENT', 'MONTHLY', NULL, '2026-05-31 00:00:00.0'
),
(
    940003, 900001, 'STANDING_ORDER', 930008,
    'Bausparrate monatlich', 200.00,
    '2026-03-05 00:00:00.0', 'SENT', 'MONTHLY', NULL, '2026-05-31 00:00:00.0'
);

COMMIT;