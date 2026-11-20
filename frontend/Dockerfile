# Serves the pre-built single-page application (run `npm run build` first, or ./scripts/build-all.sh).
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/claims-hub/browser /usr/share/nginx/html
EXPOSE 4200
