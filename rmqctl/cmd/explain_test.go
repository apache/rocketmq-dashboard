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
