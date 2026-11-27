# Verification & Test Report — KTX Hub (Dorm) CI/CD, `/dorm` Subpath & AWS Secrets

**Date:** 2026-11-27  
**Verdict:** **PASS** (All locally verifiable Tier 1–4 checks pass; live AWS Bastion/EC2/GHCR runtime triggers require GitHub Secrets `BASTION_HOST` and `BASTION_SSH_KEY` on push to `main`).

---

## Tier 1: Dev / Unit & Static Quality Results

| Check ID | Target | Command | Observed Result |
|---|---|---|---|
| **T1-FE-01** | `quan-ly-ktx-fe` Lint | `pnpm run lint` (`oxlint`) | **PASS** — `Found 0 warnings and 0 errors. Finished in 24ms on 40 files with 116 rules.` |
| **T1-FE-02** | `quan-ly-ktx-fe` Unit Tests | `pnpm run test` (`vitest run`) | **PASS** — `Test Files 4 passed (4)`, `Tests 14 passed (14)` (`api.config.test.ts` 2, `App.test.tsx` 3, `TechnicianPage.test.tsx` 6, `UserPage.test.tsx` 3). No `.skip`/`xit` present. |
| **T1-FE-03** | `quan-ly-ktx-fe` Subpath Build | `VITE_BASE_PATH=/dorm/ VITE_API_URL=/dorm pnpm run build` | **PASS** — `tsc -b && vite build` built in `1.15s`; `dist/index.html` emits `/dorm/assets/index-BlU6rZ_r.js` and `/dorm/assets/index-F_1U4kl3.css`. |
| **T1-BE-01** | `quan-ly-ktx-be` Formatting | `./mvnw -B spotless:check` | **PASS** — `Spotless.Java is keeping 259 files clean - 0 needs changes to be clean`. |
| **T1-BE-02** | `quan-ly-ktx-be` Test Suite | `make test` (`./mvnw test` via Podman socket) | **PASS** — `Tests run: 338, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`, Total time `01:11 min`). |
| **T1-BE-03** | `quan-ly-ktx-be` Package Build | `./mvnw -B clean package -DskipTests` | **PASS** — `BUILD SUCCESS` in `15.968 s`; produced `target/dormitory-management-0.0.1-SNAPSHOT.jar` (97M). |

---

## Tier 2: CI / Integration & Workflow Contract Results

| Check ID | Target | Verification | Observed Result |
|---|---|---|---|
| **T2-WF-01** | GitHub Actions Workflows | `yaml.safe_load` across BE & FE `ci.yml` and `cd-aws.yml` | **PASS** — All 4 workflow files parse cleanly; `permissions`, `concurrency`, `needs`, Node 22 + pnpm, and JDK 25 Temurin configured. |
| **T2-WF-02** | GHCR Lowercase Tagging | `quan-ly-ktx-be/.github/workflows/cd-aws.yml` | **PASS** — `IMAGE_NAME_LOWER=${IMAGE_NAME,,}` pushes `:${{ github.sha }}`, `:latest-aws`, and `:latest` to `ghcr.io`. |
| **T2-CMP-01** | GitOps Compose Schema | `podman compose -f .../compose/dorm/compose.prod.yml config --quiet` | **PASS** — `PODMAN COMPOSE CONFIG OK`. Aligned between `occupify-gitops` and `cd-aws.yml` (`mem_limit`: 192m/64m/384m). |
| **T2-NGX-01** | Nginx `/dorm` Routing | `quan-ly-ktx-fe/deploy/nginx-dorm.conf` & `occupify-infra/scripts/occupify-web.conf` | **PASS** — Exact `= /dorm` (`301 /dorm/`), `^~ /dorm/assets/` (1y immutable), `^~ /dorm/api/` (`http://10.0.10.143:8090/api/`), and `^~ /dorm/` SPA fallback verified. |

---

## Tier 3: Staging / Environment & Secret Parity Results

| Check ID | Target | Verification | Observed Result |
|---|---|---|---|
| **T3-SEC-01** | AWS Secret `occupify/prod/dorm-be` | `aws secretsmanager get-secret-value --secret-id occupify/prod/dorm-be --region ap-southeast-1` | **PASS** — Provisioned and verified (`SPRING_PROFILES_ACTIVE=prod`, `PORT=8080`, `POSTGRES_HOST=dorm-postgres`, `POSTGRES_DB=dorm_db`, `REDIS_HOST=dorm-redis`, `JWT_SECRET`, `CORS_ALLOWED_ORIGIN_PATTERNS`, `MAIL_*`, `S3_*`, `VIETQR_*`; sensitive values redacted). |
| **T3-SEC-02** | AWS Secret `occupify/prod/dorm-fe` | `aws secretsmanager get-secret-value --secret-id occupify/prod/dorm-fe --region ap-southeast-1` | **PASS** — Provisioned (`VITE_APP_NAME=KTX Hub`, `VITE_APP_ENV=production`, `VITE_BASE_PATH=/dorm/`, `VITE_API_URL=/dorm`). |
| **T3-POD-01** | Rootful Podman Contract | `cd-aws.yml` deploy job | **PASS** — Exports `DOCKER_HOST=unix:///run/podman/podman.sock` on `gateway` EC2 (no rootless runner setup). |

---

## Tier 4: Production Reality & Worst-Case Resilience Results

| Check ID | Target | Verification | Observed Result |
|---|---|---|---|
| **T4-PORT-01** | Port Isolation | Scan all `occupify-gitops/compose/*/compose.prod.yml` | **PASS** — `dorm/dorm-be` binds `8090:8080`; all 7 Occupify services bind `8080:*` on separate EC2s (`gateway/gateway` on `8080:8080`). Zero port collision. |
| **T4-SEC-02** | Secret & Key Hygiene | `cd-aws.yml` cleanup + `git check-ignore -v .env` | **PASS** — `shred -u ~/.ssh/bastion.pem` runs in `if: always()`; `.env` is matched by `.gitignore:11` and absent from `git status`. |
| **T4-FE-03** | Zero-Downtime Cutover | `quan-ly-ktx-fe/.github/workflows/cd-aws.yml` | **PASS** — Atomic `ln -sfn`, `cp -rn` asset carry-forward, max 5 releases retained, `sudo nginx -t && sudo systemctl reload nginx`. |
| **T4-BE-04** | Health Probe Convergence | `quan-ly-ktx-be/.github/workflows/cd-aws.yml` | **PASS** — Polls `127.0.0.1:8090/v3/api-docs` and `/` up to 30x (2s interval), emitting logs on timeout. |
