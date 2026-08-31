#!/usr/bin/env bash
set -euo pipefail

# ─── RocketMQ Studio 一键部署脚本（模式 A：部署 Studio 到目标机器）───
# 主线: 本地打包源码 → 复制到目标机 → 目标机构建镜像（复用 ~/.m2 与 docker 缓存）→ docker compose up
# 用法:
#   ./deploy/deploy.sh           # 部署 server + web
#   ./deploy/deploy.sh server    # 仅部署 server
#   ./deploy/deploy.sh web       # 仅部署 web
# 配置: deploy/.env 中设置 REMOTE_HOST / REMOTE_USER / REMOTE_PATH / PUBLIC_PORT

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

log()  { echo -e "✅ $*"; }
info() { echo -e "🚀 $*"; }
warn() { echo -e "⚠️  $*"; }
err()  { echo -e "❌ $*" >&2; exit 1; }

if [[ -f "$SCRIPT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env"
  set +a
fi

[[ -n "${REMOTE_HOST:-}" ]] || err "REMOTE_HOST 未配置，请在 deploy/.env 中设置"
REMOTE="${REMOTE_USER:-root}@$REMOTE_HOST"
REMOTE_PATH="${REMOTE_PATH:-/opt/rocketmq-studio}"
PUBLIC_PORT="${PUBLIC_PORT:-6789}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.16-eclipse-temurin-21}"
SRC_TAR="/tmp/rocketmq-studio-src.tar.gz"

TARGET="${1:-all}"  # all | server | web
case "$TARGET" in
  all|server|web) ;;
  *) err "用法: $0 [all|server|web]" ;;
esac

run_remote() { ssh "$REMOTE" "$@"; }

check_prereqs() {
  info "🔎 检查前置条件..."
  for c in tar scp ssh; do command -v "$c" >/dev/null 2>&1 || err "本地缺少 $c"; done
  ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" "echo ok" >/dev/null 2>&1 \
    || err "无法 SSH 连接到 $REMOTE（请确认免密登录已配置）"
  run_remote 'command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1' \
    || err "目标机缺少 docker 或 docker compose"
  run_remote "test -f $REMOTE_PATH/.env" \
    || err "目标机缺少 $REMOTE_PATH/.env（首次部署请按 SKILL.md 模式 A 步骤 1 初始化）"
  log "前置条件通过"
}

package_source() {
  info "📦 本地打包源码..."
  # deploy/.env 是环境专属配置（如 STUDIO_METRICS_PROMETHEUS_BASE_URL），不随源码覆盖远端
  tar czf "$SRC_TAR" -C "$PROJECT_DIR" \
    --exclude='web/node_modules' --exclude='web/dist' --exclude='server/target' \
    --exclude='deploy/.env' \
    server web deploy
  log "打包完成 ($(du -h "$SRC_TAR" | cut -f1))"
}

upload_source() {
  info "📤 传输源码到 $REMOTE:$REMOTE_PATH ..."
  run_remote "mkdir -p $REMOTE_PATH"
  scp -q "$SRC_TAR" "$REMOTE:$REMOTE_PATH/"
  # 保留远端环境专属配置（tar 已排除，这里防 rm -rf 误删）
  run_remote "cd $REMOTE_PATH && [ -f deploy/.env ] && cp deploy/.env /tmp/deploy-env-backup; \
    rm -rf server web deploy && tar xzf $(basename "$SRC_TAR") && rm $(basename "$SRC_TAR"); \
    [ -f /tmp/deploy-env-backup ] && mv /tmp/deploy-env-backup deploy/.env || true"
  run_remote 'docker network inspect rocketmq_net >/dev/null 2>&1 || docker network create rocketmq_net'
  log "源码就位，rocketmq_net 网络就绪"
}

build_server() {
  info "🏗️  目标机编译后端 JAR（复用 ~/.m2 缓存）..."
  local settings_flag=""
  if run_remote 'test -f ~/.m2/settings.xml'; then
    settings_flag="-s /maven-cache/settings.xml"
  else
    warn "目标机无 ~/.m2/settings.xml（国内机器请先配置 Maven 阿里源，见 SKILL.md 步骤 2）"
  fi
  run_remote "cd $REMOTE_PATH && docker run --rm -e HOME=/tmp \
    --user \$(id -u):\$(id -g) \
    -v \$PWD/server:/app -v \$HOME/.m2:/maven-cache -w /app \
    $MAVEN_IMAGE \
    mvn -B -ntp $settings_flag -Dmaven.repo.local=/maven-cache/repository package -DskipTests"
  info "🏗️  构建 rocketmq-server 镜像（runtime-prebuilt）..."
  run_remote "cd $REMOTE_PATH && docker build --target runtime-prebuilt -t rocketmq-server:latest server/"
  log "rocketmq-server 镜像构建完成"
}

build_web() {
  local commit
  commit="$(git -C "$PROJECT_DIR" rev-parse --short HEAD 2>/dev/null || echo dev)"
  info "🏗️  构建 rocketmq-web 镜像（VITE_GIT_COMMIT=$commit）..."
  run_remote "cd $REMOTE_PATH && docker build --build-arg VITE_GIT_COMMIT=$commit -t rocketmq-web:latest web/"
  log "rocketmq-web 镜像构建完成"
}

start_services() {
  local services=()
  [[ "$TARGET" == "all" || "$TARGET" == "server" ]] && services+=("rocketmq-server")
  [[ "$TARGET" == "all" || "$TARGET" == "web" ]] && services+=("rocketmq-web")
  info "▶️  启动容器: ${services[*]} ..."
  run_remote "cd $REMOTE_PATH && docker compose --env-file .env -f deploy/docker-compose.yml up -d ${services[*]}"
  log "容器已启动"
}

verify() {
  info "🔍 验证部署..."
  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    local i ok=""
    for i in $(seq 1 30); do
      if run_remote 'docker exec rocketmq-server curl -fsS http://localhost:8888/actuator/health' 2>/dev/null | grep -q '"UP"'; then
        ok="yes"; break
      fi
      sleep 5
    done
    [[ -n "$ok" ]] || err "server 健康检查未通过（docker logs rocketmq-server 查看原因）"
    log "后端 actuator/health = UP"
  fi
  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    local i code=""
    for i in $(seq 1 12); do
      code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://$REMOTE_HOST:$PUBLIC_PORT/" 2>/dev/null || true)
      [[ "$code" == "200" ]] && break
      sleep 5
    done
    [[ "$code" == "200" ]] && log "前端响应正常 (HTTP $code)" || warn "前端返回 HTTP $code"
  fi
  log "🎉 部署完成 → http://$REMOTE_HOST:$PUBLIC_PORT/"
}

cleanup() { rm -f "$SRC_TAR"; }
trap cleanup EXIT

main() {
  echo "═══════════════════════════════════════════"
  echo "  🚢 RocketMQ Studio 部署"
  echo "  目标: $TARGET | 远程: $REMOTE:$REMOTE_PATH"
  echo "═══════════════════════════════════════════"
  check_prereqs
  package_source
  upload_source
  [[ "$TARGET" == "all" || "$TARGET" == "server" ]] && build_server
  [[ "$TARGET" == "all" || "$TARGET" == "web" ]] && build_web
  start_services
  verify
}

main
