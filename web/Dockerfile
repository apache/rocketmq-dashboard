# Stage 1: Build
FROM node:20-alpine AS build
ARG VITE_GIT_COMMIT
ENV VITE_GIT_COMMIT=${VITE_GIT_COMMIT}
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
ENV NGINX_ENVSUBST_FILTER=^RESOLVER$
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY 15-resolver.envsh /docker-entrypoint.d/15-resolver.envsh
RUN chmod +x /docker-entrypoint.d/15-resolver.envsh
EXPOSE 80
