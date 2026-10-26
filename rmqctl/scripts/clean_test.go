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
	"strings"
	"testing"
)

func TestCleanKeepsDefaultBuildDirectoryAsOnePath(t *testing.T) {
	root := t.TempDir()
	checkout := filepath.Join(root, "project checkout")
	target := filepath.Join(checkout, "bin")
	// Without quoting, the absolute default splits into these two unrelated paths.
	unrelated := []string{filepath.Join(root, "project"), filepath.Join(checkout, "checkout", "bin")}
	writeCleanFixture(t, target, unrelated)

	runMakeClean(t, checkout)

	assertCleanFixture(t, target, unrelated)
}

func TestCleanKeepsExplicitBuildDirectoryAsOnePath(t *testing.T) {
	for _, test := range []struct {
		name      string
		target    string
		unrelated []string
	}{
		{name: "spaces", target: "build output", unrelated: []string{"build", "output"}},
		{name: "glob", target: "build[1]", unrelated: []string{"build1"}},
		{name: "leading dash", target: "-build", unrelated: []string{"bin"}},
		{name: "ordinary", target: "bin", unrelated: []string{"other"}},
	} {
		t.Run(test.name, func(t *testing.T) {
			checkout := t.TempDir()
			target := filepath.Join(checkout, test.target)
			unrelated := make([]string, len(test.unrelated))
			for index, name := range test.unrelated {
				unrelated[index] = filepath.Join(checkout, name)
			}
			writeCleanFixture(t, target, unrelated)

			runMakeClean(t, checkout, "BUILD_DIR="+test.target)

			assertCleanFixture(t, target, unrelated)
		})
	}
}

func TestCleanWithEmptyBuildDirectoryPreservesFiles(t *testing.T) {
	checkout := t.TempDir()
	marker := filepath.Join(checkout, "sentinel")
	if err := os.WriteFile(marker, []byte("preserve"), 0o600); err != nil {
		t.Fatal(err)
	}

	runMakeClean(t, checkout, "BUILD_DIR=")

	if content, err := os.ReadFile(marker); err != nil || string(content) != "preserve" {
		t.Fatalf("empty build directory changed files: content %q, error %v", content, err)
	}
}

func writeCleanFixture(t *testing.T, target string, unrelated []string) {
	t.Helper()
	for _, directory := range append([]string{target}, unrelated...) {
		if err := os.MkdirAll(directory, 0o700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(directory, "sentinel"), []byte("preserve"), 0o600); err != nil {
			t.Fatal(err)
		}
	}
}

func runMakeClean(t *testing.T, checkout string, arguments ...string) {
	t.Helper()
	makeCommand, err := exec.LookPath("make")
	if err != nil {
		t.Skip("make is unavailable")
	}
	makefile, err := filepath.Abs("../Makefile")
	if err != nil {
		t.Fatal(err)
	}
	command := exec.Command(makeCommand, append([]string{"-f", makefile, "clean"}, arguments...)...)
	command.Dir = checkout
	for _, entry := range os.Environ() {
		key, _, _ := strings.Cut(entry, "=")
		switch key {
		case "MAKEFLAGS", "MFLAGS", "GNUMAKEFLAGS", "MAKELEVEL", "MAKEOVERRIDES", "MAKEFILES", "BUILD_DIR":
			continue
		}
		command.Env = append(command.Env, entry)
	}
	if output, err := command.CombinedOutput(); err != nil {
		t.Fatalf("make clean failed: %v\n%s", err, output)
	}
}

func assertCleanFixture(t *testing.T, target string, unrelated []string) {
	t.Helper()
	if _, err := os.Stat(target); !os.IsNotExist(err) {
		t.Errorf("configured build directory remains: %s (stat error: %v)", target, err)
	}
	for _, directory := range unrelated {
		content, err := os.ReadFile(filepath.Join(directory, "sentinel"))
		if err != nil || string(content) != "preserve" {
			t.Errorf("unrelated directory changed: %s (content: %q, error: %v)", directory, content, err)
		}
	}
}
