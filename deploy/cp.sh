#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"

cp /etc/systemd/system/maestro-celery.service   "$DIR/maestro-celery.service"
cp /etc/systemd/system/maestro-gunicorn.service  "$DIR/maestro-gunicorn.service"
cp /etc/nginx/sites-enabled/maestro.jwchae.com   "$DIR/maestro.jwchae.com"

echo "Done."
