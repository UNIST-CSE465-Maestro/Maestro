#!/bin/bash
set -e

echo "=== Deploying maestro.jwchae.com ==="

# 0. Generate SECRET_KEY if not yet set
ENV_FILE="/home/maestro/maestro/server/.env"
if grep -q "DJANGO_SECRET_KEY=REPLACE_ME" "$ENV_FILE" 2>/dev/null; then
    echo "[0/6] Generating SECRET_KEY..."
    NEW_KEY=$(python3 -c "import secrets, string; print(''.join(secrets.choice(string.ascii_letters + string.digits + '!@#$%^&*(-_=+)') for _ in range(64)))")
    sed -i "s|DJANGO_SECRET_KEY=REPLACE_ME|DJANGO_SECRET_KEY=${NEW_KEY}|" "$ENV_FILE"
    echo "       SECRET_KEY written to $ENV_FILE"
fi

# 1. Copy Nginx config and enable site
echo "[1/6] Nginx config..."
sudo cp /home/maestro/maestro/deploy/maestro.jwchae.com /etc/nginx/sites-available/maestro.jwchae.com
sudo ln -sf /etc/nginx/sites-available/maestro.jwchae.com /etc/nginx/sites-enabled/maestro.jwchae.com

# 2. Test Nginx config
echo "[2/6] Testing Nginx config..."
sudo nginx -t

# 3. Reload Nginx
echo "[3/6] Reloading Nginx..."
sudo systemctl reload nginx

# 4. Obtain SSL certificate (certbot will also update Nginx config)
echo "[4/6] Obtaining SSL certificate..."
sudo certbot --nginx -d maestro.jwchae.com --non-interactive --agree-tos --redirect

# 5. Install and start systemd services
echo "[5/6] Setting up systemd services..."
sudo cp /home/maestro/maestro/deploy/maestro-gunicorn.service /etc/systemd/system/maestro-gunicorn.service
sudo cp /home/maestro/maestro/deploy/maestro-celery.service /etc/systemd/system/maestro-celery.service
sudo systemctl daemon-reload
sudo systemctl enable --now maestro-gunicorn.service
sudo systemctl enable --now maestro-celery.service

# 6. Verify
echo "[6/6] Verifying..."
sudo systemctl status maestro-gunicorn.service --no-pager
sudo systemctl status maestro-celery.service --no-pager
sudo systemctl status nginx --no-pager

echo ""
echo "=== Done! https://maestro.jwchae.com ==="
