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

<#
.SYNOPSIS
    Native Windows build for rmqctl (equivalent to `make build` on Unix).

.DESCRIPTION
    Compiles rmqctl.exe with the local Go toolchain on Windows, injecting the
    same version metadata as the Makefile LDFLAGS. This is the native-build
    counterpart to the cross-compilation done by `make build-all`.

    Go must be on PATH (or installed at C:\go). If the local Go is older than
    the version required by go.mod, set GOTOOLCHAIN=auto so Go fetches the
    right toolchain automatically.

.PARAMETER Version
    Value injected into cmd.CLIVersion (default: 1.0.0).

.PARAMETER Output
    Output binary path (default: bin\rmqctl.exe).

.PARAMETER RunTests
    Run `go test -count=1 ./...` after a successful build.

.EXAMPLE
    .\scripts\build-windows.ps1
    .\scripts\build-windows.ps1 -Version 1.2.3 -RunTests
#>
param(
    [string]$Version = "1.0.0",
    [string]$Output = "bin\rmqctl.exe",
    [switch]$RunTests
)

$ErrorActionPreference = "Stop"

# Resolve module root: this script lives in scripts/, module root is one level up.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$moduleRoot = Split-Path -Parent $scriptDir
Set-Location $moduleRoot

# Locate the Go toolchain.
if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
    if (Test-Path "C:\go\bin\go.exe") {
        $env:Path = "C:\go\bin;" + $env:Path
    } else {
        throw "Go not found on PATH. Install Go (>= the version in go.mod) and retry."
    }
}

Write-Host ">> $(go version)"
Write-Host ">> module root: $moduleRoot"

# Version metadata, mirroring the Makefile LDFLAGS.
$gitCommit = "unknown"
try {
    $resolved = (git rev-parse --short HEAD 2>$null)
    if ($resolved) { $gitCommit = $resolved }
} catch { }
$buildDate = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$pkg = "github.com/apache/rocketmq-dashboard/rmqctl/cmd"
$ldflags = "-s -w -X $pkg.CLIVersion=$Version -X $pkg.GitCommit=$gitCommit -X $pkg.BuildDate=$buildDate"

$env:CGO_ENABLED = "0"

Write-Host ">> building $Output (VERSION=$Version COMMIT=$gitCommit DATE=$buildDate)"
go build -ldflags "$ldflags" -o "$Output" main.go
if ($LASTEXITCODE -ne 0) { throw "go build failed (exit $LASTEXITCODE)" }

$item = Get-Item "$Output"
Write-Host ">> built $($item.FullName) ($($item.Length) bytes)"

if ($RunTests) {
    Write-Host ">> running go test -count=1 ./..."
    go test -count=1 ./...
    if ($LASTEXITCODE -ne 0) { throw "go test failed (exit $LASTEXITCODE)" }
}

Write-Host ">> done. verify with: .\$Output version"
