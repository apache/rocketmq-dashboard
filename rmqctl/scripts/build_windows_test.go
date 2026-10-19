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

func TestWindowsNativeBuildIgnoresCrossCompilationTarget(t *testing.T) {
	if runtime.GOOS != "windows" {
		t.Skip("native Windows build script")
	}
	shell, err := exec.LookPath("pwsh")
	if err != nil {
		shell, err = exec.LookPath("powershell")
	}
	if err != nil {
		t.Skip("PowerShell is unavailable")
	}
	goTool, err := exec.LookPath("go")
	if err != nil {
		t.Fatal(err)
	}
	script, err := os.ReadFile("build-windows.ps1")
	if err != nil {
		t.Fatal(err)
	}
	fixture := filepath.Join(t.TempDir(), "构建 files")
	if err := os.MkdirAll(filepath.Join(fixture, "scripts"), 0o700); err != nil {
		t.Fatal(err)
	}
	files := map[string]string{
		"go.mod":       "module buildfixture\n\ngo 1.20\n",
		"main.go":      "package main\nimport (\"fmt\"; \"runtime\")\nfunc main() { fmt.Print(runtime.GOOS + \"/\" + runtime.GOARCH + \" 构建完成\") }\n",
		"main_test.go": "package main\nimport (\"os\"; \"testing\")\nfunc TestNativeExecution(t *testing.T) { if err := os.WriteFile(\"tests-ran\", []byte(\"yes\"), 0600); err != nil { t.Fatal(err) } }\n",
		filepath.Join("scripts", "build-windows.ps1"): string(script),
	}
	for name, content := range files {
		if err := os.WriteFile(filepath.Join(fixture, name), []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	t.Setenv("PATH", filepath.Dir(goTool)+string(os.PathListSeparator)+os.Getenv("PATH"))
	t.Setenv("GOOS", "linux")
	t.Setenv("GOARCH", "arm64")
	command := exec.Command(shell, "-NoProfile", "-File", filepath.Join(fixture, "scripts", "build-windows.ps1"),
		"-Output", filepath.Join("bin", "本机构建.exe"), "-RunTests")
	if output, err := command.CombinedOutput(); err != nil {
		t.Fatalf("native build failed: %v\n%s", err, output)
	}
	output, err := exec.Command(filepath.Join(fixture, "bin", "本机构建.exe")).CombinedOutput()
	if err != nil {
		t.Fatalf("built executable cannot run: %v\n%s", err, output)
	}
	if got, want := strings.TrimSpace(string(output)), runtime.GOOS+"/"+runtime.GOARCH+" 构建完成"; got != want {
		t.Fatalf("built executable output = %q, want %q", got, want)
	}
	if _, err := os.Stat(filepath.Join(fixture, "tests-ran")); err != nil {
		t.Fatalf("optional tests did not run: %v", err)
	}
}
