#!/bin/bash
set -e

echo "=== Deploying maestro.jwchae.com ==="

# 0. Generate SECRET_KEY if not yet set
ENV_FILE="/home/maestro/maestro/server/.env"
if grep -q "DJANGO_SECRET_KEY=REPLACE_ME" "$ENV_FILE" 2>/dev/null; then
    echo "[0/7] Generating SECRET_KEY..."
    NEW_KEY=$(python3 -c "import secrets, string; print(''.join(secrets.choice(string.ascii_letters + string.digits + '!@#$%^&*(-_=+)') for _ in range(64)))")
    sed -i "s|DJANGO_SECRET_KEY=REPLACE_ME|DJANGO_SECRET_KEY=${NEW_KEY}|" "$ENV_FILE"
    echo "       SECRET_KEY written to $ENV_FILE"
fi

# 1. Copy Nginx config and enable site
echo "[1/7] Nginx config..."
sudo cp /home/maestro/maestro/deploy/maestro.jwchae.com /etc/nginx/sites-available/maestro.jwchae.com
sudo ln -sf /etc/nginx/sites-available/maestro.jwchae.com /etc/nginx/sites-enabled/maestro.jwchae.com

# 2. Test Nginx config
echo "[2/7] Testing Nginx config..."
sudo nginx -t

# 3. Reload Nginx
echo "[3/7] Reloading Nginx..."
sudo systemctl reload nginx

# 4. SSL certificate (install or reinstall into Nginx)
echo "[4/7] SSL certificate..."
sudo certbot install --nginx -d maestro.jwchae.com --non-interactive --redirect 2>/dev/null \
    || sudo certbot --nginx -d maestro.jwchae.com --non-interactive --agree-tos --redirect

# 5. Ensure Redis is running
echo "[5/7] Starting Redis..."
sudo systemctl enable --now redis-server

# 6. Install and restart systemd services
echo "[6/7] Setting up systemd services..."
sudo cp /home/maestro/maestro/deploy/maestro-gunicorn.service /etc/systemd/system/maestro-gunicorn.service
sudo cp /home/maestro/maestro/deploy/maestro-celery.service /etc/systemd/system/maestro-celery.service
sudo systemctl daemon-reload
sudo systemctl enable maestro-gunicorn.service
sudo systemctl enable maestro-celery.service
sudo systemctl restart maestro-gunicorn.service
sudo systemctl restart maestro-celery.service

# 7. Verify
echo "[7/7] Verifying..."
sudo systemctl status redis-server --no-pager
sudo systemctl status maestro-gunicorn.service --no-pager
sudo systemctl status maestro-celery.service --no-pager
sudo systemctl status nginx --no-pager

echo ""
echo "=== Done! https://maestro.jwchae.com ==="
