#!/usr/bin/env bash
set -euo pipefail

# ─── RocketMQ Studio 一键部署脚本 ───
# 用法:
#   ./deploy/deploy.sh           # 部署 server + web
#   ./deploy/deploy.sh server    # 仅部署 server
#   ./deploy/deploy.sh web       # 仅部署 web

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ─── 颜色输出 ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓]${NC} $*"; }
info() { echo -e "${CYAN}[→]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[✗]${NC} $*"; exit 1; }

# 加载配置
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env"
  set +a
fi

[[ -n "${REMOTE_HOST:-}" ]] || err "REMOTE_HOST 未配置，请在 deploy/.env 中设置"
REMOTE="${REMOTE_USER:-root}@$REMOTE_HOST"
REMOTE_PATH="${REMOTE_PATH:-/opt/rocketmq-studio}"
NETWORK="${PODMAN_NETWORK:-rocketmq-studio}"
TMP_DIR="/tmp/rocketmq-studio-deploy"

TARGET="${1:-all}"  # all | server | web

# ─── 前置检查 ───
check_prereqs() {
  info "检查前置条件..."
  command -v docker >/dev/null 2>&1 || err "docker 未安装"
  command -v ssh    >/dev/null 2>&1 || err "ssh 未安装"
  command -v scp    >/dev/null 2>&1 || err "scp 未安装"
  ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" "echo ok" >/dev/null 2>&1 \
    || err "无法 SSH 连接到 $REMOTE（请确认免密登录已配置）"
  log "前置条件通过"
}

# ─── 构建镜像 ───
build_server() {
  info "构建 rocketmq-server 镜像..."
  docker build -t rocketmq-server:latest "$PROJECT_DIR/server"
  log "rocketmq-server 镜像构建完成"
}

build_web() {
  info "构建 rocketmq-web 镜像..."
  docker build -t rocketmq-web:latest "$PROJECT_DIR/web"
  log "rocketmq-web 镜像构建完成"
}

# ─── 导出 & 传输 ───
transfer_images() {
  mkdir -p "$TMP_DIR"
  local images=()

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    images+=("rocketmq-server:latest")
  fi
  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    images+=("rocketmq-web:latest")
  fi

  local tarball="$TMP_DIR/rocketmq-studio-images.tar.gz"
  info "导出镜像: ${images[*]}"
  docker save "${images[@]}" | gzip > "$tarball"
  log "镜像导出完成 ($(du -h "$tarball" | cut -f1))"

  info "传输到 $REMOTE:$REMOTE_PATH ..."
  ssh "$REMOTE" "mkdir -p $REMOTE_PATH"
  scp "$tarball" "$REMOTE:$REMOTE_PATH/"
  log "传输完成"

  # 同步部署配置文件
  info "同步部署配置..."
  scp "$SCRIPT_DIR/docker-compose.yml" "$REMOTE:$REMOTE_PATH/"
  scp "$SCRIPT_DIR/nginx.conf"         "$REMOTE:$REMOTE_PATH/"
  log "配置同步完成"
}

# ─── 远程加载 & 启动 ───
deploy_remote() {
  local tarball="$REMOTE_PATH/rocketmq-studio-images.tar.gz"

  info "远程加载镜像..."
  ssh "$REMOTE" "gunzip -c $tarball | podman load"
  log "镜像加载完成"

  # 确保网络存在
  ssh "$REMOTE" "podman network exists $NETWORK 2>/dev/null || podman network create $NETWORK"

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    info "重启 rocketmq-server..."
    ssh "$REMOTE" "
      podman rm -f rocketmq-server 2>/dev/null || true
      podman run -d \
        --name rocketmq-server \
        --network $NETWORK \
        --restart unless-stopped \
        -p 8888:8888 \
        -e STUDIO_AUTH_LOGIN_REQUIRED=\"${STUDIO_AUTH_LOGIN_REQUIRED:-false}\" \
        -e STUDIO_AUTH_ADMIN_USERNAME=\"${STUDIO_AUTH_ADMIN_USERNAME:-}\" \
        -e STUDIO_AUTH_ADMIN_PASSWORD=\"${STUDIO_AUTH_ADMIN_PASSWORD:-}\" \
        -e STUDIO_METRICS_PROMETHEUS_BASE_URL=\"${STUDIO_METRICS_PROMETHEUS_BASE_URL:-}\" \
        -e STUDIO_METRICS_PROMETHEUS_USERNAME=\"${STUDIO_METRICS_PROMETHEUS_USERNAME:-}\" \
        -e STUDIO_METRICS_PROMETHEUS_PASSWORD=\"${STUDIO_METRICS_PROMETHEUS_PASSWORD:-}\" \
        -e STUDIO_METRICS_PROMETHEUS_BEARER_TOKEN=\"${STUDIO_METRICS_PROMETHEUS_BEARER_TOKEN:-}\" \
        rocketmq-server:latest
    "
    log "rocketmq-server 已启动"
  fi

  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    info "重启 rocketmq-web..."
    ssh "$REMOTE" "
      podman rm -f rocketmq-web 2>/dev/null || true
      podman run -d \
        --name rocketmq-web \
        --network $NETWORK \
        --restart unless-stopped \
        -p ${PUBLIC_PORT:-6789}:80 \
        rocketmq-web:latest
    "
    log "rocketmq-web 已启动"
  fi
}

# ─── 验证 ───
verify() {
  info "验证部署..."
  sleep 3

  local status
  status=$(ssh "$REMOTE" "podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'" 2>&1)
  echo "$status"

  echo ""
  local http_code
  http_code=$(ssh "$REMOTE" "curl -sf -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:${PUBLIC_PORT:-6789}" 2>/dev/null || echo "000")
  if [[ "$http_code" == "200" ]]; then
    log "前端响应正常 (HTTP $http_code)"
  else
    warn "前端返回 HTTP $http_code"
  fi

  echo ""
  log "部署完成 → http://${REMOTE_HOST}:${PUBLIC_PORT:-6789}"
}

# ─── 清理临时文件 ───
cleanup() {
  rm -rf "$TMP_DIR"
}

# ─── 主流程 ───
main() {
  echo "═══════════════════════════════════════════"
  echo "  RocketMQ Studio 部署"
  echo "  目标: $TARGET | 远程: $REMOTE:$REMOTE_PATH"
  echo "═══════════════════════════════════════════"
  echo ""

  check_prereqs

  case "$TARGET" in
    server) build_server ;;
    web)    build_web ;;
    all)    build_server && build_web ;;
    *)      err "用法: $0 [all|server|web]" ;;
  esac

  transfer_images
  deploy_remote
  verify
  cleanup
}

trap cleanup EXIT
main
