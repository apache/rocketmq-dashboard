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
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
)

func validationTool(schema toolcatalog.InputSchema) toolcatalog.Tool {
	return toolcatalog.Tool{
		Name:        "rmq.test.validate",
		CLI:         toolcatalog.CLI{Resource: "rmq.test", Verb: "validate"},
		InputSchema: schema,
	}
}

func stringField(name, flag string, required bool) toolcatalog.Field {
	return toolcatalog.Field{Name: name, Flag: flag, Kind: toolcatalog.StringField, Required: required}
}

// ─── required-field presence ─────────────────────────────────────────────────

func TestRequiredFieldPresence(t *testing.T) {
	tool := validationTool(toolcatalog.InputSchema{
		Fields: []toolcatalog.Field{stringField("name", "name", true)},
	})

	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"name": "topic-1"}); err != nil {
		t.Fatalf("present required field must pass: %v", err)
	}

	err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{})
	if err == nil || !strings.Contains(err.Error(), "rmq.test validate requires --name") {
		t.Fatalf("missing required field must name the flag, got: %v", err)
	}
}

func TestBlankValuesCountAsAbsent(t *testing.T) {
	tool := validationTool(toolcatalog.InputSchema{
		Fields: []toolcatalog.Field{stringField("name", "name", true)},
	})

	for _, blank := range []any{"", "   ", nil} {
		err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"name": blank})
		if err == nil {
			t.Fatalf("blank value %q must count as absent for a required field", blank)
		}
	}

	// a non-string type is present no matter what
	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"name": 42}); err != nil {
		t.Fatalf("non-string value must count as present, got: %v", err)
	}
}

func TestUnknownArgumentsAreTolerated(t *testing.T) {
	tool := validationTool(toolcatalog.InputSchema{
		Fields: []toolcatalog.Field{stringField("name", "name", true)},
	})

	err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{
		"name":        "topic-1",
		"undeclared":  "value",
		"anotherFlag": true,
	})
	if err != nil {
		t.Fatalf("undeclared arguments must be ignored by the validator, got: %v", err)
	}
}

// ─── object fields ───────────────────────────────────────────────────────────

func TestObjectFieldRejectsNonObjectValues(t *testing.T) {
	tool := validationTool(toolcatalog.InputSchema{
		Fields: []toolcatalog.Field{{
			Name:   "filter",
			Flag:   "filter",
			Kind:   toolcatalog.ObjectField,
			Object: &toolcatalog.InputSchema{Fields: []toolcatalog.Field{stringField("type", "type", false)}},
		}},
	})

	err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"filter": "not-an-object"})
	if err == nil || !strings.Contains(err.Error(), "must be an object") {
		t.Fatalf("non-object value for an object field must be rejected, got: %v", err)
	}
}

func TestObjectValidationRecursesIntoChildren(t *testing.T) {
	tool := validationTool(toolcatalog.InputSchema{
		Fields: []toolcatalog.Field{{
			Name:   "filter",
			Flag:   "filter",
			Kind:   toolcatalog.ObjectField,
			Object: &toolcatalog.InputSchema{Fields: []toolcatalog.Field{stringField("type", "type", true)}},
		}},
	})

	err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{
		"filter": map[string]any{"type": "   "},
	})
	if err == nil || !strings.Contains(err.Error(), "requires --type") {
		t.Fatalf("a required child of an object must be validated recursively, got: %v", err)
	}

	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{
		"filter": map[string]any{"type": "TAG"},
	}); err != nil {
		t.Fatalf("object with satisfied children must pass, got: %v", err)
	}
}

// ─── string value validation ────────────────────────────────────────────────

func TestMinLengthCountsRunesNotBytes(t *testing.T) {
	field := stringField("name", "name", false)
	field.MinLength = 2
	tool := validationTool(toolcatalog.InputSchema{Fields: []toolcatalog.Field{field}})

	// two CJK runes are six bytes but two characters: the minimum is in characters
	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"name": "主题"}); err != nil {
		t.Fatalf("two CJK runes satisfy a minLength of two, got: %v", err)
	}

	err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"name": "题"})
	if err == nil || !strings.Contains(err.Error(), "--name must not be empty") {
		t.Fatalf("one rune below a minLength of two must be rejected, got: %v", err)
	}
}

func TestEnumMembership(t *testing.T) {
	field := stringField("mode", "mode", false)
	field.Enum = []string{"TAG", "SQL"}
	tool := validationTool(toolcatalog.InputSchema{Fields: []toolcatalog.Field{field}})

	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"mode": "TAG"}); err != nil {
		t.Fatalf("enum member must pass, got: %v", err)
	}

	err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"mode": "regex"})
	if err == nil || !strings.Contains(err.Error(), "--mode must be one of: TAG, SQL") {
		t.Fatalf("non-member must list the allowed values, got: %v", err)
	}
}

// ─── numeric minimum ─────────────────────────────────────────────────────────

func TestNumericMinimumAppliesToNumbersOnly(t *testing.T) {
	field := stringField("limit", "limit", false)
	field.Minimum = 1
	field.HasMinimum = true
	tool := validationTool(toolcatalog.InputSchema{Fields: []toolcatalog.Field{field}})

	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"limit": int64(1)}); err != nil {
		t.Fatalf("value at the minimum must pass, got: %v", err)
	}
	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"limit": float64(2.5)}); err != nil {
		t.Fatalf("float above the minimum must pass, got: %v", err)
	}

	err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"limit": int64(0)})
	if err == nil || !strings.Contains(err.Error(), "--limit must be at least 1") {
		t.Fatalf("integer below the minimum must be rejected, got: %v", err)
	}

	// a non-numeric value skips the numeric check: string validation owns it
	if err := validateSchemaArguments(tool, tool.InputSchema, map[string]any{"limit": "unlimited"}); err != nil {
		t.Fatalf("non-numeric value must not hit the numeric minimum, got: %v", err)
	}
}

// ─── anyOf groups ────────────────────────────────────────────────────────────

func TestAnyOfAcceptsAnySatisfiedGroup(t *testing.T) {
	tool := validationTool(toolcatalog.InputSchema{
		Fields: []toolcatalog.Field{
			stringField("topic", "topic", false),
			stringField("group", "group", false),
			stringField("all", "all", false),
		},
		AnyOf: []toolcatalog.RequiredGroup{
			{Required: []string{"topic", "group"}},
			{Required: []string{"all"}},
		},
	})

	if err := validateAnyOf(tool, tool.InputSchema, map[string]any{"topic": "t", "group": "g"}); err != nil {
		t.Fatalf("first group satisfied must pass, got: %v", err)
	}
	if err := validateAnyOf(tool, tool.InputSchema, map[string]any{"all": "yes"}); err != nil {
		t.Fatalf("second group satisfied must pass, got: %v", err)
	}

	err := validateAnyOf(tool, tool.InputSchema, map[string]any{"topic": "t"})
	if err == nil || !strings.Contains(err.Error(), "requires at least one of: [--topic + --group] OR [--all]") {
		t.Fatalf("unsatisfied anyOf must list every group, got: %v", err)
	}
}

func TestAnyOfSkippedWhenSchemaHasNoGroups(t *testing.T) {
	tool := validationTool(toolcatalog.InputSchema{})
	if err := validateAnyOf(tool, tool.InputSchema, map[string]any{}); err != nil {
		t.Fatalf("schema without anyOf groups must not require anything, got: %v", err)
	}
}

func TestFieldArgumentHintFormatsObjects(t *testing.T) {
	plain := stringField("name", "name", false)
	if got := fieldArgumentHint(plain); got != "--name" {
		t.Fatalf("plain field hint must be its flag, got %q", got)
	}

	object := toolcatalog.Field{
		Name:   "filter",
		Flag:   "filter",
		Kind:   toolcatalog.ObjectField,
		Object: &toolcatalog.InputSchema{Fields: []toolcatalog.Field{stringField("type", "type", false)}},
	}
	got := fieldArgumentHint(object)
	if !strings.Contains(got, `object "filter" (flags: --type)`) {
		t.Fatalf("object field hint must name the object and its child flags, got %q", got)
	}
}
