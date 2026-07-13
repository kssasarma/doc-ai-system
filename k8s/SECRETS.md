# Managing secrets for the `doc-ai` namespace

`secrets.yaml` in this directory is a **plain Kubernetes `Secret` manifest for local/dev
clusters only** (kind, minikube, a personal dev namespace). Applying it as-is against a
production cluster means the credentials — DB password, LLM API keys, JWT signing secret, S3
keys, mail credentials — live in cleartext in git history and in `kubectl get secret -o yaml`
output. Use one of the two approaches below for any shared or production cluster instead.

## Option A — Sealed Secrets (encrypt once, commit safely)

[Bitnami Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) lets you commit an
**encrypted** version of the Secret to git; only the controller running in your target cluster
holds the private key needed to decrypt it, so the committed file is safe even if the repo is
public.

1. Install the controller once per cluster:
   ```
   kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/latest/download/controller.yaml
   ```
2. Install the `kubeseal` CLI locally (matching the controller version).
3. Seal your real values straight from `secrets.yaml`'s structure — don't apply the plaintext
   version first:
   ```
   kubectl create secret generic doc-ai-secrets -n doc-ai --dry-run=client -o yaml \
     --from-literal=SPRING_DATABASE_USERNAME=... \
     --from-literal=SPRING_DATABASE_PASSWORD=... \
     --from-literal=OPENAI_API_KEY=... \
     --from-literal=ANTHROPIC_API_KEY=... \
     --from-literal=JWT_SECRET=... \
     --from-literal=SEED_ADMIN_PASSWORD=... \
     --from-literal=REDIS_PASSWORD=... \
     --from-literal=S3_ACCESS_KEY=... \
     --from-literal=S3_SECRET_KEY=... \
     --from-literal=S3_BUCKET=... \
     --from-literal=MAIL_USERNAME=... \
     --from-literal=MAIL_PASSWORD=... \
     | kubeseal --format yaml > sealed-secret.yaml
   ```
4. Commit `sealed-secret.yaml` (see `sealed-secret.example.yaml` in this directory for the shape
   it produces) and apply it with `kubectl apply -f sealed-secret.yaml` — the controller decrypts
   it in-cluster into an ordinary `doc-ai-secrets` Secret that the Deployments already reference
   via `secretRef` in each manifest.
5. Rotating a value means re-running step 3 and re-applying; the old sealed file in git history
   is harmless without the controller's private key.

Best fit when: you want secrets to live in the same git-ops repo/PR flow as everything else, and
don't already run a separate secrets manager.

## Option B — External Secrets Operator (pull from a managed secret store)

[External Secrets Operator](https://external-secrets.io/) syncs values from AWS Secrets Manager,
GCP Secret Manager, Azure Key Vault, HashiCorp Vault, etc. into a regular in-cluster `Secret` —
nothing sensitive is ever stored in git at all, only a pointer to where the real value lives.

1. Install the operator: `helm install external-secrets external-secrets/external-secrets -n
   external-secrets --create-namespace`.
2. Create the credentials for it to read your secret store (e.g. an IAM role for AWS, a Vault
   token) — see `external-secret.example.yaml` in this directory for a worked AWS Secrets Manager
   `SecretStore` + `ExternalSecret` pair; swap the `provider` block for GCP/Azure/Vault as needed.
3. Store each value (`SPRING_DATABASE_PASSWORD`, `OPENAI_API_KEY`, etc.) in your chosen secret
   store under whatever naming scheme you prefer — the `ExternalSecret`'s `data[].remoteRef.key`
   maps store keys to the `doc-ai-secrets` keys the Deployments expect.
4. Apply the `SecretStore` and `ExternalSecret` manifests; the operator creates and keeps
   `doc-ai-secrets` in sync on the `refreshInterval` you configure (rotation in the secret store
   propagates automatically, no redeploy needed).

Best fit when: you already run a cloud secret manager or Vault for other services and want one
source of truth instead of a second, git-encrypted copy.

## Either way

Both options produce the same thing the Deployments already expect: a `Secret` named
`doc-ai-secrets` in the `doc-ai` namespace with the keys listed in `secrets.yaml`. Nothing else in
`k8s/*.yaml` needs to change — `envFrom.secretRef.name: doc-ai-secrets` in
`documentation-bot-deployment.yaml` / `document-ingestor-deployment.yaml` doesn't care which of
the three mechanisms produced that Secret.
