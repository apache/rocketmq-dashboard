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
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/spf13/cobra"
)

type option struct {
	context    string
	configPath string
	output     string
	timeout    time.Duration
	yes        bool
}

func (a *App) newCommand(opts *option) (*cobra.Command, error) {
	cmd := &cobra.Command{
		Use:           "rmqctl",
		Short:         "RocketMQ Studio CLI",
		SilenceUsage:  true,
		SilenceErrors: true,
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			return output.RequireFormat(opts.output)
		},
	}
	cmd.PersistentFlags().StringVar(&opts.context, "context", "", "config context name")
	cmd.PersistentFlags().StringVar(&opts.configPath, "config", "", "config file path")
	cmd.PersistentFlags().DurationVar(&opts.timeout, "timeout", studio.DefaultTimeout, "request timeout")
	cmd.PersistentFlags().StringVarP(&opts.output, "output", "o", output.FormatTable, "output format: table, json, or yaml")
	cmd.PersistentFlags().BoolVarP(&opts.yes, "yes", "y", false, "skip the interactive confirmation prompt for L2/L3 operations")

	client := studio.NewClient(a.HTTP)
	runtime := commandRuntime{client: client, store: a.Store, options: opts, confirm: a.confirm}
	catalogCommands, err := newCatalogCommands(runtime)
	if err != nil {
		return nil, err
	}
	cmd.AddCommand(a.newVersionCommand(opts))
	cmd.AddCommand(a.newExplainCommand(opts))
	cmd.AddCommand(newConfigCommand(runtime))
	cmd.AddCommand(a.newMCPCommand(runtime))
	cmd.AddCommand(catalogCommands...)
	return cmd, nil
}
