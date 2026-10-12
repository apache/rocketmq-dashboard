/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cmd

import (
	"os"
	"path/filepath"
	"regexp"
	"testing"
)

// The binary's reported version comes from three build entry points: `make build` injects the
// Makefile's VERSION, a plain `go build` falls back to CLIVersion, and the Windows script injects
// its own default. They had drifted (the script still defaulted to 1.0.0 after the CLI moved to
// 3.0.0), so a Windows build reported a version no other build did. Pin them together: a release
// bump has to update all three or this fails.
func TestVersionMetadataAgreesAcrossBuildEntryPoints(t *testing.T) {
	makefile := readRepoFile(t, "Makefile")
	script := readRepoFile(t, filepath.Join("scripts", "build-windows.ps1"))

	makefileVersion := firstCapture(t, `(?m)^VERSION \?= (\S+)\s*$`, makefile, "Makefile VERSION")
	scriptVersion := firstCapture(t, `(?m)^\s*\[string\]\$Version = "([^"]+)"`, script,
		"build-windows.ps1 $Version default")

	if CLIVersion != makefileVersion {
		t.Errorf("cmd.CLIVersion = %q but Makefile VERSION ?= %q", CLIVersion, makefileVersion)
	}
	if scriptVersion != CLIVersion {
		t.Errorf("build-windows.ps1 default = %q but cmd.CLIVersion = %q", scriptVersion, CLIVersion)
	}
}

func readRepoFile(t *testing.T, relative string) string {
	t.Helper()
	path := filepath.Join("..", relative)
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("reading %s: %v", path, err)
	}
	return string(content)
}

func firstCapture(t *testing.T, pattern, content, what string) string {
	t.Helper()
	matches := regexp.MustCompile(pattern).FindStringSubmatch(content)
	if matches == nil {
		t.Fatalf("could not read %s", what)
	}
	return matches[1]
}
