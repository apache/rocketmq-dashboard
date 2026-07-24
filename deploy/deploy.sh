#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

TARGET=""
RUN_ID=""
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
LOCAL_REGISTRY_SNAPSHOT=""
LOCAL_TMP_DIR=""
REMOTE_ARTIFACTS_CREATED=0
REMOTE_LOCK_HELD=0

SERVER_IMAGE=""
WEB_IMAGE=""
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

snapshot_registry_path() {
  local source=$1
  local destination=$2

  python3 - "$source" "$destination" <<'PYTHON'
import os
import shutil
import stat
import sys


class SnapshotError(Exception):
    pass


def validate_directory(directory_fd, current_uid, root_uid):
    metadata = os.fstat(directory_fd)
    if not stat.S_ISDIR(metadata.st_mode):
        raise SnapshotError("registry ancestry contains a non-directory")
    if metadata.st_uid not in (current_uid, root_uid):
        raise SnapshotError("registry ancestry has an untrusted owner")
    if stat.S_IMODE(metadata.st_mode) & 0o022:
        raise SnapshotError("registry ancestry is group/world-writable")


def open_registry(source):
    if not os.path.isabs(source):
        raise SnapshotError("registry path must be absolute")
    if os.path.normpath(source) != source or source == os.path.sep:
        raise SnapshotError("registry path must not contain empty, . or .. segments")
    if not hasattr(os, "O_NOFOLLOW"):
        raise SnapshotError("this platform does not support no-follow file opens")

    components = source.split(os.path.sep)[1:]
    if not components or not components[-1]:
        raise SnapshotError("registry path must name a file")

    current_uid = os.getuid()
    root_fd = os.open(
        os.path.sep,
        os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC | os.O_NOFOLLOW,
    )
    directory_fd = root_fd
    try:
        root_uid = os.fstat(root_fd).st_uid
        validate_directory(root_fd, current_uid, root_uid)
        for component in components[:-1]:
            next_fd = os.open(
                component,
                os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC | os.O_NOFOLLOW,
                dir_fd=directory_fd,
            )
            try:
                validate_directory(next_fd, current_uid, root_uid)
            except Exception:
                os.close(next_fd)
                raise
            if directory_fd != root_fd:
                os.close(directory_fd)
            directory_fd = next_fd

        source_fd = os.open(
            components[-1],
            os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW,
            dir_fd=directory_fd,
        )
        metadata = os.fstat(source_fd)
        mode = stat.S_IMODE(metadata.st_mode)
        if not stat.S_ISREG(metadata.st_mode):
            os.close(source_fd)
            raise SnapshotError("registry must be a regular file")
        if metadata.st_uid != current_uid:
            os.close(source_fd)
            raise SnapshotError("registry must be owned by the current user")
        if not mode & stat.S_IRUSR:
            os.close(source_fd)
            raise SnapshotError("registry owner must have read permission")
        if mode & 0o077:
            os.close(source_fd)
            raise SnapshotError("registry group/other permissions must be empty")
        return source_fd
    finally:
        if directory_fd != root_fd:
            os.close(directory_fd)
        os.close(root_fd)


def snapshot(source, destination):
    source_fd = open_registry(source)
    destination_fd = -1
    destination_created = False
    try:
        destination_fd = os.open(
            destination,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | os.O_CLOEXEC
            | os.O_NOFOLLOW,
            0o600,
        )
        destination_created = True
        os.fchmod(destination_fd, 0o600)
        with os.fdopen(source_fd, "rb", closefd=False) as source_stream:
            with os.fdopen(
                destination_fd,
                "wb",
                closefd=False,
            ) as destination_stream:
                shutil.copyfileobj(
                    source_stream,
                    destination_stream,
                    length=64 * 1024,
                )
                destination_stream.flush()
                os.fsync(destination_fd)
    except Exception:
        if destination_created:
            try:
                os.unlink(destination)
            except FileNotFoundError:
                pass
        raise
    finally:
        os.close(source_fd)
        if destination_fd >= 0:
            os.close(destination_fd)


try:
    snapshot(sys.argv[1], sys.argv[2])
except (OSError, SnapshotError) as error:
    print(f"registry snapshot failed: {error}", file=sys.stderr)
    raise SystemExit(1)
PYTHON
}

snapshot_registry() {
  local configured=${STUDIO_SECURITY_USER_FILE_LOCAL:-}

  [[ -n "$configured" ]] \
    || err "server/all 部署必须设置 STUDIO_SECURITY_USER_FILE_LOCAL"
  LOCAL_REGISTRY_SNAPSHOT="$LOCAL_TMP_DIR/studio-users.json"
  info "在构建和联网前创建不可变用户注册表快照..."
  snapshot_registry_path "$configured" "$LOCAL_REGISTRY_SNAPSHOT" \
    || err "无法安全快照 Studio 用户注册表"
  log "Studio 用户注册表已安全快照"
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

reset_internal_state() {
  TARGET=""
  RUN_ID=""
  REMOTE=""
  REMOTE_PATH_QUOTED=""
  REMOTE_TARBALL=""
  REMOTE_TARBALL_QUOTED=""
  REMOTE_REGISTRY_STAGE=""
  REMOTE_REGISTRY_STAGE_QUOTED=""
  NETWORK=""
  NETWORK_QUOTED=""
  LOCAL_REGISTRY_SNAPSHOT=""
  LOCAL_TMP_DIR=""
  REMOTE_ARTIFACTS_CREATED=0
  REMOTE_LOCK_HELD=0
  SERVER_IMAGE=""
  WEB_IMAGE=""
  STUDIO_USERS_VOLUME="rocketmq-studio-users"
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
  REMOTE_PATH_QUOTED=$(quote_remote_arg "$REMOTE_PATH")
  NETWORK_QUOTED=$(quote_remote_arg "$NETWORK")

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    [[ -n "${STUDIO_SECURITY_USER_FILE_LOCAL:-}" ]] \
      || err "server/all 部署必须设置 STUDIO_SECURITY_USER_FILE_LOCAL"
  fi
}

check_local_prereqs() {
  info "检查本地前置条件..."
  command -v docker >/dev/null 2>&1 || err "docker 未安装"
  command -v ssh >/dev/null 2>&1 || err "ssh 未安装"
  command -v scp >/dev/null 2>&1 || err "scp 未安装"
  command -v python3 >/dev/null 2>&1 || err "python3 未安装"
  log "本地前置条件通过"
}

check_connectivity() {
  info "检查 Docker 引擎和远程连接..."
  docker info >/dev/null 2>&1 || err "Docker 引擎不可用"
  ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" true >/dev/null 2>&1 \
    || err "无法 SSH 连接到 ${REMOTE}（请确认免密登录已配置）"
  log "Docker 引擎和远程连接通过"
}

prepare_local_temp() {
  LOCAL_TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/rocketmq-studio-deploy.XXXXXX") \
    || err "无法创建本地临时目录"
  RUN_ID=$(python3 -c 'import secrets; print(secrets.token_hex(12))') \
    || err "无法生成安全的部署运行标识"
  [[ "$RUN_ID" =~ ^[a-f0-9]{24}$ ]] || err "部署运行标识格式无效"

  SERVER_IMAGE="rocketmq-server:deploy-$RUN_ID"
  WEB_IMAGE="rocketmq-web:deploy-$RUN_ID"
  REMOTE_TARBALL="$REMOTE_PATH/.rocketmq-studio-images-$RUN_ID.tar.gz"
  REMOTE_REGISTRY_STAGE="$REMOTE_PATH/.studio-users-$RUN_ID.json.staging"

  REMOTE_TARBALL_QUOTED=$(quote_remote_arg "$REMOTE_TARBALL")
  REMOTE_REGISTRY_STAGE_QUOTED=$(quote_remote_arg "$REMOTE_REGISTRY_STAGE")
}

acquire_remote_lock() {
  local run_id_quoted
  run_id_quoted=$(quote_remote_arg "$RUN_ID")

  info "获取远程独占部署锁..."
  ssh "$REMOTE" \
    "REMOTE_PATH=$REMOTE_PATH_QUOTED RUN_ID=$run_id_quoted sh -s" <<'REMOTE_SCRIPT' \
    || err "远程已有部署在运行；如确认是陈旧锁，请按部署文档安全恢复"
set -eu
# studio-deploy-lock-acquire
umask 077
LOCK_DIR=${HOME:?}/.rocketmq-studio-deploy.lock
install -d -m 700 -- "$REMOTE_PATH"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  printf 'deployment lock is already held\n' >&2
  exit 73
fi
if ! printf '%s\n' "$RUN_ID" >"$LOCK_DIR/owner"; then
  rmdir "$LOCK_DIR" 2>/dev/null || true
  exit 1
fi
REMOTE_SCRIPT
  REMOTE_LOCK_HELD=1
  log "远程部署锁已获取"
}

release_remote_lock() {
  local run_id_quoted
  (( REMOTE_LOCK_HELD == 1 )) || return 0
  run_id_quoted=$(quote_remote_arg "$RUN_ID")

  ssh "$REMOTE" \
    "RUN_ID=$run_id_quoted sh -s" <<'REMOTE_SCRIPT'
set -eu
# studio-deploy-lock-release
LOCK_DIR=${HOME:?}/.rocketmq-studio-deploy.lock
owner=$(cat "$LOCK_DIR/owner" 2>/dev/null) \
  || {
    printf 'deployment lock owner is missing\n' >&2
    exit 1
  }
[ "$owner" = "$RUN_ID" ] \
  || {
    printf 'deployment lock ownership changed\n' >&2
    exit 1
  }
rm -f -- "$LOCK_DIR/owner"
rmdir "$LOCK_DIR"
REMOTE_SCRIPT
  REMOTE_LOCK_HELD=0
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

  info "传输镜像与部署配置..."
  REMOTE_ARTIFACTS_CREATED=1
  scp "$tarball" "$REMOTE:$REMOTE_TARBALL"
  scp "$SCRIPT_DIR/docker-compose.yml" "$REMOTE:$REMOTE_PATH/"
  scp "$SCRIPT_DIR/nginx.conf" "$REMOTE:$REMOTE_PATH/"

  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    info "暂存 Studio 用户注册表..."
    scp -p "$LOCAL_REGISTRY_SNAPSHOT" "$REMOTE:$REMOTE_REGISTRY_STAGE"
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
    temporary=$(mktemp /run/secrets/.studio-users.json.tmp.XXXXXX)
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
  local target_quoted
  target_quoted=$(quote_remote_arg "$TARGET")

  info "验证容器状态、后端 readiness 和回环入口..."
  ssh "$REMOTE" \
    "TARGET=$target_quoted PUBLIC_PORT='$PUBLIC_PORT' sh -s" <<'REMOTE_SCRIPT' \
    || err "远程容器或后端 readiness 验证失败"
set -eu
# studio-deploy-readiness-check

container_is_running() {
  [ "$(podman inspect --format '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ]
}

container_is_running rocketmq-server \
  || {
    printf 'rocketmq-server is not running\n' >&2
    exit 1
  }
if [ "$TARGET" = "all" ] || [ "$TARGET" = "web" ]; then
  container_is_running rocketmq-web \
    || {
      printf 'rocketmq-web is not running\n' >&2
      exit 1
    }
fi

attempt=1
while [ "$attempt" -le 30 ]; do
  container_is_running rocketmq-server \
    || {
      printf 'rocketmq-server exited before becoming ready\n' >&2
      exit 1
    }
  readiness=$(
    podman exec rocketmq-server \
      curl -fsS --connect-timeout 1 --max-time 1 \
      http://127.0.0.1:8888/actuator/health/readiness 2>/dev/null
  ) || readiness=""
  if printf '%s' "$readiness" \
      | grep -Eq '^[[:space:]]*\{[[:space:]]*"status"[[:space:]]*:[[:space:]]*"UP"([[:space:]]*[,}])'; then
    break
  fi
  if [ "$attempt" -eq 30 ]; then
    printf 'rocketmq-server readiness did not become UP\n' >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 1
done

if [ "$TARGET" = "all" ] || [ "$TARGET" = "web" ]; then
  http_code=$(
    curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
      "http://127.0.0.1:$PUBLIC_PORT/"
  ) || {
    printf 'loopback web entry is unreachable\n' >&2
    exit 1
  }
  if [ "$http_code" != "200" ]; then
    printf 'loopback web entry returned HTTP %s\n' "$http_code" >&2
    exit 1
  fi
fi

podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
REMOTE_SCRIPT
  log "远程容器运行且后端 readiness 为 UP"

  if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
    printf '\n通过本地 SSH 隧道访问（保持该命令运行）：\n'
    printf '  ssh -L %s:127.0.0.1:%s %s\n' "$PUBLIC_PORT" "$PUBLIC_PORT" "$REMOTE"
    printf '然后仅在本机打开：http://127.0.0.1:%s\n' "$PUBLIC_PORT"
  fi
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

  if (( REMOTE_LOCK_HELD == 1 )) \
      && [[ -n "$REMOTE" ]] \
      && [[ -n "$RUN_ID" ]]; then
    release_remote_lock >/dev/null 2>&1 || true
  fi

  if command -v docker >/dev/null 2>&1; then
    if [[ "$TARGET" == "all" || "$TARGET" == "server" ]] \
        && [[ "$SERVER_IMAGE" =~ ^rocketmq-server:deploy-[a-f0-9]{24}$ ]]; then
      docker image rm "$SERVER_IMAGE" >/dev/null 2>&1 || true
    fi
    if [[ "$TARGET" == "all" || "$TARGET" == "web" ]] \
        && [[ "$WEB_IMAGE" =~ ^rocketmq-web:deploy-[a-f0-9]{24}$ ]]; then
      docker image rm "$WEB_IMAGE" >/dev/null 2>&1 || true
    fi
  fi

  if [[ -n "$LOCAL_TMP_DIR" ]] \
      && [[ -d "$LOCAL_TMP_DIR" ]] \
      && [[ "$(basename "$LOCAL_TMP_DIR")" == rocketmq-studio-deploy.* ]]; then
    rm -rf -- "$LOCAL_TMP_DIR"
  fi

  exit "$exit_code"
}

main() {
  if [[ "${1:-}" == "snapshot-registry" ]]; then
    [[ "$#" -eq 3 ]] \
      || err "用法: $0 snapshot-registry SOURCE_ABSOLUTE_PATH DESTINATION"
    command -v python3 >/dev/null 2>&1 || err "python3 未安装"
    snapshot_registry_path "$2" "$3"
    return
  fi

  load_config
  reset_internal_state
  configure "${1:-all}"
  trap cleanup EXIT

  printf '═══════════════════════════════════════════\n'
  printf '  RocketMQ Studio 安全部署\n'
  printf '  目标: %s | 远程: %s:%s\n' "$TARGET" "$REMOTE" "$REMOTE_PATH"
  printf '═══════════════════════════════════════════\n\n'

  check_local_prereqs
  prepare_local_temp
  if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
    snapshot_registry
  fi
  check_connectivity
  acquire_remote_lock

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
  release_remote_lock
  log "部署完成；未公开任何远程 HTTP 或后端端口"
}

main "$@"
