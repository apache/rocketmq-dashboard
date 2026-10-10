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
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/spf13/cobra"
)

// The golden test (mcp_config_golden_test.go) pins the snippet rmqctl and the
// Studio server must agree on. These tests pin the command surface around it:
// the adapter registration, the stdio adapter's target resolution, and the
// config adapter's remaining flag surface (--name, --command, --context,
// structured output).

func newMCPTestRuntime(options *option) commandRuntime {
	return commandRuntime{
		store:   config.Store{Getenv: func(string) string { return "" }},
		options: options,
	}
}

func runMCPCommand(t *testing.T, cmd *cobra.Command, args ...string) (string, error) {
	t.Helper()
	cmd.SetArgs(args)
	cmd.SetOut(new(bytes.Buffer))
	cmd.SetErr(new(bytes.Buffer))
	err := cmd.Execute()
	out, _ := cmd.OutOrStdout().(*bytes.Buffer)
	if out == nil {
		return "", err
	}
	return out.String(), err
}

func TestMCPCommandRegistersBothAdapters(t *testing.T) {
	app := &App{}
	root := app.newMCPCommand(newMCPTestRuntime(&option{}))

	var names []string
	for _, sub := range root.Commands() {
		names = append(names, sub.Name())
	}
	if !strings.Contains(strings.Join(names, " "), "stdio") ||
		!strings.Contains(strings.Join(names, " "), "config") {
		t.Fatalf("mcp must register the stdio and config adapters, got %v", names)
	}
}

func TestMCPStdioResolvesTheTargetBeforeAnythingElse(t *testing.T) {
	app := &App{}
	// no --instance-id: the adapter must fail on target resolution before it
	// ever touches the network or the stdio transport
	cmd := app.newMCPStdioCommand(newMCPTestRuntime(&option{}))

	_, err := runMCPCommand(t, cmd)
	if err == nil || !strings.Contains(err.Error(), "--instance-id is required") {
		t.Fatalf("stdio without an instance id must fail on target resolution, got: %v", err)
	}
}

func TestMCPConfigHonorsNameAndCommandOverrides(t *testing.T) {
	app := &App{}
	// the default timeout keeps the snippet minimal: any other value is echoed
	cmd := app.newMCPConfigCommand(newMCPTestRuntime(&option{timeout: studio.DefaultTimeout}))

	out, err := runMCPCommand(t, cmd,
		"--name", "my-studio", "--command", "/usr/local/bin/rmqctl")
	if err != nil {
		t.Fatalf("config with name and command overrides must succeed, got: %v", err)
	}

	if !strings.Contains(out, `"my-studio"`) {
		t.Fatalf("snippet must use the overridden server name, got: %s", out)
	}
	if !strings.Contains(out, `"/usr/local/bin/rmqctl"`) {
		t.Fatalf("snippet must use the overridden executable, got: %s", out)
	}
	if !strings.Contains(out, `"mcp",`) || !strings.Contains(out, `"stdio"`) {
		t.Fatalf("snippet must keep the stdio adapter args, got: %s", out)
	}
	if strings.Contains(out, "--config") || strings.Contains(out, "--context") ||
		strings.Contains(out, "--instance-id") || strings.Contains(out, "--timeout") {
		t.Fatalf("default options must leave the minimal stdio args, got: %s", out)
	}
}

func TestMCPConfigCarriesTheContext(t *testing.T) {
	app := &App{}
	cmd := app.newMCPConfigCommand(newMCPTestRuntime(&option{context: "canary"}))

	out, err := runMCPCommand(t, cmd)
	if err != nil {
		t.Fatalf("config with a context must succeed, got: %v", err)
	}

	if !strings.Contains(out, `"--context"`) || !strings.Contains(out, `"canary"`) {
		t.Fatalf("snippet must carry the context, got: %s", out)
	}
}

func TestMCPConfigRendersYAMLWhenAsked(t *testing.T) {
	app := &App{}
	cmd := app.newMCPConfigCommand(newMCPTestRuntime(&option{output: "yaml"}))

	out, err := runMCPCommand(t, cmd, "--name", "yaml-studio")
	if err != nil {
		t.Fatalf("config with yaml output must succeed, got: %v", err)
	}

	if !strings.Contains(out, "mcpServers:") || !strings.Contains(out, "yaml-studio:") {
		t.Fatalf("yaml rendering must carry the mcpServers mapping, got: %s", out)
	}
	if strings.Contains(out, "{") {
		t.Fatalf("yaml rendering must not fall back to JSON, got: %s", out)
	}
}
