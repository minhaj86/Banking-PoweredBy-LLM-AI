FROM node:24.22.1-alpine AS build
WORKDIR /web
COPY loan-web/package.json loan-web/package-lock.json ./
RUN npm ci --no-fund
COPY loan-web/ ./
RUN npm run build
FROM nginx:1.28.0-alpine
COPY loan-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY loan-web/security-headers.conf /etc/nginx/security-headers.conf
COPY loan-web/spa.conf /etc/nginx/loan-spa.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1
