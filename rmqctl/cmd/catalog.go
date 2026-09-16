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
	"bufio"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
	"github.com/spf13/cobra"
)

type argumentBinding struct {
	name   string
	flag   string
	value  func() any
	object *argumentBinder
}

type argumentBinder struct {
	bindings []argumentBinding
}

func newCatalogCommands(runtime commandRuntime) ([]*cobra.Command, error) {
	resources := make(map[string]*cobra.Command)
	commands := make([]*cobra.Command, 0)
	for _, tool := range toolcatalog.Default().Tools {
		resource := resources[tool.CLI.Resource]
		if resource == nil {
			resource = newResourceCommand(tool.CLI.Resource)
			resources[tool.CLI.Resource] = resource
			commands = append(commands, resource)
		}
		toolCmd, err := newToolCommand(runtime, tool)
		if err != nil {
			return nil, err
		}
		resource.AddCommand(toolCmd)
	}
	return commands, nil
}

func newResourceCommand(name string) *cobra.Command {
	return &cobra.Command{
		Use:   name,
		Short: "Run RocketMQ " + strings.ReplaceAll(name, "-", " ") + " operations",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
}

func newToolCommand(runtime commandRuntime, tool toolcatalog.Tool) (*cobra.Command, error) {
	cmd := &cobra.Command{
		Use:   tool.CLI.Verb,
		Short: tool.Description,
		Args:  cobra.NoArgs,
	}
	binder, err := bindSchemaArguments(cmd, tool.Name, tool.InputSchema)
	if err != nil {
		return nil, err
	}
	cmd.RunE = func(cmd *cobra.Command, args []string) error {
		return runTool(cmd, runtime, tool, binder)
	}
	// Deprecated/Replacement is supported by the catalog generator but no tool
	// in the current catalog is marked deprecated. This branch is inert today
	// but retained for future deprecation rollouts.
	if tool.Deprecated {
		cmd.Deprecated = deprecatedMessage(tool)
	}
	return cmd, nil
}

func bindSchemaArguments(cmd *cobra.Command, toolName string, schema toolcatalog.InputSchema) (argumentBinder, error) {
	return bindSchemaArgumentsAtDepth(cmd, toolName, schema, 0)
}

// bindSchemaArgumentsAtDepth registers one flag per leaf field. At the top
// level (depth 0) the instance identifier field (instanceId, or the
// transitional cluster name in the pre-regeneration catalog) is skipped: its
// value is supplied exclusively by the global persistent --instance-id flag,
// which avoids registering a conflicting per-tool flag and keeps a single
// source of truth for the instance identity.
func bindSchemaArgumentsAtDepth(cmd *cobra.Command, toolName string, schema toolcatalog.InputSchema, depth int) (argumentBinder, error) {
	instanceField := ""
	if depth == 0 {
		if name, ok := toolcatalog.InstanceFieldName(schema); ok {
			instanceField = name
		}
	}
	binder := argumentBinder{bindings: make([]argumentBinding, 0, len(schema.Fields))}
	for _, field := range schema.Fields {
		if field.Name == instanceField {
			continue
		}
		usage := schemaFlagUsage(field)
		binding := argumentBinding{name: field.Name, flag: field.Flag}
		switch field.Kind {
		case toolcatalog.ObjectField:
			if field.Object == nil {
				return argumentBinder{}, fmt.Errorf("missing object schema for field %q in tool %q", field.Name, toolName)
			}
			object, err := bindSchemaArgumentsAtDepth(cmd, toolName, *field.Object, depth+1)
			if err != nil {
				return argumentBinder{}, err
			}
			binding.object = &object
		case toolcatalog.StringField:
			var value string
			cmd.Flags().StringVar(&value, field.Flag, "", usage)
			binding.value = func() any { return value }
		case toolcatalog.IntegerField:
			var value int64
			cmd.Flags().Int64Var(&value, field.Flag, 0, usage)
			binding.value = func() any { return value }
		// NumberField is supported by the catalog generator but no field in the
		// current catalog uses type: number. This branch is inert today but
		// retained for future float-typed properties.
		case toolcatalog.NumberField:
			var value float64
			cmd.Flags().Float64Var(&value, field.Flag, 0, usage)
			binding.value = func() any { return value }
		case toolcatalog.BooleanField:
			var value bool
			cmd.Flags().BoolVar(&value, field.Flag, false, usage)
			binding.value = func() any { return value }
		case toolcatalog.StringSliceField:
			var value []string
			cmd.Flags().StringSliceVar(&value, field.Flag, nil, usage)
			binding.value = func() any { return value }
		default:
			return argumentBinder{}, fmt.Errorf(
				"unsupported generated field kind %q for field %q in tool %q",
				field.Kind, field.Name, toolName)
		}
		binder.bindings = append(binder.bindings, binding)
	}
	return binder, nil
}

func (binder argumentBinder) arguments(cmd *cobra.Command) map[string]any {
	arguments := make(map[string]any)
	for _, binding := range binder.bindings {
		if binding.object != nil {
			if object := binding.object.arguments(cmd); len(object) > 0 {
				arguments[binding.name] = object
			}
			continue
		}
		if cmd.Flags().Changed(binding.flag) {
			arguments[binding.name] = binding.value()
		}
	}
	return arguments
}

func runTool(
	cmd *cobra.Command,
	runtime commandRuntime,
	tool toolcatalog.Tool,
	binder argumentBinder,
) error {
	format := runtime.options.output
	arguments := binder.arguments(cmd)
	target, err := runtime.resolveTarget()
	if err != nil {
		return err
	}
	// Fill client-side defaults (x-client-default: NOW) before required
	// validation and argument assembly so a schema-required field with a
	// client default is self-consistent. Explicit flags always win.
	applyClientDefaults(tool, arguments)
	// Pass the explicit --instance-id value through to tools whose schema
	// declares the instance identifier (decision 7: pass-through of the
	// caller's explicit value, not a default injection). Platform-level
	// tools have no instance field and only use it for signing/headers.
	if instanceField, ok := toolcatalog.InstanceFieldName(tool.InputSchema); ok {
		arguments[instanceField] = target.InstanceID
	}
	if err := validateSchemaArguments(tool, tool.InputSchema, arguments); err != nil {
		return err
	}

	if err := confirmRisk(cmd, runtime, tool, arguments); err != nil {
		return err
	}

	// L2/L3 mutations require a server-issued confirm_token from a matching
	// dry-run preview before they execute. When --yes is supplied without an
	// explicit --confirm-token or --dry-run, transparently run the preview first
	// to obtain the token so callers need not script the two-phase handshake
	// manually. L1 tools, explicit dry-runs, and calls that already carry a
	// confirm_token are left untouched.
	if tool.RiskLevel != "L1" && runtime.options.yes {
		if _, hasToken := arguments["confirm_token"]; !hasToken {
			if dryRun, _ := arguments["dry_run"].(bool); !dryRun {
				preview := make(map[string]any, len(arguments)+1)
				for key, value := range arguments {
					preview[key] = value
				}
				preview["dry_run"] = true
				previewResult, err := runtime.client.CallTool(cmd.Context(), target, tool.Name, preview)
				if err != nil {
					return err
				}
				mutation, err := types.DecodeMutationOutput(previewResult)
				if err != nil {
					return err
				}
				if mutation.ConfirmToken != "" {
					arguments["confirm_token"] = mutation.ConfirmToken
				}
			}
		}
	}

	result, err := runtime.client.CallTool(cmd.Context(), target, tool.Name, arguments)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if output.IsStructured(format) {
		return output.Structured(out, format, result)
	}
	if tool.RiskLevel != "L1" {
		return output.ToolCallSummary(out, result)
	}
	return renderTable(out, tool, result)
}

// nowMillis returns the current Unix epoch milliseconds. It is a variable so
// tests can pin the clock when asserting x-client-default: NOW fills.
var nowMillis = func() int64 { return time.Now().UnixMilli() }

// applyClientDefaults fills top-level fields annotated with the catalog
// extension x-client-default: NOW when the user did not pass the matching
// flag. binder.arguments only records flags explicitly set on the command
// line, so absence from arguments is exactly "not supplied by the user".
// Filling happens before required validation so required fields with a client
// default (e.g. group reset-offset --timestamp) validate cleanly. The catalog
// generator rejects x-client-default on nested properties, so only top-level
// fields need to be considered.
func applyClientDefaults(tool toolcatalog.Tool, arguments map[string]any) {
	for _, field := range tool.InputSchema.Fields {
		if field.ClientDefault != toolcatalog.ClientDefaultNow {
			continue
		}
		if argumentPresent(arguments, field.Name) {
			continue
		}
		arguments[field.Name] = nowMillis()
	}
}

func schemaFlagUsage(field toolcatalog.Field) string {
	usage := field.Description
	if usage == "" {
		usage = strings.ReplaceAll(field.Flag, "-", " ")
	}
	if len(field.Enum) > 0 {
		usage += " (allowed: " + strings.Join(field.Enum, ", ") + ")"
	}
	if field.HasMinimum {
		usage += fmt.Sprintf(" (minimum: %g)", field.Minimum)
	}
	if field.Required {
		usage += " (required)"
	}
	return usage
}

// deprecatedMessage formats a deprecation hint for a tool. No tool in the
// current catalog is marked deprecated, so this function is not invoked today;
// it is retained for future deprecation rollouts.
func deprecatedMessage(tool toolcatalog.Tool) string {
	if tool.Replacement == "" {
		return "this tool is deprecated"
	}
	return "use " + tool.Replacement + " instead"
}

// confirmRisk enforces the client-side confirmation gate for L2/L3 operations.
// L1 tools, dry-run previews and commands invoked with --yes skip the prompt.
// Prompts go to stderr to preserve structured stdout. When App.confirm is set
// (tests) it is used directly; otherwise the default TTY-aware
// implementation rejects non-interactive stdin to avoid silently executing
// dangerous actions from scripts or pipes.
func confirmRisk(cmd *cobra.Command, runtime commandRuntime, tool toolcatalog.Tool, arguments map[string]any) error {
	dryRun, _ := arguments["dry_run"].(bool)
	if tool.RiskLevel == "L1" || dryRun || runtime.options.yes {
		return nil
	}
	confirm := runtime.confirm
	if confirm == nil {
		confirm = defaultConfirm
	}
	return confirm(cmd.InOrStdin(), cmd.ErrOrStderr(), tool.CommandPath(), tool.RiskLevel, runtime.targetServer())
}

// defaultConfirm is the production confirmation implementation. It only
// prompts when stdin is a character device (interactive terminal); pipes and
// redirected input are rejected so scripts must pass --yes explicitly.
func defaultConfirm(in io.Reader, out io.Writer, commandPath, riskLevel, server string) error {
	file, ok := in.(*os.File)
	if !ok || file == nil {
		return riskConfirmationRequired(commandPath, riskLevel)
	}
	stat, err := file.Stat()
	if err != nil {
		return riskConfirmationRequired(commandPath, riskLevel)
	}
	if (stat.Mode() & os.ModeCharDevice) == 0 {
		return riskConfirmationRequired(commandPath, riskLevel)
	}
	fmt.Fprintf(out, "WARNING: %q is a %s operation.\n", commandPath, riskLevel)
	fmt.Fprintf(out, "Arguments will be sent to %s. Type \"yes\" to continue: ", server)
	reader := bufio.NewReader(in)
	answer, err := reader.ReadString('\n')
	if err != nil {
		return types.NewCLIError(
			types.CodeCommandFailed,
			fmt.Sprintf("confirmation for %q failed: %v", commandPath, err),
			"Re-run the command and confirm interactively, or pass --yes to skip the prompt.")
	}
	if !isAffirmative(strings.TrimSpace(answer)) {
		return types.NewCLIError(
			types.CodeCommandFailed,
			fmt.Sprintf("execution of %q cancelled", commandPath),
			"Re-run the command and type yes, or pass --yes to skip the prompt.")
	}
	return nil
}

func riskConfirmationRequired(commandPath, riskLevel string) error {
	return types.NewCLIError(
		types.CodeCommandFailed,
		fmt.Sprintf("%q is a %s operation and requires interactive confirmation", commandPath, riskLevel),
		"Re-run the command in an interactive terminal, or pass --yes to skip the prompt.")
}

func isAffirmative(answer string) bool {
	lowered := strings.ToLower(answer)
	return lowered == "y" || lowered == "yes"
}
