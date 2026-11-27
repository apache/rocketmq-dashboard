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
	"fmt"
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
		"--server", "https://studio.example.com",
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
	if contextValue.Server != "https://studio.example.com" {
		t.Fatalf("server = %q, want the configured URL", contextValue.Server)
	}
	if contextValue.Credential.AccessKeyRef != "env:RMQ_PROD_AK" ||
		contextValue.Credential.SecretKeyRef != "env:RMQ_PROD_SK" {
		t.Fatalf("credential refs = %#v", contextValue.Credential)
	}
	raw, err := os.ReadFile(configPath)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(raw), "cluster") {
		t.Fatalf("config still stores a cluster key: %s", raw)
	}
	if strings.Contains(string(raw), "token-from-env") || strings.Contains(string(raw), "secret-value") {
		t.Fatalf("config contains credential material: %s", raw)
	}
}

// TestConfigSetContextRejectsRemovedClusterFlag verifies the context no longer
// accepts --cluster: the instance identifier is a per-invocation global flag.
func TestConfigSetContextRejectsRemovedClusterFlag(t *testing.T) {
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
	if exitCode == 0 {
		t.Fatal("set-context must reject the removed --cluster flag")
	}
	if !strings.Contains(stderr.String(), "unknown flag") {
		t.Fatalf("stderr = %q, want an unknown flag error", stderr.String())
	}
}

func TestConfigSetContextRejectsServerCredentialsBeforeSaving(t *testing.T) {
	for _, server := range []string{
		"https://example-user:example-password@studio.example.com",
		"https://example-user@studio.example.com",
		"https://:example-password@studio.example.com",
		"https://@studio.example.com",
		"https://example-user:example-password%zz@studio.example.com",
		"https://studio.example.com/?accessKey=example-password",
		"https://studio.example.com/#secretKey=example-password",
	} {
		for _, existing := range []bool{false, true} {
			t.Run(fmt.Sprintf("%s/existing=%t", server, existing), func(t *testing.T) {
				stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
				app := NewApp(stdout, stderr)
				app.Store.Getenv = emptyEnv
				configPath := newTestConfigPath(t)
				var before []byte
				if existing {
					cfg := config.Config{CurrentContext: "prod", Contexts: map[string]config.Context{
						"prod": {Server: "https://studio.example.com", Credential: config.CredentialRef{
							AccessKeyRef: "env:RMQ_AK", SecretKeyRef: "env:RMQ_SK",
						}},
					}}
					if err := app.Store.Save(configPath, cfg); err != nil {
						t.Fatal(err)
					}
					var err error
					before, err = os.ReadFile(configPath)
					if err != nil {
						t.Fatal(err)
					}
				}
				args := []string{"--config", configPath, "config", "set-context", "prod", "--server", server}
				if !existing {
					args = append(args, "--access-key-env", "RMQ_AK", "--secret-key-env", "RMQ_SK")
				}
				if exitCode := app.Execute(args); exitCode == 0 {
					t.Fatal("set-context accepted server URL credentials")
				}
				if stdout.Len() != 0 || !strings.Contains(stderr.String(), "INVALID_ARGUMENT") || !strings.Contains(stderr.String(), "--server") {
					t.Fatalf("stdout=%q stderr=%q, want an argument error without success output", stdout, stderr)
				}
				if strings.Contains(stderr.String(), "example-password") || strings.Contains(stderr.String(), "example-user") {
					t.Fatal("error output disclosed URL credentials")
				}
				after, err := os.ReadFile(configPath)
				if existing {
					if err != nil || !bytes.Equal(before, after) {
						t.Fatalf("rejected update changed the existing config: %v", err)
					}
				} else if !os.IsNotExist(err) {
					t.Fatalf("rejected creation wrote config: %v", err)
				}
			})
		}
	}
}

func TestConfigSetContextRejectsRelativeServerURLBeforeSaving(t *testing.T) {
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	configPath := newTestConfigPath(t)

	exitCode := app.Execute([]string{
		"--config", configPath, "config", "set-context", "prod",
		"--server", "studio.example.com",
		"--access-key-env", "RMQ_AK", "--secret-key-env", "RMQ_SK",
	})

	if exitCode == 0 {
		t.Fatal("set-context accepted a server value without a URL scheme")
	}
	if stdout.Len() != 0 || !strings.Contains(stderr.String(), "INVALID_ARGUMENT") {
		t.Fatalf("stdout=%q stderr=%q, want an argument error without success output", stdout, stderr)
	}
	if _, err := os.Stat(configPath); !os.IsNotExist(err) {
		t.Fatalf("rejected creation wrote config: %v", err)
	}
}

func TestConfigSetContextCanRemoveStoredServerCredentials(t *testing.T) {
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	configPath := newTestConfigPath(t)
	if err := app.Store.Save(configPath, config.Config{CurrentContext: "prod", Contexts: map[string]config.Context{
		"prod": {Server: "https://example-user:example-password@studio.example.com", Credential: config.CredentialRef{
			AccessKeyRef: "env:RMQ_AK", SecretKeyRef: "env:RMQ_SK",
		}},
		"dev": {Server: "http://localhost:8888", Credential: config.CredentialRef{
			AccessKeyRef: "env:DEV_AK", SecretKeyRef: "env:DEV_SK",
		}},
	}}); err != nil {
		t.Fatal(err)
	}
	if exitCode := app.Execute([]string{"--config", configPath, "config", "set-context", "prod",
		"--server", "https://studio.example.com/studio"}); exitCode != 0 {
		t.Fatalf("repair failed: %s", stderr)
	}
	cfg, err := app.Store.Load(configPath)
	if err != nil {
		t.Fatal(err)
	}
	contextValue := cfg.Contexts["prod"]
	if contextValue.Server != "https://studio.example.com/studio" ||
		contextValue.Credential.AccessKeyRef != "env:RMQ_AK" || contextValue.Credential.SecretKeyRef != "env:RMQ_SK" {
		t.Fatalf("repair did not preserve context fields: %#v", contextValue)
	}
	if cfg.CurrentContext != "prod" || len(cfg.Contexts) != 2 || cfg.Contexts["dev"].Server != "http://localhost:8888" {
		t.Fatalf("repair changed unrelated configuration: %#v", cfg)
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
				Server:     "https://studio.example.com",
				Credential: config.CredentialRef{AccessKeyRef: "env:RMQ_PROD_AK", SecretKeyRef: "env:RMQ_PROD_SK"},
			},
			"dev": {
				Server:     "https://studio.dev.example.com",
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
				Server:     "https://studio.example.com",
				Credential: config.CredentialRef{AccessKeyRef: "env:RMQ_PROD_AK", SecretKeyRef: "env:RMQ_PROD_SK"},
			},
			"dev": {
				Server:     "https://studio.dev.example.com",
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
	if strings.Contains(output, "CLUSTER") || strings.Contains(output, "instance-prod") {
		t.Fatalf("stdout = %q, get-contexts must not render a CLUSTER column", output)
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
			Server:     "https://studio.example.com",
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
