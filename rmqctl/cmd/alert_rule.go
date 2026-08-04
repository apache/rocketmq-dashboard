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

func (a *App) newAlertRuleCommand(opts *globalOptions) *cobra.Command {
	root := &cobra.Command{
		Use:   "alert-rule",
		Short: "Query RocketMQ Studio alert rules",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	root.AddCommand(a.newAlertRuleListCommand(opts))
	return root
}

func (a *App) newAlertRuleListCommand(opts *globalOptions) *cobra.Command {
	var cluster string
	var search string
	var enabled bool
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List alert rules",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			target, err := a.resolveTarget(opts, cluster)
			if err != nil {
				return err
			}
			resolved, err := resolvedCluster("alert-rule list", cluster, target)
			if err != nil {
				return err
			}
			arguments := map[string]any{"cluster": resolved}
			putIfNotEmpty(arguments, "search", search)
			if cmd.Flags().Changed("enabled") {
				arguments["enabled"] = enabled
			}
			result, err := a.studioClient().CallTool(target, "rmq.alert.rule.list", arguments, false, false)
			if err != nil {
				return err
			}
			if err := toolResultError(result); err != nil {
				return err
			}
			rows := output.MapsFromAny(result.Result)
			if opts.output == "json" {
				return output.JSON(a.Out, rows)
			}
			return output.Rows(a.Out, rows, []output.Column{
				{Header: "ID", Key: "id"},
				{Header: "NAME", Key: "name"},
				{Header: "METRIC", Key: "metric"},
				{Header: "OP", Key: "operator"},
				{Header: "THRESHOLD", Key: "threshold"},
				{Header: "UNIT", Key: "thresholdUnit"},
				{Header: "DURATION", Key: "duration"},
				{Header: "ENABLED", Key: "enabled"},
				{Header: "CHANNELS", Key: "channels"},
			})
		},
	}
	cmd.Flags().StringVar(&cluster, "cluster", "", "cluster id")
	cmd.Flags().StringVar(&search, "search", "", "alert rule search")
	cmd.Flags().BoolVar(&enabled, "enabled", false, "filter alert rules by enabled state")
	return cmd
}
