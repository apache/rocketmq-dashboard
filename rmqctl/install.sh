#!/usr/bin/env bash
#
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
#
# rmqctl install script — fetches a prebuilt binary from GitHub Releases.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/<owner>/<repo>/HEAD/rmqctl/install.sh | bash
#   curl -fsSL ... | bash -s -- --version v1.0.0 --bin-dir ~/.local/bin
#
# Environment overrides:
#   RMQCTL_VERSION   specific version tag (default: latest release)
#   RMQCTL_OS        target GOOS (default: auto-detect)
#   RMQCTL_ARCH      target GOARCH (default: auto-detect)
#   RMQCTL_BIN_DIR   install directory (default: /usr/local/bin, fallback ~/.local/bin)
#   RMQCTL_REPO      github owner/repo (default: apache/rocketmq-dashboard)
set -euo pipefail

REPO="${RMQCTL_REPO:-apache/rocketmq-dashboard}"
VERSION="${RMQCTL_VERSION:-}"
BIN_DIR="${RMQCTL_BIN_DIR:-}"
TARGET_OS="${RMQCTL_OS:-}"
TARGET_ARCH="${RMQCTL_ARCH:-}"

usage() {
  cat <<'EOF'
Usage: install.sh [options]

  --version <tag>     version to install (default: latest release)
  --bin-dir <path>    install directory (default: /usr/local/bin or ~/.local/bin)
  --os <goos>         override target OS (default: auto)
  --arch <goarch>     override target arch (default: auto)
  -h, --help          show this help
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --bin-dir) BIN_DIR="$2"; shift 2 ;;
    --os)      TARGET_OS="$2"; shift 2 ;;
    --arch)    TARGET_ARCH="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

detect_os() {
  if [ -n "$TARGET_OS" ]; then echo "$TARGET_OS"; return; fi
  case "$(uname -s)" in
    Darwin) echo darwin ;;
    Linux)  echo linux ;;
    MINGW*|MSYS*|CYGWIN*) echo windows ;;
    *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
  esac
}

detect_arch() {
  if [ -n "$TARGET_ARCH" ]; then echo "$TARGET_ARCH"; return; fi
  case "$(uname -m)" in
    x86_64|amd64) echo amd64 ;;
    arm64|aarch64) echo arm64 ;;
    *) echo "unsupported arch: $(uname -m)" >&2; exit 1 ;;
  esac
}

GOOS="$(detect_os)"
GOARCH="$(detect_arch)"

if [ -z "$VERSION" ]; then
  echo ">> detecting latest release for ${REPO}" >&2
  VERSION="$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
    | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' | head -n1)"
  if [ -z "$VERSION" ]; then
    echo "could not determine latest release; pass --version <tag>" >&2
    exit 1
  fi
fi

case "$VERSION" in
  v*) RELEASE_TAG="$VERSION"; ASSET_VERSION="${VERSION#v}" ;;
  *)  RELEASE_TAG="v${VERSION}"; ASSET_VERSION="$VERSION" ;;
esac

if [ -z "$BIN_DIR" ]; then
  if [ -w /usr/local/bin ]; then BIN_DIR=/usr/local/bin
  else BIN_DIR="${HOME}/.local/bin"; fi
fi

if [ "$GOOS" = "windows" ]; then
  BINARY="rmqctl.exe"
  ASSET="rmqctl_${ASSET_VERSION}_${GOOS}_${GOARCH}.zip"
else
  BINARY="rmqctl"
  ASSET="rmqctl_${ASSET_VERSION}_${GOOS}_${GOARCH}.tar.gz"
fi
URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/${ASSET}"

INSTALL_TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$INSTALL_TMP_DIR"' EXIT

echo ">> downloading ${ASSET}" >&2
curl -fsSL "$URL" -o "${INSTALL_TMP_DIR}/${ASSET}"

echo ">> verifying checksum" >&2
CHECKSUM_URL="${URL}.sha256"
curl -fsSL "$CHECKSUM_URL" -o "${INSTALL_TMP_DIR}/${ASSET}.sha256"
( cd "$INSTALL_TMP_DIR" && if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 -c "${ASSET}.sha256"
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c "${ASSET}.sha256"
  else
    echo "a SHA-256 verification tool (shasum or sha256sum) is required" >&2
    exit 1
  fi )

echo ">> extracting" >&2
if [ "$GOOS" = "windows" ]; then
  if ! command -v unzip >/dev/null 2>&1; then
    echo "unzip is required to extract the archive" >&2
    exit 1
  fi
  unzip -oq "${INSTALL_TMP_DIR}/${ASSET}" -d "$INSTALL_TMP_DIR"
else
  tar xzf "${INSTALL_TMP_DIR}/${ASSET}" -C "$INSTALL_TMP_DIR"
fi
SRC="${INSTALL_TMP_DIR}/rmqctl/${BINARY}"

if [ ! -f "$SRC" ]; then
  echo "binary not found in archive: $SRC" >&2
  exit 1
fi

mkdir -p "$BIN_DIR"
install -m 0755 "$SRC" "${BIN_DIR}/${BINARY}"

echo ">> installed ${BINARY} to ${BIN_DIR}"
if [ "$GOOS" != "windows" ]; then
  case ":${PATH}:" in
    *":${BIN_DIR}:"*) ;;
    *) echo "note: add ${BIN_DIR} to your PATH" ;;
  esac
fi
echo ">> run: ${BIN_DIR}/${BINARY} version"
