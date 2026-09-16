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
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
)

func TestExplainResourceJSONComesFromCatalog(t *testing.T) {
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	exitCode := NewApp(stdout, stderr).Execute([]string{"--output", "json", "explain", "topic"})
	if exitCode != 0 {
		t.Fatalf("explain failed: %s", stderr)
	}
	var explanation resourceExplanation
	if err := json.Unmarshal(stdout.Bytes(), &explanation); err != nil {
		t.Fatalf("decode output: %v", err)
	}
	document := toolcatalog.Default()
	if explanation.Resource != "topic" || explanation.CatalogVersion != document.Version ||
		explanation.CatalogDigest != document.Digest {
		t.Fatalf("unexpected metadata: %#v", explanation)
	}
	want := make(map[string]toolcatalog.Tool)
	for _, tool := range document.Tools {
		if tool.CLI.Resource == "topic" {
			want[tool.CLI.Verb] = tool
		}
	}
	if len(explanation.Actions) != len(want) {
		t.Fatalf("actions = %d, want %d", len(explanation.Actions), len(want))
	}
	for _, action := range explanation.Actions {
		tool, ok := want[action.Verb]
		if !ok || action.Tool != tool.Name || action.RiskLevel != tool.RiskLevel ||
			action.Permission != tool.Permission ||
			strings.Join(action.RequiredCapabilities, ",") != strings.Join(tool.RequiredCapabilities, ",") ||
			len(action.Fields) != len(tool.InputSchema.Fields) {
			t.Fatalf("action is not Catalog-derived: %#v", action)
		}
	}
}

// TestExplainMarksInstanceFieldGlobal verifies that the instance identifier
// field stays visible in explain output even though it is no longer a per-tool
// flag: it must be presented as the global --instance-id flag.
func TestExplainMarksInstanceFieldGlobal(t *testing.T) {
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	exitCode := NewApp(stdout, stderr).Execute([]string{"--output", "json", "explain", "topic"})
	if exitCode != 0 {
		t.Fatalf("explain failed: %s", stderr)
	}
	var explanation resourceExplanation
	if err := json.Unmarshal(stdout.Bytes(), &explanation); err != nil {
		t.Fatalf("decode output: %v", err)
	}
	if len(explanation.Actions) == 0 {
		t.Fatal("no actions explained")
	}
	for _, action := range explanation.Actions {
		var found *fieldExplanation
		for index := range action.Fields {
			if action.Fields[index].Global {
				found = &action.Fields[index]
				break
			}
		}
		if found == nil {
			t.Fatalf("action %q does not mark the instance identifier field as global: %#v", action.Verb, action.Fields)
		}
		if found.Flag != "--instance-id" {
			t.Fatalf("action %q instance field flag = %q, want --instance-id", action.Verb, found.Flag)
		}
		if !found.Required {
			t.Fatalf("action %q instance field must stay required", action.Verb)
		}
	}
}
