index index.html;
add_header Cache-Control $no_cache_html;

# remotes are fetched cross-origin by the shell
add_header Access-Control-Allow-Origin "*" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;

gzip on;
gzip_types application/javascript application/json text/css image/svg+xml;

location / {
  try_files $uri $uri/ /index.html;
}
