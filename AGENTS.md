# Project Guidelines

- For full test runs, use a tool timeout of at least five minutes (`300000 ms`).

- Always write comments in the code and commit messages in **English** (Javadoc, `//` comments, inline comments). This applies to both production *and* test code.
  - Reason: The existing code is already predominantly commented in English (`db/JdbcOperations.java`, `db/repository/JdbcDaoRepository.java`, `db/repository/SqlConstants.java`).
  - Exceptions: User interface text and resource files (`src/main/resources/messages_de.properties`) remain in German.
  - Test data (names, notes, amounts in test fixtures) may contain umlauts—they are intentionally designed to pass case-insensitive searches.
  - The same rule is also listed in the “Code Style” section of `CONTRIBUTING.md`.
  
- The user creates commits and pushes themselves. Do not run `git commit` or `git push` unless the user explicitly asks for it in the current request.
  - Prepare the working tree and the index as requested, verify the result (e.g. `git status`, compile check), then report and let the user commit and push.

- Keep the Git index (staging area) empty. Local work stays as **unstaged** changes in the working tree.
  - Reason: The user works with the Eclipse Git Staging View, where staged changes follow across branch switches and appear as changes of another branch.
  - Unstage instead of staging (`git restore --staged`, `git reset --mixed`) and park work that belongs to a different feature out of the working tree (e.g. `git stash push`) instead of leaving it staged.

- Do not edit `CHANGELOG.md` manually. The release workflow (`.github/scripts/update-changelog.sh`) generates the changelog entries from the commit messages when a tag or a release is built.
  - Reason: Manually written entries are replaced by the generated release notes and would be duplicated.
  - The commit type determines the changelog section: `feat` → Added, `fix` → Fixed, `security` → Security, `perf`/`refactor`/`style` → Changed, `docs` → Documentation, `test` → Tests, `build`/`ci` → Build, `chore` → Maintenance.

- Suggest a commit message at the end of every task, the user creates the commit.
  - Format: conventional commit with a type prefix, e.g. `fix: Transaction list: keep the transaction count visible in narrow windows`
  - Allowed prefixes: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `build:`, `perf:`, `refactor:`, `style:`, `security:`
  - The whole commit message must not exceed 72 characters, including the prefix.

- If anything is unclear, ask for clarification and do not make assumptions.
- Make sure to write code that complies with SonarQube standards. 
- Commit your changes with CRLF line endings so they match the Git configuration and prevent phantom diffs. 
- Avoid the :: notation whenever reasonably possible, unless it significantly reduces the number of lines of code (LOC).
- After every change, review the modified code again and check it for potential optimization, especially regarding LOC. But not at any cost—the code should remain readable and understandable.
