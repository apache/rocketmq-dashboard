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
	"fmt"
	"maps"
	"slices"
	"strings"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

func (a *App) newExplainCommand(opts *option) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "explain <resource>",
		Short: "Explain Catalog actions, permissions, risks, and flags for a resource",
		Args:  cobra.ExactArgs(1),
		ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
			if len(args) > 0 {
				return nil, cobra.ShellCompDirectiveNoFileComp
			}
			document := toolcatalog.Default()
			resources := make([]string, 0, len(document.Tools))
			seen := make(map[string]struct{}, len(document.Tools))
			for _, tool := range document.Tools {
				if _, exists := seen[tool.CLI.Resource]; exists {
					continue
				}
				seen[tool.CLI.Resource] = struct{}{}
				if strings.HasPrefix(tool.CLI.Resource, toComplete) {
					resources = append(resources, tool.CLI.Resource)
				}
			}
			slices.Sort(resources)
			return resources, cobra.ShellCompDirectiveNoFileComp
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			explanation, err := explainResource(args[0])
			if err != nil {
				return err
			}
			if output.IsStructured(opts.output) {
				return output.Structured(cmd.OutOrStdout(), opts.output, explanation)
			}
			return renderResourceExplanation(cmd.OutOrStdout(), explanation)
		},
	}
	return cmd
}

type resourceExplanation struct {
	CatalogVersion string              `json:"catalogVersion" yaml:"catalogVersion"`
	CatalogDigest  string              `json:"catalogDigest"  yaml:"catalogDigest"`
	Resource       string              `json:"resource"       yaml:"resource"`
	Actions        []actionExplanation `json:"actions"        yaml:"actions"`
}

type actionExplanation struct {
	Verb                 string   `json:"verb"                 yaml:"verb"`
	Tool                 string   `json:"tool"                 yaml:"tool"`
	Description          string   `json:"description"          yaml:"description"`
	RiskLevel            string   `json:"riskLevel"            yaml:"riskLevel"`
	Permission           string   `json:"permission"           yaml:"permission"`
	RequiredCapabilities []string `json:"requiredCapabilities" yaml:"requiredCapabilities"`
	// Deprecated/Replacement is supported by the catalog generator but no tool
	// in the current catalog is marked deprecated. These fields are always
	// zero-valued today; retained for future deprecation rollouts.
	Deprecated  bool               `json:"deprecated"            yaml:"deprecated"`
	Replacement string             `json:"replacement,omitempty" yaml:"replacement,omitempty"`
	Fields      []fieldExplanation `json:"fields"               yaml:"fields"`
}

type fieldExplanation struct {
	Name        string   `json:"name"                  yaml:"name"`
	Flag        string   `json:"flag"                  yaml:"flag"`
	Type        string   `json:"type"                  yaml:"type"`
	Description string   `json:"description,omitempty" yaml:"description,omitempty"`
	Required    bool     `json:"required"              yaml:"required"`
	Enum        []string `json:"enum,omitempty"         yaml:"enum,omitempty"`
	Minimum     *float64 `json:"minimum,omitempty"      yaml:"minimum,omitempty"`
	MinLength   int      `json:"minLength,omitempty"    yaml:"minLength,omitempty"`
}

func explainResource(resource string) (resourceExplanation, error) {
	document := toolcatalog.Default()
	explanation := resourceExplanation{
		CatalogVersion: document.Version,
		CatalogDigest:  document.Digest,
		Resource:       resource,
		Actions:        make([]actionExplanation, 0),
	}
	resources := make(map[string]struct{})
	for _, tool := range document.Tools {
		resources[tool.CLI.Resource] = struct{}{}
		if tool.CLI.Resource != resource {
			continue
		}
		action := actionExplanation{
			Verb:                 tool.CLI.Verb,
			Tool:                 tool.Name,
			Description:          tool.Description,
			RiskLevel:            tool.RiskLevel,
			Permission:           tool.Permission,
			RequiredCapabilities: tool.RequiredCapabilities,
			// No tool in the current catalog is deprecated; these are always
			// false/"" today. Retained for future deprecation rollouts.
			Deprecated:  tool.Deprecated,
			Replacement: tool.Replacement,
		}
		action.Fields = explainSchemaFields(tool.InputSchema, "")
		explanation.Actions = append(explanation.Actions, action)
	}
	if len(explanation.Actions) == 0 {
		available := slices.Sorted(maps.Keys(resources))
		return resourceExplanation{}, invalidArgument(fmt.Sprintf(
			"unknown Catalog resource %q (available: %s)", resource, strings.Join(available, ", ")))
	}
	return explanation, nil
}

// Explanations list usable flags with their complete JSON property paths.
func explainSchemaFields(schema toolcatalog.InputSchema, prefix string) []fieldExplanation {
	fields := make([]fieldExplanation, 0, len(schema.Fields))
	for _, field := range schema.Fields {
		name := prefix + field.Name
		if field.Object != nil {
			fields = append(fields, explainSchemaFields(*field.Object, name+".")...)
			continue
		}
		explained := fieldExplanation{
			Name: name, Flag: "--" + field.Flag, Type: explainFieldType(field.Kind),
			Description: field.Description, Required: field.Required,
			Enum: field.Enum, MinLength: field.MinLength,
		}
		if field.HasMinimum {
			explained.Minimum = new(field.Minimum)
		}
		fields = append(fields, explained)
	}
	return fields
}

func renderResourceExplanation(out interface{ Write([]byte) (int, error) }, explanation resourceExplanation) error {
	metadata := []map[string]any{{
		"resource": explanation.Resource,
		"version":  explanation.CatalogVersion,
		"digest":   explanation.CatalogDigest,
	}}
	if err := output.Rows(out, metadata, []output.Column{
		{Header: "RESOURCE", Key: "resource"},
		{Header: "CATALOG VERSION", Key: "version"},
		{Header: "CATALOG DIGEST", Key: "digest"},
	}); err != nil {
		return err
	}
	if _, err := fmt.Fprintln(out); err != nil {
		return err
	}
	actions := make([]map[string]any, 0, len(explanation.Actions))
	fields := make([]map[string]any, 0)
	for _, action := range explanation.Actions {
		actions = append(actions, map[string]any{
			"verb":         action.Verb,
			"tool":         action.Tool,
			"risk":         action.RiskLevel,
			"permission":   action.Permission,
			"capabilities": strings.Join(action.RequiredCapabilities, ", "),
			"description":  action.Description,
		})
		for _, field := range action.Fields {
			constraints := make([]string, 0, 3)
			if len(field.Enum) > 0 {
				constraints = append(constraints, "enum="+strings.Join(field.Enum, "|"))
			}
			if field.Minimum != nil {
				constraints = append(constraints, fmt.Sprintf("minimum=%g", *field.Minimum))
			}
			if field.MinLength > 0 {
				constraints = append(constraints, fmt.Sprintf("minLength=%d", field.MinLength))
			}
			fields = append(fields, map[string]any{
				"action":      action.Verb,
				"flag":        field.Flag,
				"property":    field.Name,
				"type":        field.Type,
				"required":    field.Required,
				"constraints": strings.Join(constraints, ", "),
				"description": field.Description,
			})
		}
	}
	if err := output.Rows(out, actions, []output.Column{
		{Header: "ACTION", Key: "verb"},
		{Header: "TOOL", Key: "tool"},
		{Header: "RISK", Key: "risk"},
		{Header: "PERMISSION", Key: "permission"},
		{Header: "CAPABILITIES", Key: "capabilities"},
		{Header: "DESCRIPTION", Key: "description"},
	}); err != nil {
		return err
	}
	if _, err := fmt.Fprintln(out); err != nil {
		return err
	}
	return output.Rows(out, fields, []output.Column{
		{Header: "ACTION", Key: "action"},
		{Header: "FLAG", Key: "flag"},
		{Header: "PROPERTY", Key: "property"},
		{Header: "TYPE", Key: "type"},
		{Header: "REQUIRED", Key: "required"},
		{Header: "CONSTRAINTS", Key: "constraints"},
		{Header: "DESCRIPTION", Key: "description"},
	})
}

func explainFieldType(kind toolcatalog.FieldKind) string {
	if kind == toolcatalog.StringSliceField {
		return "string[]"
	}
	return string(kind)
}
