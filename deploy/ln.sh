#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"

sudo ln -sf "$DIR/maestro-celery.service"   /etc/systemd/system/maestro-celery.service
sudo ln -sf "$DIR/maestro-gunicorn.service"  /etc/systemd/system/maestro-gunicorn.service
sudo ln -sf "$DIR/maestro.jwchae.com"        /etc/nginx/sites-enabled/maestro.jwchae.com

sudo systemctl daemon-reload
sudo nginx -t && sudo systemctl reload nginx

echo "Done."
