# Production Readiness Signoff — KTX Hub (Dorm) CI/CD & `/dorm` Deployment

**Status:** **READY FOR DEPLOYMENT** (pending GitHub Actions secrets setup & push to `main`).

## 1. Security & Rollout Audit
- **Secret Isolation:** `.env` in `quan-ly-ktx-be` is ignored by `.gitignore` and never committed; production runtime secrets live in AWS Secrets Manager (`occupify/prod/dorm-be` and `occupify/prod/dorm-fe` in `ap-southeast-1`) and are fetched over Bastion SSH with `chmod 600`.
- **Runner Ephemeral Key Hygiene:** Both `cd-aws.yml` workflows normalize `BASTION_SSH_KEY` and shred `~/.ssh/bastion.pem` inside `if: always()` cleanup steps.
- **Port & Service Isolation:** `dorm-be` binds `8090:8080` on `gateway` EC2 (`10.0.10.143`) with rootful Podman (`DOCKER_HOST=unix:///run/podman/podman.sock`), avoiding any conflict with `occupify-gateway` on `8080`.
- **Memory Guardrails:** `compose.prod.yml` caps `dorm-be` (`384m`), `dorm-postgres` (`192m`), and `dorm-redis` (`64m`).
- **Frontend Zero-Downtime & Anti-ChunkLoadError:** Atomic symlink rotation (`/var/www/quan-ly-ktx-fe/current`) carries forward prior hashed chunks (`cp -rn`) and retains at most 5 releases.

## 2. One-Time GitHub Repository Setup Checklist
Before merging/pushing to `main` in **both** GitHub repositories (`Quan-ly-ktx/quan-ly-ktx-be` and `Quan-ly-ktx/quan-ly-ktx-fe`):
1. Add GitHub Actions Secrets (`Settings -> Secrets and variables -> Actions`):
   - `BASTION_HOST` (Bastion EC2 public IP)
   - `BASTION_SSH_KEY` (Bastion external private SSH key)
   *(Tip: run `occupify-infra/scripts/export-bastion-secrets.sh` to print both values.)*
2. Ensure GHCR package `ghcr.io/quan-ly-ktx/quan-ly-ktx-be` visibility is set appropriately (or `public` / readable by `gateway` EC2) after the first `cd-aws.yml` push.
3. Ensure `quan-ly-ktx-fe` pushes to `main` (currently local branch is `KTX-44-45`).

## 3. Rollback Strategy
- **Frontend (`web` EC2):** Re-point `/var/www/quan-ly-ktx-fe/current` to any of the previous 4 timestamps under `/var/www/quan-ly-ktx-fe/releases/` and run `sudo systemctl reload nginx`.
- **Backend (`gateway` EC2):** Update image tag in `~/gitops/compose/dorm/compose.prod.yml` to a prior `:${GITHUB_SHA}` tag and re-run `podman compose -f ~/gitops/compose/dorm/compose.prod.yml up -d`.
