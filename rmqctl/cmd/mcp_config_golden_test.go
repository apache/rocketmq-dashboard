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
	"encoding/json"
	"reflect"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
)

// studioJavaMcpSnippet is the MCP client configuration that the Studio server
// writes to <workspace>/mcp.json for a conversation bound to instance "x"
// (RmqctlWorkspace.mcpJson), reproduced here in the compact form asserted by
// RmqctlWorkspaceTest.MCP_CONFIG_SHAPE.
//
// This is the cross-language contract test for that snippet: the server
// generates the file itself instead of shelling out to `rmqctl mcp config`, so
// nothing but this test notices if the two drift apart. If it fails, the hosted
// agent stops finding its tools and the only symptom is an empty tool list in
// the run's init frame.
//
// The comparison is on parsed values rather than bytes because the two sides
// order object keys differently for reasons neither can change: Go's
// encoding/json sorts map keys (so "args" precedes "command"), while Jackson
// preserves the insertion order of the ObjectNode. JSON object key order is not
// significant, the argv order inside "args" is, and that is asserted separately.
//
// Note the timeout reads "1m0s" although the flag was passed as 60s: mcp.go
// echoes runtime.options.timeout.String(), and time.Duration canonicalises
// rather than repeating what the user typed. RmqctlWorkspace reproduces that
// canonicalisation, which is the only reason the two snippets can be identical
// at all — do not "simplify" either side to 60s.
const studioJavaMcpSnippet = `{"mcpServers":{"rocketmq-studio":{"command":"rmqctl","args":["mcp","stdio","--config","/tmp/c.yaml","--instance-id","x","--timeout","1m0s"]}}}`

// TestMCPConfigMatchesStudioGeneratedSnippet runs the real command line the
// Studio documentation tells a user to run when copying a conversation's tool
// access into their own agent, and asserts it emits exactly what the server
// generates for itself.
func TestMCPConfigMatchesStudioGeneratedSnippet(t *testing.T) {
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv

	// Flags after the subcommand: --config, --instance-id and --timeout are
	// persistent flags on the root command, and this is the form both the docs
	// and RmqctlWorkspace rely on. No config file is read, so /tmp/c.yaml need
	// not exist.
	exitCode := app.Execute([]string{
		"mcp", "config", "--instance-id", "x", "--config", "/tmp/c.yaml", "--timeout", "60s",
	})
	if exitCode != 0 {
		t.Fatalf("exit code = %d, want 0; stderr=%s", exitCode, stderr)
	}
	if stderr.Len() != 0 {
		t.Fatalf("unexpected stderr: %s", stderr)
	}

	var emitted, expected any
	if err := json.Unmarshal(stdout.Bytes(), &emitted); err != nil {
		t.Fatalf("decode mcp config output: %v (%s)", err, stdout)
	}
	if err := json.Unmarshal([]byte(studioJavaMcpSnippet), &expected); err != nil {
		t.Fatalf("decode the Studio snippet: %v", err)
	}
	if !reflect.DeepEqual(emitted, expected) {
		t.Fatalf("mcp config drifted from the Studio-generated snippet:\n got:  %s\n want: %s",
			stdout, studioJavaMcpSnippet)
	}

	server := emitted.(map[string]any)["mcpServers"].(map[string]any)
	if len(server) != 1 {
		t.Fatalf("expected exactly one MCP server, got %d: %s", len(server), stdout)
	}
	// "rocketmq-studio" is the name the timeline projector expects in the init frame and the name
	// claude's --allowedTools flag is built from, so it is part of the contract too.
	entry, ok := server["rocketmq-studio"].(map[string]any)
	if !ok {
		t.Fatalf("the MCP server must be named rocketmq-studio: %s", stdout)
	}
	if entry["command"] != "rmqctl" {
		t.Fatalf("command = %v, want rmqctl", entry["command"])
	}
	// No env block: the server exports RMQ_AI_ACCESS_KEY / RMQ_AI_SECRET_KEY into
	// the claude process and the MCP child inherits them, so a snippet that
	// carried credentials would put them in a file on disk.
	if _, found := entry["env"]; found {
		t.Fatalf("the snippet must not carry an env block: %s", stdout)
	}
	wantArgs := []any{"mcp", "stdio", "--config", "/tmp/c.yaml", "--instance-id", "x", "--timeout", "1m0s"}
	if !reflect.DeepEqual(entry["args"], wantArgs) {
		t.Fatalf("args = %v, want %v (argv order is the contract)", entry["args"], wantArgs)
	}
}

// TestMCPConfigOmitsDefaultTimeout documents the one intentional difference
// between the two sides: `mcp config` leaves --timeout out when it still holds the
// 30s default, while the server always passes its configured timeout explicitly.
// Passing the default is accepted by `mcp stdio` either way, so the server keeps
// the argv shape stable instead of branching on a default value it does not own.
func TestMCPConfigOmitsDefaultTimeout(t *testing.T) {
	app := &App{}
	runtime := commandRuntime{options: &option{
		configPath: "/tmp/c.yaml", instanceID: "x",
		output: "table", timeout: studio.DefaultTimeout,
	}}
	cmd := app.newMCPConfigCommand(runtime)
	stdout := &bytes.Buffer{}
	cmd.SetOut(stdout)
	cmd.SetArgs([]string{})

	if err := cmd.Execute(); err != nil {
		t.Fatal(err)
	}

	var emitted map[string]any
	if err := json.Unmarshal(stdout.Bytes(), &emitted); err != nil {
		t.Fatalf("decode output: %v (%s)", err, stdout)
	}
	entry := emitted["mcpServers"].(map[string]any)["rocketmq-studio"].(map[string]any)
	args := entry["args"].([]any)
	for _, arg := range args {
		if arg == "--timeout" {
			t.Fatalf("the default timeout must be omitted, got %v", args)
		}
	}
	wantArgs := []any{"mcp", "stdio", "--config", "/tmp/c.yaml", "--instance-id", "x"}
	if !reflect.DeepEqual(args, wantArgs) {
		t.Fatalf("args = %v, want %v", args, wantArgs)
	}
}
