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

ENV_FILE="$SCRIPT_DIR/.env"
[[ -f "$ENV_FILE" ]] || err "缺少 $ENV_FILE，请先复制 deploy/.env.example 并填写远程部署配置"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

[[ -n "${REMOTE_HOST:-}" ]] || err "REMOTE_HOST 未配置，请在 deploy/.env 中设置"
REMOTE="${REMOTE_USER:-root}@$REMOTE_HOST"
REMOTE_PATH="${REMOTE_PATH:-/opt/rocketmq-studio}"
PUBLIC_PORT="${PUBLIC_PORT:-6789}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.16-eclipse-temurin-21}"
MAVEN_CACHE_DIR="${MAVEN_CACHE_DIR:-}"
REMOTE_MAVEN_CACHE_DIR=""
SRC_TAR=""
REMOTE_LOCK_DIR="$REMOTE_PATH/.rocketmq-studio-deploy.lock"
LOCK_ACQUIRED="false"

[[ "$REMOTE_PATH" == /* && "$REMOTE_PATH" != *[[:space:]\']* && "$REMOTE_PATH" != *:* ]] \
  || err "REMOTE_PATH 必须是不含空白、单引号或冒号的绝对路径"
if [[ ! "$PUBLIC_PORT" =~ ^[0-9]+$ ]] || ((PUBLIC_PORT < 1 || PUBLIC_PORT > 65535)); then
  err "PUBLIC_PORT 必须是 1 到 65535 之间的端口"
fi

TARGET="${1:-all}"  # all | server | web
case "$TARGET" in
  all|server|web) ;;
  *) err "用法: $0 [all|server|web]" ;;
esac

# shellcheck disable=SC2029
run_remote() { ssh "$REMOTE" "$@"; }

remote_quote() {
  local value="${1//\'/\'\\\'\'}"
  printf "'%s'" "$value"
}

resolve_maven_cache_dir() {
  if [[ -n "$MAVEN_CACHE_DIR" ]]; then
    REMOTE_MAVEN_CACHE_DIR="$MAVEN_CACHE_DIR"
  else
    # shellcheck disable=SC2016
    REMOTE_MAVEN_CACHE_DIR="$(run_remote 'printf %s "$HOME/.m2"')"
  fi
  [[ "$REMOTE_MAVEN_CACHE_DIR" == /* \
      && "$REMOTE_MAVEN_CACHE_DIR" != *$'\n'* \
      && "$REMOTE_MAVEN_CACHE_DIR" != *$'\r'* \
      && "$REMOTE_MAVEN_CACHE_DIR" != *:* ]] \
    || err "MAVEN_CACHE_DIR 必须是目标机上不含换行或冒号的绝对路径"
}

acquire_deploy_lock() {
  local lock_dir
  lock_dir="$(remote_quote "$REMOTE_LOCK_DIR")"
  info "🔒 获取远端部署锁..."
  if ! run_remote "mkdir $lock_dir"; then
    err "已有部署正在操作 ${REMOTE}:${REMOTE_PATH}，请稍后重试"
  fi
  LOCK_ACQUIRED="true"
  run_remote "printf '%s\n' '$$' > $(remote_quote "$REMOTE_LOCK_DIR/pid")"
  log "远端部署锁已获取"
}

release_deploy_lock() {
  [[ "$LOCK_ACQUIRED" == "true" ]] || return 0
  if run_remote "rm -f $(remote_quote "$REMOTE_LOCK_DIR/pid") && rmdir $(remote_quote "$REMOTE_LOCK_DIR")"; then
    LOCK_ACQUIRED="false"
  else
    warn "无法释放远端部署锁 $REMOTE_LOCK_DIR，请手动检查"
    return 1
  fi
}

check_prereqs() {
  local remote_path
  remote_path="$(remote_quote "$REMOTE_PATH")"
  info "🔎 检查前置条件..."
  for c in tar scp ssh; do command -v "$c" >/dev/null 2>&1 || err "本地缺少 $c"; done
  ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" "echo ok" >/dev/null 2>&1 \
    || err "无法 SSH 连接到 $REMOTE（请确认免密登录已配置）"
  run_remote 'command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1' \
    || err "目标机缺少 docker 或 docker compose"
  run_remote "mkdir -p $remote_path"
  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    resolve_maven_cache_dir
  fi
  log "前置条件通过"
}

package_source() {
  info "📦 本地打包源码..."
  SRC_TAR="$(mktemp "${TMPDIR:-/tmp}/rocketmq-studio-src.XXXXXX")"
  tar czf "$SRC_TAR" -C "$PROJECT_DIR" \
    --exclude='web/node_modules' --exclude='web/dist' --exclude='server/target' \
    server web deploy
  log "打包完成 ($(du -h "$SRC_TAR" | cut -f1))"
}

upload_source() {
  local archive_name remote_env remote_path
  archive_name="$(basename "$SRC_TAR")"
  remote_env="$(remote_quote "$REMOTE_PATH/deploy/.env")"
  remote_path="$(remote_quote "$REMOTE_PATH")"
  info "📤 传输源码到 $REMOTE:$REMOTE_PATH ..."
  scp -q "$SRC_TAR" "$REMOTE:$REMOTE_PATH/"
  run_remote "cd $remote_path && rm -rf server web deploy && tar xzf $(remote_quote "$archive_name") && rm $(remote_quote "$archive_name")"
  run_remote "test -f $remote_env" \
    || err "源码包未包含 deploy/.env，无法使用本地部署配置启动远端服务"
  run_remote 'docker network inspect rocketmq_net >/dev/null 2>&1 || docker network create rocketmq_net'
  log "源码就位，rocketmq_net 网络就绪"
}

build_server() {
  local cache_settings cache_volume image remote_path server_volume settings_flag=""
  cache_settings="$(remote_quote "$REMOTE_MAVEN_CACHE_DIR/settings.xml")"
  cache_volume="$(remote_quote "$REMOTE_MAVEN_CACHE_DIR:/maven-cache")"
  image="$(remote_quote "$MAVEN_IMAGE")"
  remote_path="$(remote_quote "$REMOTE_PATH")"
  server_volume="$(remote_quote "$REMOTE_PATH/server:/app")"
  info "🏗️  目标机编译后端 JAR（复用 $REMOTE_MAVEN_CACHE_DIR 缓存）..."
  if run_remote "test -f $cache_settings"; then
    settings_flag="-s /maven-cache/settings.xml"
  else
    warn "目标机无 $REMOTE_MAVEN_CACHE_DIR/settings.xml，将使用 Maven 默认仓库配置"
  fi
  run_remote "cd $remote_path && docker run --rm -e HOME=/tmp \
    --user \"\$(id -u):\$(id -g)\" \
    -v $server_volume -v $cache_volume -w /app \
    $image \
    mvn -B -ntp $settings_flag -Dmaven.repo.local=/maven-cache/repository package -DskipTests"
  info "🏗️  构建 rocketmq-server 镜像（runtime-prebuilt）..."
  run_remote "cd $remote_path && docker build --target runtime-prebuilt -t rocketmq-server:latest server/"
  log "rocketmq-server 镜像构建完成"
}

build_web() {
  local commit
  commit="$(git -C "$PROJECT_DIR" rev-parse --short HEAD 2>/dev/null || echo dev)"
  info "🏗️  构建 rocketmq-web 镜像（VITE_GIT_COMMIT=${commit}）..."
  run_remote "cd $(remote_quote "$REMOTE_PATH") && docker build --build-arg VITE_GIT_COMMIT=$commit -t rocketmq-web:latest web/"
  log "rocketmq-web 镜像构建完成"
}

start_services() {
  local remote_path
  remote_path="$(remote_quote "$REMOTE_PATH")"
  local services=()
  [[ "$TARGET" == "all" || "$TARGET" == "server" ]] && services+=("rocketmq-server")
  [[ "$TARGET" == "all" || "$TARGET" == "web" ]] && services+=("rocketmq-web")
  info "▶️  启动容器: ${services[*]} ..."
  run_remote "cd $remote_path && docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d ${services[*]}"
  log "容器已启动"
}

verify() {
  info "🔍 验证部署..."
  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    local ok=""
    for _ in $(seq 1 30); do
      if run_remote 'docker exec rocketmq-server curl -fsS http://localhost:8888/actuator/health' 2>/dev/null | grep -q '"UP"'; then
        ok="yes"; break
      fi
      sleep 5
    done
    [[ -n "$ok" ]] || err "server 健康检查未通过（docker logs rocketmq-server 查看原因）"
    log "后端 actuator/health = UP"
  fi
  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    local code=""
    for _ in $(seq 1 12); do
      code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://$REMOTE_HOST:$PUBLIC_PORT/" 2>/dev/null || true)
      [[ "$code" == "200" ]] && break
      sleep 5
    done
    if [[ "$code" == "200" ]]; then
      log "前端响应正常 (HTTP $code)"
    else
      warn "前端返回 HTTP $code"
    fi
  fi
  log "🎉 部署完成 → http://$REMOTE_HOST:$PUBLIC_PORT/"
}

cleanup() {
  local status=$?
  release_deploy_lock || true
  [[ -z "$SRC_TAR" ]] || rm -f -- "$SRC_TAR"
  return "$status"
}
trap cleanup EXIT

main() {
  echo "═══════════════════════════════════════════"
  echo "  🚢 RocketMQ Studio 部署"
  echo "  目标: $TARGET | 远程: $REMOTE:$REMOTE_PATH"
  echo "═══════════════════════════════════════════"
  check_prereqs
  acquire_deploy_lock
  package_source
  upload_source
  [[ "$TARGET" == "all" || "$TARGET" == "server" ]] && build_server
  [[ "$TARGET" == "all" || "$TARGET" == "web" ]] && build_web
  start_services
  verify
}

main
