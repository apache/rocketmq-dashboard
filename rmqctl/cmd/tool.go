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
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

func (a *App) newToolCommand(opts *globalOptions) *cobra.Command {
	root := &cobra.Command{
		Use:   "tool",
		Short: "Discover and call Studio tools",
	}
	root.AddCommand(a.newToolListCommand(opts))
	root.AddCommand(a.newToolCallCommand(opts))
	return root
}

func (a *App) newToolListCommand(opts *globalOptions) *cobra.Command {
	var cluster string
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List Studio tools",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			target, err := a.resolveTarget(opts, cluster)
			if err != nil {
				return err
			}
			tools, err := a.studioClient().ListTools(target, firstNonEmpty(cluster, target.Cluster))
			if err != nil {
				return err
			}
			if opts.output == "json" {
				return output.JSON(a.Out, tools)
			}
			return output.ToolTable(a.Out, tools)
		},
	}
	cmd.Flags().StringVar(&cluster, "cluster", "", "filter tools by cluster capabilities")
	return cmd
}

func (a *App) newToolCallCommand(opts *globalOptions) *cobra.Command {
	var argValues []string
	var rawJSON string
	var dryRun bool
	var apply bool
	cmd := &cobra.Command{
		Use:   "call <tool>",
		Short: "Call a Studio tool",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			target, err := a.resolveTarget(opts, "")
			if err != nil {
				return err
			}
			arguments, err := toolArguments(rawJSON, argValues)
			if err != nil {
				return err
			}
			result, err := a.studioClient().CallTool(target, args[0], arguments, dryRun, apply)
			if err != nil {
				return err
			}
			if opts.output == "json" {
				return output.JSON(a.Out, result)
			}
			return output.ToolCallSummary(a.Out, result)
		},
	}
	cmd.Flags().StringArrayVar(&argValues, "arg", nil, "tool argument in key=value form")
	cmd.Flags().StringVar(&rawJSON, "raw-json", "", "tool arguments as a JSON object")
	cmd.Flags().BoolVar(&dryRun, "dry-run", false, "request dry-run execution")
	cmd.Flags().BoolVar(&apply, "apply", false, "request apply execution")
	return cmd
}
