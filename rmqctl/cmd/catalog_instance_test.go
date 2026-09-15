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
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
	"github.com/spf13/cobra"
)

// executeSyntheticTool wires a synthetic catalog tool into a root command that
// mirrors the production global flag set, runs it against a fresh test server,
// and returns the observed tool call request plus stderr/exit error.
func executeSyntheticTool(
	t *testing.T,
	tool toolcatalog.Tool,
	args ...string,
) (types.ToolCallRequest, string, error) {
	t.Helper()
	observed := make(chan types.ToolCallRequest, 1)
	httpServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		if r.Header.Get("x-rmq-instance-id") != "instance-x" {
			t.Errorf("x-rmq-instance-id = %q, want instance-x", r.Header.Get("x-rmq-instance-id"))
		}
		observed <- request
		writeStudioSuccess(t, w, map[string]any{"items": []any{}})
	}))
	defer httpServer.Close()

	opts := &option{output: "json", timeout: time.Second}
	root := &cobra.Command{Use: "rmqctl", SilenceUsage: true, SilenceErrors: true}
	root.PersistentFlags().StringVar(&opts.context, "context", "", "")
	root.PersistentFlags().StringVar(&opts.configPath, "config", "", "")
	root.PersistentFlags().StringVar(&opts.instanceID, "instance-id", "", "")
	root.PersistentFlags().DurationVar(&opts.timeout, "timeout", time.Second, "")
	root.PersistentFlags().BoolVarP(&opts.yes, "yes", "y", false, "")

	configPath := newTestConfigPath(t)
	store := config.Store{Getenv: testEnv, HomeDir: config.NewStore().HomeDir}
	if err := store.Save(configPath, newTestConfig(httpServer.URL)); err != nil {
		t.Fatal(err)
	}
	runtime := commandRuntime{
		client:  studio.NewClient(httpServer.Client()),
		store:   store,
		options: opts,
		confirm: stubConfirmReject,
	}
	toolCmd, err := newToolCommand(runtime, tool)
	if err != nil {
		t.Fatal(err)
	}
	root.AddCommand(toolCmd)
	stderr := &bytes.Buffer{}
	root.SetOut(&bytes.Buffer{})
	root.SetErr(stderr)
	root.SetArgs(append([]string{
		"--config", configPath, "--instance-id", "instance-x",
		tool.CLI.Verb,
	}, args...))
	if err := root.Execute(); err != nil {
		return types.ToolCallRequest{}, stderr.String(), err
	}
	select {
	case request := <-observed:
		return request, stderr.String(), nil
	default:
		return types.ToolCallRequest{}, stderr.String(), nil
	}
}

func syntheticPlatformTool() toolcatalog.Tool {
	return toolcatalog.Tool{
		Name:                 "rmq.synthetic.platform",
		CLI:                  toolcatalog.CLI{Resource: "synthetic-platform", Verb: "list"},
		Description:          "Synthetic platform-level tool without an instance field.",
		RiskLevel:            "L1",
		Permission:           "synthetic:read",
		RequiredCapabilities: []string{},
		InputSchema: toolcatalog.InputSchema{
			Fields: []toolcatalog.Field{
				{Name: "clusterName", Flag: "cluster-name", Kind: toolcatalog.StringField},
			},
		},
		ViewHint: "object",
	}
}

func syntheticResetTool() toolcatalog.Tool {
	return toolcatalog.Tool{
		Name:                 "rmq.synthetic.reset",
		CLI:                  toolcatalog.CLI{Resource: "synthetic-reset", Verb: "run"},
		Description:          "Synthetic instance-scoped tool with an x-client-default NOW field.",
		RiskLevel:            "L1",
		Permission:           "synthetic:write",
		RequiredCapabilities: []string{},
		InputSchema: toolcatalog.InputSchema{
			Fields: []toolcatalog.Field{
				{Name: "instanceId", Flag: "instance-id", Kind: toolcatalog.StringField, Required: true, MinLength: 1},
				{Name: "groupName", Flag: "group-name", Kind: toolcatalog.StringField, Required: true, MinLength: 1},
				{Name: "timestamp", Flag: "timestamp", Kind: toolcatalog.IntegerField, Required: true, ClientDefault: toolcatalog.ClientDefaultNow},
			},
		},
		ViewHint: "object",
	}
}

// TestPlatformLevelToolOmitsInstanceArgument verifies decisions 25/26: for a
// tool whose schema declares no instance identifier, the explicit
// --instance-id is used only for HMAC signing and the x-rmq-instance-id
// header, and is never added to the tool call arguments.
func TestPlatformLevelToolOmitsInstanceArgument(t *testing.T) {
	request, stderr, err := executeSyntheticTool(t, syntheticPlatformTool(),
		"--cluster-name", "physical-cluster")
	if err != nil {
		t.Fatalf("platform tool call failed: %v, stderr=%s", err, stderr)
	}
	if request.Name != "rmq.synthetic.platform" {
		t.Fatalf("tool = %q, want the synthetic platform tool", request.Name)
	}
	for _, key := range []string{"instanceId", "cluster"} {
		if value, exists := request.Arguments[key]; exists {
			t.Fatalf("platform tool arguments must not carry %q, got %#v", key, value)
		}
	}
	if request.Arguments["clusterName"] != "physical-cluster" {
		t.Fatalf("arguments = %#v, want the explicit clusterName only", request.Arguments)
	}
}

// TestClientDefaultNowFillsMissingTimestamp verifies decision 13: a required
// field annotated with x-client-default: NOW is filled with the current Unix
// milliseconds before required validation when the user did not pass the flag.
func TestClientDefaultNowFillsMissingTimestamp(t *testing.T) {
	const pinnedNow = int64(1757600000000)
	original := nowMillis
	nowMillis = func() int64 { return pinnedNow }
	defer func() { nowMillis = original }()

	request, stderr, err := executeSyntheticTool(t, syntheticResetTool(),
		"--group-name", "gid-orders")
	if err != nil {
		t.Fatalf("reset tool call failed: %v, stderr=%s", err, stderr)
	}
	if request.Arguments["instanceId"] != "instance-x" {
		t.Fatalf("instanceId = %#v, want the explicit --instance-id pass-through", request.Arguments["instanceId"])
	}
	// JSON round-trip turns the int64 fill into float64.
	if timestamp, ok := request.Arguments["timestamp"].(float64); !ok || int64(timestamp) != pinnedNow {
		t.Fatalf("timestamp = %#v, want the client-default NOW fill %d", request.Arguments["timestamp"], pinnedNow)
	}
}

// TestClientDefaultNowKeepsExplicitTimestamp verifies that an explicitly
// supplied flag value always wins over the client default.
func TestClientDefaultNowKeepsExplicitTimestamp(t *testing.T) {
	original := nowMillis
	nowMillis = func() int64 { return 1 }
	defer func() { nowMillis = original }()

	request, stderr, err := executeSyntheticTool(t, syntheticResetTool(),
		"--group-name", "gid-orders", "--timestamp", "42")
	if err != nil {
		t.Fatalf("reset tool call failed: %v, stderr=%s", err, stderr)
	}
	if timestamp, ok := request.Arguments["timestamp"].(float64); !ok || int64(timestamp) != 42 {
		t.Fatalf("timestamp = %#v, want the explicit value 42", request.Arguments["timestamp"])
	}
}

// TestClientDefaultNowSatisfiesRequiredValidation verifies the fill happens
// before required validation: a required field with a client default never
// triggers the "requires --timestamp" INVALID_ARGUMENT error.
func TestClientDefaultNowSatisfiesRequiredValidation(t *testing.T) {
	request, stderr, err := executeSyntheticTool(t, syntheticResetTool(),
		"--group-name", "gid-orders")
	if err != nil {
		t.Fatalf("required validation should pass after the NOW fill: %v, stderr=%s", err, stderr)
	}
	if _, exists := request.Arguments["timestamp"]; !exists {
		t.Fatalf("arguments = %#v, want the filled timestamp", request.Arguments)
	}
}
