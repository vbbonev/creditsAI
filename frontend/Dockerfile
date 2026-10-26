# Build stage: install the locked dependencies and build the Angular app (Node 22).
FROM node:22.23.4-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Run stage: nginx serves the SPA and proxies /api/ and /auth/ (the single entry point).
FROM nginx:1.30.6-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY nginx-proxy-headers.conf /etc/nginx/snippets/proxy-headers.conf
COPY nginx-auth-unavailable.html /usr/share/nginx/errors/auth-unavailable.html
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
RUN nginx -t
EXPOSE 80
