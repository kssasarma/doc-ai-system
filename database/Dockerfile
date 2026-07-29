# Postgres + pgvector, with the doc-ai-system bootstrap script baked in so it runs on first init
# on any platform (docker-compose, Kubernetes, bare `docker run`) — not dependent on a bind-mounted
# script, which only fires when the caller happens to mount it at container creation time.
FROM pgvector/pgvector:pg16

COPY init-db.sql /docker-entrypoint-initdb.d/init-db.sql
