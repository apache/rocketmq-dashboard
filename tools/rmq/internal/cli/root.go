// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package cli defines rmqctl's local-only command tree.
package cli

import (
	"errors"
	"fmt"
	"io"
	"os"
	"sort"
	"strings"

	"github.com/apache/rocketmq-dashboard/tools/rmq/internal/catalog"
	"github.com/apache/rocketmq-dashboard/tools/rmq/internal/config"
	"github.com/apache/rocketmq-dashboard/tools/rmq/internal/output"
	"github.com/apache/rocketmq-dashboard/tools/rmq/internal/version"
	"github.com/spf13/cobra"
)

// Options supplies all process-owned resources used by the command tree.
type Options struct {
	Out        io.Writer
	ErrOut     io.Writer
	ConfigPath string
}

type pathResolver func() (string, error)

// NewRoot constructs the local rmqctl command tree.
func NewRoot(options Options) *cobra.Command {
	out := options.Out
	if out == nil {
		out = io.Discard
	}
	errOut := options.ErrOut
	if errOut == nil {
		errOut = io.Discard
	}

	root := &cobra.Command{
		Use:           "rmqctl",
		Short:         "Inspect RocketMQ metadata and manage local rmqctl configuration",
		Args:          cobra.NoArgs,
		SilenceErrors: true,
		SilenceUsage:  true,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return cmd.Help()
		},
	}
	root.SetOut(out)
	root.SetErr(errOut)

	var explicitConfigPath string
	root.PersistentFlags().StringVar(&explicitConfigPath, "config", "", "path to the rmqctl configuration file")
	resolvePath := func() (string, error) {
		if root.PersistentFlags().Changed("config") {
			if explicitConfigPath == "" {
				return "", errors.New("--config path must not be empty")
			}
			return explicitConfigPath, nil
		}
		if path := os.Getenv("RMQCTL_CONFIG"); path != "" {
			return path, nil
		}
		if options.ConfigPath != "" {
			return options.ConfigPath, nil
		}
		return config.DefaultPath()
	}

	root.AddCommand(
		newConfigCommand(resolvePath),
		newExplainCommand(),
		newVersionCommand(),
		newCompletionCommand(),
	)
	return root
}

func newConfigCommand(resolvePath pathResolver) *cobra.Command {
	command := &cobra.Command{
		Use:   "config",
		Short: "Manage local rmqctl contexts",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return cmd.Help()
		},
	}
	command.AddCommand(
		newConfigInitCommand(resolvePath),
		newConfigGetCommand(resolvePath),
		newConfigSetCommand(resolvePath),
		newConfigUseContextCommand(resolvePath),
		newConfigCurrentContextCommand(resolvePath),
	)
	return command
}

func newConfigInitCommand(resolvePath pathResolver) *cobra.Command {
	var force bool
	command := &cobra.Command{
		Use:   "init",
		Short: "Create a local configuration with safe defaults",
		Args:  cobra.NoArgs,
		RunE: func(_ *cobra.Command, _ []string) error {
			path, err := resolvePath()
			if err != nil {
				return err
			}
			return config.Save(path, config.Default(), force)
		},
	}
	command.Flags().BoolVar(&force, "force", false, "replace an existing configuration")
	return command
}

func newConfigGetCommand(resolvePath pathResolver) *cobra.Command {
	return &cobra.Command{
		Use:   "get",
		Short: "Print the local configuration",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			path, err := resolvePath()
			if err != nil {
				return err
			}
			cfg, err := config.Load(path)
			if err != nil {
				return err
			}
			return output.Write(cmd.OutOrStdout(), "yaml", cfg, nil)
		},
	}
}

func newConfigSetCommand(resolvePath pathResolver) *cobra.Command {
	var (
		server   string
		cluster  string
		format   string
		tokenEnv string
		caFile   string
	)
	command := &cobra.Command{
		Use:   "set NAME",
		Short: "Create or update a local context",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			path, err := resolvePath()
			if err != nil {
				return err
			}
			cfg, err := config.Load(path)
			if err != nil {
				return err
			}

			context, exists := cfg.Contexts[args[0]]
			if !exists {
				defaults := config.Default()
				context = defaults.Contexts[defaults.CurrentContext]
			}
			if cmd.Flags().Changed("server") {
				context.Server = server
			}
			if cmd.Flags().Changed("cluster") {
				context.Cluster = cluster
			}
			if cmd.Flags().Changed("output") {
				context.Output = format
			}
			if cmd.Flags().Changed("token-env") {
				context.TokenEnv = tokenEnv
			}
			if cmd.Flags().Changed("ca-file") {
				context.CAFile = caFile
			}
			if err := config.SetContext(&cfg, args[0], context); err != nil {
				return err
			}
			return config.Save(path, cfg, true)
		},
	}
	command.Flags().StringVar(&server, "server", "", "RocketMQ Dashboard server URL")
	command.Flags().StringVar(&cluster, "cluster", "", "default RocketMQ cluster ID")
	command.Flags().StringVar(&format, "output", "", "default output format: table, json, or yaml")
	command.Flags().StringVar(&tokenEnv, "token-env", "", "environment variable containing the access token")
	command.Flags().StringVar(&caFile, "ca-file", "", "CA certificate path")
	return command
}

func newConfigUseContextCommand(resolvePath pathResolver) *cobra.Command {
	return &cobra.Command{
		Use:   "use-context NAME",
		Short: "Select the current local context",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			path, err := resolvePath()
			if err != nil {
				return err
			}
			cfg, err := config.Load(path)
			if err != nil {
				return err
			}
			if err := config.UseContext(&cfg, args[0]); err != nil {
				return err
			}
			return config.Save(path, cfg, true)
		},
	}
}

func newConfigCurrentContextCommand(resolvePath pathResolver) *cobra.Command {
	return &cobra.Command{
		Use:   "current-context",
		Short: "Print the current context name",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			path, err := resolvePath()
			if err != nil {
				return err
			}
			cfg, err := config.Load(path)
			if err != nil {
				return err
			}
			_, err = fmt.Fprintln(cmd.OutOrStdout(), cfg.CurrentContext)
			return err
		},
	}
}

func newExplainCommand() *cobra.Command {
	var format string
	command := &cobra.Command{
		Use:   "explain TOOL_OR_RESOURCE",
		Short: "Explain a catalog tool without executing it",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			tool, err := findTool(args[0])
			if err != nil {
				return err
			}
			table := &output.Table{
				Headers: []string{"NAME", "RESOURCE", "VERB", "RISK", "PERMISSION", "DESCRIPTION"},
				Rows: [][]string{{
					tool.Name,
					tool.CLI.Resource,
					tool.CLI.Verb,
					tool.RiskLevel,
					tool.Permission,
					tool.Description,
				}},
			}
			return output.Write(cmd.OutOrStdout(), format, tool, table)
		},
	}
	command.Flags().StringVarP(&format, "output", "o", "table", "output format: table, json, or yaml")
	return command
}

func findTool(nameOrResource string) (catalog.Tool, error) {
	if tool, ok := catalog.Find(nameOrResource); ok {
		return tool, nil
	}
	matches := catalog.FindByResource(nameOrResource)
	switch len(matches) {
	case 1:
		return matches[0], nil
	case 0:
		return catalog.Tool{}, fmt.Errorf(
			"no tool or resource %q found; use an exact tool name such as %s",
			nameOrResource,
			strings.Join(catalog.Names(), ", "),
		)
	default:
		names := make([]string, len(matches))
		for index, tool := range matches {
			names[index] = tool.Name
		}
		sort.Strings(names)
		return catalog.Tool{}, fmt.Errorf(
			"resource %q matches multiple tools (%s); use an exact tool name",
			nameOrResource,
			strings.Join(names, ", "),
		)
	}
}

func newVersionCommand() *cobra.Command {
	var format string
	command := &cobra.Command{
		Use:   "version",
		Short: "Print rmqctl build information",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			info := version.Info()
			table := &output.Table{
				Headers: []string{"VERSION", "COMMIT", "DATE"},
				Rows:    [][]string{{info.Version, info.Commit, info.Date}},
			}
			return output.Write(cmd.OutOrStdout(), format, info, table)
		},
	}
	command.Flags().StringVarP(&format, "output", "o", "table", "output format: table, json, or yaml")
	return command
}

func newCompletionCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "completion bash|zsh|fish|powershell",
		Short: "Generate a shell completion script",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			out := cmd.OutOrStdout()
			root := cmd.Root()
			switch args[0] {
			case "bash":
				return root.GenBashCompletion(out)
			case "zsh":
				return root.GenZshCompletion(out)
			case "fish":
				return root.GenFishCompletion(out, true)
			case "powershell":
				return root.GenPowerShellCompletion(out)
			default:
				return fmt.Errorf(
					"unsupported shell %q; choose bash, zsh, fish, or powershell",
					args[0],
				)
			}
		},
	}
}
