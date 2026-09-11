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
package catalog

import (
	"encoding/hex"
	"testing"
)

func TestDefaultCatalogRuntimeContract(t *testing.T) {
	document := Default()
	if document.Version == "" || document.MinimumClientVersion == "" {
		t.Fatal("catalog versions must not be empty")
	}
	digest, err := hex.DecodeString(document.Digest)
	if err != nil || len(digest) != 32 {
		t.Fatalf("catalog digest must be SHA-256, got %q", document.Digest)
	}
	if len(document.Tools) == 0 {
		t.Fatal("catalog must expose tools")
	}

	names := make(map[string]bool, len(document.Tools))
	commands := make(map[string]bool, len(document.Tools))
	for _, tool := range document.Tools {
		if tool.Permission == "" {
			t.Errorf("tool %q must declare a permission", tool.Name)
		}
		if names[tool.Name] {
			t.Errorf("duplicate tool name %q", tool.Name)
		}
		names[tool.Name] = true
		if commands[tool.CommandPath()] {
			t.Errorf("duplicate command path %q", tool.CommandPath())
		}
		commands[tool.CommandPath()] = true
		assertToolFields(t, tool)
		assertRiskFields(t, tool)
	}
	if names["rmq.message.query_by_group"] {
		t.Fatal("catalog must not expose the removed message group query")
	}
}

func assertToolFields(t *testing.T, tool Tool) {
	t.Helper()
	flagNames := make(map[string]bool, len(tool.InputSchema.Fields))
	var assertSchema func(InputSchema)
	assertSchema = func(schema InputSchema) {
		fieldNames := make(map[string]bool, len(schema.Fields))
		for _, field := range schema.Fields {
			if fieldNames[field.Name] {
				t.Errorf("tool %q has duplicate field %q", tool.Name, field.Name)
			}
			fieldNames[field.Name] = true
			if field.Kind == ObjectField {
				if field.Object == nil || field.Flag != "" {
					t.Fatalf("tool %q has an invalid object field %q", tool.Name, field.Name)
				}
				assertSchema(*field.Object)
				continue
			}
			if flagNames[field.Flag] {
				t.Errorf("tool %q has duplicate flag %q", tool.Name, field.Flag)
			}
			flagNames[field.Flag] = true
		}
	}
	assertSchema(tool.InputSchema)
	cluster, ok := tool.InputSchema.Field("cluster")
	if !ok || !cluster.Required || cluster.Kind != StringField {
		t.Errorf("tool %q must require a string cluster field", tool.Name)
	}
}

func assertRiskFields(t *testing.T, tool Tool) {
	t.Helper()
	if tool.RiskLevel == "L1" {
		return
	}
	assertField := func(name string, kind FieldKind) {
		field, ok := tool.InputSchema.Field(name)
		if !ok || field.Kind != kind {
			t.Errorf("tool %q must define %s as %s", tool.Name, name, kind)
		}
	}
	assertField("dry_run", BooleanField)
	assertField("confirm_token", StringField)
	if tool.RiskLevel == "L3" {
		assertField("break_glass", BooleanField)
		assertField("reason", StringField)
	}
}
