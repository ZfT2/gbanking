# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

## [0.4.0] - 2026-08-26

### Added
- Import filters for CSV import data
- add SEPA XML format import / export

### Fixed
- Set product version in FinTS messages
- In-place Update:
- Avoid unnecessary instute.db changes
- BankAccess Sparkasse if account type is not set.

### Changed
- Foreign currency handling and persisting

### Maintenance
- external libraries minor updates
- release: prepare next development version 0.3.1-SNAPSHOT

## [0.3.0] - 2026-08-20

### Added
- add enablebanking PSD2 provider
- add institute import source: reachable-payment-service-providers

### Fixed
- use JavaFxTestSupport.runFx(...) in JUnit Test to fix build
- Recipient Bank name enrichment during file import
- Synchronize institute imports to avoid blocking main window.
- Keep comments in properties files if modified by program

### Maintenance
- release: prepare next development version 0.2.1-SNAPSHOT

## [0.2.0] - 2026-08-19

### Added
- PayPal account assignment

### Changed
- Services refactoring

### Maintenance
- release: prepare next development version 0.1.1-SNAPSHOT

## [0.1.0] - 2026-07-31

### Other
- inital commit
- Initial commit

## [0.14.6] - 2026-07-29

### Added
- Show processes duration
- Add category analysis
- Updated documentation
- save properties also to DB and sync
- PayPal integration (SOAP)
- Setting to encrypt account statements
- Generate diagnostics package
- select box in header so select all table entries
- store / restore backups in program
- add institute overview directory

### Fixed
- DB missing field and booking N+1 error
- SpotBugs Issues
- SonarQube Issues
- remove unused imports
- Icon
- Bank messages activation
- Recipient and institute.db handling
- copy institute.db to custom data directory if needed
- copy institute.db to packages

### Changed
- GUI Layout templates
- Rebooking handling
- remove unused code
- PIN clearing
- Text utils
- Helper for FP3- and MT940-exports
- Cross account resolving
- Wrong PIN detection
- Institute import

### Maintenance
- AGENTS.md
- release: prepare next development version 0.14.6-SNAPSHOT

## [0.14.5] - 2026-07-18

### Fixed
- institute.db

### Changed
- MoneyTransferArchiveStatus

### Maintenance
- release: prepare next development version 0.14.5-SNAPSHOT

## [0.14.4] - 2026-07-17

### Added
- add bank messages download
- Add demo tenant and adjust demo data
- Results dialog for open tasks page
- Save account retrieval results
- New overview page for open tasks
- Introduce database and backup encrytion

### Fixed
- Better progress indication on import
- Do not use only accountNumber as Recipient Identifier
- Adjust column widths
- Add SQLLite PRAGMA quick_check and PRAGMA integrity_check
- abort on wrong pin also when executing from open actions site
- Bookings count during import

### Changed
- environment-specific properties

### Maintenance
- release: prepare next development version 0.14.4-SNAPSHOT

## [0.14.3] - 2026-07-15

### Added
- Support standing order transfers deletion and change on bank side
- localize enums and german texts
- add keyboard shortcuts
- add note to booking
- persist gui layout state
- add detail filter for transactions
- standingorder transfers history

### Fixed
- move institute.db
- better error handling for db errors
- complete bank names for import recipients

### Changed
- tenant related data and include account docs in backups
- db transaction handling completeness
- Running action handling
- Introduce db transactions

### Maintenance
- release: prepare next development version 0.14.3-SNAPSHOT

## [0.14.2] - 2026-07-13

### Added
- add new accounts manally
- allow value copy from form fields
- add creditcard CSV import
- improved HBCI status dialog

### Fixed
- grey out unavailable moneytransfer types
- ci build
- Sonarqube Findings

### Changed
- Booking model in code

### Maintenance
- release: prepare next development version 0.14.2-SNAPSHOT

## [0.14.1] - 2026-07-09

### Added
- add account statement download

### Maintenance
- fixed and reactivated old JUnit Tests
- release: prepare next development version 0.14.1-SNAPSHOT

## [0.14.0] - 2026-07-08

### Added
- category rule enhancement
- add filter option to show only online accounts
- sort recipient list by orders usage: JUnit-Tests
- sort recipient list by orders usage

### Fixed
- tenant deletion before first program start
- log4j shutdown error

### Maintenance
- release: prepare next development version 0.13.2-SNAPSHOT

## [0.13.1] - 2026-07-06

### Fixed
- Update status bar
- constraint error on category deletion

### Maintenance
- release: prepare next development version 0.13.1-SNAPSHOT

## [0.13.0] - 2026-07-03

### Added
- add isDefault column for recipient
- add createdAt column for bankAccount.
- add and remove manual categories to/from bookings
- progress bar for program update download

### Fixed
- Sonar findings
- add bank name from institute db lookup for online transactions
- add institute.db to packages that bankName lookup works in program

### Changed
- better code readability

### Maintenance
- release: prepare next development version 0.11.3-SNAPSHOT

## [0.11.2] - 2026-07-02

### Added
- add context menu to remove rebooking join on booking(s)
- extend moneytransfer import/export for optional protocol
- add optical TAN methods

### Fixed
- Tenant test run on GitHub build
- avoid rebookings on same account if referenced in import data
- extend job for prenotification bookings
- Skip account booking polling if wrong pin is entered.
- general Sonarqube findings
- TenantLock Sonarqube findings
- min. width for amount column

### Maintenance
- automatic build on GitHub after commit
- release: prepare next development version 0.11.2-SNAPSHOT
- fix DB Controller singletons
- release: prepare next development version 0.11.1-SNAPSHOT

## [0.11.1] - 2026-07-01

### Added
- extend moneytransfer import/export for optional protocol
- add optical TAN methods

### Fixed
- min. width for amount column

### Maintenance
- fix DB Controller singletons
- release: prepare next development version 0.11.1-SNAPSHOT

## [0.11.0] - 2026-07-01

### Fixed
- ask for password before tenant deletion
- add seperate job for prenotification bookings
- matching between imported and new online bookings (V0000x codes)
- repair and reactivate rebooking-related tests
- small layout and renaming changes

### Maintenance
- fix DB Controller singletons
- release: prepare next development version 0.10.2-SNAPSHOT

## [0.10.1] - 2026-07-01

### Fixed
- institute dk import: avoid database trigger raise when update
- wording about dialog

### Maintenance
- institute import cleanup
- release: prepare next development version 0.10.1-SNAPSHOT

## [0.10.0] - 2026-06-26

### Added
- add third institute import source for EPC data

### Fixed
- commented out JUnit Test
- Fallback to MT940 call if CAMT booking call fails
- Avoid database exception if parameterList is empty

### Changed
- Package refactoring BankAccessService methods
- Package refactoring

### Maintenance
- release: prepare next development version 0.9.2-SNAPSHOT

## [0.9.1] - 2026-06-22

### Added
- Only only PIN ask dialog per bank, not one per each account

### Fixed
- Allow recipient consisting only name, e.g. for interest booking DKB
- Verwendungszweck from text field if usage is empty
- Sparkasse FinTS call (Alle Geräte)
- add BIC to FinTS call to fix DKB request

### Changed
- Message handling refactoring

### Maintenance
- release: prepare next development version 0.9.1-SNAPSHOT

## [0.9.0] - 2026-06-19

### Added
- add second institute import source for Bundesbank data

### Fixed
- booking-core version

### Changed
- Sonarqube findings, classes refactoring
- package refactoring
- cleanup unused db statements

### Maintenance
- disabled some rebooking related tests as they might be wrong
- release: prepare next development version 0.8.1-SNAPSHOT

## [0.8.0] - 2026-06-03

### Added
- add turnover analysis first version

### Fixed
- GitHub build Node.js warnings

### Maintenance
- add JUnit Tests
- release: prepare next development version 0.7.2-SNAPSHOT

## [0.7.1] - 2026-06-03

### Added
- add logging configuration for gbanking and hbci4java

### Fixed
- Typo in AccountState enum
- TAN procedures handling
- start scripts have to use program dir to find properties etc.

### Maintenance
- release: prepare next development version 0.7.1-SNAPSHOT

## [0.7.0] - 2026-06-02

### Added
- add rebooking creation tool
- add category context menu
- add rebooking detection tool
- visible link crossbookings in transaction table
- Search and link crossbookings with online request
- Show amount in callback dialog
- Add field to moneytransfer validation message
- Add urgent moneytransfer type
- Bankname tooltip in account list
- pre select account if view changes
- IBAN calculation
- Check bank parameter data to enable/disable program functions
- Extend foreign moneytransfer
- Handle of cancel bookings

### Fixed
- shrink fix mac build
- try to fix mac build
- DB-migration test
- Category panel issues
- update/duplicate detection for institute import
- moneytransfers bank name lookup
- delete institute CSV
- avoid editing of sent moneytransfers
- amount formatting in moneytransfer form
- width of amount column
- same-day booking requests

### Changed
- Normalize booking db table
- Recipient handling
- Institute in own global db

### Maintenance
- release: prepare next development version 0.7.1-SNAPSHOT
- cleanup institute test data
- added institute.db
- commit hook to avoid SNAPSHOT versions in dependencies
- delete also bpd and upd if bankaccess is deleted
- add db constraint triggers for booking/recipient relation
- add db constraint triggers
- Normalize line endings
- Update bank list
- release: prepare next development version 0.6.1-SNAPSHOT

## [0.6.0] - 2026-05-27

### Added
- In-place update feature
- add foreign moneytransfer
- show BPD and UPD buttons in bankaccess view
- Configure db dir
- Introduce split bookings
- Fp3 export & import
- MT940 export & import
- Added documentation
- Extended file import/export
- Moneytransfer CSV import
- default booking sorting by booking date desc
- balance from bank in account details
- duplicate check for new bookings
- context menue for deleting manuell bookings
- add category column to booking table
- restore booking table striping
- VOP display
- save additional hbci data
- Mapping preno bookings
- Automatic alignment booking if difference between bookings and account balance
- Account balance from hbci call
- Zip tenant database backups
- Tenant database backup
- Teant lock, avoid multiple instances per tenant
- Moneytransfer Export
- Improve VOP handling
- Moneytransfer templates

### Fixed
- Missing field in select statement
- neutral form in text
- Styling transaction detail panel
- Styling moneytransfer panel
- better display of VOP return data
- booking balance calculation by booking date instead of id
- second hbci call failed
- isolate DB migrations tests

### Changed
- Use interfaces in daos step 2
- Use interfaces in daos step 1
- Change string dates to java LocalDate
- Improved recipient handling and matching
- extracted booking logic to new project booking-core
- Improved logging
- Sub dir for import properties
- File export & import
- Balance calculation in Code instead SQL.
- Rename import dao mapper
- Use Enum DB Ids

### Maintenance
- Update libs
- release: prepare next development version 0.5.1-SNAPSHOT

## [0.5.0] - 2026-05-08

### Added
- Account editing
- Archive tab for moneytransfers
- Protocol for moneytransfers
- Progress monitor for DB migration
- Standing orders and sheduled money transfers
- Recipient name in booking detail

### Fixed
- NPE on adjustRebookings
- Icon handling
- updatedAt to timestamp type
- sonar findings
- repair warnings etc
- avoid online transfer if no baankaccess is configured for account

### Changed
- Code refactoring

### Maintenance
- Update libs
- release: prepare next development version 0.4.1-SNAPSHOT

