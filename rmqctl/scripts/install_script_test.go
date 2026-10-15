// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
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

// install.sh parses its options under `set -u`: a value-less --version used to die on a bare
// unbound-variable error before any usage text, and `--version --os linux` swallowed `--os` as
// the version and went on downloading. Both must be rejected with the flag named, before any
// network access. The fake curl on PATH makes any download attempt loud.
func TestInstallScriptRejectsValuelessOptionsBeforeAnyDownload(t *testing.T) {
	fakeBin := t.TempDir()
	if err := os.WriteFile(filepath.Join(fakeBin, "curl"), []byte(
		"#!/bin/sh\necho 'curl must not run in this test' >&2\nexit 99\n"), 0o755); err != nil {
		t.Fatal(err)
	}

	for _, tc := range []struct {
		name string
		args []string
		flag string
	}{
		{"version without a value", []string{"--version"}, "--version"},
		{"bin-dir without a value", []string{"--bin-dir"}, "--bin-dir"},
		{"os without a value", []string{"--os"}, "--os"},
		{"arch without a value", []string{"--arch"}, "--arch"},
		{"version swallowing the next option", []string{"--version", "--os", "linux"}, "--version"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			cmd := exec.Command("bash", append([]string{"../install.sh"}, tc.args...)...)
			cmd.Env = append(os.Environ(),
				"PATH="+fakeBin+string(os.PathListSeparator)+os.Getenv("PATH"))
			out, err := cmd.CombinedOutput()
			text := string(out)
			if err == nil {
				t.Fatalf("install.sh %v unexpectedly succeeded:\n%s", tc.args, text)
			}
			if !strings.Contains(text, "option "+tc.flag+" requires a value") {
				t.Errorf("the error must name %s and print the usage; got:\n%s", tc.flag, text)
			}
			if strings.Contains(text, "unbound variable") {
				t.Errorf("a value-less option must reach the usage error, not a shell trap:\n%s", text)
			}
			if strings.Contains(text, ">> downloading") || strings.Contains(text, ">> detecting") {
				t.Errorf("validation must fail before any network access:\n%s", text)
			}
		})
	}
}

// A value that merely starts with a dash is a value: only the installer's own option words are
// rejected as swallowed flags, so a directory like "-tools/bin" still installs where asked.
func TestInstallScriptKeepsValuesThatStartWithADash(t *testing.T) {
	fakeBin := t.TempDir()
	if err := os.WriteFile(filepath.Join(fakeBin, "curl"), []byte(
		"#!/bin/sh\nexit 99\n"), 0o755); err != nil {
		t.Fatal(err)
	}

	cmd := exec.Command("bash", "../install.sh", "--version", "1.0.0", "--bin-dir", "-tools/bin")
	cmd.Env = append(os.Environ(),
		"PATH="+fakeBin+string(os.PathListSeparator)+os.Getenv("PATH"))
	out, _ := cmd.CombinedOutput()

	if !strings.Contains(string(out), ">> downloading") {
		t.Errorf("the dashed value must survive option parsing and reach the download; got:\n%s", out)
	}
}
