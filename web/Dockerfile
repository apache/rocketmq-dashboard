# Stage 1: Build
# Pin Node and nginx base images to a specific minor so CI builds are
# reproducible (the bare :20-alpine and :alpine tags are rolling).
FROM node:20.19.0-alpine AS build
ARG VITE_GIT_COMMIT
ENV VITE_GIT_COMMIT=${VITE_GIT_COMMIT}
WORKDIR /app
COPY package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:1.27.2-alpine
ENV NGINX_ENVSUBST_FILTER=^RESOLVER$
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY 15-resolver.envsh /docker-entrypoint.d/15-resolver.envsh
RUN chmod +x /docker-entrypoint.d/15-resolver.envsh
EXPOSE 80
