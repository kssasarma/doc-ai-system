# SeaweedFS with the S3 gateway enabled, plus a small entrypoint that renders the S3 identity
# config from env vars on container start (SeaweedFS has no MINIO_ROOT_USER-style env var support
# for S3 credentials — the access/secret key pair has to live in a JSON identity file).
FROM chrislusf/seaweedfs:latest

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
