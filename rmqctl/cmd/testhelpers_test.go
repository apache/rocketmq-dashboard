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
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

// testAccessKey and testSecretKey are the credential values returned by
// testEnv for RMQ_TEST_AK / RMQ_TEST_SK. They are reused across
// catalog, config and MCP tests so assertions can reference stable values.
const (
	testAccessKey = "test-ak"
	testSecretKey = "test-sk"
)

// newTestConfigPath returns a path to a config.yaml inside a private temp
// directory (mode 0o700). It is the shared replacement for the former
// appTestConfigPath / configCommandTestPath duplicates.
func newTestConfigPath(t *testing.T) string {
	t.Helper()
	directory := t.TempDir()
	if err := os.Chmod(directory, 0o700); err != nil {
		t.Fatal(err)
	}
	return filepath.Join(directory, "config.yaml")
}

// testEnv mimics os.Getenv for the test credential pair so that
// config.Store can resolve env: references without touching the real
// environment.
func testEnv(name string) string {
	switch name {
	case "RMQ_TEST_AK":
		return testAccessKey
	case "RMQ_TEST_SK":
		return testSecretKey
	default:
		return ""
	}
}

// emptyEnv always returns the empty string. It is the convenience
// replacement for the repeated `func(string) string { return "" }` literal
// used by config tests that do not need credential resolution.
func emptyEnv(string) string { return "" }

// newTestConfig builds a single-context config.Config pointing at serverURL
// with the given instance suffix. The context uses env: credential references that
// testEnv can resolve.
func newTestConfig(serverURL, instanceSuffix string) config.Config {
	return config.Config{
		CurrentContext: "test",
		Contexts: map[string]config.Context{"test": {
			Server: serverURL, Cluster: "instance-" + instanceSuffix,
			Credential: config.CredentialRef{
				AccessKeyRef: "env:RMQ_TEST_AK", SecretKeyRef: "env:RMQ_TEST_SK",
			},
		}},
	}
}

// writeStudioSuccess writes a Studio-style success envelope
// {"code":200,"message":"success","data":<data>} as JSON to w. It is the
// shared replacement for the former writeStudioSuccess in app_test.go and
// the hand-rolled JSON in mcp_stdio_test.go catalog-style responses.
func writeStudioSuccess(t *testing.T, w http.ResponseWriter, data any) {
	t.Helper()
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(map[string]any{
		"code": 200, "message": "success", "data": data,
	}); err != nil {
		t.Errorf("write response: %v", err)
	}
}

func executeTestApp(t *testing.T, client *http.Client, serverURL string, arguments ...string) (string, string, int) {
	return executeTestAppWithInstance(t, client, serverURL, "dev", arguments...)
}

func executeTestAppWithInstance(t *testing.T, client *http.Client, serverURL, instanceSuffix string, arguments ...string) (string, string, int) {
	t.Helper()
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.HTTP = client
	app.Store.Getenv = testEnv
	app.confirm = stubConfirmReject
	configPath := newTestConfigPath(t)
	if err := app.Store.Save(configPath, newTestConfig(serverURL, instanceSuffix)); err != nil {
		t.Fatal(err)
	}
	arguments = append([]string{"--config", configPath}, arguments...)
	exitCode := app.Execute(arguments)
	return stdout.String(), stderr.String(), exitCode
}

// executeTestAppWithStdin runs the app with a custom confirm callback that
// reads from the provided stdin content, simulating an interactive terminal.
func executeTestAppWithStdin(t *testing.T, client *http.Client, serverURL, instanceSuffix, stdinContent string, arguments ...string) (string, string, int) {
	t.Helper()
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.HTTP = client
	app.Store.Getenv = testEnv
	app.confirm = func(in io.Reader, out io.Writer, commandPath, riskLevel, server string) error {
		fmt.Fprintf(out, "WARNING: %q is a %s operation.\n", commandPath, riskLevel)
		fmt.Fprintf(out, "Arguments will be sent to %s. Type \"yes\" to continue: ", server)
		reader := bufio.NewReader(in)
		answer, err := reader.ReadString('\n')
		if err != nil {
			return types.NewCLIError(types.CodeCommandFailed,
				fmt.Sprintf("confirmation for %q failed: %v", commandPath, err),
				"Re-run the command and confirm interactively, or pass --yes to skip the prompt.")
		}
		answer = strings.TrimSpace(strings.ToLower(answer))
		if answer != "y" && answer != "yes" {
			return types.NewCLIError(types.CodeCommandFailed,
				fmt.Sprintf("execution of %q cancelled", commandPath),
				"Re-run the command and type yes, or pass --yes to skip the prompt.")
		}
		return nil
	}
	configPath := newTestConfigPath(t)
	if err := app.Store.Save(configPath, newTestConfig(serverURL, instanceSuffix)); err != nil {
		t.Fatal(err)
	}
	app.In = strings.NewReader(stdinContent)
	arguments = append([]string{"--config", configPath}, arguments...)
	exitCode := app.Execute(arguments)
	return stdout.String(), stderr.String(), exitCode
}

// stubConfirmReject mimics the production non-TTY rejection: it always returns
// the "requires interactive confirmation" error without reading stdin.
func stubConfirmReject(in io.Reader, out io.Writer, commandPath, riskLevel, server string) error {
	return types.NewCLIError(
		types.CodeCommandFailed,
		fmt.Sprintf("%q is a %s operation and requires interactive confirmation", commandPath, riskLevel),
		"Re-run the command in an interactive terminal, or pass --yes to skip the prompt.")
}
