// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestPackageReleasePreservesExistingTarball(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("package-release.sh requires a Unix shell")
	}
	if _, err := exec.LookPath("bash"); err != nil {
		t.Skip("bash is unavailable")
	}

	root := t.TempDir()
	legal := filepath.Join(root, "legal")
	completion := filepath.Join(root, "completion")
	packageDir := filepath.Join(root, "packages")
	for _, dir := range []string{legal, completion, packageDir} {
		if err := os.MkdirAll(dir, 0755); err != nil {
			t.Fatal(err)
		}
	}
	for name, content := range map[string]string{
		"binary":                 "binary contents",
		"legal/LICENSE":          "license contents",
		"legal/NOTICE":           "notice contents",
		"completion/rmqctl.bash": "completion contents",
		"fake-go":                "#!/bin/sh\nexit 0\n",
	} {
		mode := os.FileMode(0644)
		if name == "fake-go" {
			mode = 0755
		}
		if err := os.WriteFile(filepath.Join(root, name), []byte(content), mode); err != nil {
			t.Fatal(err)
		}
	}

	archive := filepath.Join(packageDir, "rmqctl-linux-amd64-1.0.0.tar.gz")
	original := []byte("existing release artifact must survive")
	if err := os.WriteFile(archive, original, 0644); err != nil {
		t.Fatal(err)
	}
	cmd := exec.Command("bash", "package-release.sh", filepath.Join(root, "binary"),
		"linux", "amd64", "1.0.0", legal, packageDir)
	cmd.Env = append(os.Environ(), "GO="+filepath.Join(root, "fake-go"), "COMPLETION_DIR="+completion)
	output, err := cmd.CombinedOutput()
	if err == nil || !strings.Contains(string(output), "Target archive already exists") {
		t.Fatalf("expected existing archive rejection, got err=%v, output=%s", err, output)
	}
	contents, err := os.ReadFile(archive)
	if err != nil {
		t.Fatal(err)
	}
	if string(contents) != string(original) {
		t.Fatalf("existing release artifact was overwritten: %q", contents)
	}
}

func TestPackageDirectoryPreservesExistingOutput(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("rmqctl's Makefile requires a Unix shell")
	}
	if _, err := exec.LookPath("make"); err != nil {
		t.Skip("make is unavailable")
	}

	packageDir := t.TempDir()
	marker := filepath.Join(packageDir, "previous-release.tar.gz")
	original := []byte("previous release")
	if err := os.WriteFile(marker, original, 0644); err != nil {
		t.Fatal(err)
	}
	cmd := exec.Command("make", "-C", "..", "prepare-package-dir", "PACKAGE_DIR="+packageDir)
	output, err := cmd.CombinedOutput()
	if err == nil || !strings.Contains(string(output), "Package directory already contains files") {
		t.Fatalf("expected populated package directory rejection, got err=%v, output=%s", err, output)
	}
	contents, err := os.ReadFile(marker)
	if err != nil || string(contents) != string(original) {
		t.Fatalf("previous release changed: content=%q, err=%v", contents, err)
	}
}
