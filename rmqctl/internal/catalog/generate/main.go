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
	"crypto/sha256"
	"encoding/json"
	"flag"
	"fmt"
	"go/format"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"text/template"
	"unicode"

	"gopkg.in/yaml.v3"
)

type catalogDocument struct {
	Version              string     `yaml:"version"`
	MinimumClientVersion string     `yaml:"minimumClientVersion"`
	Tools                []toolSpec `yaml:"tools"`
}

type toolSpec struct {
	Name                 string         `yaml:"name"`
	CLI                  cliSpec        `yaml:"cli"`
	Description          string         `yaml:"description"`
	RiskLevel            string         `yaml:"riskLevel"`
	Permission           string         `yaml:"permission"`
	RequiredCapabilities []string       `yaml:"requiredCapabilities"`
	InputSchema          inputSchema    `yaml:"inputSchema"`
	OutputSchema         map[string]any `yaml:"outputSchema"`
	ViewHint             string         `yaml:"viewHint"`
	Deprecated           bool           `yaml:"deprecated"`
	Replacement          string         `yaml:"replacement"`
}

type cliSpec struct {
	Resource string `yaml:"resource"`
	Verb     string `yaml:"verb"`
}

// Root schemas and nested properties use the same recursive JSON Schema shape.
type inputSchema = property

type requiredGroup struct {
	Required []string `yaml:"required" json:"required"`
}

type property struct {
	TargetMode           string              `yaml:"x-target-mode,omitempty" json:"x-target-mode,omitempty"`
	CLIFlag              string              `yaml:"x-cli-flag,omitempty" json:"x-cli-flag,omitempty"`
	Type                 string              `yaml:"type" json:"type,omitempty"`
	Description          string              `yaml:"description" json:"description,omitempty"`
	Enum                 []string            `yaml:"enum" json:"enum,omitempty"`
	Minimum              *float64            `yaml:"minimum" json:"minimum,omitempty"`
	MinLength            int                 `yaml:"minLength" json:"minLength,omitempty"`
	Items                *property           `yaml:"items" json:"items,omitempty"`
	Required             []string            `yaml:"required,omitempty" json:"required,omitempty"`
	Properties           map[string]property `yaml:"properties,omitempty" json:"properties,omitempty"`
	AdditionalProperties *bool               `yaml:"additionalProperties,omitempty" json:"additionalProperties,omitempty"`
	PropertyOrder        []string            `yaml:"-" json:"-"`
	AnyOf                []requiredGroup     `yaml:"anyOf,omitempty" json:"anyOf,omitempty"`
}

// The go* types are the generator's compiled representation. They contain
// exactly the values needed by the Go template, keeping presentation logic out
// of the catalog model and the template itself.
type goDocument struct {
	Version              string
	MinimumClientVersion string
	Digest               string
	Tools                []goTool
}

type goTool struct {
	Name                 string
	CLI                  goCLI
	Description          string
	RiskLevel            string
	Permission           string
	RequiredCapabilities []string
	InputSchema          goInputSchema
	ViewHint             string
	TableDataKey         string
	Deprecated           bool
	Replacement          string
}

type goCLI struct {
	Resource string
	Verb     string
}

type goInputSchema struct {
	Fields []goField
	AnyOf  []goRequiredGroup
}

type goRequiredGroup struct {
	Required []string
}

type goField struct {
	Name        string
	Flag        string
	Description string
	Kind        string
	Required    bool
	Enum        []string
	Minimum     *float64
	MinLength   int
	Object      *goInputSchema
}

const goCatalogTemplate = `/*
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
// Code generated by cataloggen. DO NOT EDIT.

package catalog

var defaultDocument = Document{
	Version:              {{ quote .Version }},
	MinimumClientVersion: {{ quote .MinimumClientVersion }},
	Digest:               {{ quote .Digest }},
	Tools: []Tool{
{{- range .Tools }}
		{
			Name:                 {{ quote .Name }},
			CLI:                  CLI{Resource: {{ quote .CLI.Resource }}, Verb: {{ quote .CLI.Verb }}},
			Description:          {{ quote .Description }},
			RiskLevel:            {{ quote .RiskLevel }},
			Permission:           {{ quote .Permission }},
			RequiredCapabilities: {{ stringSlice .RequiredCapabilities }},
			InputSchema: InputSchema{
{{- if .InputSchema.Fields }}
				Fields: []Field{
{{- range .InputSchema.Fields }}
					{{ template "field" . }},
{{- end }}
				},
{{- end }}
{{- if .InputSchema.AnyOf }}
				AnyOf: []RequiredGroup{
{{- range .InputSchema.AnyOf }}
					{Required: {{ stringSlice .Required }}},
{{- end }}
				},
{{- end }}
			},
			ViewHint: {{ quote .ViewHint }},
{{- if .TableDataKey }}
			TableDataKey: {{ quote .TableDataKey }},
{{- end }}
{{- if .Deprecated }}
			Deprecated: true,
{{- end }}
{{- if .Replacement }}
			Replacement: {{ quote .Replacement }},
{{- end }}
		},
{{- end }}
	},
}
{{ define "field" }}{Name: {{ quote .Name }}{{ if .Flag }}, Flag: {{ quote .Flag }}{{ end }}{{ if .Description }}, Description: {{ quote .Description }}{{ end }}, Kind: {{ .Kind }}{{ if .Required }}, Required: true{{ end }}{{ if .Enum }}, Enum: {{ stringSlice .Enum }}{{ end }}{{ if .Minimum }}, Minimum: {{ number .Minimum }}, HasMinimum: true{{ end }}{{ if .MinLength }}, MinLength: {{ .MinLength }}{{ end }}{{ with .Object }}, Object: &InputSchema{
	Fields: []Field{
	{{ range .Fields }}{{ template "field" . }},
	{{ end }}},
	{{ if .AnyOf }}AnyOf: []RequiredGroup{
	{{ range .AnyOf }}{Required: {{ stringSlice .Required }}},
	{{ end }}},{{ end }}
}{{ end }}}{{ end }}
`

var parsedGoCatalogTemplate = template.Must(template.New("catalog_gen.go").
	Option("missingkey=error").
	Funcs(template.FuncMap{
		"number":      formatNumber,
		"quote":       strconv.Quote,
		"stringSlice": formatStringSlice,
	}).
	Parse(goCatalogTemplate))

func (group *requiredGroup) UnmarshalYAML(node *yaml.Node) error {
	for index := 0; index+1 < len(node.Content); index += 2 {
		if key := node.Content[index].Value; key != "required" {
			return fmt.Errorf("unsupported anyOf keyword %q; only required groups are supported", key)
		}
	}
	type rawRequiredGroup requiredGroup
	var raw rawRequiredGroup
	if err := node.Decode(&raw); err != nil {
		return err
	}
	*group = requiredGroup(raw)
	return nil
}

func (schema *property) UnmarshalYAML(node *yaml.Node) error {
	// Reject unsupported input keywords instead of silently dropping constraints.
	for index := 0; index+1 < len(node.Content); index += 2 {
		switch key := node.Content[index].Value; key {
		case "type", "description", "enum", "minimum", "minLength", "items",
			"required", "properties", "additionalProperties", "anyOf", "x-target-mode", "x-cli-flag":
		default:
			return fmt.Errorf("unsupported input schema keyword %q at line %d", key, node.Content[index].Line)
		}
	}
	type rawInputSchema property
	var raw rawInputSchema
	if err := node.Decode(&raw); err != nil {
		return err
	}
	*schema = property(raw)

	for index := 0; index+1 < len(node.Content); index += 2 {
		if node.Content[index].Value != "properties" {
			continue
		}
		properties := node.Content[index+1]
		for propertyIndex := 0; propertyIndex+1 < len(properties.Content); propertyIndex += 2 {
			schema.PropertyOrder = append(schema.PropertyOrder, properties.Content[propertyIndex].Value)
		}
		break
	}
	return nil
}

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "cataloggen:", err)
		os.Exit(1)
	}
}

func run(arguments []string) error {
	flags := flag.NewFlagSet("cataloggen", flag.ContinueOnError)
	var inputPath string
	var inputDir string
	var outputPath string
	var markdownPath string
	var sdkPath string
	var check bool
	flags.StringVar(&inputPath, "input", "", "path to rmq-tools.yaml (single-file mode)")
	flags.StringVar(&inputDir, "input-dir", "", "path to directory of YAML shards (multi-file mode)")
	flags.StringVar(&outputPath, "output", "", "path to catalog_gen.go")
	flags.StringVar(&markdownPath, "markdown", "", "optional path to generated Markdown documentation")
	flags.StringVar(&sdkPath, "sdk", "", "optional path to generated JSON SDK contract")
	flags.BoolVar(&check, "check", false, "verify that the generated file is current")
	if err := flags.Parse(arguments); err != nil {
		return err
	}
	if flags.NArg() != 0 {
		return fmt.Errorf("unexpected arguments: %s", strings.Join(flags.Args(), " "))
	}
	if outputPath == "" {
		return fmt.Errorf("-output is required")
	}
	if inputPath == "" && inputDir == "" {
		return fmt.Errorf("either -input or -input-dir is required")
	}
	if inputPath != "" && inputDir != "" {
		return fmt.Errorf("-input and -input-dir are mutually exclusive")
	}

	var source []byte
	var document catalogDocument

	if inputDir != "" {
		merged, err := loadShards(inputDir)
		if err != nil {
			return err
		}
		source = merged
		if err := yaml.Unmarshal(source, &document); err != nil {
			return fmt.Errorf("parse merged catalog: %w", err)
		}
	} else {
		source, err := os.ReadFile(inputPath)
		if err != nil {
			return fmt.Errorf("read catalog: %w", err)
		}
		if err := yaml.Unmarshal(source, &document); err != nil {
			return fmt.Errorf("parse catalog: %w", err)
		}
	}
	if err := validate(document); err != nil {
		return err
	}
	digest := sha256.Sum256(source)
	digestText := fmt.Sprintf("%x", digest)
	compiled, err := compileDocument(document, digestText)
	if err != nil {
		return err
	}
	generated, err := renderGo(compiled)
	if err != nil {
		return err
	}
	markdown, err := renderMarkdown(document, digestText)
	if err != nil {
		return err
	}
	sdk, err := renderSDKContract(source)
	if err != nil {
		return err
	}
	outputs := []generatedOutput{{path: outputPath, content: generated}}
	if markdownPath != "" {
		outputs = append(outputs, generatedOutput{path: markdownPath, content: markdown})
	}
	if sdkPath != "" {
		outputs = append(outputs, generatedOutput{path: sdkPath, content: sdk})
	}
	for _, output := range outputs {
		if check {
			if err := verify(output.path, output.content); err != nil {
				return err
			}
			continue
		}
		if err := writeGenerated(output.path, output.content); err != nil {
			return err
		}
	}
	return nil
}

func loadShards(dir string) ([]byte, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("read shard directory: %w", err)
	}
	manifestPath := filepath.Join(filepath.Dir(dir), "manifest.yaml")
	manifestBytes, err := os.ReadFile(manifestPath)
	if err != nil {
		return nil, fmt.Errorf("read manifest: %w", err)
	}
	var shardFiles []string
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".yaml") {
			continue
		}
		shardFiles = append(shardFiles, entry.Name())
	}
	slices.Sort(shardFiles)

	var manifest struct {
		Version              string `yaml:"version"`
		MinimumClientVersion string `yaml:"minimumClientVersion"`
	}
	if err := yaml.Unmarshal(manifestBytes, &manifest); err != nil {
		return nil, fmt.Errorf("parse manifest: %w", err)
	}

	var buf bytes.Buffer
	buf.WriteString("version: ")
	buf.WriteString(manifest.Version)
	buf.WriteString("\nminimumClientVersion: ")
	buf.WriteString(manifest.MinimumClientVersion)
	buf.WriteString("\ntools:\n")

	for _, shardFile := range shardFiles {
		shardBytes, err := os.ReadFile(filepath.Join(dir, shardFile))
		if err != nil {
			return nil, fmt.Errorf("read shard %s: %w", shardFile, err)
		}
		var shard struct {
			Version string      `yaml:"version"`
			Tools   []yaml.Node `yaml:"tools"`
		}
		if err := yaml.Unmarshal(shardBytes, &shard); err != nil {
			return nil, fmt.Errorf("parse shard %s: %w", shardFile, err)
		}
		if shard.Version != manifest.Version {
			return nil, fmt.Errorf("shard %s version mismatch: expected %s, got %s",
				shardFile, manifest.Version, shard.Version)
		}
		if len(shard.Tools) == 0 {
			return nil, fmt.Errorf("shard %s must contain at least one tool", shardFile)
		}
		for _, tool := range shard.Tools {
			toolYAML, err := yaml.Marshal(tool)
			if err != nil {
				return nil, fmt.Errorf("marshal tool from %s: %w", shardFile, err)
			}
			indented := indentYAMLBlock(toolYAML)
			buf.WriteString(indented)
		}
	}

	return buf.Bytes(), nil
}

func indentYAMLBlock(data []byte) string {
	var b strings.Builder
	first := true
	for line := range strings.Lines(string(data)) {
		line = strings.TrimSuffix(line, "\n")
		if line == "" {
			continue
		}
		if first {
			b.WriteString("  - ")
			first = false
		} else {
			b.WriteString("    ")
		}
		b.WriteString(line)
		b.WriteString("\n")
	}
	return b.String()
}

type generatedOutput struct {
	path    string
	content []byte
}

func writeGenerated(outputPath string, content []byte) error {
	if err := os.MkdirAll(filepath.Dir(outputPath), 0755); err != nil {
		return fmt.Errorf("create output directory: %w", err)
	}
	if err := os.WriteFile(outputPath, content, 0644); err != nil {
		return fmt.Errorf("write generated catalog: %w", err)
	}
	return nil
}

func validate(document catalogDocument) error {
	if strings.TrimSpace(document.Version) == "" || strings.TrimSpace(document.MinimumClientVersion) == "" {
		return fmt.Errorf("version and minimumClientVersion are required")
	}
	if len(document.Tools) == 0 {
		return fmt.Errorf("at least one tool is required")
	}

	names := make(map[string]struct{}, len(document.Tools))
	commands := make(map[string]struct{}, len(document.Tools))
	for _, tool := range document.Tools {
		if strings.TrimSpace(tool.Name) == "" {
			return fmt.Errorf("tool name is required")
		}
		if _, exists := names[tool.Name]; exists {
			return fmt.Errorf("duplicate tool name %q", tool.Name)
		}
		names[tool.Name] = struct{}{}

		command := tool.CLI.Resource + " " + tool.CLI.Verb
		if strings.TrimSpace(tool.CLI.Resource) == "" || strings.TrimSpace(tool.CLI.Verb) == "" {
			return fmt.Errorf("tool %q has an incomplete CLI path", tool.Name)
		}
		if _, exists := commands[command]; exists {
			return fmt.Errorf("duplicate CLI command %q", command)
		}
		commands[command] = struct{}{}

		if strings.TrimSpace(tool.Description) == "" {
			return fmt.Errorf("tool %q description is required", tool.Name)
		}
		if strings.TrimSpace(tool.Permission) == "" {
			return fmt.Errorf("tool %q permission is required", tool.Name)
		}
		if tool.ViewHint != "object" && tool.ViewHint != "table" {
			return fmt.Errorf("tool %q has unsupported view hint %q", tool.Name, tool.ViewHint)
		}
		if tool.OutputSchema["type"] != "object" {
			return fmt.Errorf("tool %q output schema must be an object", tool.Name)
		}
		if tool.InputSchema.Type != "object" {
			return fmt.Errorf("tool %q input schema must be an object", tool.Name)
		}
		if !slices.Contains(tool.InputSchema.Required, "cluster") {
			return fmt.Errorf("tool %q must require cluster", tool.Name)
		}
		if err := validateSchema(tool.InputSchema, make(map[string]struct{})); err != nil {
			return fmt.Errorf("tool %q: %w", tool.Name, err)
		}
		if err := validateRisk(tool); err != nil {
			return err
		}
	}
	return nil
}

// Flags retain their leaf names at every depth for CLI compatibility. Reject
// collisions across the entire command before Cobra can register them.
func validateSchema(schema inputSchema, flags map[string]struct{}) error {
	if len(schema.PropertyOrder) != len(schema.Properties) {
		return fmt.Errorf("invalid input property ordering")
	}
	if len(schema.Properties) == 0 {
		return fmt.Errorf("object must define properties for CLI flags")
	}
	for _, name := range schema.PropertyOrder {
		field, exists := schema.Properties[name]
		if !exists {
			return fmt.Errorf("input property %q is undefined", name)
		}
		kind, err := fieldKind(field)
		if err != nil {
			return fmt.Errorf("property %q: %w", name, err)
		}
		if kind == "ObjectField" {
			if err := validateSchema(field, flags); err != nil {
				return fmt.Errorf("property %q: %w", name, err)
			}
			continue
		}
		flagName := schemaFlagName(name, field)
		if field.CLIFlag != "" {
			if !validCLIFlag(field.CLIFlag) {
				return fmt.Errorf("property %q has invalid x-cli-flag %q", name, field.CLIFlag)
			}
			if _, reserved := reservedCLIFlags[field.CLIFlag]; reserved {
				return fmt.Errorf("property %q uses reserved CLI flag --%s", name, field.CLIFlag)
			}
		}
		if _, exists := flags[flagName]; exists {
			return fmt.Errorf("contains duplicate flag --%s", flagName)
		}
		flags[flagName] = struct{}{}
	}
	for _, name := range schema.Required {
		if _, exists := schema.Properties[name]; !exists {
			return fmt.Errorf("requires undefined property %q", name)
		}
	}
	for _, group := range schema.AnyOf {
		if len(group.Required) == 0 {
			return fmt.Errorf("contains an empty anyOf requirement")
		}
		for _, name := range group.Required {
			if _, exists := schema.Properties[name]; !exists {
				return fmt.Errorf("anyOf requires undefined property %q", name)
			}
		}
	}
	return nil
}

func validateRisk(tool toolSpec) error {
	switch tool.RiskLevel {
	case "L1":
		return nil
	case "L2", "L3":
		for _, name := range []string{"dry_run", "confirm_token"} {
			if _, exists := tool.InputSchema.Properties[name]; !exists {
				return fmt.Errorf("tool %q must define property %q", tool.Name, name)
			}
		}
		dryRun := tool.InputSchema.Properties["dry_run"]
		if dryRun.Type != "boolean" {
			return fmt.Errorf("tool %q dry_run must be a boolean", tool.Name)
		}
		if tool.RiskLevel == "L3" {
			for _, name := range []string{"break_glass", "reason"} {
				if _, exists := tool.InputSchema.Properties[name]; !exists {
					return fmt.Errorf("tool %q must define property %q", tool.Name, name)
				}
			}
		}
		return nil
	default:
		return fmt.Errorf("tool %q has unsupported risk level %q", tool.Name, tool.RiskLevel)
	}
}

func fieldKind(field property) (string, error) {
	kind := field.Type
	if kind == "" && len(field.Enum) > 0 {
		kind = "string"
	}
	switch kind {
	case "string":
		return "StringField", nil
	case "integer":
		return "IntegerField", nil
	case "number":
		return "NumberField", nil
	case "boolean":
		return "BooleanField", nil
	case "object":
		return "ObjectField", nil
	case "array":
		if field.Items == nil || field.Items.Type != "string" {
			return "", fmt.Errorf("only array<string> is supported")
		}
		return "StringSliceField", nil
	default:
		return "", fmt.Errorf("unsupported type %q", kind)
	}
}

func compileDocument(document catalogDocument, digest string) (goDocument, error) {
	compiled := goDocument{
		Version:              document.Version,
		MinimumClientVersion: document.MinimumClientVersion,
		Digest:               digest,
		Tools:                make([]goTool, 0, len(document.Tools)),
	}

	for _, tool := range document.Tools {
		compiledTool := goTool{
			Name:                 tool.Name,
			CLI:                  goCLI{Resource: tool.CLI.Resource, Verb: tool.CLI.Verb},
			Description:          tool.Description,
			RiskLevel:            tool.RiskLevel,
			Permission:           tool.Permission,
			RequiredCapabilities: slices.Clone(tool.RequiredCapabilities),
			ViewHint:             tool.ViewHint,
			TableDataKey:         tableDataKey(tool),
			Deprecated:           tool.Deprecated,
			Replacement:          tool.Replacement,
		}

		var err error
		compiledTool.InputSchema, err = compileSchema(tool.InputSchema)
		if err != nil {
			return goDocument{}, fmt.Errorf("compile tool %q: %w", tool.Name, err)
		}
		compiled.Tools = append(compiled.Tools, compiledTool)
	}

	return compiled, nil
}

func compileSchema(schema inputSchema) (goInputSchema, error) {
	compiled := goInputSchema{Fields: make([]goField, 0, len(schema.PropertyOrder))}
	required := stringSet(schema.Required)
	for _, name := range schema.PropertyOrder {
		field, exists := schema.Properties[name]
		if !exists {
			return goInputSchema{}, fmt.Errorf("input property %q is undefined", name)
		}
		kind, err := fieldKind(field)
		if err != nil {
			return goInputSchema{}, fmt.Errorf("property %q: %w", name, err)
		}
		compiledField := goField{
			Name: name, Flag: schemaFlagName(name, field), Description: field.Description,
			Kind: kind, Required: required[name], Enum: slices.Clone(field.Enum),
			MinLength: field.MinLength,
		}
		if field.Minimum != nil {
			compiledField.Minimum = new(*field.Minimum)
		}
		if kind == "ObjectField" {
			object, err := compileSchema(field)
			if err != nil {
				return goInputSchema{}, fmt.Errorf("property %q: %w", name, err)
			}
			compiledField.Flag = ""
			compiledField.Object = &object
		}
		compiled.Fields = append(compiled.Fields, compiledField)
	}
	for _, group := range schema.AnyOf {
		compiled.AnyOf = append(compiled.AnyOf, goRequiredGroup{Required: slices.Clone(group.Required)})
	}
	return compiled, nil
}

func renderGo(document goDocument) ([]byte, error) {
	var output bytes.Buffer
	if err := parsedGoCatalogTemplate.Execute(&output, document); err != nil {
		return nil, fmt.Errorf("render generated catalog: %w", err)
	}
	generated, err := format.Source(output.Bytes())
	if err != nil {
		return nil, fmt.Errorf("format generated catalog: %w", err)
	}
	return generated, nil
}

func renderMarkdown(document catalogDocument, digest string) ([]byte, error) {
	var output bytes.Buffer
	output.WriteString("<!-- Code generated by cataloggen. DO NOT EDIT. -->\n\n")
	output.WriteString("# RocketMQ Tool Catalog\n\n")
	fmt.Fprintf(&output, "- Version: `%s`\n", document.Version)
	fmt.Fprintf(&output, "- Minimum client version: `%s`\n", document.MinimumClientVersion)
	fmt.Fprintf(&output, "- SHA-256: `%s`\n\n", digest)
	output.WriteString("| Tool | CLI | Risk | Permission | Capabilities |\n")
	output.WriteString("|------|-----|------|------------|--------------|\n")
	for _, tool := range document.Tools {
		fmt.Fprintf(&output, "| `%s` | `rmqctl %s %s` | %s | `%s` | %s |\n",
			tool.Name, tool.CLI.Resource, tool.CLI.Verb, tool.RiskLevel, tool.Permission,
			strings.Join(tool.RequiredCapabilities, ", "))
	}
	for _, tool := range document.Tools {
		fmt.Fprintf(&output, "\n## `%s`\n\n%s\n\n", tool.Name, tool.Description)
		fmt.Fprintf(&output, "CLI: `rmqctl %s %s`  \n", tool.CLI.Resource, tool.CLI.Verb)
		fmt.Fprintf(&output, "Risk: `%s` · Permission: `%s`\n\n", tool.RiskLevel, tool.Permission)
		inputJSON, err := json.MarshalIndent(tool.InputSchema, "", "  ")
		if err != nil {
			return nil, fmt.Errorf("render input schema for %s: %w", tool.Name, err)
		}
		outputJSON, err := json.MarshalIndent(tool.OutputSchema, "", "  ")
		if err != nil {
			return nil, fmt.Errorf("render output schema for %s: %w", tool.Name, err)
		}
		output.WriteString("Input schema:\n\n```json\n")
		output.Write(inputJSON)
		output.WriteString("\n```\n\nOutput schema:\n\n```json\n")
		output.Write(outputJSON)
		output.WriteString("\n```\n")
	}
	return output.Bytes(), nil
}

func renderSDKContract(source []byte) ([]byte, error) {
	var document any
	if err := yaml.Unmarshal(source, &document); err != nil {
		return nil, fmt.Errorf("parse SDK catalog: %w", err)
	}
	encoded, err := json.MarshalIndent(document, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("render SDK catalog: %w", err)
	}
	return append(encoded, '\n'), nil
}

func tableDataKey(tool toolSpec) string {
	if tool.ViewHint != "table" {
		return ""
	}
	properties, ok := tool.OutputSchema["properties"].(map[string]any)
	if !ok {
		return ""
	}
	key := ""
	for name, value := range properties {
		property, ok := value.(map[string]any)
		if !ok || property["type"] != "array" {
			continue
		}
		if key != "" {
			return ""
		}
		key = name
	}
	return key
}

func formatStringSlice(values []string) string {
	var output strings.Builder
	output.WriteString("[]string{")
	for index, value := range values {
		if index > 0 {
			output.WriteString(", ")
		}
		output.WriteString(strconv.Quote(value))
	}
	output.WriteString("}")
	return output.String()
}

func formatNumber(value *float64) string {
	return strconv.FormatFloat(*value, 'g', -1, 64)
}

func verify(outputPath string, expected []byte) error {
	actual, err := os.ReadFile(outputPath)
	if err != nil {
		return fmt.Errorf("read generated catalog: %w", err)
	}
	if !bytes.Equal(actual, expected) {
		return fmt.Errorf("%s is stale; run make catalog-generate", outputPath)
	}
	return nil
}

func schemaFlagName(name string, field property) string {
	if field.CLIFlag != "" {
		return field.CLIFlag
	}
	return toFlagName(name)
}

func toFlagName(name string) string {
	var result strings.Builder
	runes := []rune(name)
	for index, character := range runes {
		switch {
		case character == '_':
			result.WriteByte('-')
		case unicode.IsUpper(character):
			if index > 0 && !unicode.IsUpper(runes[index-1]) {
				result.WriteByte('-')
			}
			result.WriteRune(unicode.ToLower(character))
		default:
			result.WriteRune(character)
		}
	}
	return result.String()
}

func stringSet(values []string) map[string]bool {
	set := make(map[string]bool, len(values))
	for _, value := range values {
		set[value] = true
	}
	return set
}

var reservedCLIFlags = map[string]struct{}{
	"help": {}, "version": {}, "context": {}, "config": {}, "output": {}, "timeout": {}, "yes": {},
}

func validCLIFlag(value string) bool {
	if value == "" || value[0] < 'a' || value[0] > 'z' || value[len(value)-1] == '-' {
		return false
	}
	for _, char := range value {
		if (char < 'a' || char > 'z') && (char < '0' || char > '9') && char != '-' {
			return false
		}
	}
	return !strings.Contains(value, "--")
}
