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
	"bytes"
	"os"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
)

func TestConfigSetContextStoresOnlyEnvironmentReferences(t *testing.T) {
	stdout := &bytes.Buffer{}
	stderr := &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	configPath := newTestConfigPath(t)
	exitCode := app.Execute([]string{
		"--config", configPath, "config", "set-context", "prod",
		"--server", "https://studio.example.com", "--cluster", "instance-prod",
		"--access-key-env", "RMQ_PROD_AK", "--secret-key-env", "RMQ_PROD_SK",
	})
	if exitCode != 0 {
		t.Fatalf("exit code = %d, want 0; stderr=%s", exitCode, stderr.String())
	}
	if !strings.Contains(stdout.String(), "updated context prod") {
		t.Fatalf("stdout = %q, want context update", stdout.String())
	}
	cfg, err := config.NewStore().Load(configPath)
	if err != nil {
		t.Fatal(err)
	}
	contextValue := cfg.Contexts["prod"]
	if contextValue.Credential.AccessKeyRef != "env:RMQ_PROD_AK" ||
		contextValue.Credential.SecretKeyRef != "env:RMQ_PROD_SK" {
		t.Fatalf("credential refs = %#v", contextValue.Credential)
	}
	raw, err := os.ReadFile(configPath)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(raw), "token-from-env") || strings.Contains(string(raw), "secret-value") {
		t.Fatalf("config contains credential material: %s", raw)
	}
}

func TestConfigDeleteContextRemovesContextAndClearsCurrent(t *testing.T) {
	stdout := &bytes.Buffer{}
	stderr := &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	configPath := newTestConfigPath(t)
	if err := app.Store.Save(configPath, config.Config{
		CurrentContext: "prod",
		Contexts: map[string]config.Context{
			"prod": {
				Server: "https://studio.example.com", Cluster: "instance-prod",
				Credential: config.CredentialRef{AccessKeyRef: "env:RMQ_PROD_AK", SecretKeyRef: "env:RMQ_PROD_SK"},
			},
			"dev": {
				Server: "https://studio.dev.example.com", Cluster: "instance-dev",
				Credential: config.CredentialRef{AccessKeyRef: "env:RMQ_DEV_AK", SecretKeyRef: "env:RMQ_DEV_SK"},
			},
		},
	}); err != nil {
		t.Fatal(err)
	}

	exitCode := app.Execute([]string{"--config", configPath, "config", "delete-context", "prod"})
	if exitCode != 0 {
		t.Fatalf("exit code = %d, want 0; stderr=%s", exitCode, stderr.String())
	}
	if !strings.Contains(stdout.String(), "deleted context prod") {
		t.Fatalf("stdout = %q, want delete confirmation", stdout.String())
	}

	cfg, err := config.NewStore().Load(configPath)
	if err != nil {
		t.Fatal(err)
	}
	if _, exists := cfg.Contexts["prod"]; exists {
		t.Fatalf("prod context still present after delete")
	}
	if _, exists := cfg.Contexts["dev"]; !exists {
		t.Fatalf("dev context should remain after deleting prod")
	}
	if cfg.CurrentContext != "" {
		t.Fatalf("currentContext = %q, want empty after deleting current context", cfg.CurrentContext)
	}
}

func TestConfigGetContextsListsAllContexts(t *testing.T) {
	stdout := &bytes.Buffer{}
	stderr := &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	configPath := newTestConfigPath(t)
	if err := app.Store.Save(configPath, config.Config{
		CurrentContext: "prod",
		Contexts: map[string]config.Context{
			"prod": {
				Server: "https://studio.example.com", Cluster: "instance-prod",
				Credential: config.CredentialRef{AccessKeyRef: "env:RMQ_PROD_AK", SecretKeyRef: "env:RMQ_PROD_SK"},
			},
			"dev": {
				Server: "https://studio.dev.example.com", Cluster: "instance-dev",
				Credential: config.CredentialRef{AccessKeyRef: "env:RMQ_DEV_AK", SecretKeyRef: "env:RMQ_DEV_SK"},
			},
		},
	}); err != nil {
		t.Fatal(err)
	}

	exitCode := app.Execute([]string{"--config", configPath, "config", "get-contexts"})
	if exitCode != 0 {
		t.Fatalf("exit code = %d, want 0; stderr=%s", exitCode, stderr.String())
	}
	output := stdout.String()
	if !strings.Contains(output, "prod") || !strings.Contains(output, "dev") {
		t.Fatalf("stdout = %q, want both prod and dev contexts listed", output)
	}
}

func TestConfigCurrentContextJSONOutput(t *testing.T) {
	stdout := &bytes.Buffer{}
	stderr := &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	configPath := newTestConfigPath(t)
	if err := app.Store.Save(configPath, config.Config{
		CurrentContext: "prod",
		Contexts: map[string]config.Context{"prod": {
			Server: "https://studio.example.com", Cluster: "instance-prod",
			Credential: config.CredentialRef{AccessKeyRef: "env:RMQ_PROD_AK", SecretKeyRef: "env:RMQ_PROD_SK"},
		}},
	}); err != nil {
		t.Fatal(err)
	}

	exitCode := app.Execute([]string{"--config", configPath, "--output", "json", "config", "current-context"})
	if exitCode != 0 {
		t.Fatalf("exit code = %d, want 0; stderr=%s", exitCode, stderr.String())
	}
	if !strings.Contains(stdout.String(), `"currentContext": "prod"`) {
		t.Fatalf("stdout = %q, want JSON with currentContext prod", stdout.String())
	}
}
