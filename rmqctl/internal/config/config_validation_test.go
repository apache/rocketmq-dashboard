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
package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func validContext() Context {
	return Context{
		Server:     "https://studio.example.com",
		Credential: CredentialRef{AccessKeyRef: "env:RMQ_AK", SecretKeyRef: "env:RMQ_SK"},
	}
}

// ─── Normalize ───────────────────────────────────────────────────────────────

func TestNormalizeCreatesTheContextMap(t *testing.T) {
	cfg := Config{CurrentContext: "prod"}
	Normalize(&cfg)
	if cfg.Contexts == nil {
		t.Fatal("Normalize must replace a nil context map with an empty one")
	}
}

// ─── Validate ────────────────────────────────────────────────────────────────

func TestValidateRejectsBlankContextNames(t *testing.T) {
	err := Validate(Config{Contexts: map[string]Context{"  ": validContext()}})
	if err == nil || !strings.Contains(err.Error(), "context name must not be empty") {
		t.Fatalf("blank context name must be rejected, got: %v", err)
	}
}

func TestValidateNamesTheFailingContext(t *testing.T) {
	broken := validContext()
	broken.Server = "   "
	err := Validate(Config{Contexts: map[string]Context{"canary": broken}})
	if err == nil || !strings.Contains(err.Error(), `context "canary"`) {
		t.Fatalf("validation errors must name the failing context, got: %v", err)
	}
	if err == nil || !strings.Contains(err.Error(), "server is required") {
		t.Fatalf("validation errors must name the failing field, got: %v", err)
	}
}

func TestValidateRequiresEachContextField(t *testing.T) {
	for name, mutate := range map[string]func(*Context){
		"server":                  func(c *Context) { c.Server = "" },
		"credential.accessKeyRef": func(c *Context) { c.Credential.AccessKeyRef = "  " },
		"credential.secretKeyRef": func(c *Context) { c.Credential.SecretKeyRef = "" },
	} {
		context := validContext()
		mutate(&context)
		err := Validate(Config{Contexts: map[string]Context{"prod": context}})
		if err == nil || !strings.Contains(err.Error(), name+" is required") {
			t.Fatalf("missing %s must be rejected by name, got: %v", name, err)
		}
	}
}

func TestValidateRequiresEnvReferences(t *testing.T) {
	context := validContext()
	context.Credential.AccessKeyRef = "RMQ_AK"
	err := Validate(Config{Contexts: map[string]Context{"prod": context}})
	if err == nil || !strings.Contains(err.Error(), "must be an env:NAME reference") {
		t.Fatalf("bare credential name must be rejected, got: %v", err)
	}
}

func TestValidateRequiresCurrentContextToExist(t *testing.T) {
	err := Validate(Config{
		CurrentContext: "ghost",
		Contexts:       map[string]Context{"prod": validContext()},
	})
	if err == nil || !strings.Contains(err.Error(), `currentContext "ghost" does not exist`) {
		t.Fatalf("dangling currentContext must be rejected, got: %v", err)
	}

	// an empty currentContext is fine: it only means nothing is selected yet
	if err := Validate(Config{Contexts: map[string]Context{"prod": validContext()}}); err != nil {
		t.Fatalf("empty currentContext must be valid, got: %v", err)
	}
}

// ─── EnvironmentReference ────────────────────────────────────────────────────

func TestEnvironmentReferenceAcceptsOnlyEnvColonNames(t *testing.T) {
	for _, valid := range []string{"env:RMQ_AK", "env:_private", "env:A_1"} {
		if _, err := EnvironmentReference(valid); err != nil {
			t.Fatalf("%q must be accepted, got: %v", valid, err)
		}
	}
	for _, invalid := range []string{"RMQ_AK", "env:", "env:bad-name", "env:9start", "file:/etc/x"} {
		if _, err := EnvironmentReference(invalid); err == nil {
			t.Fatalf("%q must be rejected", invalid)
		}
	}
}

// ─── Load ────────────────────────────────────────────────────────────────────

func TestLoadReturnsAnEmptyConfigForAbsentFiles(t *testing.T) {
	store := NewStore()
	cfg, err := store.Load(filepath.Join(t.TempDir(), "absent.yaml"))
	if err != nil {
		t.Fatalf("absent config must load as empty, got: %v", err)
	}
	if cfg.CurrentContext != "" || len(cfg.Contexts) != 0 {
		t.Fatalf("absent config must be empty, got %+v", cfg)
	}
}

func TestLoadRejectsNonRegularFiles(t *testing.T) {
	store := NewStore()
	// The exact message differs by platform (the read fails on Windows before
	// the regular-file check runs on Unix); the contract is the rejection.
	if _, err := store.Load(t.TempDir()); err == nil {
		t.Fatal("a directory must never load as a config file")
	}
}

func TestLoadRejectsInvalidYAML(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(path, []byte("contexts: ["), 0o600); err != nil {
		t.Fatal(err)
	}
	store := NewStore()
	if _, err := store.Load(path); err == nil {
		t.Fatal("malformed YAML must be rejected")
	}
}

func TestLoadRejectsInvalidConfigs(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(path, []byte("currentContext: ghost\ncontexts: {}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	store := NewStore()
	_, err := store.Load(path)
	if err == nil || !strings.Contains(err.Error(), "currentContext \"ghost\" does not exist") {
		t.Fatalf("invalid stored config must be rejected at load, got: %v", err)
	}
}

// ─── Save ────────────────────────────────────────────────────────────────────

func TestSaveRejectsInvalidConfigsWithoutTouchingDisk(t *testing.T) {
	directory := t.TempDir()
	if err := os.Chmod(directory, 0o700); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(directory, "config.yaml")

	store := NewStore()
	err := store.Save(path, Config{
		CurrentContext: "ghost",
		Contexts:       map[string]Context{"prod": validContext()},
	})
	if err == nil || !strings.Contains(err.Error(), `currentContext "ghost" does not exist`) {
		t.Fatalf("invalid config must be rejected at save, got: %v", err)
	}
	if _, statErr := os.Stat(path); !os.IsNotExist(statErr) {
		t.Fatalf("a rejected save must not create the file, stat: %v", statErr)
	}
}

func TestSaveReplacesAtomicallyWithoutTempLeftovers(t *testing.T) {
	directory := t.TempDir()
	if err := os.Chmod(directory, 0o700); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(directory, "config.yaml")
	store := NewStore()

	first := Config{Contexts: map[string]Context{"first": validContext()}}
	if err := store.Save(path, first); err != nil {
		t.Fatalf("first save: %v", err)
	}
	second := Config{CurrentContext: "second", Contexts: map[string]Context{"second": validContext()}}
	if err := store.Save(path, second); err != nil {
		t.Fatalf("second save: %v", err)
	}

	loaded, err := store.Load(path)
	if err != nil {
		t.Fatalf("reload after replace: %v", err)
	}
	if loaded.CurrentContext != "second" || len(loaded.Contexts) != 1 {
		t.Fatalf("the second save must fully replace the first, got %+v", loaded)
	}

	entries, err := os.ReadDir(directory)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), ".rmqctl-config-") {
			t.Fatalf("atomic replace must clean up its temp file, found %s", entry.Name())
		}
	}
}
