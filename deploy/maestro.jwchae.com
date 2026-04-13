server {
    listen 80;
    server_name maestro.jwchae.com;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        client_max_body_size 500M;
        proxy_read_timeout 600s;
        proxy_connect_timeout 600s;
    }

    location /static/ {
        alias /home/maestro/maestro/server/staticfiles/;
    }

    location /media/ {
        alias /home/maestro/maestro/server/media/;
    }
}
