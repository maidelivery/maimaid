#!/bin/sh

set -eu

until mc alias set local http://minio:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"; do
  echo "waiting minio..."
  sleep 1
done

mc mb --ignore-existing local/maimaid-assets
mc anonymous set none local/maimaid-assets || true
