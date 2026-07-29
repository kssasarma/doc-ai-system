#!/bin/sh
set -e

: "${SEAWEEDFS_ACCESS_KEY:?SEAWEEDFS_ACCESS_KEY is required}"
: "${SEAWEEDFS_SECRET_KEY:?SEAWEEDFS_SECRET_KEY is required}"

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
  -ip=seaweedfs \
  -master.volumeSizeLimitMB=1024 \
  -s3 \
  -s3.port=8333 \
  -s3.config=/etc/seaweedfs/s3-config.json
