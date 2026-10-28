#!/bin/sh
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

# 构建 apache-rocketmq:develop 镜像
# 自动判断地理位置：国内走阿里源（apt / Maven），国外用默认源
set -e
cd "$(dirname "$0")"

country=$(curl -s --max-time 3 'http://ip-api.com/line?fields=countryCode' 2>/dev/null || true)
if [ -z "$country" ]; then
    # 探测失败时用 google 可达性兜底：不可达视为国内
    if curl -s --max-time 3 -o /dev/null https://www.google.com/generate_204 2>/dev/null; then
        country=OTHER
    else
        country=CN
    fi
fi

if [ "$country" = "CN" ]; then
    USE_CN_MIRROR=true
else
    USE_CN_MIRROR=false
fi
echo "geo=$country USE_CN_MIRROR=$USE_CN_MIRROR"

USE_CN_MIRROR=$USE_CN_MIRROR docker compose build "$@"
