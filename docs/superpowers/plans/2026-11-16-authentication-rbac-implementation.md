# Authentication & RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden login, JWT/Refresh lifecycle, password recovery, and Technician administration while retaining the existing routes and the simplified Refresh-reuse behavior.

**Architecture:** Keep `AuthServiceImpl`, `UserServiceImpl`, `JwtService`, and the current Redis-backed token registry. Add focused collaborators only for user-wide session generations, User-bound password recovery, and random password generation; normal Refresh rotation remains a simple active-JTI revoke/register flow.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Security, Spring Data JPA, Spring Data Redis, JJWT, PostgreSQL, JUnit 5, Mockito, MockMvc, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-16-authentication-rbac-design.md`

**Git execution policy:** Commit snippets are reference-only boundaries. An implementation agent MUST NOT stage, commit, or push unless the user or final execution prompt explicitly authorizes it.

## Global Constraints

- Preserve `/api/v1/auth/**` and `/api/v1/users/**` routes unless the approved spec explicitly adds a route.
- `users.status` remains exactly `ACTIVE` or `LOCKED`; never add `INACTIVE` or a PostgreSQL auth/OTP table.
- Single logout requires the Authorization Access Token and request-body Refresh Token and validates Refresh signature, expiry, `REFRESH` type, current User/session generation, active JTI and common ownership before revocation.
- Refresh reuse returns `401 INVALID_REFRESH_TOKEN`; it never invalidates unrelated sessions and has no `USED`/replay state or concurrent single-use guarantee.
- User-wide session-generation rotation remains mandatory for logout-all, account lock, and password change/reset.
- Tenant login uses 12-digit CCCD; Manager/Technician login uses canonical email; Tenant email, phone, and username never authenticate.
- Do not modify Flyway or the Phase 1 database schema.
- Phase 3 onboarding and Phase 5 notifications remain out of scope.

## Review Focus

- A Refresh Token belonging to another User must not be revoked by single logout; AUTH-3 adds the ownership-negative test.
- Redis generation loss must fail closed and a later login must not resurrect old tokens; AUTH-2 adds the missing-key/new-generation test.
- Known and unknown recovery identifiers must expose the same 200 response shape; AUTH-5 adds paired controller/service assertions.
- A third wrong OTP must invalidate the challenge and return 429, while the first two return 400; AUTH-5 pins the exact boundary.
- Reactivating a locked Technician must not restore pre-lock tokens; AUTH-2 and AUTH-6 add generation and security-chain tests.

---

### AUTH-1: Correct the sixth-failure temporary login lock

**Classification:** REFACTOR — FR-21.

**Spec sections:** Authentication §4.1–§4.2, §8, §9.1.

**Dependencies:** None; preserve the existing identifier routing and password encoder.

**Acceptance criteria:** The sixth consecutive bad login starts a 15-minute temporary lock, successful login clears failures, and public responses do not reveal whether the account exists or is locked.

**Scope exclusions:** No JWT/Refresh redesign, account-status change, or new login identifier.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/LoginAttemptServiceImpl.java:17-91`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java:53-88`
- Test: `src/test/java/com/uit/dormmanagement/services/LoginAttemptServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java`

**Interfaces:**
- Consumes: existing `LoginAttemptService.recordFailedAttempt/resetAttempts/isLocked`.
- Produces: sixth-failure lock with 15-minute TTL and canonical identifier behavior.

- [ ] **Step 1: Update failing lock-boundary tests**

Add `firstFiveFailuresDoNotLock`, `sixthFailureCreatesFifteenMinuteLock`, `successfulLoginClearsAttemptAndLockKeys`, canonical email/CCCD key tests, and `permanentlyLockedLoginUsesGenericCredentialsError`. Assert attempts 1–5 remain unlocked, attempt 6 returns `ACCOUNT_TEMPORARILY_LOCKED`, Redis lock TTL is 15 minutes, and public login does not reveal permanent account status.

- [ ] **Step 2: Run the focused tests and confirm the old fifth-attempt behavior fails**

Run: `./mvnw -Dtest=LoginAttemptServiceTest,AuthServiceTest test`

Expected: FAIL on the new sixth-attempt assertions before implementation.

- [ ] **Step 3: Implement the exact boundary**

Change `LoginAttemptServiceImpl.recordFailedAttempt(String)` so the counter/TTL update is atomic, attempts 1–5 do not set the lock key, and attempt 6 sets a 15-minute lock. After recording a failed password/unknown identifier, make `AuthServiceImpl.login` return the temporary-lock error on the sixth failure. Map a permanently `LOCKED` User to the same public credentials error as unknown/wrong password. Keep the existing interface; do not add a new lock service.

- [ ] **Step 4: Verify the focused tests**

Run: `./mvnw -Dtest=LoginAttemptServiceTest,AuthServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit the isolated lock-boundary change**

```bash
git add src/main/java/com/uit/dormmanagement/services/impl/LoginAttemptServiceImpl.java src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java src/test/java/com/uit/dormmanagement/services/LoginAttemptServiceTest.java src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java
git commit -m "fix: lock login after sixth failure"
```

### AUTH-2: Add user-wide session generation validation

**Classification:** EXTEND — FR-21–FR-23.

**Spec sections:** Authentication §3.2, §4.3, §4.5, §7, §9.

**Dependencies:** AUTH-1 may land first; consumes the existing Redis token-revocation service.

**Acceptance criteria:** Every issued pair carries one current User generation, missing/mismatched generations fail closed, and `invalidateAll` makes all earlier Access/Refresh Tokens unusable.

**Scope exclusions:** No database/session table, replay marker, or normal-refresh global invalidation.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/UserTokenInvalidationService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/UserTokenInvalidationServiceImpl.java`
- Modify: `src/main/java/com/uit/dormmanagement/constants/RedisKeyConstants.java:5-48`
- Modify: `src/main/java/com/uit/dormmanagement/security/TokenPayload.java:6-14`
- Modify: `src/main/java/com/uit/dormmanagement/security/JwtService.java:54-129`
- Modify: `src/main/java/com/uit/dormmanagement/security/JwtAuthenticationFilter.java:23-83`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java:274-289`
- Test: `src/test/java/com/uit/dormmanagement/security/JwtServiceTest.java`
- Create: `src/test/java/com/uit/dormmanagement/services/UserTokenInvalidationServiceTest.java`
- Create: `src/test/java/com/uit/dormmanagement/security/JwtAuthenticationFilterTest.java`

**Interfaces:**
- Produces: `String getOrCreateGeneration(UUID userId)`, `boolean isCurrentGeneration(UUID userId, String generation)`, and `void invalidateAll(UUID userId)` on `UserTokenInvalidationService`.
- Produces: `JwtService.generateAccessToken(User,String,String)` and `generateRefreshToken(User,String,String)` where the third argument is `sessionGeneration`.
- Produces: `TokenPayload.sessionGeneration()`.

- [ ] **Step 1: Write failing generation and JWT tests**

Assert a generated Access/Refresh pair carries the same nonblank generation; a token without the claim is rejected by the filter; a missing Redis key fails validation; `invalidateAll` stores a different cryptographically random generation and invokes `TokenRevocationService.revokeAllUserSessions(userId)`; recreating after Redis loss never equals the old generation.

- [ ] **Step 2: Run the focused tests**

Run: `./mvnw -Dtest=JwtServiceTest,JwtAuthenticationFilterTest,UserTokenInvalidationServiceTest test`

Expected: FAIL because generation APIs/claim do not exist.

- [ ] **Step 3: Implement generation Redis keys and service**

Add `RedisKeyConstants.sessionGenerationKey(UUID)`. Implement the three interface methods with opaque `SecureRandom`/UUID-strength values, compare the exact stored value, retain the key for at least the Refresh lifetime, and fail closed on missing state during token validation.

- [ ] **Step 4: Add the JWT claim and filter check**

Extend `TokenPayload`, both token generators, parsing, and `JwtAuthenticationFilter`. The filter must require an ACCESS token, nonblacklisted JTI, matching generation, loadable current User, and `ACTIVE` status before authenticating.

- [ ] **Step 5: Issue tokens with the effective generation**

Update `AuthServiceImpl.generateAuthResponse(User)` to call `getOrCreateGeneration(userId)` once and pass that exact value to both token generators.

- [ ] **Step 6: Run the focused tests**

Run: `./mvnw -Dtest=JwtServiceTest,JwtAuthenticationFilterTest,UserTokenInvalidationServiceTest,AuthServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit the session-generation foundation**

```bash
git add src/main/java/com/uit/dormmanagement/constants/RedisKeyConstants.java src/main/java/com/uit/dormmanagement/security/TokenPayload.java src/main/java/com/uit/dormmanagement/security/JwtService.java src/main/java/com/uit/dormmanagement/security/JwtAuthenticationFilter.java src/main/java/com/uit/dormmanagement/services/UserTokenInvalidationService.java src/main/java/com/uit/dormmanagement/services/impl/UserTokenInvalidationServiceImpl.java src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java src/test/java/com/uit/dormmanagement/security/JwtServiceTest.java src/test/java/com/uit/dormmanagement/security/JwtAuthenticationFilterTest.java src/test/java/com/uit/dormmanagement/services/UserTokenInvalidationServiceTest.java src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java
git commit -m "feat: invalidate jwt by user session generation"
```

### AUTH-3: Preserve simple Refresh rotation and strict single logout

**Classification:** REFACTOR — FR-21–FR-22.

**Spec sections:** Authentication §4.4, §4.6, §6, §8, §9.

**Dependencies:** AUTH-2 session-generation and JWT claim support.

**Acceptance criteria:** Active Refresh rotation revokes only the supplied token; invalid/expired/revoked reuse returns `401 INVALID_REFRESH_TOKEN`; logout validates the supplied token's signature, expiry, `REFRESH` type, current User/session generation, active Refresh JTI, and authenticated-User ownership before blacklisting the Access Token and revoking the Refresh Token.

**Scope exclusions:** No session discovery, `USED` state, concurrent-refresh guarantee, or replay-triggered logout-all.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/controllers/AuthController.java:48-77`
- Modify: `src/main/java/com/uit/dormmanagement/services/TokenRevocationService.java:5-18`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/TokenRevocationServiceImpl.java:16-80`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java:90-183`
- Modify: `src/main/java/com/uit/dormmanagement/exceptions/error/AuthErrorCode.java`
- Test: `src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/AuthControllerTest.java`
- Test: `src/test/java/com/uit/dormmanagement/services/TokenRevocationServiceTest.java`

**Interfaces:**
- Consumes: AUTH-2 generation-aware tokens.
- Preserves: `AuthService.refreshToken(RefreshTokenRequest)` and `logout(String,RefreshTokenRequest)`.
- Produces: `TokenRevocationService.recordRefreshToken(UUID userId,String jti,long ttlSeconds)` storing an active marker rather than plaintext JWT.
- Produces: required logout body and `401 INVALID_REFRESH_TOKEN` for invalid/expired/revoked Refresh Tokens.

- [ ] **Step 1: Write failing Refresh and logout tests**

Add tests asserting: valid active Refresh returns a new pair and revokes only the supplied old JTI; sequential reuse returns `INVALID_REFRESH_TOKEN`; another active Refresh session remains valid. For logout, reject a missing body, invalid signature, expired token, Access Token supplied as Refresh, missing/revoked Refresh JTI, stale session generation, locked/missing User, and different-User Refresh Token without blacklisting or revoking either supplied token; blacklist/revoke both caller-owned tokens only after every check succeeds.

- [ ] **Step 2: Run the focused tests**

Run: `./mvnw -Dtest=AuthServiceTest,AuthControllerTest,TokenRevocationServiceTest test`

Expected: FAIL because reuse currently calls `revokeAllUserSessions` and the logout body is optional.

- [ ] **Step 3: Simplify Refresh rotation**

In `AuthServiceImpl.refreshToken`, validate signature/type/expiry/User/status/generation/active JTI, revoke that JTI, then issue/register the new pair. Map invalid, expired, missing, and revoked cases to `AuthErrorCode.INVALID_REFRESH_TOKEN`; remove replay-triggered global revocation and `TOKEN_REVOKED` from this path.

- [ ] **Step 4: Stop storing plaintext Refresh Tokens**

Update `TokenRevocationService`/implementation so Refresh entries contain only an active marker and TTL; remove the plaintext token parameter from all callers.

- [ ] **Step 5: Enforce the existing logout contract strictly**

Make `AuthController.logout` require `@Valid @RequestBody RefreshTokenRequest`. In `AuthServiceImpl.logout`, validate the body token's signature and expiry, require token type `REFRESH`, load the current `ACTIVE` User, require the current session generation and active Refresh JTI, and require its User ID to equal the authenticated Access principal. Only after all checks succeed, blacklist the current Access JTI and revoke the supplied Refresh JTI. Do not enumerate or discover sessions, add replay state, or claim concurrent-refresh guarantees.

- [ ] **Step 6: Verify focused tests**

Run: `./mvnw -Dtest=AuthServiceTest,AuthControllerTest,TokenRevocationServiceTest test`

Expected: PASS; no test claims concurrent single-use behavior.

- [ ] **Step 7: Commit Refresh/logout behavior**

```bash
git add src/main/java/com/uit/dormmanagement/controllers/AuthController.java src/main/java/com/uit/dormmanagement/services/TokenRevocationService.java src/main/java/com/uit/dormmanagement/services/impl/TokenRevocationServiceImpl.java src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java src/main/java/com/uit/dormmanagement/exceptions/error/AuthErrorCode.java src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java src/test/java/com/uit/dormmanagement/controllers/AuthControllerTest.java src/test/java/com/uit/dormmanagement/services/TokenRevocationServiceTest.java
git commit -m "fix: simplify refresh rotation and validate logout ownership"
```

### AUTH-4: Apply global invalidation to logout-all and password mutations

**Classification:** REFACTOR — FR-22/FR-24.

**Spec sections:** Authentication §4.5, §5.1, §8, §9.

**Dependencies:** AUTH-2 and AUTH-3.

**Acceptance criteria:** Logout-all and every successful password change/reset rotate User generation and revoke registered sessions; Redis invalidation failure is surfaced and the command is never reported successful with an unrotated generation.

**Scope exclusions:** No password-recovery redesign in this task and no global invalidation for ordinary Refresh reuse.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java:165-210,244-271`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/UserServiceImpl.java:167-185`
- Test: `src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/services/UserServiceTest.java`

**Interfaces:**
- Consumes: `UserTokenInvalidationService.invalidateAll(UUID)` from AUTH-2.
- Produces: irreversible invalidation of every old Access/Refresh token on logout-all, lock, and password change/reset.

- [ ] **Step 1: Add failing global-invalidation tests**

Assert each triggering command invokes generation rotation once; Technician reactivation does not restore or reset the generation; normal Refresh reuse from AUTH-3 never invokes global invalidation.

- [ ] **Step 2: Run tests and confirm missing calls**

Run: `./mvnw -Dtest=AuthServiceTest,UserServiceTest test`

Expected: FAIL on generation invalidation assertions.

- [ ] **Step 3: Replace Refresh-only revocation calls**

Inject `UserTokenInvalidationService` into `AuthServiceImpl`/`UserServiceImpl`. Invoke `invalidateAll` after successful logout-all, password change/reset, and Technician lock; preserve the existing database transactions and fail closed on Redis failure.

- [ ] **Step 4: Verify tests**

Run: `./mvnw -Dtest=AuthServiceTest,UserServiceTest,JwtAuthenticationFilterTest test`

Expected: PASS.

- [ ] **Step 5: Commit global invalidation wiring**

```bash
git add src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java src/main/java/com/uit/dormmanagement/services/impl/UserServiceImpl.java src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java src/test/java/com/uit/dormmanagement/services/UserServiceTest.java
git commit -m "fix: invalidate all tokens on security mutations"
```

### AUTH-5: Replace email-keyed recovery with User-bound challenges

**Classification:** REFACTOR — FR-24.

**Spec sections:** Authentication §5.2–§5.3, §6–§9.

**Dependencies:** AUTH-2 invalidation contract; AUTH-4 password-mutation semantics.

**Acceptance criteria:** Recovery is User-bound, enumeration-resistant, expiring, retry-limited, and one-use; successful reset changes the password and invalidates recovery credentials and all sessions.

**Scope exclusions:** No PostgreSQL OTP table, Tenant profile correction, SMS, or new external identity provider.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/PasswordRecoveryService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/PasswordRecoveryServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/auth/PasswordRecoveryRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/auth/VerifyRecoveryOtpRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/auth/PasswordRecoveryRequestedResponse.java`
- Modify: `src/main/java/com/uit/dormmanagement/dtos/request/auth/ResetPasswordRequest.java`
- Remove after reference check: `src/main/java/com/uit/dormmanagement/dtos/request/auth/ForgotPasswordRequest.java`
- Remove after reference check: `src/main/java/com/uit/dormmanagement/dtos/request/auth/VerifyOtpRequest.java`
- Modify: `src/main/java/com/uit/dormmanagement/controllers/AuthController.java:90-118`
- Modify: `src/main/java/com/uit/dormmanagement/services/AuthService.java:25-29`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java:212-271`
- Modify: `src/main/java/com/uit/dormmanagement/constants/RedisKeyConstants.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/UserRepository.java:12-18`
- Modify: `src/main/java/com/uit/dormmanagement/services/OtpService.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/OtpServiceImpl.java`
- Test: `src/test/java/com/uit/dormmanagement/services/OtpServiceTest.java`
- Create: `src/test/java/com/uit/dormmanagement/services/PasswordRecoveryServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/AuthControllerTest.java`

**Interfaces:**
- Produces: `PasswordRecoveryRequestedResponse request(String identifier)`, `String verify(UUID recoveryRequestId,String otp)`, `void reset(String resetToken,String newPassword,String confirmation)`, and `void invalidateRecoveryCredentials(UUID userId)`.
- Produces: `UserRepository.findByIdForUpdate(UUID)` with `PESSIMISTIC_WRITE`.
- Consumes: AUTH-2 session invalidation and existing `EmailService.sendForgotPasswordOtp`.

- [ ] **Step 1: Add DTO and controller-contract tests**

Assert forgot-password accepts `identifier`; verify accepts `recoveryRequestId` plus six-digit OTP; reset accepts only reset token/new/confirmation; known and unknown identifiers return the same 200 fields; raw email/CCCD is absent from responses.

- [ ] **Step 2: Add failing service tests for recovery state**

Cover Tenant CCCD/staff email resolution, Tenant-email rejection, 60-second cooldown/hourly cap, challenge replacement, five-minute expiry, wrong OTP attempts 1–2 as 400 and attempt 3 as 429+invalidation, reset-token one-time use, recovery-version mismatch, and password/session invalidation after success.

- [ ] **Step 3: Run recovery tests**

Run: `./mvnw -Dtest=PasswordRecoveryServiceTest,OtpServiceTest,AuthControllerTest test`

Expected: FAIL because opaque challenge APIs do not exist.

- [ ] **Step 4: Implement repository locking and Redis key shapes**

Add the User write-lock query and keys for hashed-identifier limits, challenge ID, reset token, and per-User recovery version. Store OTP digests, immutable User IDs, destination snapshot, version, expiry, and attempt count; never log raw identifiers, OTPs, or reset tokens.

- [ ] **Step 5: Implement recovery orchestration**

Implement the three `PasswordRecoveryService` methods with User-lock → recovery-version recheck → Redis credential ordering. Release the DB lock before email I/O; keep known/unknown public responses indistinguishable; consume reset tokens once and rotate both recovery version and session generation after password update.

- [ ] **Step 6: Rewire Auth routes and retire email-shaped DTO usage**

Update `AuthController`, `AuthService`, and `AuthServiceImpl` to delegate to the focused service. Remove usages of `ForgotPasswordRequest.email`, `VerifyOtpRequest.email`, and `ResetPasswordRequest.email`; delete obsolete DTOs only after `rg` confirms no references.

- [ ] **Step 7: Run focused recovery tests**

Run: `./mvnw -Dtest=PasswordRecoveryServiceTest,OtpServiceTest,AuthServiceTest,AuthControllerTest test`

Expected: PASS.

- [ ] **Step 8: Commit recovery refactor**

```bash
git add src/main/java/com/uit/dormmanagement/controllers/AuthController.java src/main/java/com/uit/dormmanagement/constants/RedisKeyConstants.java src/main/java/com/uit/dormmanagement/dtos/request/auth/PasswordRecoveryRequest.java src/main/java/com/uit/dormmanagement/dtos/request/auth/VerifyRecoveryOtpRequest.java src/main/java/com/uit/dormmanagement/dtos/request/auth/ResetPasswordRequest.java src/main/java/com/uit/dormmanagement/dtos/request/auth/ForgotPasswordRequest.java src/main/java/com/uit/dormmanagement/dtos/request/auth/VerifyOtpRequest.java src/main/java/com/uit/dormmanagement/dtos/response/auth/PasswordRecoveryRequestedResponse.java src/main/java/com/uit/dormmanagement/repositories/UserRepository.java src/main/java/com/uit/dormmanagement/services/PasswordRecoveryService.java src/main/java/com/uit/dormmanagement/services/AuthService.java src/main/java/com/uit/dormmanagement/services/OtpService.java src/main/java/com/uit/dormmanagement/services/impl/PasswordRecoveryServiceImpl.java src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java src/main/java/com/uit/dormmanagement/services/impl/OtpServiceImpl.java src/test/java/com/uit/dormmanagement/controllers/AuthControllerTest.java src/test/java/com/uit/dormmanagement/services/PasswordRecoveryServiceTest.java src/test/java/com/uit/dormmanagement/services/OtpServiceTest.java src/test/java/com/uit/dormmanagement/services/AuthServiceTest.java
git commit -m "feat: bind password recovery to user challenges"
```

### AUTH-6: Generate Technician and assisted-reset passwords server-side

**Classification:** REFACTOR/IMPLEMENT — FR-23–FR-24.

**Spec sections:** Authentication §5.4, §6, §8–§9.

**Dependencies:** AUTH-2 and AUTH-5 invalidation services.

**Acceptance criteria:** Technician create/reset credentials are generated with `SecureRandom`, only hashes persist, plaintext is returned once, and reset invalidates recovery state and all sessions.

**Scope exclusions:** No email-delivery workflow, onboarding credential creation, or Manager account mutation.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/RandomPasswordGenerator.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/SecureRandomPasswordGenerator.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/auth/OneTimePasswordResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/auth/CreateTechnicianResponse.java`
- Modify: `src/main/java/com/uit/dormmanagement/dtos/request/auth/CreateTechnicianRequest.java`
- Modify: `src/main/java/com/uit/dormmanagement/controllers/UserController.java:50-108`
- Modify: `src/main/java/com/uit/dormmanagement/services/UserService.java:12-25`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/UserServiceImpl.java:95-225`
- Test: `src/test/java/com/uit/dormmanagement/services/UserServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/UserControllerTest.java`
- Create: `src/test/java/com/uit/dormmanagement/services/SecureRandomPasswordGeneratorTest.java`

**Interfaces:**
- Produces: `String generate()` on `RandomPasswordGenerator`.
- Produces: `CreateTechnicianResponse createTechnician(CreateTechnicianRequest)` and `OneTimePasswordResponse resetTechnicianPassword(UUID)`.
- Consumes: AUTH-2/AUTH-5 invalidation services.

- [ ] **Step 1: Add failing generator/DTO/service tests**

Assert create request has no password; generated values meet the existing password policy and differ across calls; only BCrypt hash is persisted; plaintext appears once in the response and never in logs/entities; assisted reset rotates recovery/session state.

- [ ] **Step 2: Run focused tests**

Run: `./mvnw -Dtest=SecureRandomPasswordGeneratorTest,UserServiceTest,UserControllerTest test`

Expected: FAIL because the caller currently supplies the password and reset route is absent.

- [ ] **Step 3: Implement the generator and response contracts**

Use `SecureRandom` and the existing password-policy character requirements. Remove the password field from `CreateTechnicianRequest`; add one-time response records without persistence/logging helpers.

- [ ] **Step 4: Update Technician creation and add reset route**

Retain `POST /api/v1/users/technicians`, changing only request/response fields. Add Manager-only `POST /api/v1/users/technicians/{id}/reset-password`; lock/load the target User, store the hash, advance recovery version, and invalidate all sessions.

- [ ] **Step 5: Verify focused tests**

Run: `./mvnw -Dtest=SecureRandomPasswordGeneratorTest,UserServiceTest,UserControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit credential-generation behavior**

```bash
git add src/main/java/com/uit/dormmanagement/controllers/UserController.java src/main/java/com/uit/dormmanagement/dtos/request/auth/CreateTechnicianRequest.java src/main/java/com/uit/dormmanagement/dtos/response/auth/OneTimePasswordResponse.java src/main/java/com/uit/dormmanagement/dtos/response/auth/CreateTechnicianResponse.java src/main/java/com/uit/dormmanagement/services/RandomPasswordGenerator.java src/main/java/com/uit/dormmanagement/services/UserService.java src/main/java/com/uit/dormmanagement/services/impl/SecureRandomPasswordGenerator.java src/main/java/com/uit/dormmanagement/services/impl/UserServiceImpl.java src/test/java/com/uit/dormmanagement/controllers/UserControllerTest.java src/test/java/com/uit/dormmanagement/services/SecureRandomPasswordGeneratorTest.java src/test/java/com/uit/dormmanagement/services/UserServiceTest.java
git commit -m "feat: generate technician credentials server side"
```

### AUTH-7: Prove the real Spring Security chain

**Classification:** EXTEND — BR-09/BR-11 regression gate.

**Spec sections:** Authentication §9.2–§9.3, §11; Overview §9.

**Dependencies:** AUTH-1 through AUTH-6 complete.

**Acceptance criteria:** Real filter-chain tests prove 401/403 boundaries, identifier rules, generation invalidation, strict logout ownership, recovery limits, and Technician role restrictions with PostgreSQL/Redis isolation.

**Scope exclusions:** No production behavior or runtime configuration is added solely for these acceptance tests.

**Files:**
- Create: `src/test/java/com/uit/dormmanagement/security/AuthenticationSecurityIntegrationTest.java`
- Create: `src/test/java/com/uit/dormmanagement/support/RedisTestContainerSupport.java`
- Modify: `src/test/java/com/uit/dormmanagement/controllers/AuthControllerTest.java`
- Modify: `src/test/java/com/uit/dormmanagement/controllers/UserControllerTest.java`

**Interfaces:**
- Consumes: all previous Auth tasks.
- Produces: real-filter-chain evidence for 401/403/status/generation behavior.

- [ ] **Step 1: Add Spring Security integration scenarios**

Using `@SpringBootTest` + `@AutoConfigureMockMvc` and a Testcontainers `GenericContainer("redis:7-alpine")` registered through `RedisTestContainerSupport`, assert anonymous protected requests return 401; active wrong-role requests return 403; locked, blacklisted, missing-generation, and old-generation Access Tokens return 401; Manager can administer Technicians; Tenant/Technician cannot; Manager email has no writable route.

- [ ] **Step 2: Run security integration tests**

Run: `./mvnw -Dtest=AuthenticationSecurityIntegrationTest test`

Expected: PASS after AUTH-1–AUTH-6; investigate any mismatch rather than weakening assertions.

- [ ] **Step 3: Run the complete Auth/RBAC slice**

Run: `./mvnw -Dtest='AuthControllerTest,UserControllerTest,AuthServiceTest,UserServiceTest,JwtServiceTest,JwtAuthenticationFilterTest,LoginAttemptServiceTest,OtpServiceTest,PasswordRecoveryServiceTest,TokenRevocationServiceTest,UserTokenInvalidationServiceTest,SecureRandomPasswordGeneratorTest,AuthenticationSecurityIntegrationTest' test`

Expected: all selected tests PASS; no concurrent-Refresh guarantee is asserted.

- [ ] **Step 4: Commit the security acceptance suite**

```bash
git add src/test/java/com/uit/dormmanagement/security/AuthenticationSecurityIntegrationTest.java src/test/java/com/uit/dormmanagement/support/RedisTestContainerSupport.java src/test/java/com/uit/dormmanagement/controllers/AuthControllerTest.java src/test/java/com/uit/dormmanagement/controllers/UserControllerTest.java
git commit -m "test: verify authentication authorization chain"
```

## Plan Acceptance Criteria

- Existing login routes and valid behavior are preserved.
- Temporary lock starts on failure 6 and lasts 15 minutes.
- Normal Refresh rotation revokes the old Refresh only; reuse returns `401 INVALID_REFRESH_TOKEN` without global logout.
- Single logout requires both tokens and validates signature, expiry, `REFRESH` type, current User/session generation, active JTI and common ownership before revocation.
- Logout-all, lock, and password change/reset invalidate every previous Access/Refresh token.
- Recovery is User-bound, enumeration-resistant, rate-limited, one-time, and CCCD/email-role correct.
- Technician credentials are generated server-side and returned once.
- Real Spring Security tests prove 401, 403, lock, generation, and role behavior.
- No schema/Flyway change, Phase 3 workflow, or notification implementation is included.
