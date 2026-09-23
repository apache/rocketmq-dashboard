# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
# A separate path carries the same build-checked materials; COPY must fail when they are missing.
COPY --from=build /app/dist/LICENSE /app/dist/NOTICE /usr/share/licenses/rocketmq-studio/
COPY --from=build /app/dist/legal /usr/share/licenses/rocketmq-studio/legal
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY 15-resolver.envsh /docker-entrypoint.d/15-resolver.envsh
RUN chmod +x /docker-entrypoint.d/15-resolver.envsh
EXPOSE 80
