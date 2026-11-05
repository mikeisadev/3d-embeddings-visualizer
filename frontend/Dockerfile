# syntax=docker/dockerfile:1

# ---- Build: install exactly the locked dependencies, then type check, lint, test and build.
FROM node:24.22.2-alpine3.24 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run typecheck && npm run lint && npm test && npm run build

# ---- Runtime: unprivileged nginx (uid 101, port 8080) serving the static build.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
