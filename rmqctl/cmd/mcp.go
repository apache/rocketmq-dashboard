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
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/spf13/cobra"
)

func (a *App) newMCPCommand(runtime commandRuntime) *cobra.Command {
	root := &cobra.Command{
		Use:   "mcp",
		Short: "Run MCP protocol adapters",
	}
	root.AddCommand(a.newMCPStdioCommand(runtime))
	root.AddCommand(a.newMCPConfigCommand(runtime))
	return root
}

func (a *App) newMCPStdioCommand(runtime commandRuntime) *cobra.Command {
	// The adapter follows the newline-delimited MCP stdio transport:
	// https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#stdio
	cmd := &cobra.Command{
		Use:   "stdio",
		Short: "Proxy JSON-RPC MCP stdio traffic to Studio Server",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := runtime.resolveTarget()
			if err != nil {
				return err
			}
			session, err := runtime.client.NewMCPClientSession(target)
			if err != nil {
				return err
			}
			return runStdioProxy(cmd.Context(), cmd.InOrStdin(), cmd.OutOrStdout(), cmd.ErrOrStderr(), session)
		},
	}
	return cmd
}

func (a *App) newMCPConfigCommand(runtime commandRuntime) *cobra.Command {
	var name string
	var command string
	cmd := &cobra.Command{
		Use:   "config",
		Short: "Print an MCP client configuration snippet",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			out := cmd.OutOrStdout()
			stdioArgs := []string{"mcp", "stdio"}
			if runtime.options.configPath != "" {
				stdioArgs = append(stdioArgs, "--config", runtime.options.configPath)
			}
			if runtime.options.context != "" {
				stdioArgs = append(stdioArgs, "--context", runtime.options.context)
			}
			if runtime.options.timeout != studio.DefaultTimeout {
				stdioArgs = append(stdioArgs, "--timeout", runtime.options.timeout.String())
			}
			config := map[string]any{
				"mcpServers": map[string]any{
					name: map[string]any{
						"command": command,
						"args":    stdioArgs,
					},
				},
			}
			if output.IsStructured(runtime.options.output) {
				return output.Structured(out, runtime.options.output, config)
			}
			return output.JSON(out, config)
		},
	}
	cmd.Flags().StringVar(&name, "name", "rocketmq-studio", "MCP server name in the client config")
	cmd.Flags().StringVar(&command, "command", "rmqctl", "rmqctl executable path used by the MCP client")
	return cmd
}
