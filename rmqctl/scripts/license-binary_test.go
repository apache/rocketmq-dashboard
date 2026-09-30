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
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestBinaryLicensePackagingTest(t *testing.T) {
	_, source, _, _ := runtime.Caller(0)
	root := filepath.Clean(filepath.Join(filepath.Dir(source), "../.."))
	goCommand := filepath.Join(runtime.GOROOT(), "bin/go")
	for _, target := range []string{"linux", "windows"} {
		t.Run(target, func(t *testing.T) {
			dir := t.TempDir()
			binary := filepath.Join(dir, "rmqctl")
			build := exec.Command(goCommand, "build", "-mod=readonly", "-o", binary, "main.go")
			build.Dir = filepath.Join(root, "rmqctl")
			build.Env = append(os.Environ(), "CGO_ENABLED=0", "GOOS="+target, "GOARCH=amd64")
			if output, err := build.CombinedOutput(); err != nil {
				t.Fatalf("targeted packaging build failed: %v\n%s", err, output)
			}
			legal := filepath.Join(dir, "legal")
			generate := exec.Command(goCommand, "run", filepath.Join(root, "rmqctl/scripts/license-binary.go"),
				"-go", goCommand, "-root", root, "-binary", binary, "-output", legal)
			if output, err := generate.CombinedOutput(); err != nil {
				t.Fatalf("license generation failed: %v\n%s", err, output)
			}
			pack := func(osName, material string, success bool) {
				t.Helper()
				cmd := exec.Command("bash", filepath.Join(root, "rmqctl/scripts/package-release.sh"),
					binary, osName, "amd64", "fixture", material, filepath.Join(dir, "packages"))
				cmd.Env = append(os.Environ(), "GO="+goCommand)
				output, err := cmd.CombinedOutput()
				if (err == nil) != success {
					t.Fatalf("packaging gate returned the wrong result success=%v err=%v\n%s", success, err, output)
				}
			}
			pack(target, legal, true)
			contents := map[string]string{}
			archive := filepath.Join(dir, "packages", "rmqctl-"+target+"-amd64-fixture")
			if target == "windows" {
				z, err := zip.OpenReader(archive + ".zip")
				if err != nil {
					t.Fatal(err)
				}
				defer z.Close()
				for _, file := range z.File {
					r, err := file.Open()
					if err != nil {
						t.Fatal(err)
					}
					data, err := io.ReadAll(r)
					r.Close()
					if err != nil {
						t.Fatal(err)
					}
					contents[strings.TrimPrefix(file.Name, "./")] = string(data)
				}
			} else {
				f, err := os.Open(archive + ".tar.gz")
				if err != nil {
					t.Fatal(err)
				}
				defer f.Close()
				gz, err := gzip.NewReader(f)
				if err != nil {
					t.Fatal(err)
				}
				defer gz.Close()
				tr := tar.NewReader(gz)
				for {
					header, err := tr.Next()
					if err == io.EOF {
						break
					}
					if err != nil {
						t.Fatal(err)
					}
					data, err := io.ReadAll(tr)
					if err != nil {
						t.Fatal(err)
					}
					contents[strings.TrimPrefix(header.Name, "./")] = string(data)
				}
			}
			if !strings.Contains(contents["legal/licenses/github.com/spf13/pflag@v1.0.9/LICENSE.txt"], "Alex Ogier") {
				t.Fatal("package is missing the original pflag BSD license")
			}
			if !strings.Contains(contents["legal/licenses/github.com/mark3labs/mcp-go@v0.58.0/LICENSE.txt"], "2024 Anthropic, PBC") {
				t.Fatal("package is missing the original mcp-go MIT license")
			}
			if !strings.Contains(contents["NOTICE"], "The Apache Software Foundation") || strings.Contains(contents["LICENSE"], "LobeHub") {
				t.Fatal("the binary must carry the ASF NOTICE and must not mix in the web source-package attribution")
			}
			if target == "windows" && !strings.Contains(contents["LICENSE"], "mousetrap") {
				t.Fatal("the Windows package is missing a platform-specific dependency license")
			}
			pack("darwin", legal, false)
			pack(target, "", false)
			if err := os.WriteFile(filepath.Join(legal, "NOTICE"), []byte("tampered"), 0644); err != nil {
				t.Fatal(err)
			}
			pack(target, legal, false)
		})
	}
}
