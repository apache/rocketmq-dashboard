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
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"go/format"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"

	"github.com/spf13/cobra"
	"go.yaml.in/yaml/v3"
)

const (
	defaultCatalogPath = "../../server/src/main/resources/tool-catalog/rmq-tools.yaml"
	defaultGoOutput    = "internal/catalog/generated.go"
	defaultDocsOutput  = "../../docs/rmqctl-reference.md"
)

var (
	toolNamePattern   = regexp.MustCompile(`^rmq\.[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)*$`)
	cliMappingPattern = regexp.MustCompile(`^[a-z][a-z0-9-]*$`)
	permissionPattern = regexp.MustCompile(`^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$`)
	capabilityPattern = regexp.MustCompile(`^[A-Z][A-Z0-9_]*$`)
	semVerCorePattern = regexp.MustCompile(`^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([^+]+))?(?:\+(.+))?$`)
)

type options struct {
	catalogPath string
	goOutput    string
	docsOutput  string
	check       bool
}

type sourceCatalog struct {
	Version              string       `yaml:"version"`
	MinimumClientVersion string       `yaml:"minimumClientVersion"`
	Tools                []sourceTool `yaml:"tools"`
}

type sourceTool struct {
	Name                 string         `yaml:"name"`
	CLI                  sourceCLI      `yaml:"cli"`
	Description          string         `yaml:"description"`
	RiskLevel            string         `yaml:"riskLevel"`
	Permission           string         `yaml:"permission"`
	RequiredCapabilities []string       `yaml:"requiredCapabilities"`
	InputSchema          map[string]any `yaml:"inputSchema"`
	OutputSchema         map[string]any `yaml:"outputSchema"`
	ViewHint             string         `yaml:"viewHint"`
	Deprecated           bool           `yaml:"deprecated"`
	Replacement          string         `yaml:"replacement,omitempty"`
}

type sourceCLI struct {
	Resource string `yaml:"resource"`
	Verb     string `yaml:"verb"`
}

func main() {
	command := newCommand()
	command.SetArgs(normalizeArgs(os.Args[1:]))
	if err := command.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func normalizeArgs(args []string) []string {
	normalized := append([]string(nil), args...)
	longFlags := []string{"catalog", "go-output", "docs-output", "check"}
	for i, argument := range normalized {
		for _, name := range longFlags {
			if argument == "-"+name || strings.HasPrefix(argument, "-"+name+"=") {
				normalized[i] = "-" + argument
				break
			}
		}
	}
	return normalized
}

func newCommand() *cobra.Command {
	opts := options{}
	command := &cobra.Command{
		Use:           "cataloggen",
		Short:         "Generate the Go tool catalog and rmqctl reference",
		SilenceErrors: true,
		SilenceUsage:  true,
		Args:          cobra.NoArgs,
		RunE: func(_ *cobra.Command, _ []string) error {
			return generate(opts)
		},
	}
	command.Flags().StringVar(&opts.catalogPath, "catalog", defaultCatalogPath, "canonical tool catalog YAML")
	command.Flags().StringVar(&opts.goOutput, "go-output", defaultGoOutput, "generated Go output")
	command.Flags().StringVar(&opts.docsOutput, "docs-output", defaultDocsOutput, "generated Markdown output")
	command.Flags().BoolVar(&opts.check, "check", false, "check generated files without writing")
	return command
}

func generate(opts options) error {
	source, err := os.ReadFile(opts.catalogPath)
	if err != nil {
		return fmt.Errorf("read catalog %s: %w", opts.catalogPath, err)
	}

	catalog, err := decodeCatalog(source)
	if err != nil {
		return fmt.Errorf("decode catalog %s: %w", opts.catalogPath, err)
	}
	if err := validateCatalog(&catalog); err != nil {
		return fmt.Errorf("validate catalog %s: %w", opts.catalogPath, err)
	}

	digestBytes := sha256.Sum256(source)
	digest := hex.EncodeToString(digestBytes[:])
	goSource, err := renderGo(catalog, digest)
	if err != nil {
		return fmt.Errorf("render Go catalog: %w", err)
	}
	docs := renderDocs(catalog, digest)

	outputs := []struct {
		path    string
		content []byte
	}{
		{path: opts.goOutput, content: goSource},
		{path: opts.docsOutput, content: docs},
	}

	if opts.check {
		for _, output := range outputs {
			current, err := os.ReadFile(output.path)
			if err != nil {
				if os.IsNotExist(err) {
					return fmt.Errorf("stale generated file: %s", output.path)
				}
				return fmt.Errorf("read generated file %s: %w", output.path, err)
			}
			if !bytes.Equal(current, output.content) {
				return fmt.Errorf("stale generated file: %s", output.path)
			}
		}
		return nil
	}

	for _, output := range outputs {
		if err := writeOutput(output.path, output.content); err != nil {
			return err
		}
	}
	return nil
}

func decodeCatalog(source []byte) (sourceCatalog, error) {
	nodeDecoder := yaml.NewDecoder(bytes.NewReader(source))
	var document yaml.Node
	if err := nodeDecoder.Decode(&document); err != nil {
		return sourceCatalog{}, err
	}
	if err := rejectTrailingDocuments(nodeDecoder); err != nil {
		return sourceCatalog{}, err
	}
	if err := validateCatalogDocument(&document); err != nil {
		return sourceCatalog{}, err
	}

	var catalog sourceCatalog
	typedDecoder := yaml.NewDecoder(bytes.NewReader(source))
	typedDecoder.KnownFields(true)
	if err := typedDecoder.Decode(&catalog); err != nil {
		return sourceCatalog{}, err
	}
	return catalog, nil
}

func rejectTrailingDocuments(decoder *yaml.Decoder) error {
	var trailing yaml.Node
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		if err == nil {
			return errors.New("multiple YAML documents are not allowed")
		}
		return err
	}
	return nil
}

func validateCatalogDocument(document *yaml.Node) error {
	if document.Kind != yaml.DocumentNode || len(document.Content) != 1 {
		return errors.New("catalog must be a YAML document")
	}
	root := document.Content[0]
	if err := requireNodeKind(root, yaml.MappingNode, "catalog", "a mapping"); err != nil {
		return err
	}

	version, err := requiredMappingValue(root, "version", "version")
	if err != nil {
		return err
	}
	if err := requireYAMLString(version, "version"); err != nil {
		return err
	}

	minimumClientVersion, err := requiredMappingValue(root, "minimumClientVersion", "minimumClientVersion")
	if err != nil {
		return err
	}
	if err := requireYAMLString(minimumClientVersion, "minimumClientVersion"); err != nil {
		return err
	}

	tools, err := requiredMappingValue(root, "tools", "tools")
	if err != nil {
		return err
	}
	if err := requireNodeKind(tools, yaml.SequenceNode, "tools", "a sequence"); err != nil {
		return err
	}
	for i, tool := range tools.Content {
		path := fmt.Sprintf("tools[%d]", i)
		if err := validateToolDocument(tool, path); err != nil {
			return err
		}
	}
	return nil
}

func validateToolDocument(tool *yaml.Node, path string) error {
	if err := requireNodeKind(tool, yaml.MappingNode, path, "a mapping"); err != nil {
		return err
	}

	stringFields := []string{"name", "description", "riskLevel", "permission", "viewHint"}
	for _, field := range stringFields {
		fieldPath := path + "." + field
		value, err := requiredMappingValue(tool, field, fieldPath)
		if err != nil {
			return err
		}
		if err := requireYAMLString(value, fieldPath); err != nil {
			return err
		}
	}

	cli, err := requiredMappingValue(tool, "cli", path+".cli")
	if err != nil {
		return err
	}
	if err := requireNodeKind(cli, yaml.MappingNode, path+".cli", "a mapping"); err != nil {
		return err
	}
	for _, field := range []string{"resource", "verb"} {
		fieldPath := path + ".cli." + field
		value, err := requiredMappingValue(cli, field, fieldPath)
		if err != nil {
			return err
		}
		if err := requireYAMLString(value, fieldPath); err != nil {
			return err
		}
	}

	capabilities, err := requiredMappingValue(tool, "requiredCapabilities", path+".requiredCapabilities")
	if err != nil {
		return err
	}
	if err := requireNodeKind(capabilities, yaml.SequenceNode, path+".requiredCapabilities", "a sequence"); err != nil {
		return err
	}
	for _, capability := range capabilities.Content {
		if err := requireYAMLString(capability, path+".requiredCapabilities[]"); err != nil {
			return err
		}
	}

	for _, field := range []string{"inputSchema", "outputSchema"} {
		fieldPath := path + "." + field
		schema, err := requiredMappingValue(tool, field, fieldPath)
		if err != nil {
			return err
		}
		if err := requireNodeKind(schema, yaml.MappingNode, fieldPath, "a mapping"); err != nil {
			return err
		}
	}

	deprecated, err := requiredMappingValue(tool, "deprecated", path+".deprecated")
	if err != nil {
		return err
	}
	if deprecated.Kind != yaml.ScalarNode || deprecated.ShortTag() != "!!bool" {
		return fmt.Errorf(`field %q must be a bool`, path+".deprecated")
	}

	if replacement, exists := mappingValue(tool, "replacement"); exists {
		replacementPath := path + ".replacement"
		if isNullNode(replacement) {
			return fmt.Errorf(`optional field %q must not be null`, replacementPath)
		}
		if err := requireYAMLString(replacement, replacementPath); err != nil {
			return err
		}
	}
	return nil
}

func requiredMappingValue(mapping *yaml.Node, key, path string) (*yaml.Node, error) {
	value, exists := mappingValue(mapping, key)
	if !exists {
		return nil, fmt.Errorf(`required field %q is missing`, path)
	}
	if isNullNode(value) {
		return nil, fmt.Errorf(`required field %q must not be null`, path)
	}
	return value, nil
}

func mappingValue(mapping *yaml.Node, key string) (*yaml.Node, bool) {
	if mapping.Kind != yaml.MappingNode {
		return nil, false
	}
	for i := 0; i+1 < len(mapping.Content); i += 2 {
		if mapping.Content[i].Value == key {
			return mapping.Content[i+1], true
		}
	}
	return nil, false
}

func isNullNode(node *yaml.Node) bool {
	return node == nil || node.ShortTag() == "!!null"
}

func requireYAMLString(node *yaml.Node, path string) error {
	if node.Kind != yaml.ScalarNode || node.ShortTag() != "!!str" {
		return fmt.Errorf(`field %q must be a string`, path)
	}
	return nil
}

func requireNodeKind(node *yaml.Node, kind yaml.Kind, path, description string) error {
	if node.Kind != kind {
		return fmt.Errorf(`field %q must be %s`, path, description)
	}
	return nil
}

func validateCatalog(catalog *sourceCatalog) error {
	if !isStrictSemVer(catalog.Version) {
		return fmt.Errorf("version %q is not strict SemVer", catalog.Version)
	}
	if !isStrictSemVer(catalog.MinimumClientVersion) {
		return fmt.Errorf("minimumClientVersion %q is not strict SemVer", catalog.MinimumClientVersion)
	}
	if len(catalog.Tools) == 0 {
		return errors.New("tools must not be empty")
	}

	seenNames := make(map[string]struct{}, len(catalog.Tools))
	for i := range catalog.Tools {
		tool := &catalog.Tools[i]
		if !toolNamePattern.MatchString(tool.Name) {
			return fmt.Errorf("tool name %q must match rmq.* vocabulary", tool.Name)
		}
		if _, exists := seenNames[tool.Name]; exists {
			return fmt.Errorf("duplicate tool name %q", tool.Name)
		}
		seenNames[tool.Name] = struct{}{}

		if strings.TrimSpace(tool.Description) == "" {
			return fmt.Errorf("%s description must not be empty", tool.Name)
		}
		if !cliMappingPattern.MatchString(tool.CLI.Resource) || !cliMappingPattern.MatchString(tool.CLI.Verb) {
			return fmt.Errorf("%s CLI resource and verb must be non-empty lowercase mappings", tool.Name)
		}
		if !permissionPattern.MatchString(tool.Permission) {
			return fmt.Errorf("%s permission %q must use resource:verb syntax", tool.Name, tool.Permission)
		}
		switch tool.RiskLevel {
		case "L1", "L2", "L3":
		default:
			return fmt.Errorf("%s riskLevel %q is not one of L1, L2, L3", tool.Name, tool.RiskLevel)
		}
		switch tool.ViewHint {
		case "object", "table", "text", "timeline", "topology":
		default:
			return fmt.Errorf("%s viewHint %q is not allowed", tool.Name, tool.ViewHint)
		}
		if tool.Replacement != "" {
			if !tool.Deprecated {
				return fmt.Errorf("%s replacement is allowed only for deprecated tools", tool.Name)
			}
			if !toolNamePattern.MatchString(tool.Replacement) {
				return fmt.Errorf("%s replacement %q is not a valid tool name", tool.Name, tool.Replacement)
			}
		}
		if err := validateCapabilities(tool); err != nil {
			return err
		}
		if err := validateSchemas(tool); err != nil {
			return err
		}
		if _, err := json.Marshal(tool.InputSchema); err != nil {
			return fmt.Errorf("%s inputSchema is not JSON-compatible: %w", tool.Name, err)
		}
		if _, err := json.Marshal(tool.OutputSchema); err != nil {
			return fmt.Errorf("%s outputSchema is not JSON-compatible: %w", tool.Name, err)
		}

		slices.Sort(tool.RequiredCapabilities)
	}
	slices.SortFunc(catalog.Tools, func(a, b sourceTool) int {
		return strings.Compare(a.Name, b.Name)
	})
	return nil
}

func validateCapabilities(tool *sourceTool) error {
	seen := make(map[string]struct{}, len(tool.RequiredCapabilities))
	for _, capability := range tool.RequiredCapabilities {
		if !capabilityPattern.MatchString(capability) {
			return fmt.Errorf("%s capability %q is not valid", tool.Name, capability)
		}
		if _, exists := seen[capability]; exists {
			return fmt.Errorf("%s capability %q is duplicated", tool.Name, capability)
		}
		seen[capability] = struct{}{}
	}
	return nil
}

func validateSchemas(tool *sourceTool) error {
	if tool.InputSchema == nil {
		return fmt.Errorf("%s inputSchema must be a mapping", tool.Name)
	}
	if tool.OutputSchema == nil {
		return fmt.Errorf("%s outputSchema must be a mapping", tool.Name)
	}
	schemaType, ok := tool.InputSchema["type"].(string)
	if !ok || schemaType != "object" {
		return fmt.Errorf("%s inputSchema type must be string object", tool.Name)
	}

	requiredValue, hasRequired := tool.InputSchema["required"]
	if !hasRequired {
		if tool.Name != "rmq.cluster.list" {
			return fmt.Errorf("%s inputSchema must require cluster", tool.Name)
		}
		return nil
	}
	required, ok := requiredValue.([]any)
	if !ok {
		return fmt.Errorf("%s inputSchema required must be an array of strings", tool.Name)
	}
	hasCluster := false
	for _, property := range required {
		propertyName, ok := property.(string)
		if !ok {
			return fmt.Errorf("%s inputSchema required must be an array of strings", tool.Name)
		}
		if propertyName == "cluster" {
			hasCluster = true
		}
	}
	if tool.Name != "rmq.cluster.list" && !hasCluster {
		return fmt.Errorf("%s inputSchema must require cluster", tool.Name)
	}
	return nil
}

func isStrictSemVer(version string) bool {
	matches := semVerCorePattern.FindStringSubmatch(version)
	if matches == nil {
		return false
	}
	if matches[4] != "" && !validIdentifiers(matches[4], true) {
		return false
	}
	return matches[5] == "" || validIdentifiers(matches[5], false)
}

func validIdentifiers(value string, prerelease bool) bool {
	for _, identifier := range strings.Split(value, ".") {
		if identifier == "" {
			return false
		}
		numeric := true
		for _, character := range identifier {
			if !((character >= '0' && character <= '9') ||
				(character >= 'A' && character <= 'Z') ||
				(character >= 'a' && character <= 'z') ||
				character == '-') {
				return false
			}
			if character < '0' || character > '9' {
				numeric = false
			}
		}
		if prerelease && numeric && len(identifier) > 1 && identifier[0] == '0' {
			return false
		}
	}
	return true
}

func renderGo(catalog sourceCatalog, digest string) ([]byte, error) {
	var output bytes.Buffer
	output.WriteString(apacheGoHeader)
	output.WriteString("\n// Code generated by cataloggen; DO NOT EDIT.\n\n")
	output.WriteString("package catalog\n\n")
	output.WriteString("import \"encoding/json\"\n\n")
	fmt.Fprintf(&output, "const (\n\tVersion = %q\n\tMinimumClientVersion = %q\n\tSourceSHA256 = %q\n)\n\n",
		catalog.Version, catalog.MinimumClientVersion, digest)
	output.WriteString("var generatedTools = []Tool{\n")
	for _, tool := range catalog.Tools {
		inputSchema, err := json.Marshal(tool.InputSchema)
		if err != nil {
			return nil, err
		}
		outputSchema, err := json.Marshal(tool.OutputSchema)
		if err != nil {
			return nil, err
		}

		output.WriteString("\t{\n")
		fmt.Fprintf(&output, "\t\tName: %q,\n", tool.Name)
		fmt.Fprintf(&output, "\t\tCLI: CLI{Resource: %q, Verb: %q},\n", tool.CLI.Resource, tool.CLI.Verb)
		fmt.Fprintf(&output, "\t\tDescription: %q,\n", tool.Description)
		fmt.Fprintf(&output, "\t\tRiskLevel: %q,\n", tool.RiskLevel)
		fmt.Fprintf(&output, "\t\tPermission: %q,\n", tool.Permission)
		output.WriteString("\t\tRequiredCapabilities: []string{")
		for i, capability := range tool.RequiredCapabilities {
			if i > 0 {
				output.WriteString(", ")
			}
			fmt.Fprintf(&output, "%q", capability)
		}
		output.WriteString("},\n")
		fmt.Fprintf(&output, "\t\tInputSchema: mustSchema(%s),\n", strconv.Quote(string(inputSchema)))
		fmt.Fprintf(&output, "\t\tOutputSchema: mustSchema(%s),\n", strconv.Quote(string(outputSchema)))
		fmt.Fprintf(&output, "\t\tViewHint: %q,\n", tool.ViewHint)
		fmt.Fprintf(&output, "\t\tDeprecated: %t,\n", tool.Deprecated)
		if tool.Replacement != "" {
			fmt.Fprintf(&output, "\t\tReplacement: %q,\n", tool.Replacement)
		}
		output.WriteString("\t},\n")
	}
	output.WriteString("}\n\n")
	output.WriteString("func mustSchema(source string) map[string]any {\n")
	output.WriteString("\tvar schema map[string]any\n")
	output.WriteString("\tif err := json.Unmarshal([]byte(source), &schema); err != nil {\n")
	output.WriteString("\t\tpanic(err)\n")
	output.WriteString("\t}\n")
	output.WriteString("\treturn schema\n")
	output.WriteString("}\n")

	formatted, err := format.Source(output.Bytes())
	if err != nil {
		return nil, err
	}
	return formatted, nil
}

func renderDocs(catalog sourceCatalog, digest string) []byte {
	var output bytes.Buffer
	output.WriteString("<!-- Code generated by cataloggen; DO NOT EDIT. -->\n\n")
	output.WriteString("# rmqctl Tool Reference\n\n")
	fmt.Fprintf(&output, "- Source catalog version: `%s`\n", catalog.Version)
	fmt.Fprintf(&output, "- Minimum client version: `%s`\n", catalog.MinimumClientVersion)
	fmt.Fprintf(&output, "- Source SHA-256: `%s`\n\n", digest)

	for i, tool := range catalog.Tools {
		fmt.Fprintf(&output, "## `%s`\n\n", tool.Name)
		fmt.Fprintf(&output, "- CLI: `rmqctl %s %s`\n", tool.CLI.Resource, tool.CLI.Verb)
		fmt.Fprintf(&output, "- Risk: `%s`\n", tool.RiskLevel)
		fmt.Fprintf(&output, "- Permission: `%s`\n", tool.Permission)
		fmt.Fprintf(&output, "- Required capabilities: %s\n", markdownCapabilities(tool.RequiredCapabilities))
		fmt.Fprintf(&output, "- Description: %s\n", tool.Description)
		if tool.Deprecated {
			output.WriteString("- Deprecated: `true`\n")
			if tool.Replacement != "" {
				fmt.Fprintf(&output, "- Replacement: `%s`\n", tool.Replacement)
			}
		}
		if i < len(catalog.Tools)-1 {
			output.WriteString("\n")
		}
	}
	return output.Bytes()
}

func markdownCapabilities(capabilities []string) string {
	if len(capabilities) == 0 {
		return "None"
	}

	quoted := make([]string, len(capabilities))
	for i, capability := range capabilities {
		quoted[i] = "`" + capability + "`"
	}
	return strings.Join(quoted, ", ")
}

func writeOutput(path string, content []byte) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return fmt.Errorf("create output directory for %s: %w", path, err)
	}
	if err := os.WriteFile(path, content, 0o644); err != nil {
		return fmt.Errorf("write output %s: %w", path, err)
	}
	return nil
}

const apacheGoHeader = `/*
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
 */`
