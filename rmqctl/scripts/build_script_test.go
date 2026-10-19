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
	"regexp"
	"testing"
)

// The executable regression in build_windows_test.go only runs on Windows, and CI has a single
// ubuntu-latest job, so it is always skipped there and the script itself would stay unprotected.
// This file carries no `_windows` suffix on purpose: it has to run on Linux. It pins the script
// text instead -- the native target must be resolved from GOHOSTOS/GOHOSTARCH, the resolution must
// fail closed, and both must happen before `go build` and before the optional `go test`. Otherwise
// a GOOS/GOARCH left over from a previous `make build-all` cross-compilation silently produces a
// foreign-architecture rmqctl.exe that cannot run on the build host.
func TestWindowsBuildScriptPinsHostTargetBeforeCompiling(t *testing.T) {
	content, err := os.ReadFile("build-windows.ps1")
	if err != nil {
		t.Fatal(err)
	}
	script := string(content)

	buildAt := regexp.MustCompile(`(?m)^go build `).FindStringIndex(script)
	if buildAt == nil {
		t.Fatal("build-windows.ps1 no longer invokes `go build`")
	}
	testAt := regexp.MustCompile(`(?m)^\s*go test -count=1 \./\.\.\.$`).FindStringIndex(script)
	if testAt == nil {
		t.Fatal("build-windows.ps1 no longer invokes `go test`")
	}

	for _, pin := range []struct{ what, pattern string }{
		{"host target lookup", `(?m)^.*go env -json GOHOSTOS GOHOSTARCH.*$`},
		{"fail-closed guard", `(?m)^\s*throw "Could not determine the native Go build target"$`},
		{"GOOS binding", `(?m)^\$env:GOOS = \$nativeTarget\.GOHOSTOS$`},
		{"GOARCH binding", `(?m)^\$env:GOARCH = \$nativeTarget\.GOHOSTARCH$`},
	} {
		at := regexp.MustCompile(pin.pattern).FindStringIndex(script)
		if at == nil {
			t.Errorf("build-windows.ps1 lost the %s matching %s", pin.what, pin.pattern)
			continue
		}
		if at[0] > buildAt[0] {
			t.Errorf("the %s at offset %d must precede `go build` at offset %d",
				pin.what, at[0], buildAt[0])
		}
		if at[0] > testAt[0] {
			t.Errorf("the %s at offset %d must precede `go test` at offset %d",
				pin.what, at[0], testAt[0])
		}
	}
}
