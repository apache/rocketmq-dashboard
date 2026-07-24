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
	"path/filepath"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/tools/rmq/internal/config"
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

func TestRunUsesFreshRootForEveryInvocation(t *testing.T) {
	t.Run("output", func(t *testing.T) {
		var firstOut, firstErr bytes.Buffer
		if code := run([]string{"version", "--output", "json"}, &firstOut, &firstErr); code != 0 {
			t.Fatalf("first run = %d, stderr = %q", code, firstErr.String())
		}

		var secondOut, secondErr bytes.Buffer
		if code := run([]string{"version"}, &secondOut, &secondErr); code != 0 {
			t.Fatalf("second run = %d, stderr = %q", code, secondErr.String())
		}
		if !strings.HasPrefix(secondOut.String(), "VERSION  COMMIT") {
			t.Fatalf("second stdout inherited JSON output: %q", secondOut.String())
		}
	})

	t.Run("config", func(t *testing.T) {
		dir := t.TempDir()
		envPath := filepath.Join(dir, "environment.yaml")
		explicitPath := filepath.Join(dir, "explicit.yaml")
		cfg := config.Default()
		cfg.Contexts["environment"] = cfg.Contexts["default"]
		if err := config.UseContext(&cfg, "environment"); err != nil {
			t.Fatal(err)
		}
		if err := config.Save(envPath, cfg, false); err != nil {
			t.Fatal(err)
		}
		t.Setenv("RMQCTL_CONFIG", envPath)

		var firstOut, firstErr bytes.Buffer
		if code := run([]string{"--config", explicitPath, "config", "init", "--force"}, &firstOut, &firstErr); code != 0 {
			t.Fatalf("first run = %d, stderr = %q", code, firstErr.String())
		}

		var secondOut, secondErr bytes.Buffer
		if code := run([]string{"config", "current-context"}, &secondOut, &secondErr); code != 0 {
			t.Fatalf("second run = %d, stderr = %q", code, secondErr.String())
		}
		if secondOut.String() != "environment\n" {
			t.Fatalf("second stdout inherited explicit config path: %q", secondOut.String())
		}
	})

	t.Run("force", func(t *testing.T) {
		dir := t.TempDir()
		firstPath := filepath.Join(dir, "first.yaml")
		existingPath := filepath.Join(dir, "existing.yaml")
		if err := config.Save(existingPath, config.Default(), false); err != nil {
			t.Fatal(err)
		}

		var firstOut, firstErr bytes.Buffer
		if code := run([]string{"--config", firstPath, "config", "init", "--force"}, &firstOut, &firstErr); code != 0 {
			t.Fatalf("first run = %d, stderr = %q", code, firstErr.String())
		}

		var secondOut, secondErr bytes.Buffer
		if code := run([]string{"--config", existingPath, "config", "init"}, &secondOut, &secondErr); code == 0 {
			t.Fatal("second run inherited --force")
		}
		if !strings.Contains(secondErr.String(), "config already exists") {
			t.Fatalf("second stderr = %q", secondErr.String())
		}
	})
}
