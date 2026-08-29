#!/bin/sh
set -eu

cat > /usr/share/nginx/html/env-config.js <<EOF
window.__ENV__ = {
  API_BASE_URL: "${API_BASE_URL:-}"
};
EOF

exec nginx -g 'daemon off;'
