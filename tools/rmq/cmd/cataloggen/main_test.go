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

package main

import (
	"bytes"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

const validCatalogYAML = `version: 1.0.0
minimumClientVersion: 1.0.0
tools:
  - name: rmq.cluster.list
    cli:
      resource: cluster
      verb: list
    description: List clusters.
    riskLevel: L1
    permission: cluster:read
    requiredCapabilities: []
    inputSchema:
      type: object
      required:
        - cluster
      additionalProperties: false
    outputSchema:
      type: array
      items:
        type: object
    viewHint: table
    deprecated: true
    replacement: rmq.cluster.list
`

func TestGenerateRejectsMissingAndNullRequiredFieldsWithoutWriting(t *testing.T) {
	fields := []struct {
		name string
		path []any
	}{
		{name: "version", path: []any{"version"}},
		{name: "minimumClientVersion", path: []any{"minimumClientVersion"}},
		{name: "tools", path: []any{"tools"}},
		{name: "tools[0].name", path: []any{"tools", 0, "name"}},
		{name: "tools[0].cli", path: []any{"tools", 0, "cli"}},
		{name: "tools[0].description", path: []any{"tools", 0, "description"}},
		{name: "tools[0].riskLevel", path: []any{"tools", 0, "riskLevel"}},
		{name: "tools[0].permission", path: []any{"tools", 0, "permission"}},
		{name: "tools[0].requiredCapabilities", path: []any{"tools", 0, "requiredCapabilities"}},
		{name: "tools[0].inputSchema", path: []any{"tools", 0, "inputSchema"}},
		{name: "tools[0].outputSchema", path: []any{"tools", 0, "outputSchema"}},
		{name: "tools[0].viewHint", path: []any{"tools", 0, "viewHint"}},
		{name: "tools[0].deprecated", path: []any{"tools", 0, "deprecated"}},
		{name: "tools[0].cli.resource", path: []any{"tools", 0, "cli", "resource"}},
		{name: "tools[0].cli.verb", path: []any{"tools", 0, "cli", "verb"}},
	}

	for _, field := range fields {
		for _, mutation := range []string{"missing", "null"} {
			t.Run(field.name+"/"+mutation, func(t *testing.T) {
				source := mutateYAMLField(t, []byte(validCatalogYAML), field.path, mutation, "")
				err, goOutput, docsOutput := generateFixture(t, source, false)
				if err == nil {
					t.Fatal("generate() accepted an invalid required field")
				}
				if !strings.Contains(err.Error(), `required field "`+field.name+`"`) {
					t.Fatalf("generate() error = %q, want required-field error for %s", err, field.name)
				}
				assertFilesDoNotExist(t, goOutput, docsOutput)
			})
		}
	}
}

func TestGenerateRejectsNullReplacementWithoutWriting(t *testing.T) {
	source := mutateYAMLField(t, []byte(validCatalogYAML), []any{"tools", 0, "replacement"}, "null", "")
	err, goOutput, docsOutput := generateFixture(t, source, false)
	if err == nil {
		t.Fatal("generate() accepted a null replacement")
	}
	if !strings.Contains(err.Error(), `optional field "tools[0].replacement" must not be null`) {
		t.Fatalf("generate() error = %q", err)
	}
	assertFilesDoNotExist(t, goOutput, docsOutput)
}

func TestGenerateRejectsWrongRequiredFieldTypesWithoutWriting(t *testing.T) {
	tests := []struct {
		name  string
		path  []any
		value string
		want  string
	}{
		{name: "version must be string", path: []any{"version"}, value: "1", want: "must be a string"},
		{name: "tools must be sequence", path: []any{"tools"}, value: "{}", want: "must be a sequence"},
		{name: "tool must be mapping", path: []any{"tools", 0}, value: "[]", want: "must be a mapping"},
		{name: "cli must be mapping", path: []any{"tools", 0, "cli"}, value: "[]", want: "must be a mapping"},
		{name: "capabilities must be sequence", path: []any{"tools", 0, "requiredCapabilities"}, value: "{}", want: "must be a sequence"},
		{name: "input schema must be mapping", path: []any{"tools", 0, "inputSchema"}, value: "[]", want: "must be a mapping"},
		{name: "output schema must be mapping", path: []any{"tools", 0, "outputSchema"}, value: "[]", want: "must be a mapping"},
		{name: "deprecated must be bool", path: []any{"tools", 0, "deprecated"}, value: `"true"`, want: "must be a bool"},
		{name: "replacement must be string", path: []any{"tools", 0, "replacement"}, value: "7", want: "must be a string"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			source := mutateYAMLField(t, []byte(validCatalogYAML), test.path, "replace", test.value)
			err, goOutput, docsOutput := generateFixture(t, source, false)
			if err == nil {
				t.Fatal("generate() accepted the wrong YAML node type")
			}
			if !strings.Contains(err.Error(), test.want) {
				t.Fatalf("generate() error = %q, want substring %q", err, test.want)
			}
			assertFilesDoNotExist(t, goOutput, docsOutput)
		})
	}
}

func TestGenerateAcceptsEmptyClusterListInputSchema(t *testing.T) {
	canonicalPath := filepath.Clean(filepath.Join("..", "..", defaultCatalogPath))
	source, err := os.ReadFile(canonicalPath)
	if err != nil {
		t.Fatal(err)
	}
	clusterListIndex := yamlToolIndex(t, source, "rmq.cluster.list")
	source = mutateYAMLField(
		t,
		source,
		[]any{"tools", clusterListIndex, "inputSchema"},
		"replace",
		"{}",
	)

	err, goOutput, docsOutput := generateFixture(t, source, false)
	if err != nil {
		t.Fatalf("generate() rejected canonical-compatible cluster-list schema: %v", err)
	}
	for _, path := range []string{goOutput, docsOutput} {
		if _, err := os.Stat(path); err != nil {
			t.Fatalf("generated output %s: %v", path, err)
		}
	}
}

func TestGenerateRejectsInvalidInputSchemaShapeWithoutWriting(t *testing.T) {
	remoteCatalog := strings.Replace(validCatalogYAML, "rmq.cluster.list", "rmq.capabilities", 2)
	tests := []struct {
		name  string
		path  []any
		mode  string
		value string
		want  string
	}{
		{
			name: "missing schema type",
			path: []any{"tools", 0, "inputSchema", "type"},
			mode: "missing",
			want: "inputSchema type must be string object",
		},
		{
			name:  "non-object schema type",
			path:  []any{"tools", 0, "inputSchema", "type"},
			mode:  "replace",
			value: "array",
			want:  "inputSchema type must be string object",
		},
		{
			name:  "nullable schema type",
			path:  []any{"tools", 0, "inputSchema", "type"},
			mode:  "replace",
			value: `[object, "null"]`,
			want:  "inputSchema type must be string object",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			source := mutateYAMLField(t, []byte(remoteCatalog), test.path, test.mode, test.value)
			err, goOutput, docsOutput := generateFixture(t, source, false)
			if err == nil {
				t.Fatal("generate() accepted an invalid input schema type")
			}
			if !strings.Contains(err.Error(), test.want) {
				t.Fatalf("generate() error = %q, want substring %q", err, test.want)
			}
			assertFilesDoNotExist(t, goOutput, docsOutput)
		})
	}
}

func TestGenerateRejectsMalformedClusterRequirementsWithoutWriting(t *testing.T) {
	remoteCatalog := strings.Replace(validCatalogYAML, "rmq.cluster.list", "rmq.capabilities", 2)
	tests := []struct {
		name  string
		mode  string
		value string
		want  string
	}{
		{name: "missing required", mode: "missing", want: "inputSchema must require cluster"},
		{name: "null required", mode: "null", want: "inputSchema required must be an array of strings"},
		{name: "scalar required", mode: "replace", value: "cluster", want: "inputSchema required must be an array of strings"},
		{name: "mixed null required", mode: "replace", value: "[cluster, null]", want: "inputSchema required must be an array of strings"},
		{name: "mixed numeric required", mode: "replace", value: "[cluster, 7]", want: "inputSchema required must be an array of strings"},
		{name: "cluster absent", mode: "replace", value: "[topic]", want: "inputSchema must require cluster"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			source := mutateYAMLField(
				t,
				[]byte(remoteCatalog),
				[]any{"tools", 0, "inputSchema", "required"},
				test.mode,
				test.value,
			)
			err, goOutput, docsOutput := generateFixture(t, source, false)
			if err == nil {
				t.Fatal("generate() accepted malformed cluster requirements")
			}
			if !strings.Contains(err.Error(), test.want) {
				t.Fatalf("generate() error = %q, want substring %q", err, test.want)
			}
			assertFilesDoNotExist(t, goOutput, docsOutput)
		})
	}
}

func TestStrictSemVer(t *testing.T) {
	tests := []struct {
		version string
		valid   bool
	}{
		{version: "0.0.0", valid: true},
		{version: "1.2.3-alpha.1+build.5", valid: true},
		{version: "01.2.3", valid: false},
		{version: "1.2.3-alpha..1", valid: false},
		{version: "1.2.3-01", valid: false},
		{version: "1.2", valid: false},
	}

	for _, test := range tests {
		t.Run(test.version, func(t *testing.T) {
			if got := isStrictSemVer(test.version); got != test.valid {
				t.Fatalf("isStrictSemVer(%q) = %t, want %t", test.version, got, test.valid)
			}
		})
	}
}

func TestDecodeCatalogRejectsUnknownFieldsAndMultipleDocuments(t *testing.T) {
	tests := []struct {
		name   string
		source string
	}{
		{name: "unknown field", source: validCatalogYAML + "unknown: true\n"},
		{name: "multiple documents", source: validCatalogYAML + "---\nversion: 1.0.0\n"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if _, err := decodeCatalog([]byte(test.source)); err == nil {
				t.Fatal("decodeCatalog() accepted invalid YAML")
			}
		})
	}
}

func TestCheckDoesNotWriteStaleOutputs(t *testing.T) {
	err, goOutput, docsOutput := generateFixture(t, []byte(validCatalogYAML), false)
	if err != nil {
		t.Fatalf("initial generate() error = %v", err)
	}
	docsBefore, err := os.ReadFile(docsOutput)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(goOutput, []byte("stale"), 0o644); err != nil {
		t.Fatal(err)
	}

	opts := options{
		catalogPath: filepath.Join(filepath.Dir(goOutput), "catalog.yaml"),
		goOutput:    goOutput,
		docsOutput:  docsOutput,
		check:       true,
	}
	err = generate(opts)
	if err == nil || err.Error() != "stale generated file: "+goOutput {
		t.Fatalf("generate(check) error = %q", err)
	}
	goAfter, err := os.ReadFile(goOutput)
	if err != nil {
		t.Fatal(err)
	}
	docsAfter, err := os.ReadFile(docsOutput)
	if err != nil {
		t.Fatal(err)
	}
	if string(goAfter) != "stale" || !bytes.Equal(docsAfter, docsBefore) {
		t.Fatal("generate(check) modified an output file")
	}
}

func TestCheckClassifiesMissingOutputAsStale(t *testing.T) {
	err, goOutput, _ := generateFixture(t, []byte(validCatalogYAML), true)
	if err == nil || err.Error() != "stale generated file: "+goOutput {
		t.Fatalf("generate(check) error = %q", err)
	}
}

func TestCheckPreservesUnexpectedReadError(t *testing.T) {
	tempDir := t.TempDir()
	catalogPath := filepath.Join(tempDir, "catalog.yaml")
	if err := os.WriteFile(catalogPath, []byte(validCatalogYAML), 0o644); err != nil {
		t.Fatal(err)
	}
	goOutput := filepath.Join(tempDir, "generated.go")
	if err := os.Mkdir(goOutput, 0o755); err != nil {
		t.Fatal(err)
	}

	err := generate(options{
		catalogPath: catalogPath,
		goOutput:    goOutput,
		docsOutput:  filepath.Join(tempDir, "reference.md"),
		check:       true,
	})
	if err == nil || !strings.Contains(err.Error(), "read generated file "+goOutput) {
		t.Fatalf("generate(check) error = %q", err)
	}
	var pathError *os.PathError
	if !errors.As(err, &pathError) {
		t.Fatalf("generate(check) did not preserve *os.PathError: %v", err)
	}
}

func generateFixture(t *testing.T, source []byte, check bool) (error, string, string) {
	t.Helper()

	tempDir := t.TempDir()
	catalogPath := filepath.Join(tempDir, "catalog.yaml")
	goOutput := filepath.Join(tempDir, "generated.go")
	docsOutput := filepath.Join(tempDir, "reference.md")
	if err := os.WriteFile(catalogPath, source, 0o644); err != nil {
		t.Fatal(err)
	}
	return generate(options{
		catalogPath: catalogPath,
		goOutput:    goOutput,
		docsOutput:  docsOutput,
		check:       check,
	}), goOutput, docsOutput
}

func assertFilesDoNotExist(t *testing.T, paths ...string) {
	t.Helper()

	for _, path := range paths {
		if _, err := os.Stat(path); !os.IsNotExist(err) {
			t.Fatalf("output %s exists after validation failure", path)
		}
	}
}

func mutateYAMLField(t *testing.T, source []byte, path []any, mode, replacement string) []byte {
	t.Helper()

	var document yaml.Node
	if err := yaml.Unmarshal(source, &document); err != nil {
		t.Fatal(err)
	}
	if len(path) == 0 {
		t.Fatal("mutation path must not be empty")
	}

	parent := yamlNodeAtPath(t, document.Content[0], path[:len(path)-1])
	last := path[len(path)-1]
	switch last := last.(type) {
	case string:
		if parent.Kind != yaml.MappingNode {
			t.Fatalf("parent of %v is not a mapping", path)
		}
		index := yamlMappingValueIndex(t, parent, last)
		switch mode {
		case "missing":
			parent.Content = append(parent.Content[:index-1], parent.Content[index+1:]...)
		case "null":
			parent.Content[index] = &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!null", Value: "null"}
		case "replace":
			parent.Content[index] = parseYAMLValue(t, replacement)
		default:
			t.Fatalf("unknown mutation mode %q", mode)
		}
	case int:
		if parent.Kind != yaml.SequenceNode {
			t.Fatalf("parent of %v is not a sequence", path)
		}
		if mode != "replace" {
			t.Fatalf("sequence entries only support replace, got %q", mode)
		}
		parent.Content[last] = parseYAMLValue(t, replacement)
	default:
		t.Fatalf("unsupported path element %T", last)
	}

	result, err := yaml.Marshal(&document)
	if err != nil {
		t.Fatal(err)
	}
	return result
}

func yamlNodeAtPath(t *testing.T, node *yaml.Node, path []any) *yaml.Node {
	t.Helper()

	current := node
	for _, element := range path {
		switch element := element.(type) {
		case string:
			current = current.Content[yamlMappingValueIndex(t, current, element)]
		case int:
			if current.Kind != yaml.SequenceNode || element < 0 || element >= len(current.Content) {
				t.Fatalf("invalid sequence path element %d", element)
			}
			current = current.Content[element]
		default:
			t.Fatalf("unsupported path element %T", element)
		}
	}
	return current
}

func yamlToolIndex(t *testing.T, source []byte, name string) int {
	t.Helper()

	var document yaml.Node
	if err := yaml.Unmarshal(source, &document); err != nil {
		t.Fatal(err)
	}
	tools := yamlNodeAtPath(t, document.Content[0], []any{"tools"})
	for i, tool := range tools.Content {
		nameNode := yamlNodeAtPath(t, tool, []any{"name"})
		if nameNode.Value == name {
			return i
		}
	}
	t.Fatalf("tool %q not found", name)
	return 0
}

func yamlMappingValueIndex(t *testing.T, node *yaml.Node, key string) int {
	t.Helper()

	if node.Kind != yaml.MappingNode {
		t.Fatalf("node containing %q is not a mapping", key)
	}
	for i := 0; i < len(node.Content); i += 2 {
		if node.Content[i].Value == key {
			return i + 1
		}
	}
	t.Fatalf("mapping key %q not found", key)
	return 0
}

func parseYAMLValue(t *testing.T, source string) *yaml.Node {
	t.Helper()

	var document yaml.Node
	if err := yaml.Unmarshal([]byte(source), &document); err != nil {
		t.Fatal(err)
	}
	return document.Content[0]
}
