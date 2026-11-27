# Test Plan (Dev-to-Prod Failure Taxonomy Matrix) — KTX Hub CI/CD

## Tier 1: Dev / Unit & Static Quality Verification

| ID | Target | Check / Assertion | Pass Criteria |
|---|---|---|---|
| **T1-FE-01** | `quan-ly-ktx-fe` Lint | `pnpm run lint` (`oxlint`) | 0 errors, 0 warnings across all source files. |
| **T1-FE-02** | `quan-ly-ktx-fe` Unit Tests | `pnpm run test` (`vitest run`) | All existing tests + new `/dorm/` router basename & API config tests pass (100% green, no skipped tests). |
| **T1-FE-03** | `quan-ly-ktx-fe` Production Build | `VITE_BASE_PATH=/dorm/ VITE_API_URL=/dorm pnpm run build` | `tsc -b` + `vite build` succeeds; `dist/index.html` references `/dorm/assets/...`. |
| **T1-BE-01** | `quan-ly-ktx-be` Formatting | `./mvnw -B spotless:check` | 0 formatting violations across all Java files. |
| **T1-BE-02** | `quan-ly-ktx-be` Unit/Slice Tests | `./mvnw -B test -DfailIfNoTests=false` | Unit/slice tests pass cleanly; JUnit XML reports generated under `target/surefire-reports/`. |
| **T1-BE-03** | `quan-ly-ktx-be` Package Build | `./mvnw -B clean package -DskipTests` | Spring Boot executable layered JAR builds cleanly. |

## Tier 2: CI / Integration & Workflow Contract Verification

| ID | Target | Check / Assertion | Pass Criteria |
|---|---|---|---|
| **T2-WF-01** | GitHub Actions YAML | Parse `ci.yml` and `cd-aws.yml` in both FE and BE repos | Valid YAML syntax, explicit `permissions`, `concurrency` groups, `needs` dependency graph, and Node 22 / JDK 25 setups. |
| **T2-WF-02** | GHCR Image Naming | Inspect `cd-aws.yml` BE tags | Lowercase `ghcr.io/${owner}/quan-ly-ktx-be` with `:sha`, `:latest-aws`, and `:latest`. |
| **T2-CMP-01** | GitOps Compose Schema | Validate `occupify-gitops/compose/dorm/compose.prod.yml` | Valid compose YAML; `dorm-be` uses `8090:8080`, `env_file: ../../env/dorm-be.env`, `depends_on` healthy `dorm-postgres` and `dorm-redis`. |
| **T2-NGX-01** | Nginx `/dorm` Routing | Inspect `deploy/nginx-dorm.conf` & `occupify-web.conf` | Exact match `= /dorm` returns `301 /dorm/`; `/dorm/assets/` has 1y cache; `/dorm/api/` proxies to `http://10.0.10.143:8090/api/`; `/dorm/` falls back to `/dorm/index.html`. |

## Tier 3: Staging / Environment & Secret Parity Verification

| ID | Target | Check / Assertion | Pass Criteria |
|---|---|---|---|
| **T3-SEC-01** | AWS Secrets (`dorm-be`) | `aws secretsmanager get-secret-value --secret-id occupify/prod/dorm-be --region ap-southeast-1` | Secret exists and includes all fail-fast variables required by `application-prod.yml` (`SPRING_PROFILES_ACTIVE`, `PORT`, `POSTGRES_*`, `REDIS_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGIN_PATTERNS`). |
| **T3-SEC-02** | AWS Secrets (`dorm-fe`) | `aws secretsmanager get-secret-value --secret-id occupify/prod/dorm-fe --region ap-southeast-1` | Secret exists and includes `VITE_APP_NAME`, `VITE_APP_ENV`, `VITE_BASE_PATH=/dorm/`, `VITE_API_URL=/dorm`. |
| **T3-POD-01** | Rootful Podman Contract | Inspect `cd-aws.yml` deploy step | Sets `export DOCKER_HOST=unix:///run/podman/podman.sock` and never uses rootless user sockets. |

## Tier 4: Production Reality & Worst-Case Resilience Verification

| ID | Target | Check / Assertion | Pass Criteria |
|---|---|---|---|
| **T4-PORT-01** | Port Isolation | Check host port mapping across `occupify-gitops` | `occupify-gateway` owns `8080`; `dorm-be` owns `8090` — zero port conflict. |
| **T4-SEC-02** | Runner Secret Cleanup | Inspect `cd-aws.yml` SSH steps | `~/.ssh/bastion.pem` is created with `chmod 600` (CRLF stripped) and shredded via `shred -u` in an `always()` cleanup block or trap. |
| **T4-FE-03** | Zero-Downtime & Disk Cap | Inspect FE atomic release script | Previous `assets/*` carried forward (`cp -rn`), atomic `ln -sfn`, max 5 releases kept, `sudo nginx -t` validated before `systemctl reload nginx`. |
| **T4-BE-04** | Health Probe Resilience | Inspect BE deploy health loop | Polls `http://127.0.0.1:8090` up to 30x (2s interval) and prints status cleanly without hanging indefinitely. |
