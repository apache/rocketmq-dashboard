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

# Package a single rmqctl binary into a release archive (tar.gz or zip)
# Bundles LICENSE, NOTICE, the completion script, and the third-party legal materials that must pass the check.
#
# Usage: package-release.sh <binary> <os> <arch> <version> <legal_dir> <package_dir>
#
# Outputs:
#   <package_dir>/rmqctl-<os>-<arch>-<version>.tar.gz   (unix)
#   <package_dir>/rmqctl-<os>-<arch>-<version>.zip      (windows)
#   <package_dir>/rmqctl-<os>-<arch>-<version>.tar.gz.sha256
#   <package_dir>/rmqctl-<os>-<arch>-<version>.zip.sha256

set -euo pipefail

if [ "$#" -ne 6 ]; then
    echo "Usage: $0 <binary> <os> <arch> <version> <legal_dir> <package_dir>" >&2
    exit 1
fi

binary="$1"
os="$2"
arch="$3"
version="$4"
legal_dir="$5"
package_dir="$6"

# Resolve project root (two levels up from this script: scripts/ -> rmqctl/ -> root)
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/../.." && pwd)"

# Verify the materials for the actual binary byte by byte before packaging; an empty directory, a stale manifest, or a silent omission is not allowed.
if [ -z "$legal_dir" ] || [ ! -d "$legal_dir" ]; then
    echo "Missing third-party legal materials; run make license-binary first" >&2
    exit 1
fi
for value in "$os" "$arch" "$version"; do
    [[ "$value" =~ ^[a-zA-Z0-9._+-]+$ ]] || { echo "Invalid packaging argument" >&2; exit 1; }
done
binary="$(cd "$(dirname "$binary")" && pwd)/$(basename "$binary")"
legal_dir="$(cd "$legal_dir" && pwd)"
mkdir -p "$package_dir"
package_dir="$(cd "$package_dir" && pwd)"
"${GO:-go}" run "${script_dir}/license-binary.go" -root "$project_root" \
    -go "${GO:-go}" -binary "$binary" -output "$legal_dir" -os "$os" -arch "$arch" -check

# Staging directory for archive contents
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

# Binary name inside the archive
if [ "$os" = "windows" ]; then
    bin_name="rmqctl.exe"
else
    bin_name="rmqctl"
fi

# Copy binary
cp "$binary" "${stage}/${bin_name}"

# Use the binary-specific LICENSE/NOTICE; do not mix in the web icon attribution from the source package.
cp "${legal_dir}/LICENSE" "${stage}/LICENSE"
cp "${legal_dir}/NOTICE" "${stage}/NOTICE"

# Copy completion scripts if they exist (produced by `make completion`)
completion_dir="${script_dir}/../bin/completion"
if [ -d "$completion_dir" ]; then
    mkdir -p "${stage}/completion"
    cp "$completion_dir"/* "${stage}/completion/" 2>/dev/null || true
fi

# The license manifest and the full license texts must ship with the package.
cp -r "$legal_dir" "${stage}/legal"

# Build archive
archive_name="rmqctl-${os}-${arch}-${version}"
cd "$stage"

if [ "$os" = "windows" ]; then
    archive="${package_dir}/${archive_name}.zip"
    # Updating an existing zip archive keeps the old files, so refuse to reuse an existing target package.
    [ ! -e "$archive" ] || { echo "Target archive already exists: $archive" >&2; exit 1; }
    # Use zip if available; fall back to python zipfile for portability
    if command -v zip >/dev/null 2>&1; then
        zip -r -q "$archive" .
    else
        python3 - "$archive" <<'PY'
import zipfile, os, sys
with zipfile.ZipFile(sys.argv[1], 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk('.'):
        for f in files:
            path = os.path.join(root, f)
            z.write(path, os.path.relpath(path, '.'))
PY
    fi
else
    archive="${package_dir}/${archive_name}.tar.gz"
    tar czf "$archive" .
fi

# Generate SHA-256 checksum
cd "$package_dir"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$(basename "$archive")" > "$(basename "$archive").sha256"
else
    shasum -a 256 "$(basename "$archive")" > "$(basename "$archive").sha256"
fi

echo "Created $(basename "$archive") + $(basename "$archive").sha256"
