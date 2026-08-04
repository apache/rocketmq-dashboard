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

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

func (a *App) newClusterCommand(opts *globalOptions) *cobra.Command {
	root := &cobra.Command{
		Use:   "cluster",
		Short: "Query RocketMQ clusters",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	root.AddCommand(a.newClusterListCommand(opts))
	root.AddCommand(a.newClusterCapabilitiesCommand(opts))
	return root
}

func (a *App) newClusterListCommand(opts *globalOptions) *cobra.Command {
	var status string
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List clusters",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			target, err := a.resolveTarget(opts, "")
			if err != nil {
				return err
			}
			result, err := a.studioClient().CallTool(target, "rmq.cluster.list", map[string]any{}, false, false)
			if err != nil {
				return err
			}
			if result.ErrorCode != "" {
				return fmt.Errorf("%s: %s", result.ErrorCode, result.Message)
			}
			rows := output.MapsFromAny(result.Result)
			if status != "" {
				rows = output.FilterRows(rows, "status", status)
			}
			if opts.output == "json" {
				return output.JSON(a.Out, rows)
			}
			return output.Rows(a.Out, rows, []output.Column{
				{Header: "ID", Key: "id"},
				{Header: "NAME", Key: "name"},
				{Header: "TYPE", Key: "type"},
				{Header: "STATUS", Key: "status"},
				{Header: "VERSION", Key: "version"},
			})
		},
	}
	cmd.Flags().StringVar(&status, "status", "", "filter clusters by status")
	return cmd
}

func (a *App) newClusterCapabilitiesCommand(opts *globalOptions) *cobra.Command {
	var cluster string
	cmd := &cobra.Command{
		Use:   "capabilities",
		Short: "Show cluster capabilities",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			target, err := a.resolveTarget(opts, cluster)
			if err != nil {
				return err
			}
			resolved, err := resolvedCluster("cluster capabilities", cluster, target)
			if err != nil {
				return err
			}
			result, err := a.studioClient().CallTool(target, "rmq.capabilities", map[string]any{
				"cluster": resolved,
			}, false, false)
			if err != nil {
				return err
			}
			if err := toolResultError(result); err != nil {
				return err
			}
			if opts.output == "json" {
				return output.JSON(a.Out, result.Result)
			}
			return output.Rows(a.Out, output.MapsFromAny(result.Result), []output.Column{
				{Header: "CLUSTER", Key: "cluster"},
				{Header: "TYPE", Key: "type"},
				{Header: "VERSION", Key: "version"},
				{Header: "CAPABILITIES", Key: "capabilities"},
			})
		},
	}
	cmd.Flags().StringVar(&cluster, "cluster", "", "cluster id")
	return cmd
}
