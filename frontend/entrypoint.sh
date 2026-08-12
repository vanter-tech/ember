#!/bin/sh

echo "Starting variable injections..."

echo "window.ENV = {" > /usr/share/nginx/html/env-config.js
echo " EMBW_API_URL: '${EMBW_API_URL:-http://localhost:8080/api/v1}'," >> /usr/share/nginx/html/env-config.js
echo " EMBW_WS_URL: '${EMBW_WS_URL:-http://localhost:8080/v1/ws}'" >> /usr/share/nginx/html/env-config.js
echo "};" >> /usr/share/nginx/html/env-config.js

echo "env-config.js created successfully"