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

package main

import (
	"os"
	"os/exec"
	"strings"
	"testing"
)

// `make build` is the native Unix entry point (build-windows.ps1 names it as the counterpart of
// the Windows script), but its recipe did not bind GOOS/GOARCH: a cross target left in the
// environment is inherited by the compile and by the license commands that inspect the result.
// `GOOS=windows make build` therefore writes a Windows PE to the native output path and then
// fails the license gate with `license gate: exec: "go": executable file not found in %PATH%`,
// and `GOOS=linux GOARCH=arm64` leaves an arm64 binary at bin/rmqctl. #6461 pinned GOHOSTOS/
// GOHOSTARCH for build-windows.ps1 for exactly this reason. This test drives both Makefile
// targets with `make -n` under an exported cross target and expects the host binding on the
// native compile and on every license command, whose helper is a host program by nature.
func TestMakeBuildBindsHostTargetTest(t *testing.T) {
	makeCommand, err := exec.LookPath("make")
	if err != nil {
		t.Skip("make is not installed")
	}
	goCommand, err := exec.LookPath("go")
	if err != nil {
		t.Skip("go is not installed")
	}
	hostOS := goEnv(t, goCommand, "GOHOSTOS")
	hostArch := goEnv(t, goCommand, "GOHOSTARCH")

	for _, target := range []string{"build", "build-all"} {
		t.Run(target, func(t *testing.T) {
			command := exec.Command(makeCommand, "-C", "..", "-n", target)
			command.Env = append(os.Environ(), "GOOS=windows", "GOARCH=amd64")
			output, err := command.CombinedOutput()
			if err != nil {
				t.Fatalf("make -C .. -n %s failed: %v\n%s", target, err, output)
			}
			checked := 0
			for _, line := range strings.Split(string(output), "\n") {
				// build-all cross-compiles its binaries per command; every license command it
				// runs - and everything the native build target runs - has to stay native.
				if !strings.Contains(line, "license-binary.go") &&
					!(target == "build" && strings.Contains(line, "go build ")) {
					continue
				}
				checked++
				if !strings.Contains(line, "GOOS="+hostOS) || !strings.Contains(line, "GOARCH="+hostArch) {
					t.Errorf("make %s inherits the caller's GOOS/GOARCH instead of binding %s/%s: %s",
						target, hostOS, hostArch, strings.TrimSpace(line))
				}
			}
			if checked == 0 {
				t.Fatalf("make -n %s printed no command to check", target)
			}
		})
	}
}

func goEnv(t *testing.T, goCommand string, name string) string {
	t.Helper()
	output, err := exec.Command(goCommand, "env", name).Output()
	if err != nil {
		t.Fatalf("go env %s failed: %v", name, err)
	}
	value := strings.TrimSpace(string(output))
	if value == "" {
		t.Fatalf("go env %s returned an empty value", name)
	}
	return value
}
