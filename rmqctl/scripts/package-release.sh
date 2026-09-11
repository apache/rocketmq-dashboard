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
# with LICENSE, NOTICE, completion scripts, and optional legal bundle.
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

# Copy LICENSE and NOTICE from project root
cp "${project_root}/LICENSE" "${stage}/LICENSE"
if [ -f "${project_root}/NOTICE" ]; then
    cp "${project_root}/NOTICE" "${stage}/NOTICE"
fi

# Copy completion scripts if they exist (produced by `make completion`)
completion_dir="${script_dir}/../bin/completion"
if [ -d "$completion_dir" ]; then
    mkdir -p "${stage}/completion"
    cp "$completion_dir"/* "${stage}/completion/" 2>/dev/null || true
fi

# Copy legal bundle if it exists (produced by license-binary, currently unused)
if [ -n "$legal_dir" ] && [ -d "$legal_dir" ]; then
    cp -r "$legal_dir" "${stage}/legal"
fi

# Build archive
archive_name="rmqctl-${os}-${arch}-${version}"
cd "$stage"

if [ "$os" = "windows" ]; then
    archive="${package_dir}/${archive_name}.zip"
    # Use zip if available; fall back to python zipfile for portability
    if command -v zip >/dev/null 2>&1; then
        zip -r -q "$archive" .
    else
        python3 -c "
import zipfile, os
with zipfile.ZipFile('${archive}', 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk('.'):
        for f in files:
            path = os.path.join(root, f)
            z.write(path, os.path.relpath(path, '.'))
"
    fi
else
    archive="${package_dir}/${archive_name}.tar.gz"
    tar czf "$archive" .
fi

# Generate SHA-256 checksum
cd "$package_dir"
shasum -a 256 "$(basename "$archive")" > "$(basename "$archive").sha256"

echo "Created $(basename "$archive") + $(basename "$archive").sha256"
