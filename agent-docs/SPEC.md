# Specification — KTX Hub (Dorm) CI/CD, Subpath Routing & AWS Secrets Architecture

## 1. Workload Profile Scaling

- **Primary Profiles:**
  - `Backend/API` (`quan-ly-ktx-be` — Java 25, Spring Boot 4.1.2, Maven, PostgreSQL 17, Redis 8)
  - `Frontend/UI` (`quan-ly-ktx-fe` — React 19, TypeScript, Vite 8, Tailwind CSS v4, pnpm, Oxlint, Vitest)
  - `CI/CD & Infrastructure Automation` (GitHub Actions, GHCR, AWS Bastion SSH, rootful Podman Compose, Nginx reverse proxy, AWS Secrets Manager)
- **Applicable Stage 4–6 Verification Scope:**
  - Workflow YAML syntax, permissions, concurrency, and job dependency validation (`ci.yml`, `cd-aws.yml` in both repos).
  - Frontend `/dorm` subpath build (`dist/index.html` asset URLs), `BrowserRouter` `basename`, API base URL (`/dorm`), and unit/lint/build checks (`pnpm lint`, `pnpm test`, `pnpm build`).
  - Backend Spotless formatting (`./mvnw -B spotless:check`), unit/integration tests (`./mvnw -B test`), layered JAR packaging (`docker/Dockerfile`), and GHCR lowercase image tagging (`:sha`, `:latest-aws`, `:latest`).
  - AWS Secrets Manager secret parity (`occupify/prod/dorm-be` and `occupify/prod/dorm-fe` in `ap-southeast-1`).
  - Nginx `/dorm` subpath routing contract on `web` EC2 (`10.0.1.78`) and rootful Podman Compose isolation on `gateway` EC2 (`10.0.10.143:8090`).
  - *Skipped (out of scope):* Provisioning new EC2 instances or VPCs via Terraform, self-hosted GitHub Actions runners, and rootless Podman user systemd services.

---

## 2. Frozen Architecture & Contracts

### 2.1 Deployment Topology (Shared with Occupify AWS Cluster)

```text
Internet
  │
  ▼ HTTPS (443) / HTTP (80)
[Web EC2 (Nginx) — 10.0.1.78]
  ├── /                  ──► Occupify FE (/var/www/occupify-web/current)
  ├── /api/              ──► http://10.0.10.143:8080/        (occupify-gateway)
  ├── /dorm   (exact)    ──► 301 /dorm/
  ├── /dorm/assets/      ──► /var/www/quan-ly-ktx-fe/current/assets/ (1y immutable)
  ├── /dorm/api/         ──► http://10.0.10.143:8090/api/    (dorm-be)
  └── /dorm/             ──► /var/www/quan-ly-ktx-fe/current/index.html (SPA fallback)

[Bastion EC2 — ubuntu@$BASTION_HOST]
  ├── SSH Jump ──► web EC2 (FE atomic tarball release + Nginx reload)
  └── SSH Jump ──► gateway EC2 (10.0.10.143 — rootful Podman via /run/podman/podman.sock)
                     ├── occupify-gateway (port 8080:8080)
                     └── dorm stack (compose/dorm/compose.prod.yml):
                           ├── dorm-be       (ghcr.io/quan-ly-ktx/quan-ly-ktx-be:latest, port 8090:8080)
                           ├── dorm-postgres (postgres:17-alpine, internal network)
                           └── dorm-redis    (redis:8-alpine, internal network)
```

---

### 2.2 Frontend Contract (`quan-ly-ktx-fe`)

#### Workflows
1. **`.github/workflows/ci.yml`** (`CI Quality Gate`):
   - **Triggers:** `push` (`main`), `pull_request` (`main`), `workflow_dispatch`.
   - **Concurrency:** `${{ github.workflow }}-${{ github.ref }}` (`cancel-in-progress: true`).
   - **Environment:** `ubuntu-latest`, `pnpm/action-setup@v4`, Node `22` with `cache: 'pnpm'`.
   - **Gates (sequential):**
     1. `pnpm install --frozen-lockfile`
     2. `pnpm run lint` (`oxlint`)
     3. `pnpm run test` (`vitest run`)
     4. `pnpm run build` (`tsc -b && vite build`)

2. **`.github/workflows/cd-aws.yml`** (`Dorm Web CD flow (AWS)`):
   - **Triggers:** `push` (`main`), `workflow_dispatch`.
   - **Concurrency:** `fe-deploy-dorm-web-aws` (`cancel-in-progress: false`).
   - **Job `ci`:** Calls `./.github/workflows/ci.yml`.
   - **Job `build` (`needs: ci`):**
     - Fetches `occupify/prod/dorm-fe` from AWS Secrets Manager (`ap-southeast-1`) via Bastion SSH jump to `web` (or `bastion` with AWS CLI) into `.env.production` — falling back to safe non-secret defaults (`VITE_APP_NAME=KTX Hub`, `VITE_APP_ENV=production`, `VITE_BASE_PATH=/dorm/`, `VITE_API_URL=/dorm`) if needed.
     - Runs `pnpm run build` and uploads `dist/` as `dorm-dist-bundle`.
   - **Job `deploy` (`needs: build`):**
     - Downloads `dorm-dist-bundle` to `./temp-dist` and packages `/tmp/dorm-dist.tar.gz`.
     - Copies bundle and Nginx `/dorm` snippet (`deploy/nginx-dorm.conf`) via `scp` using `BASTION_SSH_KEY` (`~/.ssh/bastion.pem`, normalized with `tr -d '\r'`, `chmod 600`) to `ubuntu@$BASTION_HOST`, then jumps to `web`.
     - Atomic deploy on `web`:
       - Extracts into `/var/www/quan-ly-ktx-fe/releases/$TIMESTAMP`.
       - Carries forward prior hashed chunks (`cp -rn "$CURRENT_LINK/assets"/* "$RELEASE_DIR/assets/" 2>/dev/null || true`) to prevent `ChunkLoadError`.
       - Atomically switches `/var/www/quan-ly-ktx-fe/current` symlink (`ln -sfn`).
       - Prunes old releases, keeping the latest 5.
       - Ensures `/dorm` location block is included in `/etc/nginx/sites-available/occupify-web.conf` (idempotently via `/etc/nginx/snippets/dorm.conf` + `include` directive or site conf update), validates `sudo nginx -t`, and runs `sudo systemctl reload nginx`.
     - Cleanup (`if: always()`): shreds `~/.ssh/bastion.pem` and removes `/tmp/dorm-dist.tar.gz`.

#### Subpath Configuration (`quan-ly-ktx-fe`)
- `vite.config.ts`:
  - `base: process.env.VITE_BASE_PATH || '/dorm/'`
- `src/App.tsx`:
  - `<BrowserRouter basename={import.meta.env.BASE_URL}>` (ensures React Router handles `/dorm/login`, `/dorm/admin`, `/dorm/user`, `/dorm/technician` transparently while `ROUTES` constants remain `/login`, `/admin`, etc.).
- `src/config/api.config.ts`:
  - `BASE_URL: import.meta.env.VITE_API_URL || ''` (with `VITE_API_URL=/dorm` in production, `apiClient` requests to `/api/v1/...` resolve to `/dorm/api/v1/...`).

---

### 2.3 Nginx `/dorm` Subpath & API Proxy Contract on `web` EC2 (`10.0.1.78`)

Stored in `quan-ly-ktx-fe/deploy/nginx-dorm.conf` (and mirrored in `occupify-infra/scripts/occupify-web.conf` for IaC parity):

```nginx
# Redirect /dorm -> /dorm/
location = /dorm {
    return 301 /dorm/;
}

# 1-year immutable caching for hashed Dorm static assets
location ^~ /dorm/assets/ {
    alias /var/www/quan-ly-ktx-fe/current/assets/;
    try_files $uri =404;
    expires 1y;
    add_header Cache-Control "public, max-age=31536000, immutable" always;
    access_log off;
}

# Reverse proxy Dorm API requests to Dorm Backend on Gateway EC2 (:8090)
location ^~ /dorm/api/ {
    proxy_pass http://10.0.10.143:8090/api/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    proxy_buffering off;
    proxy_cache off;
    proxy_connect_timeout 10s;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
}

# Dorm SPA routing fallback
location ^~ /dorm/ {
    alias /var/www/quan-ly-ktx-fe/current/;
    index index.html;
    try_files $uri $uri/ /dorm/index.html;
    add_header Cache-Control "no-cache, no-store, must-revalidate" always;
}
```

---

### 2.4 Backend Contract (`quan-ly-ktx-be`)

#### Workflows
1. **`.github/workflows/ci.yml`** (`Dorm Backend CI flow`):
   - **Triggers:** `workflow_call`, `push` (`main`), `pull_request` (`main`), `workflow_dispatch`.
   - **Concurrency:** `${{ github.workflow }}-${{ github.ref }}` (`cancel-in-progress: true`).
   - **Permissions:** `contents: read`, `checks: write`, `pull-requests: write`.
   - **Environment:** `ubuntu-latest`, `actions/setup-java@v4` (`java-version: "25"`, `distribution: "temurin"`, `cache: maven`).
   - **Steps:**
     1. Normalize `mvnw` line endings (`sed -i 's/\r$//' mvnw && chmod +x mvnw`).
     2. Verify code formatting: `./mvnw -B spotless:check`.
     3. Build & run unit/integration tests: `./mvnw -B test -DfailIfNoTests=false`.
     4. Publish JUnit results via `dorny/test-reporter@v1` (`target/surefire-reports/*.xml`).

2. **`.github/workflows/cd-aws.yml`** (`Dorm Backend CD flow (AWS)`):
   - **Triggers:** `push` (`main`), `workflow_dispatch`.
   - **Concurrency:** `be-deploy-dorm-gateway-aws` (`cancel-in-progress: true`).
   - **Env:**
     - `REGISTRY: ghcr.io`
     - `IMAGE_NAME: ${{ github.repository_owner }}/quan-ly-ktx-be`
     - `AWS_REGION: ap-southeast-1`
   - **Job `ci`:** Calls `./.github/workflows/ci.yml`.
   - **Job `build-and-push-ghcr` (`needs: ci`):**
     - Permissions: `contents: read`, `packages: write`.
     - Logs in to `ghcr.io` with `${{ github.actor }}` / `${{ secrets.GITHUB_TOKEN }}`.
     - Lowercases `IMAGE_NAME` (`IMAGE_NAME_LOWER=${IMAGE_NAME,,}`).
     - Builds and pushes `docker/Dockerfile` (`platforms: linux/amd64`) to:
       - `ghcr.io/quan-ly-ktx/quan-ly-ktx-be:${{ github.sha }}`
       - `ghcr.io/quan-ly-ktx/quan-ly-ktx-be:latest-aws`
       - `ghcr.io/quan-ly-ktx/quan-ly-ktx-be:latest`
   - **Job `deploy` (`needs: build-and-push-ghcr`):**
     - SSHes to `ubuntu@$BASTION_HOST`, jumps to `gateway` (`10.0.10.143`):
       1. Fetches `occupify/prod/dorm-be` from AWS Secrets Manager (`ap-southeast-1`) into `~/gitops/env/dorm-be.env` (`chmod 600`).
       2. Logs Podman into GHCR if `GHCR_TOKEN` / `GITHUB_TOKEN` is needed or pulls public/authenticated image.
       3. Exports `DOCKER_HOST=unix:///run/podman/podman.sock` (rootful Podman, no rootless).
       4. Pulls latest image and runs `cd ~/gitops && make dorm` (`podman compose -f compose/dorm/compose.prod.yml up -d`).
       5. Polls `http://127.0.0.1:8090/actuator/health` or `http://127.0.0.1:8090/v3/api-docs` up to 30 times (60s) to confirm container readiness.
     - Cleanup (`if: always()`): `shred -u ~/.ssh/bastion.pem`.

---

### 2.5 AWS Secrets Manager & GitOps Compose Contract

#### AWS Secrets Manager (`ap-southeast-1`)
1. **`occupify/prod/dorm-be`** (stored as dotenv key-value payload matching `occupify/prod/*` convention):
   - `SPRING_PROFILES_ACTIVE=prod`
   - `PORT=8080`
   - `POSTGRES_HOST=dorm-postgres`
   - `POSTGRES_PORT=5432`
   - `POSTGRES_DB=dormitory_db`
   - `POSTGRES_USER=dorm_user`
   - `POSTGRES_PASSWORD=<generated-strong-secret>`
   - `REDIS_HOST=dorm-redis`
   - `REDIS_PORT=6379`
   - `REDIS_PASSWORD=<generated-strong-secret>`
   - `JWT_SECRET=<generated-256-bit-hex-secret>`
   - `JWT_ACCESS_EXPIRATION=86400000`
   - `JWT_REFRESH_EXPIRATION=604800000`
   - `CORS_ALLOWED_ORIGIN_PATTERNS=https://occupify.tienvm.dpdns.org,http://localhost:*,http://127.0.0.1:*`
   - `MAIL_HOST=localhost`
   - `MAIL_PORT=1025`
   - `EMAIL_USERNAME=dev`
   - `EMAIL_PASSWORD=dev`
2. **`occupify/prod/dorm-fe`** (stored as dotenv key-value payload):
   - `VITE_APP_NAME=KTX Hub`
   - `VITE_APP_ENV=production`
   - `VITE_BASE_PATH=/dorm/`
   - `VITE_API_URL=/dorm`

#### GitOps Production Compose (`occupify-gitops/compose/dorm/compose.prod.yml`)
- Services:
  - `dorm-postgres` (`docker.io/library/postgres:17-alpine`, volume `dorm_postgres_data`, `env_file: ../../env/dorm-be.env`, memory cap `192m`, healthcheck `pg_isready`)
  - `dorm-redis` (`docker.io/library/redis:8-alpine`, volume `dorm_redis_data`, `env_file: ../../env/dorm-be.env`, `--requirepass ${REDIS_PASSWORD}`, memory cap `64m`, healthcheck `redis-cli ping`)
  - `dorm-be` (`ghcr.io/quan-ly-ktx/quan-ly-ktx-be:latest`, `env_file: ../../env/dorm-be.env`, `depends_on` healthy postgres & redis, ports `8090:8080`, memory cap `384m`, `restart: on-failure:3`)

---

## 3. Triad Analysis (Edge Cases, Eval Cases, Worst Cases)

### 3.1 Edge Cases
| ID | Area | Scenario | Expected Resilience Behavior |
|---|---|---|---|
| **EDGE-01** | Nginx `/dorm` Redirect | User navigates to `/dorm` without trailing slash | `location = /dorm { return 301 /dorm/; }` redirects cleanly before SPA fallback. |
| **EDGE-02** | SSH Key & `mvnw` CRLF | `BASTION_SSH_KEY` secret or `mvnw` contains `\r\n` endings | Workflows normalize line endings (`tr -d '\r'` / `sed -i 's/\r$//' mvnw`) and enforce `chmod 600` / `chmod +x`. |
| **EDGE-03** | Hashed Asset Chunk Skew | Active user loads page just before FE deploy swaps `current` | `cp -rn "$CURRENT_LINK/assets"/* "$RELEASE_DIR/assets/"` preserves old hashed JS/CSS bundles across releases. |
| **EDGE-04** | Uppercase GHCR Owner | GitHub org `Quan-ly-ktx` has uppercase `Q` | GHCR requires lowercase repository names; `IMAGE_NAME_LOWER=${IMAGE_NAME,,}` enforces lowercase. |
| **EDGE-05** | Testcontainers Socket | Local/CI environments with Podman or absent Docker socket | Ensure CI runs on `ubuntu-latest` (Docker available) and `RedisTestContainerSupport` handles container configuration cleanly. |
| **EDGE-06** | Spotless Drift | Existing BE codebase has 91 files with Spotless formatting drift | Run `./mvnw spotless:apply` before enabling `spotless:check` in CI so the gate starts green. |

### 3.2 Eval Cases (SLA, Performance & UX)
| ID | Dimension | Metric / Target | Verification Method |
|---|---|---|---|
| **EVAL-01** | CI Feedback Speed | FE CI < 3 min; BE CI < 5 min with dependency caching | Maven cache (`actions/setup-java`) and pnpm store cache (`actions/setup-node`). |
| **EVAL-02** | Zero-Downtime FE Cutover | Sub-second atomic switch on `web` EC2 | Extract to timestamped release dir first, then single `ln -sfn` + `nginx -t && systemctl reload nginx`. |
| **EVAL-03** | BE Health Convergence | Container responds on `127.0.0.1:8090` within 60s | Deploy script polls 30x every 2s before reporting success. |
| **EVAL-04** | Subpath Routing Fidelity | All FE static assets, SPA deep links (`/dorm/login`, `/dorm/admin`), and `/dorm/api/*` resolve with 200/valid JSON | Vitest router basename test + `dist/index.html` asset prefix check + Nginx config validation. |

### 3.3 Worst Cases & Blast Radius Containment
| ID | Threat Vector | Worst-Case Impact | Safeguard & Mitigation |
|---|---|---|---|
| **WORST-01** | Port 8080 Collision | Dorm BE crashes on startup or hijacks `occupify-gateway` traffic on `gateway` EC2 | Bind Dorm BE host port strictly to `8090:8080` (`compose.prod.yml` + Nginx `proxy_pass http://10.0.10.143:8090/api/`). |
| **WORST-02** | SSH Key / Secret Leakage | Runner failure leaves `~/.ssh/bastion.pem` or `.env` exposed in logs/disk | `shred -u ~/.ssh/bastion.pem` runs in cleanup; Secrets Manager payloads written with `chmod 600` and never echoed to stdout. |
| **WORST-03** | `gateway` EC2 OOM | Running `occupify-gateway` + `dorm-be` + Postgres + Redis exhausts RAM | Container memory limits (`384m` BE, `192m` Postgres, `64m` Redis) and `restart: on-failure:3`. |
| **WORST-04** | Disk Full from FE Releases | Repeated deploys fill `/var/www/quan-ly-ktx-fe/releases` | Release pruning retains only the latest 5 releases on every deploy. |
| **WORST-05** | Missing Prod `JWT_SECRET` | Backend boots in prod with insecure dev secret | `application-prod.yml` uses fail-fast `${JWT_SECRET}` with no fallback; deploy pulls `occupify/prod/dorm-be` before `compose up`. |
