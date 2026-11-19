# Phase 2 Authentication & RBAC — Focused Hardening Design

**Date:** 2026-11-16
**Status:** Revised — ready for final explicit approval
**Requirements:** FR-21–FR-24; BR-09, BR-11; Logical ERD T09
**Parent design:** `2026-11-16-phase-2-focused-brownfield-hardening-design.md`

## 1. Existing baseline

### 1.1 Preserve

- One login route and `LoginRequest.identifier` contract (`AuthController.java:38-46`).
- Staff email versus Tenant CCCD lookup; Tenant email is filtered out (`AuthServiceImpl.java:292-304`).
- BCrypt strength 12 and hash-only persistence (`SecurityConfig.java:52-55`; `User.java:45-49`).
- Signed Access/Refresh JWTs with UUID subject, JTI and token type (`JwtService.java:54-103`).
- Redis Refresh Token registry, JTI blacklist, login-attempt counter and OTP storage concepts.
- JWT filter reloads the current User and rejects `LOCKED` accounts (`JwtAuthenticationFilter.java:51-70`).
- Manager-only Technician list/detail/update/status routes and three fixed roles (`UserController.java:50-108`).
- Authenticated change-password flow requires the old password and no OTP (`AuthServiceImpl.java:185-210`).

### 1.2 Correct

- CCCD login tests and Tenant-email rejection already exist (`AuthServiceTest.java:105-160`).
- Refresh rotation revokes the consumed Refresh JTI in the normal path (`AuthServiceImpl.java:90-131`).
- OTPs are six digits, expire after five minutes, have a retry cap and are deleted on successful verification (`OtpServiceImpl.java:21-87`).
- Manager email has no update API.

### 1.3 Incorrect or incomplete

- Temporary lock activates on the fifth failure, not after more than five (`LoginAttemptServiceImpl.java:17-47`).
- `revokeAllUserSessions` removes Refresh records only; other Access Tokens survive logout-all and password changes/resets (`TokenRevocationServiceImpl.java:69-80`).
- Forgot Password accepts email only, including Tenant email, rather than Tenant CCCD/staff email (`AuthServiceImpl.java:212-271`).
- OTP/reset state is keyed by caller-supplied email rather than an immutable User-bound recovery challenge.
- Technician creation accepts a caller-supplied initial password (`CreateTechnicianRequest.java:45-59`).
- Current standalone controller tests do not load the Security filter chain.

## 2. Design goals and exclusions

### 2.1 Goals

1. Retain current routes where the route itself is valid.
2. Preserve simple Refresh rotation while making user-wide invalidation complete for logout-all, lock, and password change/reset.
3. Implement recovery with CCCD for Tenant and email for staff without account enumeration.
4. Generate initial/assisted-reset passwords server-side and show them once.
5. Preserve three roles and the Phase 1 User schema.

### 2.2 Exclusions

- No OAuth/OIDC, social login, MFA, SQL session/OTP tables, custom permission matrix or multiple Manager administration.
- No forced password change on first login.
- No email-change OTP or password/email audit-history entity.
- No notification broker or new infrastructure.

## 3. Focused component design

### 3.1 Existing components retained

- `AuthController`, `UserController`.
- `AuthService`/`AuthServiceImpl`, `UserService`/`UserServiceImpl`.
- `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `SecurityConfig`.
- `LoginAttemptService`, `OtpService`, `TokenRevocationService` interfaces, refined rather than replaced wholesale.

### 3.2 Focused collaborators

1. **`UserTokenInvalidationService` (new):** owns user-wide Access invalidation and Refresh-session revocation in Redis.
2. **`PasswordRecoveryService` (new or extracted from `AuthServiceImpl`):** resolves recovery identifiers, creates opaque challenges, verifies OTP atomically and consumes reset tokens.
3. **`RandomPasswordGenerator` (new):** one implementation shared by Technician creation and Manager-assisted reset.

These collaborators prevent `AuthServiceImpl` and `UserServiceImpl` from duplicating security-critical Redis/random-password logic. They do not create a new architectural layer.

`PasswordRecoveryService` also owns a per-User Redis `recoveryVersion`. Every OTP challenge and reset token records that version. Email correction, assisted password reset and successful password reset advance it, invalidating all earlier recovery credentials without enumerating keys.

## 4. Authentication behavior

### 4.1 Login identifier and status

1. Trim the identifier.
2. If it matches 12 digits, resolve `Tenant.id_card_number` and its User. Do not fall back to email or phone.
3. Otherwise canonicalize as lowercase email and resolve only `MANAGER`/`TECHNICIAN` Users.
4. Missing identity and wrong password both return the same `401 AUTH_401_CREDENTIALS` envelope.
5. A login attempt for a `LOCKED` User returns the same `401 AUTH_401_CREDENTIALS` envelope as an unknown identifier/wrong password, so the public login route does not reveal account existence or status. Authenticated requests using an old token are denied because the filter reloads the locked User.
6. Password comparison uses the configured BCrypt encoder.

### 4.2 Temporary login lock

- Failure counts are keyed by canonical login identifier.
- Attempts 1–5 record failure but do not create the lock key.
- The sixth consecutive failure creates a 15-minute lock and returns `429 Too Many Requests`; subsequent attempts during the TTL return the same response and `Retry-After` when the remaining TTL is available.
- Successful login deletes the counter and lock key.
- A 15-minute inactivity TTL on the counter defines the consecutive-failure window.
- Redis increment/TTL/lock creation is atomic, implemented with a Redis script or equivalent atomic operation.

### 4.3 Token issuance

- Access and Refresh JWTs keep UUID subject, JTI, token type, issued/expiry times and role.
- Access and Refresh JWTs add a per-User `sessionGeneration` claim containing a cryptographically random opaque value; standard `iat` remains for interoperability.
- Refresh Redis entries store only the active JTI marker/TTL needed by the existing registry, not the full plaintext JWT.
- A successful login creates one active Refresh session and returns the existing `AuthResponse` shape unless a field is explicitly deprecated.

### 4.4 Simple Refresh rotation

`POST /api/v1/auth/refresh-token` preserves the existing DTO and uses the existing Redis Refresh registry:

1. Verify signature, expiry, Refresh token type, User ID, current User status, `sessionGeneration`, and that the Refresh JTI is active.
2. Generate a new Access/Refresh pair carrying the same current session generation.
3. Revoke/remove the supplied old Refresh JTI during rotation and register the new Refresh JTI with its TTL.
4. Return the new pair.

An invalid, expired, missing, or already-revoked Refresh Token returns `401 INVALID_REFRESH_TOKEN`. Reuse never rotates the User session generation and never revokes other active sessions. There is no `USED` state, replay marker, replay-triggered global logout, server-side session discovery, or dedicated concurrent-refresh protocol.

The implementation must not claim an atomic single-use guarantee that it does not provide. Sequential reuse after a successful rotation is rejected; simultaneous requests are outside the dedicated Phase 2 concurrency guarantee for this academic system.

### 4.5 User-wide token invalidation

Redis stores a cryptographically random opaque `sessionGeneration` per User. A successful credential login atomically creates a fresh unpredictable generation only when the key is missing, then embeds the effective stored value in both Access and Refresh JWTs. Token validation requires the key to exist and match; a missing key is always rejected. Global invalidation atomically replaces the generation and revokes active Refresh sessions.

The generation key is retained for at least the maximum Refresh Token lifetime and its TTL is extended whenever a token carrying that generation is issued. Redis loss therefore invalidates all existing tokens. A later credential login creates a different unpredictable generation, so pre-loss tokens cannot become valid again. JWTs issued before this cutover, which have no generation claim, are deliberately rejected and require a fresh login; no compatibility bypass is permitted.

`JwtAuthenticationFilter` rejects an Access Token when:

- its JTI is individually blacklisted;
- its `sessionGeneration` differs from the current Redis generation;
- its subject cannot be loaded; or
- the current User is `LOCKED`.

Global invalidation is invoked by:

- logout-all;
- password change;
- password reset;
- Manager-assisted password reset;
- Technician lock.

The new generation remains effective after Technician reactivation, so pre-lock Access Tokens do not become valid again. Generation comparison also removes the same-millisecond ambiguity of a timestamp-based marker.

### 4.6 Logout

**Single logout:** preserve the current request design. Require the Access Token in the Authorization header and a Refresh Token in the request body. Before any revocation, verify the supplied token's signature, expiry, token type `REFRESH`, current `ACTIVE` User, current session generation, active Refresh JTI and User-subject equality with the authenticated principal. Only then blacklist the current Access JTI for its remaining TTL and revoke the supplied Refresh JTI. A missing Refresh Token is a 400 request error; any invalid or non-owned Refresh Token is rejected without modifying either token. The endpoint does not discover sessions server-side and adds no replay state or concurrent-refresh guarantee.

**Logout-all:** authenticate the caller, rotate the User session generation, and revoke all active Refresh sessions. The route remains `POST /api/v1/auth/logout-all`.

## 5. Password behavior

### 5.1 Authenticated change password

1. Require current Access authentication.
2. Verify old password, confirmation, policy, and difference from the current password.
3. Save/flush the new BCrypt hash.
4. Invoke global token invalidation. If Redis invalidation fails, throw and roll back the database change; an early Redis invalidation followed by a later DB rollback is acceptable because it only logs the User out.
5. Return success and require login again.

No OTP is generated or accepted.

### 5.2 Forgot Password challenge

`POST /api/v1/auth/forgot-password` changes from email-only to a generic identifier.

1. Accept `{ "identifier": "<CCCD-or-staff-email>" }`.
2. Normalize the supplied identifier, hash it for rate-limit keys, and atomically apply a 60-second resend cooldown plus the configured hourly cap before resolving an account. The exact same keys and limits apply to known and unknown identifiers. A rate-limited request returns `429` with `Retry-After` and no challenge ID.
3. Resolve exactly as login does.
4. Generate an opaque `recoveryRequestId` for every allowed structurally valid request, including an unknown identifier.
5. For a real User, acquire the same short User-row lock used by identity correction, read the current email and recovery version, invalidate any earlier active challenge, and store the new challenge keyed by request ID and bound to `userId`, destination email, recovery version, OTP digest, expiry and attempt count. Commit/release the DB lock before mail I/O, then send the six-digit OTP to the locked snapshot of `users.email`. If an email correction follows, it advances the recovery version and makes this challenge unusable.
6. For an unknown User, return an indistinguishable response and a non-resolvable random request ID. Do not log the raw identifier.
7. Response message, HTTP status and public fields are identical for existing and missing identities.

### 5.3 OTP verification and reset

- `POST /api/v1/auth/verify-forgot-password-otp` accepts `recoveryRequestId` and six-digit `otp`, not email.
- Redis atomically compares the challenge and increments failed attempts. Wrong attempts 1 and 2 return the generic invalid-OTP 400. The third wrong attempt invalidates the challenge immediately and returns `429 OTP_MAX_ATTEMPTS_EXCEEDED`; later use of that request ID returns the generic expired/invalid 400.
- Success returns a high-entropy single-use `resetToken`; Redis maps only that token/challenge to the immutable User ID and current recovery version for a short TTL.
- `POST /api/v1/auth/reset-password` accepts `resetToken`, new password and confirmation; it does not accept email.
- Consuming the reset token, changing the hash and invalidating tokens must not permit a second password reset. Under the common User-row lock, the service rechecks the stored recovery version, atomically consumes the Redis reset token, saves the password hash, advances the recovery version to invalidate sibling credentials, and rotates the session generation. If the DB write fails after the security credential is consumed, the User requests a new challenge rather than reusing it.

Every recovery-sensitive mutation follows one order: lock the User row, read/recheck `recoveryVersion`, then read or mutate the Redis challenge/reset credential. Email correction, public reset, and Manager-assisted reset all use this protocol. No command may consume a reset token first and acquire the User lock afterward. Mail is sent only after the short database transaction releases the lock.

### 5.4 Initial and assisted passwords

- `RandomPasswordGenerator` uses `SecureRandom`, the application password policy, and enough entropy; no default/shared password is allowed.
- Technician creation removes `password` from the request and returns the generated plaintext exactly once in a dedicated create response. It is never logged or stored outside the BCrypt hash.
- Manager-assisted reset for Technician and Tenant uses explicit Manager-only commands after offline identity verification and returns a one-time generated password. Under the same User-row lock, it advances the recovery version and rotates the session generation, invalidating all recovery credentials and login tokens.

## 6. API contract

| Method and route | Role | Request | Response | Errors / breaking change |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | Public | Existing `LoginRequest(identifier,password)` | Existing `AuthResponse` | 401 generic invalid/locked, 429 temporary lock; route retained |
| `POST /api/v1/auth/refresh-token` | Public | Existing required Refresh Token DTO | Existing `AuthResponse` | 401 invalid/expired/revoked; route retained; no global invalidation on reuse |
| `POST /api/v1/auth/logout` | Authenticated | Required Refresh Token body + current Authorization header | Success | Validate Refresh ownership; blacklist current Access JTI and revoke supplied Refresh JTI; route retained |
| `POST /api/v1/auth/logout-all` | Authenticated | None | Success | Invalidates all old Access/Refresh tokens |
| `POST /api/v1/auth/change-password` | Authenticated | Existing old/new/confirmation DTO | Success | Route retained; all old tokens invalidated |
| `POST /api/v1/auth/forgot-password` | Public | `PasswordRecoveryRequest(identifier)` | `PasswordRecoveryRequestedResponse(recoveryRequestId, expiresInSeconds)` in standard envelope | **Breaking:** `email` becomes `identifier`; generic 200 |
| `POST /api/v1/auth/verify-forgot-password-otp` | Public | `VerifyRecoveryOtpRequest(recoveryRequestId,otp)` | Existing reset-token concept | **Breaking:** email removed; 400 invalid/expired, 429 attempts |
| `POST /api/v1/auth/reset-password` | Public | `ResetPasswordRequest(resetToken,newPassword,confirmPassword)` | Success | **Breaking:** email removed; 400 invalid/expired |
| `POST /api/v1/users/technicians` | Manager | Name/email/phone; no password | `CreateTechnicianResponse(technician,initialPassword)` | **Breaking request/response**; 409 email conflict |
| `PATCH /api/v1/users/technicians/{id}/status` | Manager | Existing active boolean may be retained for compatibility | Technician | Lock invalidates all tokens; reactivation does not restore old tokens |
| `POST /api/v1/users/technicians/{id}/reset-password` | Manager | None | One-time password response | New; 404 target, 409 already locked is not required |

Tenant-assisted reset is specified in the Tenant Profile design to keep its route and actor semantics with that module.

## 7. Persistence and configuration

- No PostgreSQL schema or Flyway migration change.
- `users.status` remains `ACTIVE/LOCKED` only.
- Redis keys are namespaced under the existing `dorm:auth:` prefix.
- New Redis keys cover User session generation, recovery request rate limits/challenge and reset token. Refresh storage retains the existing active/revoked registry model; no `USED` or replay-marker state is added.
- Redis scripts/operations must set TTLs atomically and must not expose OTP, password or full JWT values in logs.
- Existing JWT expiry configuration remains authoritative.

## 8. Error and failure behavior

- Redis unavailable during login/refresh/recovery is an authentication-service failure, not a silent bypass.
- Redis failure during a security-sensitive DB mutation causes the mutation to fail/roll back where possible. Advancing recovery/session state before a later database rollback is an acceptable fail-closed logout or credential invalidation; the reverse ordering is not allowed when it could leave an old credential usable after a committed password/status change.
- Mail delivery failure must not create an account-existence oracle: invalidate the undelivered challenge, record a masked operational error, and still return the same generic public response. The User may retry after the normal cooldown; no message broker is required.
- Unique-email races are translated from PostgreSQL to `409 AUTH_409_EMAIL`.
- Remove `ACCOUNT_INACTIVE` from touched flows; map non-`ACTIVE` only to the finalized `LOCKED` behavior.

## 9. Testing strategy

### 9.1 Unit/service

- Staff email and Tenant CCCD login; Tenant email, phone and username never authenticate.
- Failures 1–5 do not lock; failure 6 locks for 15 minutes; success resets.
- A valid active Refresh Token produces a new pair and the old Refresh Token is revoked.
- Sequential reuse of an invalid, expired or revoked Refresh Token returns `401 INVALID_REFRESH_TOKEN` without revoking other User sessions.
- Single logout requires both tokens and cannot revoke another User's Refresh Token.
- Global invalidation rejects every old-generation Access/Refresh Token after logout-all, account lock, or password change/reset.
- Lock/reactivate does not resurrect old tokens.
- Change password uses old password/no OTP and invalidates tokens.
- Recovery resolves Tenant by CCCD/staff by email and never by Tenant email.
- Unknown and known recovery requests have the same public response contract.
- OTP expiry, attempt cap, one-time use and concurrent verification.
- Reset token is bound to one User, consumed once, and invalidates tokens.
- Generated password meets policy and is never supplied by caller.

### 9.2 Security integration

- Real Spring Security chain returns 401 for anonymous, invalid/revoked/legacy-generation tokens and tokens whose current User is `LOCKED`; it returns 403 only for an authenticated active User with the wrong role/ownership.
- Locked User's previously issued token is denied with 401 and reactivation does not restore it.
- Blacklisted JTI and User session generation are enforced by `JwtAuthenticationFilter`.
- Technician endpoints are inaccessible to Technician/Tenant roles.
- Manager email has no writable route.

### 9.3 Redis integration

Use an isolated Redis test instance or container for login-attempt/OTP atomic scripts, TTLs, session-generation invalidation, and normal Refresh registration/revocation. Dedicated concurrent-refresh testing is not an acceptance requirement.

## 10. Cross-phase contracts

- Phase 3 onboarding uses `RandomPasswordGenerator` for new Tenant credentials and displays them once after the database transaction succeeds.
- Phase 3 returning-Tenant onboarding reuses the User and does not reset credentials automatically.
- Phase 5 Tenant Portal relies on the same post-checkout login behavior.
- No notification is emitted for password actions unless a future requirement explicitly adds it.

## 11. Acceptance criteria

1. Every old Access Token is denied after logout-all, lock, password change or reset.
2. Normal Refresh rotation revokes the old Refresh Token; later reuse returns `401 INVALID_REFRESH_TOKEN` without invalidating unrelated sessions.
3. Temporary lock begins on the sixth consecutive failure and expires after 15 minutes.
4. Forgot Password accepts Tenant CCCD or staff email and binds OTP/reset state to one User.
5. Technician creation and Manager-assisted reset generate one-time random passwords server-side.
6. Real authorization integration tests cover 401/403/locked/revoked behavior.
7. No PostgreSQL auth session/OTP table or `INACTIVE` status is introduced.
