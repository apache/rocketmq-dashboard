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
	"strings"
	"testing"
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

const testConfigYAML = `currentContext: prod
contexts:
  prod:
    server: http://studio:8080
    credential:
      accessKeyRef: env:RMQ_TEST_ACCESS_KEY
      secretKeyRef: env:RMQ_TEST_SECRET_KEY
`

func writeRuntimeConfig(t *testing.T, content string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatalf("write config: %v", err)
	}
	return path
}

func runtimeWith(options *option, getenv func(string) string) commandRuntime {
	return commandRuntime{
		store:   config.Store{Getenv: getenv},
		options: options,
	}
}

// ─── resolveTarget ───────────────────────────────────────────────────────────

func TestResolveTargetRequiresAnExplicitInstanceID(t *testing.T) {
	for _, blank := range []string{"", "   "} {
		runtime := runtimeWith(&option{instanceID: blank, configPath: writeRuntimeConfig(t, testConfigYAML)},
			func(string) string { return "" })
		_, err := runtime.resolveTarget()
		if err == nil {
			t.Fatalf("blank instance id %q must be rejected", blank)
		}
		if !strings.Contains(err.Error(), "--instance-id is required") {
			t.Fatalf("error must name the flag, got: %v", err)
		}
		// decision 7: the identifier is never defaulted from the context
		cliErr, ok := err.(*types.CLIError)
		if !ok {
			t.Fatalf("missing instance id must be a CLIError, got %T", err)
		}
		if !strings.Contains(cliErr.Hint, "never defaults") {
			t.Fatalf("hint must state that rmqctl never defaults the instance id, got: %q", cliErr.Hint)
		}
	}
}

func TestResolveTargetAssemblesTheConfiguredTarget(t *testing.T) {
	runtime := runtimeWith(
		&option{
			instanceID: "  my-instance  ",
			configPath: writeRuntimeConfig(t, testConfigYAML),
			timeout:    5 * time.Second,
		},
		func(name string) string {
			switch name {
			case "RMQ_TEST_ACCESS_KEY":
				return "ak-1"
			case "RMQ_TEST_SECRET_KEY":
				return " sk-1 "
			default:
				return ""
			}
		})

	target, err := runtime.resolveTarget()
	if err != nil {
		t.Fatalf("valid runtime must resolve, got: %v", err)
	}

	if target.Server != "http://studio:8080" {
		t.Fatalf("server must come from the active context, got %q", target.Server)
	}
	if target.InstanceID != "my-instance" {
		t.Fatalf("instance id must be trimmed, got %q", target.InstanceID)
	}
	if target.Credential.AccessKey != "ak-1" || target.Credential.SecretKey != "sk-1" {
		t.Fatalf("credentials must resolve from the referenced environment, got %+v", target.Credential)
	}
	if target.Timeout != 5*time.Second {
		t.Fatalf("timeout must be carried onto the target, got %v", target.Timeout)
	}
}

func TestResolveTargetHonorsAnExplicitContext(t *testing.T) {
	content := `currentContext: prod
contexts:
  prod:
    server: http://prod:8080
    credential: {accessKeyRef: env:AK, secretKeyRef: env:SK}
  canary:
    server: http://canary:8080
    credential: {accessKeyRef: env:AK, secretKeyRef: env:SK}
`
	runtime := runtimeWith(
		&option{
			instanceID: "my-instance",
			context:    "canary",
			configPath: writeRuntimeConfig(t, content),
		},
		func(string) string { return "x" })

	target, err := runtime.resolveTarget()
	if err != nil {
		t.Fatalf("explicit context must resolve, got: %v", err)
	}
	if target.Server != "http://canary:8080" {
		t.Fatalf("explicit context must win over currentContext, got %q", target.Server)
	}
}

func TestResolveTargetPropagatesContextErrors(t *testing.T) {
	// a missing config file loads as the empty config, which selects no context
	runtime := runtimeWith(
		&option{instanceID: "my-instance", configPath: filepath.Join(t.TempDir(), "absent.yaml")},
		func(string) string { return "" })
	_, err := runtime.resolveTarget()
	if err == nil || !strings.Contains(err.Error(), "no current context is selected") {
		t.Fatalf("absent current context must be rejected, got: %v", err)
	}

	runtime = runtimeWith(
		&option{instanceID: "my-instance", context: "ghost", configPath: writeRuntimeConfig(t, testConfigYAML)},
		func(string) string { return "" })
	_, err = runtime.resolveTarget()
	if err == nil || !strings.Contains(err.Error(), `context "ghost" does not exist`) {
		t.Fatalf("unknown context must be rejected by name, got: %v", err)
	}
}

func TestResolveTargetRejectsMissingCredentials(t *testing.T) {
	runtime := runtimeWith(
		&option{instanceID: "my-instance", configPath: writeRuntimeConfig(t, testConfigYAML)},
		func(string) string { return "" })

	_, err := runtime.resolveTarget()
	if err == nil {
		t.Fatal("empty credential environment must be rejected")
	}
	if !strings.Contains(err.Error(), "RMQ_TEST_ACCESS_KEY") {
		t.Fatalf("error must name the empty environment variable, got: %v", err)
	}
	cliErr, ok := err.(*types.CLIError)
	if !ok {
		t.Fatalf("credential failure must be a CLIError, got %T", err)
	}
	if !strings.Contains(cliErr.Hint, "Set the access-key and secret-key environment variables") {
		t.Fatalf("hint must explain how to fix the credentials, got: %q", cliErr.Hint)
	}
}

// ─── targetServer ────────────────────────────────────────────────────────────

func TestTargetServerReturnsTheConfiguredServer(t *testing.T) {
	runtime := runtimeWith(
		&option{configPath: writeRuntimeConfig(t, testConfigYAML)},
		func(string) string { return "" })

	if got := runtime.targetServer(); got != "http://studio:8080" {
		t.Fatalf("confirmation prompts must show the configured server, got %q", got)
	}
}

func TestTargetServerFallsBackToTheGenericLabel(t *testing.T) {
	// absent config -> no context -> generic label
	runtime := runtimeWith(
		&option{configPath: filepath.Join(t.TempDir(), "absent.yaml")},
		func(string) string { return "" })
	if got := runtime.targetServer(); got != "Studio Server" {
		t.Fatalf("absent config must fall back to the generic label, got %q", got)
	}

	// a context without a server must not show an empty URL in the prompt
	content := `currentContext: blank
contexts:
  blank:
    server: ""
    credential: {accessKeyRef: env:AK, secretKeyRef: env:SK}
`
	runtime = runtimeWith(
		&option{configPath: writeRuntimeConfig(t, content)},
		func(string) string { return "" })
	if got := runtime.targetServer(); got != "Studio Server" {
		t.Fatalf("empty server must fall back to the generic label, got %q", got)
	}
}
