# T2 First Iteration – Comprehensive Checklist (100 pts)
**Due:** Oct 23, 11:59 PM (America/New_York)  
**Submission Window:** Opens Sep 8, 12:00 AM — Closes Oct 24, 11:59 PM  
**Submission:** Website URL to your GitHub repository

---

## Quick Navigation
- [0. Dates & Admin](#0-dates--admin)
- [1. Prerequisite (Mentor Setup)](#1-prerequisite-mentor-setup)
- [2. Implementation & Repo Hygiene (Overview)](#2-implementation--repo-hygiene-overview)
- [3. Style Checking](#3-style-checking)
- [4. Internal Code Documentation](#4-internal-code-documentation)
- [5. Unit Testing](#5-unit-testing)
- [6. API (System) Testing](#6-api-system-testing)
- [7. Coverage & Bug Fixing](#7-coverage--bug-fixing)
- [8. External Documentation (README)](#8-external-documentation-readme)
- [9. Project Management Evidence](#9-project-management-evidence)
- [10. AI Usage Documentation](#10-ai-usage-documentation)
- [11. Submission Instructions](#11-submission-instructions)
- [12. Grading Rubric Cross‑Check](#12-grading-rubric-crosscheck)
- [13. Final Pre‑Submission Sanity Pass](#13-final-presubmission-sanity-pass)

---

## 0. Dates & Admin
- [ ] Confirm the team is aware of the **deadline (Oct 23, 11:59 PM)**.
- [ ] Confirm the **submission window** (Sep 8 — Oct 24, 11:59 PM) and that the URL will be active.
- [ ] Decide which teammate will **submit the URL**.
- [ ] If the repo is **private**, ensure the **Mentor has collaborator access**.
- [ ] Share the repo URL internally and pin it in your team PM tool.

---

## 1. Prerequisite (Mentor Setup)
- [ ] Verify **Mentor assigned** (should be a few days after proposal submission).
- [ ] **Schedule** a meeting with Mentor **ASAP** after assignment.
- [ ] **Meet with Mentor** (ideally with **all team members** present).
- [ ] **Incorporate Mentor feedback**; if significant, **revise proposal** and commit changes.
- [ ] If **no contact by Oct 3**, **reach out to Head IA** for assistance.
- [ ] Record the **meeting summary**, decisions, and action items in your PM tool.

**Evidence to provide:**
- [ ] Link to Mentor meeting notes (PM board/comment/doc).

---

## 2. Implementation & Repo Hygiene (Overview)
- [ ] Implement a **rudimentary but demoable** version of your service.
- [ ] Ensure **some API entry points are operational** (not just stubs).
- [ ] All **implementation code** is committed to **main**.
- [ ] All **test code** is committed to **main**.
- [ ] Use **Pull Requests** for changes; avoid direct pushes to main.
- [ ] **Enforce branch protection rules** (e.g., PR review required, status checks).
- [ ] **Every PR reviewed** by another team member; leave meaningful review comments.
- [ ] **Commit messages** (except initial setup) are **meaningful** (what/why changed).
- [ ] Create a **tag** (or release) that marks **all file revisions** included in **First Iteration**.
- [ ] Keep experimental or in‑progress code in **separate branches** (not part of the tag).
- [ ] Repository includes **.gitignore**, **license** (if applicable), and basic repo hygiene.

**Evidence to provide:**
- [ ] Screenshot or link showing **PR reviews**.
- [ ] **Git tag/release** named clearly (e.g., `iter1`), attached to main.
- [ ] Sample **commit log** demonstrating meaningful messages.

---

## 3. Style Checking
- [ ] Choose a **style checker** appropriate for your language/platform (e.g., ESLint, Prettier, Flake8, Black, Pylint, Checkstyle, ktlint, gofmt/golangci-lint, RuboCop, etc.).
- [ ] Configure **ruleset** and commit the config file(s) (e.g., `.eslintrc`, `pyproject.toml`, `.pylintrc`, `checkstyle.xml`).
- [ ] Run style checks on the **entire codebase**, **including tests**.
- [ ] Include at least **one style checker report** in the repo (artifact or saved output).
- [ ] Ensure the report eventually shows **“clean”** (no style violations) for main.
- [ ] Document in README: **which tool, which ruleset, how to run**.

**Evidence to provide:**
- [ ] `README` section: **Style Checker** with tool + ruleset + commands.
- [ ] **Report file** or CI artifact link proving a clean run.

---

## 4. Internal Code Documentation
- [ ] Use **mnemonic, self‑documenting identifiers** (classes, methods, variables).
- [ ] Add **header comments** for all **non‑trivial files/classes/modules**.
- [ ] Add **inline comments** where logic is subtle or nonobvious.
- [ ] Document **test code** (purpose, setup, tricky cases).
- [ ] Maintain consistent **docstring/Javadoc/KDoc** style where applicable.
- [ ] Avoid dead code; remove or clearly label experimental code.

**Evidence to provide:**
- [ ] Representative files showing **headers + inline comments**.
- [ ] Linting that checks for doc formatting (optional but nice).

---

## 5. Unit Testing
- [ ] Identify **all major units** (modules/classes/functions with non‑trivial logic).
- [ ] For **each unit**, implement **≥ 3 unit tests**:
  - [ ] **Typical valid input** case(s).
  - [ ] **Atypical valid input** (edge/corner cases).
  - [ ] **Invalid input** (error paths/exceptions).
- [ ] Use **mocking/test doubles** where appropriate (I/O, DB, external APIs).
- [ ] Use **setup/teardown** fixtures where appropriate to share state.
- [ ] **Group related tests** into **test classes/suites** for clarity.
- [ ] Ensure tests are **automated via a test runner** (e.g., `pytest`, `jest`, `JUnit`, `go test`, `RSpec`, etc.).
- [ ] Ensure **push‑button test execution** (single command/script/CI job).
- [ ] Fix **most bugs found** by unit tests; document notable fixes in PRs.

**Evidence to provide:**
- [ ] README section **“Testing”** with tools + how to run + where configs live.
- [ ] Test tree structure shows **grouped tests** and **fixtures**.
- [ ] PRs referencing bugs found/fixed by unit tests.

---

## 6. API (System) Testing
- [ ] Identify **initial API entry points** (list all endpoints).
- [ ] For **each entry point**, implement **≥ 3 API tests**:
  - [ ] **Typical valid** request(s).
  - [ ] **Atypical valid** request(s).
  - [ ] **Invalid** request(s) (malformed/unauthorized/conflict/etc.).
- [ ] Use an **API testing tool** (e.g., Postman, Thunder Client, Insomnia, k6 for smoke, etc.).
- [ ] Store tests in the **repo** or in a **linked workspace** (and link in README).
- [ ] API tests **write (save)** some **persistent data**.
- [ ] API tests **read/access** previously saved data; verify correctness.
- [ ] Verify **logging**: service **logs calls** to **all entry points** (assert on logs).
- [ ] Test **multiple clients** (faked users/tenants) to ensure **isolation** (no interference).
- [ ] Fix **most bugs found** by API tests; document notable fixes.

**Evidence to provide:**
- [ ] Postman/Thunder workspace **export** checked into repo or linked.
- [ ] **Instructions** in README to run the API test suite.
- [ ] **Sample logs** (redacted) proving endpoint call logging.

---

## 7. Coverage & Bug Fixing
- [ ] Configure an appropriate **coverage tool** (e.g., `coverage.py`, `nyc/istanbul`, `JaCoCo`, `go test -cover`, `pytest-cov`).
- [ ] Generate **branch coverage** reports (not just line coverage).
- [ ] Achieve **≥ 55% total branch coverage** **across unit + API tests** (first iteration requirement).
- [ ] Save **coverage report artifacts** in repo (HTML/LCOV/XML) or CI artifacts with a link.
- [ ] Document **how to reproduce coverage** in README.
- [ ] Identify **bugs found** by testing and **fix** a meaningful subset (describe in PRs/issues).

**Evidence to provide:**
- [ ] Coverage **report files** in repo (e.g., `coverage/`, `target/site/jacoco/`, `lcov.info`).
- [ ] Issues/PRs referencing bugs discovered and **fix commits**.

---

## 8. External Documentation (README)
- [ ] **API Reference**: Document **every operational entry point**:
  - [ ] **Endpoint/verb/path** and description.
  - [ ] **Inputs** (params/body/query/auth) and **validation rules**.
  - [ ] **Outputs** (schemas/examples).
  - [ ] **Status/error codes** and meanings.
  - [ ] **Ordering constraints** (calls that must/ must not be called in certain orders).
- [ ] **Build/Run/Test Instructions**:
  - [ ] Build steps (toolchain, scripts, env vars).
  - [ ] Run steps (services, ports, DB migrations/seed).
  - [ ] Test steps (unit, API, coverage); **single command** if possible.
  - [ ] **Locations of config files** (style, tests, coverage, CI).
- [ ] **Third‑party code**:
  - [ ] If bundled, list **exact code**, **location in repo**, **source (URL)**, and **why**.
  - [ ] Prefer **package manager**/build system deps rather than vendoring big libs.
- [ ] **Style checker**: tools, ruleset, how to run.
- [ ] **Testing & mocking frameworks** used and where configs live.
- [ ] **Link to PM tool** (GitHub Projects/Issues/JIRA/Trello) with Mentor access.
- [ ] **Link to API testing workspace** (if external) and export location.
- [ ] **How to view logs** and expected log format/location.
- [ ] **Security/tenancy notes** (how multiple clients are isolated) if applicable.

**Evidence to provide:**
- [ ] `README.md` sections cover all bullets above with **copy‑pasteable commands**.

---

## 9. Project Management Evidence
- [ ] Use a **PM tool** (GitHub Projects/Issues, JIRA, Trello, etc.).
- [ ] Ensure **Mentor & team have access** (viewer/collaborator).
- [ ] Maintain **columns** (To‑Do / In‑Progress / In‑Review / Done).
- [ ] Create **issues/tasks** with **assignees**, **labels**, **estimates** (if used).
- [ ] Track **work assignments** and realistic progress.
- [ ] Cross‑link **issues ↔ PRs**; auto‑close via commit messages where possible.
- [ ] Include **burndown/throughput** snapshot (optional but helpful).

**Evidence to provide:**
- [ ] Link to **project board** and a **screenshot** showing activity.

---

## 10. AI Usage Documentation
- [ ] If any **AI tools** were used: **name** them and **how** they were used.
- [ ] Include **prompts** (or representative prompts) used for generation/debugging.
- [ ] **Mark any code** (including tests) that was **generated or debugged by AI**.
- [ ] Ensure **no paid tools** were used **unless** covered by **school credits** or **student plans**.
- [ ] Place an **AI Usage** section in README with links to examples/PRs.

**Evidence to provide:**
- [ ] README **AI section** + file annotations/comments marking AI‑assisted code.

---

## 11. Submission Instructions
- [ ] **One teammate submits** a **website URL** pointing to the **GitHub repo**.
- [ ] Prefer **public repo**; if private, **Mentor is a collaborator**.
- [ ] Confirm the URL in the LMS submission form works for graders.
- [ ] Verify the **tag/release** clearly marks the **First Iteration** scope.

---

## 12. Grading Rubric Cross‑Check
**Prerequisite (0 pts but gating)**
- [ ] Mentor access to repo confirmed & team met with Mentor.

**Overview (5 pts)**
- [ ] **Tag** revisions included in First Iteration (1 pt).
- [ ] **PRs reviewed** by teammate(s) (2 pts).
- [ ] **Meaningful commit messages** (2 pts).

**Style Checking (5 pts)**
- [ ] Style checker **report present** (1 pt).
- [ ] Report shows **clean compliance** for **all code incl. tests** (4 pts).

**Internal Documentation (5 pts)**
- [ ] **Self‑documenting code** + headers + inline comments (5 pts).

**Unit Testing (25 pts)**
- [ ] ≥3 tests per non‑trivial unit: **typical/atypical/invalid** (18 pts, prorated).
- [ ] **Mocking** used when appropriate (2 pts).
- [ ] **Setup/teardown** used when appropriate (2 pts).
- [ ] **Grouped into test classes/suites** (2 pts).
- [ ] **Automated with test runner** (1 pt).

**API Testing (30 pts)**
- [ ] ≥3 tests per **API entry point**: typical/atypical/invalid (18 pts, prorated).
- [ ] **Write** then **read** persistent data (3 pts).
- [ ] **Logging** of all entrypoints verified (3 pts).
- [ ] **Multi‑client isolation** verified (3 pts).
- [ ] **API testing tool** used (3 pts).

**Branch Coverage & Bug Fixing (15 pts)**
- [ ] **Coverage reports included** (1 pt).
- [ ] **≥55% branch coverage** overall (9 pts).
- [ ] **Evidence of bugs found & fixed** (5 pts).

**README Documentation (10 pts)**
- [ ] **API docs** for every entry point (4 pts).
- [ ] **Ordering discussion** (if applicable) (1 pt).
- [ ] **Build/Run/Test instructions** with config locations (4 pts).
- [ ] **Third‑party code** documented or N/A (1 pt).

**Project Management (5 pts)**
- [ ] **PM tool used** (1 pt).
- [ ] **Assignments & workflow plausible/reasonable** (4 pts).

---

## 13. Final Pre‑Submission Sanity Pass
- [ ] **`README.md`** is complete, accurate, and newcomer‑friendly.
- [ ] **Tag/release** exists and matches files intended for First Iteration.
- [ ] **CI pipeline** (if present) is green for the tagged commit.
- [ ] **Style checker** passes cleanly on main at tag.
- [ ] **Unit tests**: green; **API tests**: green.
- [ ] **Coverage** report present and **≥55% branch coverage**.
- [ ] **Logs** show expected entries for each endpoint during tests.
- [ ] **Multi‑client tests** show isolation guarantees.
- [ ] **PM board** reflects done work; Mentor access verified.
- [ ] **AI usage** documented (or explicitly stated “none used”).
- [ ] **Submission URL** verified and accessible (private repos grant access).
- [ ] **Timebox** a 10‑minute dry run: clone → build → run → test per README.

---

### Optional (Nice‑to‑Have for Iteration 1, Required in Iteration 2)
- [ ] **Static analysis bug finder** configured (e.g., Sonar, CodeQL, FindBugs/SpotBugs).
- [ ] **Continuous Integration** with test + style + coverage gates.
- [ ] **Integration testing** (beyond unit/API) and a **sample client** app.

