# Phase 2 Regression & Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the four Phase 2 modules work together without changing the Phase 1 schema or absorbing later-phase workflows.

**Architecture:** Add only cross-module acceptance tests and run the existing migration/context/full-suite gates. Reuse the module plans’ fixtures and real Spring Security/PostgreSQL/Redis boundaries; do not add production abstractions merely for acceptance testing.

**Tech Stack:** Java 25, Maven Wrapper, Spring Boot 4.1.2, JUnit 5, MockMvc, Testcontainers PostgreSQL/Redis, Flyway, Hibernate validate.

**Spec:** `docs/superpowers/specs/2026-11-16-phase-2-focused-brownfield-hardening-design.md`

**Git execution policy:** Commit snippets are reference-only boundaries. An implementation agent MUST NOT stage, commit, or push unless the user or final execution prompt explicitly authorizes it.

## Global Constraints

- Execute only after Authentication, Room/Bed, Tenant Profile, and Vehicle plans pass independently.
- The Phase 1 `V1__clean_baseline.sql` remains unchanged and must still create exactly 20 application tables.
- No H2-only result may be reported as PostgreSQL verification.
- No test may reset or connect to an unidentified/shared database or Redis instance.
- FR-09 onward, Room Transfer, Finance, Notifications, OCI, Maintenance, and Violations remain outside Phase 2 acceptance.
- Refresh reuse has no concurrent single-use acceptance guarantee.
- Vehicle testing uses targeted PostgreSQL constraints/transactions, not stress or distributed-concurrency testing.

## Review Focus

- A locked User with a previously valid token must receive 401 across every module; REG-2 tests each protected route family.
- A checked-out Tenant must retain profile/history access but lose Vehicle current-residency commands; REG-2 covers the cross-module split.
- Phase 2 must not alter the 20-table baseline or Hibernate mappings; REG-1 reruns migration and validation.
- Room and Vehicle lock orders must remain local and noncontradictory; REG-2 exercises rollback/stale-state sequences without stress testing.
- No response may expose Vehicle object keys or public image URLs; REG-2 scans serialized cross-module responses.

---

### REG-1: Revalidate the Phase 1 persistence foundation

**Classification:** KEEP — schema regression gate.

**Spec sections:** Overview §3, §6.2, §9.

**Dependencies:** All module plans have completed their production changes.

**Acceptance criteria:** Fresh isolated PostgreSQL migration, Hibernate validation, and Spring context startup pass with exactly the unchanged 20-table Phase 1 baseline.

**Scope exclusions:** No migration, entity, dependency, or runtime-configuration change to make the gate pass.

**Files:**
- Modify only if missing assertions are needed: `src/test/java/com/uit/dormmanagement/repositories/Phase1FullPostgresMigrationTest.java`
- Modify only if missing assertions are needed: `src/test/java/com/uit/dormmanagement/repositories/Phase1AEntityMappingTest.java`
- Modify only if missing assertions are needed: `src/test/java/com/uit/dormmanagement/DormitoryManagementApplicationTests.java`

**Interfaces:**
- Consumes: completed module production code.
- Produces: fresh Flyway, Hibernate validation, and application-context evidence.

- [ ] **Step 1: Inspect the final diff before adding assertions**

Run: `git diff -- src/main/resources/db/migration pom.xml src/main/resources/application.yml`

Expected: no Phase 2 migration/dependency/runtime-configuration change unless an approved plan explicitly required a narrow source configuration bean; `V1__clean_baseline.sql` is untouched.

- [ ] **Step 2: Run fresh PostgreSQL migration and mapping tests**

Run: `./mvnw -Dtest=Phase1FullPostgresMigrationTest,Phase1APostgresMigrationTest,Phase1AEntityMappingTest test`

Expected: PASS on Testcontainers PostgreSQL; 20 tables and all Phase 1 constraints remain valid.

- [ ] **Step 3: Run application context/Hibernate validation**

Run: `./mvnw -Dtest=DormitoryManagementApplicationTests test`

Expected: PASS with Hibernate `ddl-auto=validate`.

- [ ] **Step 4: Commit only genuinely missing regression assertions**

If no test edit was required, make no commit. Otherwise:

```bash
git add src/test/java/com/uit/dormmanagement/repositories/Phase1FullPostgresMigrationTest.java src/test/java/com/uit/dormmanagement/repositories/Phase1AEntityMappingTest.java src/test/java/com/uit/dormmanagement/DormitoryManagementApplicationTests.java
git commit -m "test: preserve phase one persistence baseline"
```

### REG-2: Add cross-module Phase 2 acceptance scenarios

**Classification:** EXTEND — FR-01–FR-08/FR-21–FR-24 integration.

**Spec sections:** Overview §6–§10 and each domain specification's testing/acceptance sections.

**Dependencies:** AUTH-7, ROOM-6, TEN-5, VEH-7, and REG-1.

**Acceptance criteria:** Cross-module tests prove locked-token denial, checked-out permission split, Room command guards, and atomic Vehicle replacement/rollback through real security and isolated infrastructure.

**Scope exclusions:** Use fixtures instead of implementing Onboarding/Checkout/Notifications/OCI/Maintenance/Violations.

**Files:**
- Create: `src/test/java/com/uit/dormmanagement/e2e/Phase2CoreModulesE2ETest.java`
- Reuse fixtures from: `src/test/java/com/uit/dormmanagement/e2e/ResidentLifecycleE2ETest.java`

**Interfaces:**
- Consumes: all module services/controllers and real Security filter chain.
- Produces: one bounded Phase 2 acceptance suite; it does not test onboarding/checkout implementation.

- [ ] **Step 1: Add authentication-to-authorization scenario**

Create Manager/Technician/Tenant fixtures directly through repositories; assert correct login identifier, role access, logout token ownership, old-generation rejection after lock/password reset, and no unrelated-session invalidation after Refresh reuse.

- [ ] **Step 2: Add Manager Room/Tenant scenario**

Assert Manager creates Room with exact Beds/Amenities, updates allowed Room fields, corrects Tenant identity/contact under uniqueness rules, and cannot rename/gender/archive when fixture Contract/Bed state violates guards. Use controlled repository fixtures rather than Phase 3 onboarding.

- [ ] **Step 3: Add Tenant self-service/Vehicle scenario**

Assert active Tenant updates own contact, registers type-valid Vehicle with private evidence, Manager issues/rejects through allowed transitions, and Tenant sees own history/evidence only. Change the Tenant fixture to CHECKED_OUT directly, then assert contact/history access remains while registration/change creation is denied.

- [ ] **Step 4: Add atomic replacement rollback scenario**

Create ISSUED Vehicle/PENDING Change Request fixtures, approve and assert old/new/request/Card Code/history facts. In a separate test force an intermediate persistence failure and assert all three aggregates retain pre-command state.

- [ ] **Step 5: Run the cross-module suite**

Run: `./mvnw -Dtest=Phase2CoreModulesE2ETest test`

Expected: PASS using isolated test infrastructure; no Phase 3 service call is needed.

- [ ] **Step 6: Commit acceptance scenarios**

```bash
git add src/test/java/com/uit/dormmanagement/e2e/Phase2CoreModulesE2ETest.java
git commit -m "test: add phase two core acceptance scenarios"
```

### REG-3: Execute final compile, module slices, and full regression

**Classification:** Verification gate.

**Spec sections:** Overview §9–§10 and all domain acceptance sections.

**Dependencies:** REG-1 and REG-2 plus every module acceptance task.

**Acceptance criteria:** Compilation, each module slice, full Maven suite, authoritative-document/Flyway diff checks, and final status review all pass with actual counts and environment limitations reported.

**Scope exclusions:** No code repair is hidden inside this gate; failures return to the owning task and no commit/push occurs without approval.

**Files:**
- No production files.
- Update tests only to correct genuine requirement changes; never weaken obsolete assertions merely to turn the build green.

**Interfaces:**
- Consumes: every previous plan task.
- Produces: reproducible verification record for implementation review.

- [ ] **Step 1: Compile without tests**

Run: `./mvnw -DskipTests compile`

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run Auth/RBAC slice**

Run the exact final command from AUTH-7.

Expected: zero failures/errors; record pass/fail/skip counts.

- [ ] **Step 3: Run Room/Bed slice**

Run the exact final command from ROOM-6.

Expected: zero failures/errors; record Docker/PostgreSQL status.

- [ ] **Step 4: Run Tenant slice**

Run the exact final command from TEN-5.

Expected: zero failures/errors.

- [ ] **Step 5: Run Vehicle slice**

Run the exact final command from VEH-7.

Expected: zero failures/errors; no legacy migration/event/concurrency-stress tests.

- [ ] **Step 6: Run full Maven test suite**

Run: `./mvnw test`

Expected: `BUILD SUCCESS`, zero failures/errors. Report the actual pass/fail/skip counts; never repeat the historical 223 count unless this run produces it.

- [ ] **Step 7: Inspect final scope and authoritative files**

Run: `git diff --check && git status --short && git diff --stat && git diff -- docs/SRS.md docs/Conceptual_ERD.md docs/Logical-ERD.md src/main/resources/db/migration`

Expected: no whitespace errors; authoritative documents and Flyway unchanged; only approved Phase 2 code/tests/docs changed; no Phase 3–5 or teammate-owned implementation.

## Plan Acceptance Criteria

- Fresh PostgreSQL migration, Hibernate validation, context startup, compilation, module slices, and full tests pass.
- Actual test counts and infrastructure limitations are reported accurately.
- Cross-module authorization, historical access, private evidence, and atomic Vehicle replacement are demonstrated.
- Phase 1 schema and authoritative documents are unchanged.
- No later-phase implementation, Notification event architecture, OCI, distributed lock, or Refresh replay framework appears.
