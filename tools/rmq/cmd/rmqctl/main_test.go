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
	"bytes"
	"strings"
	"testing"
)

func TestRunSeparatesSuccessAndErrorStreams(t *testing.T) {
	var stdout, stderr bytes.Buffer
	if code := run([]string{"version"}, &stdout, &stderr); code != 0 {
		t.Fatalf("run(version) = %d, stderr = %q", code, stderr.String())
	}
	if stdout.Len() == 0 || stderr.Len() != 0 {
		t.Fatalf("run(version) stdout = %q, stderr = %q", stdout.String(), stderr.String())
	}

	stdout.Reset()
	stderr.Reset()
	if code := run([]string{"definitely-not-a-command"}, &stdout, &stderr); code == 0 {
		t.Fatal("run(unknown) returned success")
	}
	if stdout.Len() != 0 || stderr.Len() == 0 {
		t.Fatalf("run(unknown) stdout = %q, stderr = %q", stdout.String(), stderr.String())
	}
}

func TestRunWithoutArgumentsPrintsUsageAndSucceeds(t *testing.T) {
	var stdout, stderr bytes.Buffer
	if code := run(nil, &stdout, &stderr); code != 0 {
		t.Fatalf("run(nil) = %d, stderr = %q", code, stderr.String())
	}
	if !strings.Contains(stdout.String(), "Usage:") {
		t.Fatalf("stdout = %q, want usage", stdout.String())
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q", stderr.String())
	}
}

func TestRunInvalidCompletionWritesOnlyError(t *testing.T) {
	var stdout, stderr bytes.Buffer
	if code := run([]string{"completion", "tcsh"}, &stdout, &stderr); code == 0 {
		t.Fatal("run(invalid completion) returned success")
	}
	if stdout.Len() != 0 {
		t.Fatalf("stdout = %q", stdout.String())
	}
	if !strings.Contains(stderr.String(), `unsupported shell "tcsh"`) {
		t.Fatalf("stderr = %q", stderr.String())
	}
	if strings.Contains(stderr.String(), "Usage:") {
		t.Fatalf("stderr contains usage: %q", stderr.String())
	}
}

func TestRunUsageErrorIsNonzero(t *testing.T) {
	var stdout, stderr bytes.Buffer
	if code := run([]string{"config", "set"}, &stdout, &stderr); code == 0 {
		t.Fatal("run(usage error) returned success")
	}
	if stdout.Len() != 0 || stderr.Len() == 0 {
		t.Fatalf("stdout = %q, stderr = %q", stdout.String(), stderr.String())
	}
}
