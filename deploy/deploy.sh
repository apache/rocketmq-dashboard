#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

TARGET=""
REMOTE=""
REMOTE_PATH="${REMOTE_PATH:-}"
REMOTE_PATH_QUOTED=""
REMOTE_TARBALL=""
REMOTE_TARBALL_QUOTED=""
REMOTE_REGISTRY_STAGE=""
REMOTE_REGISTRY_STAGE_QUOTED=""
NETWORK=""
NETWORK_QUOTED=""
PUBLIC_PORT="${PUBLIC_PORT:-}"
LOCAL_REGISTRY=""
LOCAL_TMP_DIR=""
REMOTE_ARTIFACTS_CREATED=0

SERVER_IMAGE="rocketmq-server:latest"
WEB_IMAGE="rocketmq-web:latest"
STUDIO_USERS_VOLUME="rocketmq-studio-users"

log() {
  printf "${GREEN}[✓]${NC} %s\n" "$*"
}

info() {
  printf "${CYAN}[→]${NC} %s\n" "$*"
}

err() {
  printf "${RED}[✗]${NC} %s\n" "$*" >&2
  exit 1
}

quote_remote_arg() {
  # All callers validate against allowlists before quoting. Single quotes are
  # rejected so the result is safe in the one command string accepted by SSH.
  [[ "$1" != *"'"* ]] || return 1
  printf "'%s'" "$1"
}

validate_name() {
  local label=$1
  local value=$2
  [[ "$value" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]{0,62}$ ]] \
    || err "$label 包含不安全字符"
}

validate_remote_path() {
  local value=$1
  [[ "$value" =~ ^/[A-Za-z0-9._/-]+$ ]] \
    || err "REMOTE_PATH 必须是仅含安全字符的绝对路径"
  [[ "$value" != "/" ]] || err "REMOTE_PATH 不得为根目录"
  case "$value/" in
    *"//"*|*"/../"*|*"/./"*) err "REMOTE_PATH 不得包含空、. 或 .. 路径段" ;;
  esac
}

validate_port() {
  local value=$1
  [[ "$value" =~ ^[0-9]+$ ]] || err "PUBLIC_PORT 必须是数字"
  (( value >= 1 && value <= 65535 )) || err "PUBLIC_PORT 必须在 1-65535 范围内"
}

file_mode() {
  local path=$1
  local mode
  if mode=$(stat -f '%Lp' -- "$path" 2>/dev/null); then
    printf '%s' "$mode"
    return
  fi
  stat -c '%a' -- "$path"
}

resolve_registry_file() {
  local configured=${STUDIO_SECURITY_USER_FILE_LOCAL:-}
  local parent
  local filename
  local mode
  local mode_number

  [[ -n "$configured" ]] \
    || err "server/all 部署必须设置 STUDIO_SECURITY_USER_FILE_LOCAL"
  [[ ! -L "$configured" ]] || err "用户注册表不得为符号链接"
  [[ -f "$configured" ]] || err "用户注册表必须是存在的普通文件"
  [[ -r "$configured" ]] || err "用户注册表不可读"
  [[ -O "$configured" ]] || err "用户注册表必须由当前用户所有"

  parent=$(cd "$(dirname "$configured")" && pwd -P) \
    || err "无法解析用户注册表父目录"
  filename=$(basename "$configured")
  LOCAL_REGISTRY="$parent/$filename"

  [[ ! -L "$LOCAL_REGISTRY" ]] || err "解析后的用户注册表不得为符号链接"
  [[ -f "$LOCAL_REGISTRY" ]] || err "解析后的用户注册表不是普通文件"

  mode=$(file_mode "$LOCAL_REGISTRY") || err "无法读取用户注册表权限"
  [[ "$mode" =~ ^[0-7]{3,4}$ ]] || err "无法解析用户注册表权限"
  mode_number=$((8#$mode))
  (( (mode_number & 0400) != 0 )) || err "用户注册表必须允许所有者读取"
  (( (mode_number & 0077) == 0 )) \
    || err "用户注册表权限不安全；组和其他用户不得拥有任何权限"
}

load_config() {
  if [[ -f "$SCRIPT_DIR/.env" ]]; then
    set -a
    # deploy/.env is operator-controlled configuration, not application input.
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/.env"
    set +a
  fi
}

configure() {
  TARGET=${1:-all}
  case "$TARGET" in
    all|server|web) ;;
    *) err "用法: $0 [all|server|web]" ;;
  esac

  [[ -n "${REMOTE_HOST:-}" ]] \
    || err "REMOTE_HOST 未配置，请在 deploy/.env 中设置"
  [[ "$REMOTE_HOST" =~ ^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$ ]] \
    || err "REMOTE_HOST 包含不安全字符"

  REMOTE_USER=${REMOTE_USER:-root}
  [[ "$REMOTE_USER" =~ ^[A-Za-z_][A-Za-z0-9_.-]{0,31}$ ]] \
    || err "REMOTE_USER 包含不安全字符"

  REMOTE_PATH=${REMOTE_PATH:-/opt/rocketmq-studio}
  validate_remote_path "$REMOTE_PATH"

  PUBLIC_PORT=${PUBLIC_PORT:-6789}
  validate_port "$PUBLIC_PORT"

  NETWORK=${PODMAN_NETWORK:-rocketmq-studio}
  validate_name "PODMAN_NETWORK" "$NETWORK"
  validate_name "Studio 用户卷名称" "$STUDIO_USERS_VOLUME"

  REMOTE="$REMOTE_USER@$REMOTE_HOST"
  REMOTE_TARBALL="$REMOTE_PATH/rocketmq-studio-images.tar.gz"
  REMOTE_REGISTRY_STAGE="$REMOTE_PATH/.studio-users.json.staging"

  REMOTE_PATH_QUOTED=$(quote_remote_arg "$REMOTE_PATH")
  REMOTE_TARBALL_QUOTED=$(quote_remote_arg "$REMOTE_TARBALL")
  REMOTE_REGISTRY_STAGE_QUOTED=$(quote_remote_arg "$REMOTE_REGISTRY_STAGE")
  NETWORK_QUOTED=$(quote_remote_arg "$NETWORK")

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    resolve_registry_file
  fi
}

check_prereqs() {
  info "检查前置条件..."
  command -v docker >/dev/null 2>&1 || err "docker 未安装"
  command -v ssh >/dev/null 2>&1 || err "ssh 未安装"
  command -v scp >/dev/null 2>&1 || err "scp 未安装"
  docker info >/dev/null 2>&1 || err "Docker 引擎不可用"
  ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" true >/dev/null 2>&1 \
    || err "无法 SSH 连接到 ${REMOTE}（请确认免密登录已配置）"
  log "前置条件通过"
}

prepare_local_temp() {
  LOCAL_TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/rocketmq-studio-deploy.XXXXXX") \
    || err "无法创建本地临时目录"
}

build_server() {
  info "构建 rocketmq-server 镜像..."
  docker build -t "$SERVER_IMAGE" "$PROJECT_DIR/server"
  log "rocketmq-server 镜像构建完成"
}

build_web() {
  info "构建 rocketmq-web 镜像..."
  docker build -t "$WEB_IMAGE" "$PROJECT_DIR/web"
  log "rocketmq-web 镜像构建完成"
}

transfer_images_and_registry() {
  local images=()
  local tarball="$LOCAL_TMP_DIR/rocketmq-studio-images.tar.gz"

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    images+=("$SERVER_IMAGE")
  fi
  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    images+=("$WEB_IMAGE")
  fi

  info "导出部署镜像..."
  docker save "${images[@]}" | gzip >"$tarball"

  info "创建受限远程暂存目录..."
  ssh "$REMOTE" "install -d -m 700 -- $REMOTE_PATH_QUOTED"
  REMOTE_ARTIFACTS_CREATED=1

  info "传输镜像与部署配置..."
  scp "$tarball" "$REMOTE:$REMOTE_TARBALL"
  scp "$SCRIPT_DIR/docker-compose.yml" "$REMOTE:$REMOTE_PATH/"
  scp "$SCRIPT_DIR/nginx.conf" "$REMOTE:$REMOTE_PATH/"

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    info "暂存 Studio 用户注册表..."
    scp -p "$LOCAL_REGISTRY" "$REMOTE:$REMOTE_REGISTRY_STAGE"
    ssh "$REMOTE" "chmod 600 -- $REMOTE_REGISTRY_STAGE_QUOTED"
  fi
  log "传输完成"
}

load_images_and_prepare_network() {
  info "远程加载镜像并准备私有网络..."
  ssh "$REMOTE" \
    "REMOTE_TARBALL=$REMOTE_TARBALL_QUOTED NETWORK=$NETWORK_QUOTED sh -s" <<'REMOTE_SCRIPT'
set -eu
gzip -dc -- "$REMOTE_TARBALL" | podman load >/dev/null
podman network exists "$NETWORK" 2>/dev/null \
  || podman network create "$NETWORK" >/dev/null
REMOTE_SCRIPT
  log "镜像与网络准备完成"
}

populate_registry_volume() {
  local volume_quoted
  local image_quoted
  volume_quoted=$(quote_remote_arg "$STUDIO_USERS_VOLUME")
  image_quoted=$(quote_remote_arg "$SERVER_IMAGE")

  info "原子填充私有 Studio 用户卷..."
  ssh "$REMOTE" \
    "REGISTRY_STAGE=$REMOTE_REGISTRY_STAGE_QUOTED STUDIO_VOLUME=$volume_quoted SERVER_IMAGE=$image_quoted sh -s" <<'REMOTE_SCRIPT'
set -eu
podman image exists "$SERVER_IMAGE"
podman volume exists "$STUDIO_VOLUME" 2>/dev/null \
  || podman volume create "$STUDIO_VOLUME" >/dev/null

podman run --rm \
  --entrypoint sh \
  -v "$REGISTRY_STAGE:/run/staging/studio-users.json:ro" \
  -v "$STUDIO_VOLUME:/run/secrets" \
  "$SERVER_IMAGE" -c '
    set -eu
    umask 077
    destination=/run/secrets/studio-users.json
    temporary=/run/secrets/.studio-users.json.tmp.$$
    trap '\''rm -f -- "$temporary"'\'' EXIT HUP INT TERM
    cp /run/staging/studio-users.json "$temporary"
    chmod 600 "$temporary"
    mv -f -- "$temporary" "$destination"
    trap - EXIT HUP INT TERM
  '
REMOTE_SCRIPT
  log "Studio 用户卷已安全填充"
}

start_server() {
  local volume_quoted
  local image_quoted
  volume_quoted=$(quote_remote_arg "$STUDIO_USERS_VOLUME")
  image_quoted=$(quote_remote_arg "$SERVER_IMAGE")

  info "重启 rocketmq-server（无宿主机端口）..."
  ssh "$REMOTE" \
    "NETWORK=$NETWORK_QUOTED STUDIO_VOLUME=$volume_quoted SERVER_IMAGE=$image_quoted sh -s" <<'REMOTE_SCRIPT'
set -eu
podman rm -f rocketmq-server >/dev/null 2>&1 || true
podman run -d \
  --name rocketmq-server \
  --network "$NETWORK" \
  --restart unless-stopped \
  -e STUDIO_SECURITY_USER_FILE=/run/secrets/studio-users.json \
  -v "$STUDIO_VOLUME:/run/secrets:ro" \
  "$SERVER_IMAGE" >/dev/null
REMOTE_SCRIPT
  log "rocketmq-server 已启动"
}

start_web() {
  local image_quoted
  image_quoted=$(quote_remote_arg "$WEB_IMAGE")

  info "重启 rocketmq-web（仅远程回环监听）..."
  ssh "$REMOTE" \
    "NETWORK=$NETWORK_QUOTED PUBLIC_PORT='$PUBLIC_PORT' WEB_IMAGE=$image_quoted sh -s" <<'REMOTE_SCRIPT'
set -eu
podman rm -f rocketmq-web >/dev/null 2>&1 || true
podman run -d \
  --name rocketmq-web \
  --network "$NETWORK" \
  --restart unless-stopped \
  -p "127.0.0.1:${PUBLIC_PORT:-6789}:80" \
  "$WEB_IMAGE" >/dev/null
REMOTE_SCRIPT
  log "rocketmq-web 已启动"
}

remove_remote_staging() {
  (( REMOTE_ARTIFACTS_CREATED == 1 )) || return 0
  ssh "$REMOTE" \
    "REMOTE_TARBALL=$REMOTE_TARBALL_QUOTED REGISTRY_STAGE=$REMOTE_REGISTRY_STAGE_QUOTED sh -s" <<'REMOTE_SCRIPT'
set -eu
rm -f -- "$REMOTE_TARBALL" "$REGISTRY_STAGE"
REMOTE_SCRIPT
  REMOTE_ARTIFACTS_CREATED=0
}

verify_remote() {
  local status
  local http_code

  info "验证远程容器和回环入口..."
  status=$(ssh "$REMOTE" "podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'")
  printf '%s\n' "$status"

  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    http_code=$(ssh "$REMOTE" \
      "curl -sS -o /dev/null -w '%{http_code}' --max-time 10 'http://127.0.0.1:$PUBLIC_PORT'") \
      || err "远程回环入口不可达"
    [[ "$http_code" == "200" ]] || err "远程回环入口返回 HTTP $http_code"
    log "远程回环入口响应正常 (HTTP $http_code)"
  fi

  printf '\n通过本地 SSH 隧道访问（保持该命令运行）：\n'
  printf '  ssh -L %s:127.0.0.1:%s %s\n' "$PUBLIC_PORT" "$PUBLIC_PORT" "$REMOTE"
  printf '然后仅在本机打开：http://127.0.0.1:%s\n' "$PUBLIC_PORT"
}

cleanup() {
  local exit_code=$?
  trap - EXIT

  if (( REMOTE_ARTIFACTS_CREATED == 1 )) \
      && [[ -n "$REMOTE" ]] \
      && [[ -n "$REMOTE_TARBALL_QUOTED" ]] \
      && [[ -n "$REMOTE_REGISTRY_STAGE_QUOTED" ]]; then
    ssh "$REMOTE" \
      "rm -f -- $REMOTE_TARBALL_QUOTED $REMOTE_REGISTRY_STAGE_QUOTED" \
      >/dev/null 2>&1 || true
  fi

  if [[ -n "$LOCAL_TMP_DIR" ]] \
      && [[ -d "$LOCAL_TMP_DIR" ]] \
      && [[ "$(basename "$LOCAL_TMP_DIR")" == rocketmq-studio-deploy.* ]]; then
    rm -rf -- "$LOCAL_TMP_DIR"
  fi

  exit "$exit_code"
}

main() {
  load_config
  configure "${1:-all}"
  trap cleanup EXIT

  printf '═══════════════════════════════════════════\n'
  printf '  RocketMQ Studio 安全部署\n'
  printf '  目标: %s | 远程: %s:%s\n' "$TARGET" "$REMOTE" "$REMOTE_PATH"
  printf '═══════════════════════════════════════════\n\n'

  check_prereqs
  prepare_local_temp

  case "$TARGET" in
    server) build_server ;;
    web) build_web ;;
    all)
      build_server
      build_web
      ;;
  esac

  transfer_images_and_registry
  load_images_and_prepare_network

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    populate_registry_volume
    start_server
  fi
  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    start_web
  fi

  remove_remote_staging
  verify_remote
  log "部署完成；未公开任何远程 HTTP 或后端端口"
}

main "$@"
