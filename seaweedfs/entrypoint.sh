#!/bin/sh
set -e

: "${SEAWEEDFS_ACCESS_KEY:?SEAWEEDFS_ACCESS_KEY is required}"
: "${SEAWEEDFS_SECRET_KEY:?SEAWEEDFS_SECRET_KEY is required}"

# The master advertises this as its own address, and other components resolve it via Docker's
# embedded DNS to dial back — it must match whatever hostname this container is actually
# reachable at on its network (the compose *service name*, not container_name; Compose only
# registers the service name as a network alias). Defaults to "seaweedfs" to match this repo's
# own docker-compose.yml; override if you rename the service in your own deployment, or the
# master will try to resolve a name nothing on the network answers to and fail to start.
SEAWEEDFS_ADVERTISE_IP="${SEAWEEDFS_ADVERTISE_IP:-seaweedfs}"

# Built with printf (not sed) so arbitrary access/secret key characters can't be misread as a
# sed regex or replacement token.
printf '%s\n' \
  '{' \
  '  "identities": [' \
  '    {' \
  '      "name": "docai",' \
  '      "credentials": [' \
  '        {' \
  "          \"accessKey\": \"${SEAWEEDFS_ACCESS_KEY}\"," \
  "          \"secretKey\": \"${SEAWEEDFS_SECRET_KEY}\"" \
  '        }' \
  '      ],' \
  '      "actions": ["Admin", "Read", "Write"]' \
  '    }' \
  '  ]' \
  '}' \
  > /etc/seaweedfs/s3-config.json

exec weed server \
  -dir=/data \
  -ip="${SEAWEEDFS_ADVERTISE_IP}" \
  -master.volumeSizeLimitMB=1024 \
  -s3 \
  -s3.port=8333 \
  -s3.config=/etc/seaweedfs/s3-config.json
