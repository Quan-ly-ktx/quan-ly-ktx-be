# Implementation Plan — KTX Hub (Dorm) CI/CD, `/dorm` Nginx Routing & AWS Secrets

## 1. Execution Sequence

### Task 1: Foundation — AWS Secrets Manager & GitOps Compose
- **Secrets Manager (`ap-southeast-1`):**
  - Create `occupify/prod/dorm-be` and `occupify/prod/dorm-fe` secrets with production dotenv payloads matching `SPEC.md`.
  - Register `"dorm-be"` and `"dorm-fe"` in `occupify-infra/modules/secrets/variables.tf` so Terraform stays aligned with live AWS Secrets Manager state.
- **GitOps & Nginx (`occupify-gitops` & `occupify-infra`):**
  - Create `occupify-gitops/compose/dorm/compose.prod.yml` (PostgreSQL 17, Redis 8, `ghcr.io/quan-ly-ktx/quan-ly-ktx-be:latest` on `8090:8080`, `env_file: ../../env/dorm-be.env`, healthchecks, memory caps).
  - Add `dorm` and `stop-dorm` targets to `occupify-gitops/Makefile`.
  - Add `/dorm` redirect, static SPA, and `/dorm/api/` proxy blocks to `occupify-infra/scripts/occupify-web.conf`.

### Task 2: Backend CI/CD & Readiness (`quan-ly-ktx-be`)
- Run `./mvnw -B spotless:apply` so the repository complies with `spotless:check` in CI.
- Verify unit/integration tests (`./mvnw -B test`) and fix/document any environment-dependent Testcontainers behavior cleanly.
- Create `.github/workflows/ci.yml` (JDK 25 Temurin, Maven cache, `spotless:check`, `mvnw test`, `dorny/test-reporter@v1`).
- Create `.github/workflows/cd-aws.yml` (calls `ci.yml`, builds & pushes `ghcr.io/quan-ly-ktx/quan-ly-ktx-be` to GHCR, SSHes via `BASTION_HOST` -> `gateway`, pulls `occupify/prod/dorm-be` from AWS Secrets Manager, runs rootful Podman via `DOCKER_HOST=unix:///run/podman/podman.sock`, polls `127.0.0.1:8090`).

### Task 3: Frontend `/dorm` Subpath & CI/CD (`quan-ly-ktx-fe`)
- Update `vite.config.ts` (`base: process.env.VITE_BASE_PATH || '/dorm/'`) and `src/App.tsx` (`<BrowserRouter basename={import.meta.env.BASE_URL}>`).
- Add `deploy/nginx-dorm.conf` with `/dorm` redirect (`301 /dorm/`), `/dorm/assets/`, `/dorm/api/` -> `http://10.0.10.143:8090/api/`, and `/dorm/` SPA fallback.
- Add unit tests verifying `/dorm/` basename routing and `API_CONFIG` resolution.
- Create `.github/workflows/ci.yml` (`pnpm install --frozen-lockfile`, `pnpm run lint`, `pnpm run test`, `pnpm run build`).
- Create `.github/workflows/cd-aws.yml` (calls `ci.yml`, pulls `occupify/prod/dorm-fe` from Secrets Manager via Bastion, builds `dist/`, deploys atomically to `/var/www/quan-ly-ktx-fe/releases/$TIMESTAMP` on `web`, updates Nginx `/dorm` snippet & reloads Nginx).

---

## 2. Disjoint File-Ownership Partitioning

| Task | Owner / Subagent | Exclusive Owned Files / Resources | Dependencies |
|---|---|---|---|
| **Task 1** | Infrastructure / Secrets Lead | AWS Secrets Manager (`occupify/prod/dorm-be`, `occupify/prod/dorm-fe`), `occupify-infra/modules/secrets/variables.tf`, `occupify-infra/scripts/occupify-web.conf`, `occupify-gitops/compose/dorm/compose.prod.yml`, `occupify-gitops/Makefile` | None |
| **Task 2** | Backend CI/CD Implementer (`self`) | `quan-ly-ktx-be/.github/workflows/ci.yml`, `quan-ly-ktx-be/.github/workflows/cd-aws.yml`, `quan-ly-ktx-be/src/**` (Spotless formatting & test readiness) | Task 1 (secret IDs & compose path) |
| **Task 3** | Frontend CI/CD Implementer (`self`) | `quan-ly-ktx-fe/.github/workflows/ci.yml`, `quan-ly-ktx-fe/.github/workflows/cd-aws.yml`, `quan-ly-ktx-fe/vite.config.ts`, `quan-ly-ktx-fe/src/App.tsx`, `quan-ly-ktx-fe/src/**/*.test.*`, `quan-ly-ktx-fe/deploy/nginx-dorm.conf` | Task 1 (secret IDs & Nginx contract) |

*Tasks 2 and 3 operate on completely separate repositories and disjoint file trees, so they can execute in parallel after Task 1.*

---

## 3. Subagent & Skill Assignments

- **Stage 3 (Implementation):** `self` subagents adopting `ci-cd-and-automation`, `security-and-hardening`, and `test-driven-development`.
- **Stage 4 (Independent Verification):** `test-engineer` subagent adopting `test-driven-development`, `constraint-driven-development`, and `verification-before-completion`.
- **Stage 5 (Persona / End-to-End Dry Run):** `self` subagent verifying simulated Nginx/Subpath routing, Vite build bundle URLs, Podman compose validation, and AWS Secrets Manager retrieval.
- **Stage 6 (Pre-Release Review):** `code-reviewer` + `security-auditor` patterns via `code-review-and-quality`, `security-and-hardening`, and `shipping-and-launch`.
