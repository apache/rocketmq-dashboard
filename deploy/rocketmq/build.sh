#!/bin/sh
# 构建 apache-rocketmq:develop 镜像
# 自动判断地理位置：国内走阿里源（apt / Maven），国外用默认源
set -e
cd "$(dirname "$0")"

# -f：ip-api 限流/出错时返回 4xx/5xx 且带非空正文，旧写法把 "HTTP 429: ..." 当成了国家代码；
# 形状校验只认两位大写国家码，其余一律视为探测失败走 google 兜底，不再误判为国外。
country=$(curl -sf --max-time 3 'http://ip-api.com/line?fields=countryCode' 2>/dev/null || true)
country=$(printf '%s' "$country" | tr -d '\r\n' | tr -d ' \t')
case "$country" in
    [A-Z][A-Z]) ;;
    *) country= ;;
esac
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
