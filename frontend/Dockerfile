# Serves the pre-built shell and micro-frontends (run `npm run build:all` first, or ./scripts/build-all.sh).
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY spa.inc /etc/nginx/conf.d/spa.inc
COPY dist/shell/browser /usr/share/nginx/html/shell
COPY dist/claimant/browser /usr/share/nginx/html/claimant
COPY dist/staff/browser /usr/share/nginx/html/staff
EXPOSE 4200 4201 4202
