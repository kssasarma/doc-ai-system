# Deployment

---

## Docker Compose — Development

The default `docker-compose.yml` starts all services including MinIO (S3-compatible local storage) and Redis.

```bash
cp .env.example .env
# Fill in required values (see Configuration)
docker compose up -d --build
```

Services:

| Container | Port | Notes |
|---|---|---|
| `docai-postgres` | 5432 | pgvector/pgvector:pg16 |
| `docai-redis` | 6379 | redis:7-alpine |
| `docai-minio` | 9000 / 9001 | MinIO API / console |
| `docai-ingestor` | 8081 | document-ingestor |
| `docai-bot` | 8082 | documentation-bot |
| `docai-frontend` | 3000 | React SPA |

---

## Docker Compose — Production

`docker-compose.prod.yml` is the production variant. Key differences from dev:

- No MinIO — requires real AWS S3 (`S3_ENDPOINT` must be empty)
- No Redis (single-instance; embedding cache is in-memory)
- Both Java services run with `SPRING_PROFILES_ACTIVE=prod`
- Startup fails fast if any of `JWT_SECRET`, `SEED_ADMIN_PASSWORD`, or `SECRETS_ENCRYPTION_KEY` is unset

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

### Building images

```bash
docker build -t documentation-bot:latest ./documentation-bot
docker build -t document-ingestor:latest ./document-ingestor
cd frontend && npm run build  # outputs to frontend/dist/
docker build -t docai-frontend:latest ./frontend
```

### Required production environment variables

```dotenv
# Both services
OPENAI_API_KEY=sk-...
JWT_SECRET=<base64-64-byte-random>
SECRETS_ENCRYPTION_KEY=<base64-32-byte-random>
INTERNAL_SERVICE_SECRET=<strong-random-string>
SPRING_DATABASE_URL=jdbc:postgresql://<host>:5432/docai
SPRING_DATABASE_USERNAME=<db-user>
SPRING_DATABASE_PASSWORD=<db-password>

# document-ingestor
S3_BUCKET=your-bucket
S3_REGION=us-east-1
S3_ACCESS_KEY=<key>
S3_SECRET_KEY=<secret>
S3_ENDPOINT=                  # empty = real AWS S3
S3_PATH_STYLE_ACCESS=false    # false for real AWS S3

# documentation-bot
SEED_ADMIN_PASSWORD=<strong-initial-password>
APP_URL=https://app.your-domain.com
CORS_ALLOWED_ORIGINS=https://app.your-domain.com
MAIL_HOST=smtp.your-domain.com
MAIL_USERNAME=<smtp-user>
MAIL_PASSWORD=<smtp-password>

# Frontend (baked in at build time)
VITE_BACKEND_URL=https://api.your-domain.com
VITE_INGESTOR_URL=https://ingest.your-domain.com
```

---

## Kubernetes — Raw Manifests

The `k8s/` directory contains raw Kubernetes manifests. Apply them in order:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml            # see Secrets Management below
kubectl apply -f k8s/postgres-statefulset.yaml
kubectl apply -f k8s/postgres-backup-cronjob.yaml
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/documentation-bot-deployment.yaml
kubectl apply -f k8s/document-ingestor-deployment.yaml
kubectl apply -f k8s/ingress.yaml
```

### Autoscaling

| Service | Min pods | Max pods | Scale metric |
|---|---|---|---|
| documentation-bot | 2 | 10 | 70% CPU |
| document-ingestor | 2 | 6 | 70% CPU |

### Resource limits

| Service | CPU request/limit | Memory request/limit |
|---|---|---|
| documentation-bot | 500m / 2000m | 768Mi / 2Gi |
| document-ingestor | 500m / 2000m | 1Gi / 3Gi |

### PodDisruptionBudget

Both services have `minAvailable: 1` — rolling updates and node drains never take the service below one running pod.

### Network Policy

Default-deny with explicit allows matching actual traffic:

- Ingress → bot (8082) and ingestor (8081)
- Bot → ingestor (internal API, port 8081)
- Bot → PostgreSQL (5432) and Redis (6379)
- Ingestor → PostgreSQL (5432) and S3 (443)
- No pod can initiate outbound connections outside the allowed set

---

## Kubernetes — Helm Chart

The Helm chart in `helm/doc-ai-system/` is the recommended way to deploy to a production cluster.

### Install

```bash
helm install doc-ai ./helm/doc-ai-system \
  --namespace doc-ai --create-namespace \
  --set global.imageRegistry=your-registry.io \
  --set secrets.openaiApiKey="sk-..." \
  --set secrets.jwtSecret="$(openssl rand -base64 64)" \
  --set secrets.secretsEncryptionKey="$(openssl rand -base64 32)" \
  --set secrets.dbPassword="$(openssl rand -base64 32)" \
  --set secrets.internalServiceSecret="$(openssl rand -hex 32)" \
  --set ingress.host=api.your-domain.com \
  --set config.corsAllowedOrigins="https://app.your-domain.com" \
  --set config.appUrl="https://app.your-domain.com"
```

### Upgrade

```bash
helm upgrade doc-ai ./helm/doc-ai-system \
  --reuse-values \
  --set bot.image.tag=v1.2.0
```

### Key `values.yaml` settings

| Key | Default | Description |
|---|---|---|
| `bot.replicaCount` | `2` | documentation-bot replica count |
| `bot.hpa.maxReplicas` | `10` | documentation-bot HPA ceiling |
| `ingestor.replicaCount` | `2` | document-ingestor replica count |
| `ingestor.hpa.maxReplicas` | `6` | document-ingestor HPA ceiling |
| `postgres.enabled` | `true` | Deploy PostgreSQL in-cluster; set `false` to use an external managed DB |
| `postgres.storageSize` | `50Gi` | PVC size for PostgreSQL data |
| `redis.enabled` | `true` | Deploy Redis in-cluster; set `false` to use external Redis |
| `ingress.host` | — | Public hostname; TLS auto-provisioned via cert-manager |
| `ingress.tlsEnabled` | `true` | Enable TLS on the ingress |

---

## Database Backups

A Kubernetes `CronJob` (`k8s/postgres-backup-cronjob.yaml`, or `postgres.backup.*` in the Helm chart) runs nightly at 03:00 UTC:

1. Connects to PostgreSQL inside the cluster
2. Runs `pg_dump -Fc docai`
3. Uploads the dump to S3 at `backups/docai-<timestamp>.dump`
4. Prunes backups older than 14 days (configurable via `postgres.backup.retentionDays` in Helm)

This CronJob only applies when PostgreSQL is deployed by this chart. If you point `SPRING_DATABASE_URL` at an external managed database (RDS, Cloud SQL, etc.), backups are that provider's responsibility.

### Restore procedure

```bash
# 1. Download the dump from S3/MinIO
aws s3 cp s3://<bucket>/backups/docai-<timestamp>.dump ./docai-restore.dump
#   (For MinIO: add --endpoint-url http://<minio-host>:9000)

# 2. Restore into a new database — never overwrite a live one directly
createdb -h <host> -U <user> docai_restore_test
pg_restore -h <host> -U <user> -d docai_restore_test \
  --no-owner --clean docai-restore.dump

# 3. Validate: check row counts, spot-check known tenants
psql -h <host> -U <user> -d docai_restore_test \
  -c "SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM document_chunks;"

# 4. For a real recovery: rename the broken DB aside, rename docai_restore_test to docai,
#    then restart both services.
```

---

## Secrets Management

`k8s/secrets.yaml` is a plain Kubernetes `Secret` for **local / dev clusters only** (kind, minikube). Applying it as-is to a production cluster stores credentials in plaintext in git history. Use one of the two production approaches below.

### Option A — Sealed Secrets (encrypt-and-commit)

[Bitnami Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) encrypts a Secret manifest so it is safe to commit; only the in-cluster controller can decrypt it.

```bash
# 1. Install the controller once per cluster
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/latest/download/controller.yaml

# 2. Install the kubeseal CLI (must match the controller version)

# 3. Seal your values
kubectl create secret generic doc-ai-secrets -n doc-ai --dry-run=client -o yaml \
  --from-literal=SPRING_DATABASE_USERNAME=<user> \
  --from-literal=SPRING_DATABASE_PASSWORD=<password> \
  --from-literal=OPENAI_API_KEY=sk-... \
  --from-literal=JWT_SECRET=<jwt-secret> \
  --from-literal=SECRETS_ENCRYPTION_KEY=<aes-key> \
  --from-literal=SEED_ADMIN_PASSWORD=<password> \
  --from-literal=S3_ACCESS_KEY=<key> \
  --from-literal=S3_SECRET_KEY=<secret> \
  --from-literal=MAIL_USERNAME=<smtp-user> \
  --from-literal=MAIL_PASSWORD=<smtp-password> \
  | kubeseal --format yaml > sealed-secret.yaml

# 4. Commit sealed-secret.yaml (safe — only the controller can decrypt it)
kubectl apply -f sealed-secret.yaml
```

The Deployments already reference the Secret via `envFrom.secretRef.name: doc-ai-secrets`. No other manifests need changing.

Rotating a secret: re-run step 3 and re-apply. The old sealed file in git history is harmless without the controller's private key.

### Option B — External Secrets Operator (pull from a managed secret store)

[External Secrets Operator](https://external-secrets.io/) syncs values from AWS Secrets Manager, GCP Secret Manager, Azure Key Vault, or HashiCorp Vault into an in-cluster Secret. Nothing sensitive ever lives in git.

```bash
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace
```

Create a `SecretStore` (points at your AWS/GCP/Azure/Vault) and an `ExternalSecret` (maps store keys to `doc-ai-secrets` keys). Example templates are in `k8s/external-secret.example.yaml`.

The `refreshInterval` on the `ExternalSecret` controls how quickly secret rotations propagate — no pod restart needed.

**Best fit:** you already run a cloud secret manager for other services and want one source of truth.

---

## CI/CD (GitLab)

`.gitlab-ci.yml` defines the pipeline:

| Stage | Jobs |
|---|---|
| `.pre` | Lint (ESLint + TypeScript) |
| `build` | Frontend Vite build; Maven compile |
| `test` | Backend `mvn test` (Testcontainers — needs `docker:24-dind`); frontend type check |
| `security` | GitLab SAST + Dependency Scanning |
| `publish` | Docker images pushed to registry (tag builds only) |

Images are published only on git tags (`v*`). The publish job tags images as `$CI_REGISTRY_IMAGE/documentation-bot:$CI_COMMIT_TAG`.

### Frontend typecheck note

The root `tsconfig.json` is a solution file. The CI type-check step must use `-p tsconfig.app.json` explicitly:

```bash
npx tsc --noEmit -p tsconfig.app.json
```

Running `npx tsc --noEmit` against the root config is a no-op and will not catch type errors.
