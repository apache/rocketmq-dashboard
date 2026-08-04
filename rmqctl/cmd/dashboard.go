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
	"io"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

func (a *App) newDashboardCommand(opts *globalOptions) *cobra.Command {
	root := &cobra.Command{
		Use:   "dashboard",
		Short: "Query RocketMQ Studio dashboard data",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	root.AddCommand(a.newDashboardSummaryCommand(opts))
	return root
}

func (a *App) newDashboardSummaryCommand(opts *globalOptions) *cobra.Command {
	var cluster string
	cmd := &cobra.Command{
		Use:   "summary",
		Short: "Show dashboard summary",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			target, err := a.resolveTarget(opts, cluster)
			if err != nil {
				return err
			}
			resolved, err := resolvedCluster("dashboard summary", cluster, target)
			if err != nil {
				return err
			}
			result, err := a.studioClient().CallTool(target, "rmq.dashboard.summary", map[string]any{
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
			return writeDashboardSummary(a.Out, result.Result)
		},
	}
	cmd.Flags().StringVar(&cluster, "cluster", "", "cluster id")
	return cmd
}

func writeDashboardSummary(w io.Writer, result any) error {
	payload, ok := result.(map[string]any)
	if !ok {
		return output.JSON(w, result)
	}
	fmt.Fprintln(w, "CLUSTER")
	if err := output.Rows(w, output.MapsFromAny(payload["cluster"]), []output.Column{
		{Header: "ID", Key: "id"},
		{Header: "NAME", Key: "name"},
		{Header: "TYPE", Key: "type"},
		{Header: "STATUS", Key: "status"},
		{Header: "BROKERS", Key: "brokers"},
		{Header: "PROXIES", Key: "proxies"},
		{Header: "TOPICS", Key: "topics"},
		{Header: "GROUPS", Key: "groups"},
		{Header: "TPS IN", Key: "tpsIn"},
		{Header: "TPS OUT", Key: "tpsOut"},
		{Header: "VERSION", Key: "version"},
	}); err != nil {
		return err
	}
	fmt.Fprintln(w)
	fmt.Fprintln(w, "STATS")
	return output.Rows(w, output.MapsFromAny(payload["stats"]), []output.Column{
		{Header: "CLUSTERS", Key: "totalClusters"},
		{Header: "HEALTHY", Key: "healthyClusters"},
		{Header: "BROKERS", Key: "totalBrokers"},
		{Header: "PROXIES", Key: "totalProxies"},
		{Header: "NAMESERVERS", Key: "totalNameServers"},
		{Header: "TOPICS", Key: "totalTopics"},
		{Header: "GROUPS", Key: "totalConsumerGroups"},
		{Header: "TPS IN", Key: "tpsIn"},
		{Header: "TPS OUT", Key: "tpsOut"},
	})
}
