# Project Guidelines

- For full test runs, use a tool timeout of at least five minutes (`300000 ms`).

- Always write comments in the code and commit messages in **English** (Javadoc, `//` comments, inline comments). This applies to both production *and* test code.
  - Reason: The existing code is already predominantly commented in English (`db/JdbcOperations.java`, `db/repository/JdbcDaoRepository.java`, `db/repository/SqlConstants.java`).
  - Exceptions: User interface text and resource files (`src/main/resources/messages_de.properties`) remain in German.
  - Test data (names, notes, amounts in test fixtures) may contain umlauts—they are intentionally designed to pass case-insensitive searches.
  - The same rule is also listed in the “Code Style” section of `CONTRIBUTING.md`.
  
- If anything is unclear, ask for clarification and do not make assumptions.
- Make sure to write code that complies with SonarQube standards. 
- Commit your changes with CRLF line endings so they match the Git configuration and prevent phantom diffs. 
- Avoid the :: notation whenever reasonably possible, unless it significantly reduces the number of lines of code (LOC).
- After every change, review the modified code again and check it for potential optimization, especially regarding LOC. But not at any cost—the code should remain readable and understandable.
