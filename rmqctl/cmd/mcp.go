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
	"encoding/json"
	"fmt"
	"io"
	"net/url"
	"strings"
	"text/tabwriter"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/spf13/cobra"
)

const mcpStdioMaxLineBytes = 16 * 1024 * 1024

func (a *App) newMcpCommand(opts *globalOptions) *cobra.Command {
	root := &cobra.Command{
		Use:   "mcp",
		Short: "Run MCP protocol adapters",
	}
	root.AddCommand(a.newMcpStdioCommand(opts))
	root.AddCommand(a.newMcpConfigCommand(opts))
	root.AddCommand(a.newMcpDoctorCommand(opts))
	return root
}

func (a *App) newMcpStdioCommand(opts *globalOptions) *cobra.Command {
	var cluster string
	cmd := &cobra.Command{
		Use:   "stdio",
		Short: "Proxy JSON-RPC MCP stdio traffic to Studio Server",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := a.resolveTarget(opts, cluster)
			if err != nil {
				return err
			}
			client := a.studioClient()
			session, err := client.OpenMCPSSESession(target)
			if err != nil {
				return err
			}
			defer session.Close()
			scanner := bufio.NewScanner(a.In)
			scanner.Buffer(make([]byte, 0, 64*1024), mcpStdioMaxLineBytes)
			for scanner.Scan() {
				line := strings.TrimSpace(scanner.Text())
				if line == "" {
					continue
				}
				response, ok, err := session.Call([]byte(line))
				if err != nil {
					return err
				}
				if ok {
					fmt.Fprintln(a.Out, string(response))
				}
			}
			return scanner.Err()
		},
	}
	cmd.Flags().StringVar(&cluster, "cluster", "", "RocketMQ cluster id for MCP tool discovery and default tool calls")
	return cmd
}

func (a *App) newMcpDoctorCommand(opts *globalOptions) *cobra.Command {
	var cluster string
	var tool string
	var skipCall bool
	var transport string
	cmd := &cobra.Command{
		Use:   "doctor",
		Short: "Run MCP endpoint smoke checks against Studio Server",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			transport = strings.ToLower(strings.TrimSpace(transport))
			if err := requireMcpDoctorTransport(transport); err != nil {
				return err
			}
			target, err := a.resolveTarget(opts, cluster)
			if err != nil {
				return err
			}
			report := a.runMcpDoctor(target, transport, firstNonEmpty(tool, defaultMcpDoctorTool(target.Cluster)), !skipCall)
			if opts.output == "json" {
				if err := output.JSON(a.Out, report); err != nil {
					return err
				}
			} else if err := mcpDoctorTable(a.Out, report); err != nil {
				return err
			}
			if !report.Passed {
				return fmt.Errorf("mcp doctor failed")
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&cluster, "cluster", "", "RocketMQ cluster id for MCP tool discovery and default tool calls")
	cmd.Flags().StringVar(&tool, "tool", "", "tool name to call during smoke validation")
	cmd.Flags().StringVar(&transport, "transport", mcpDoctorSSETransport,
		"MCP transport to validate: http, sse, or sse-session")
	cmd.Flags().BoolVar(&skipCall, "skip-call", false, "skip MCP tools/call validation")
	return cmd
}

func (a *App) newMcpConfigCommand(opts *globalOptions) *cobra.Command {
	var name string
	var command string
	var client string
	var cluster string
	var transport string
	cmd := &cobra.Command{
		Use:   "config",
		Short: "Print an MCP client configuration snippet",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if opts.token != "" {
				return fmt.Errorf("mcp config refuses --token because it would write a secret into client config; use --token-env")
			}
			if name == "" {
				name = "rocketmq-studio"
			}
			if command == "" {
				command = "rmqctl"
			}
			transport = strings.ToLower(strings.TrimSpace(transport))
			if err := requireMcpConfigTransport(transport); err != nil {
				return err
			}
			config, err := mcpClientConfig(mcpClientConfigOptions{
				Client:    client,
				Name:      name,
				Command:   command,
				Transport: transport,
				Server:    opts.server,
				Cluster:   cluster,
				TokenEnv:  opts.tokenEnv,
				StdioArgs: mcpStdioArgs(opts, cluster),
			})
			if err != nil {
				return err
			}
			encoder := json.NewEncoder(a.Out)
			encoder.SetIndent("", "  ")
			return encoder.Encode(config)
		},
	}
	cmd.Flags().StringVar(&name, "name", "rocketmq-studio", "MCP server name in the client config")
	cmd.Flags().StringVar(&command, "command", "rmqctl", "rmqctl executable path used by the MCP client")
	cmd.Flags().StringVar(&client, "client", "generic", "MCP client template: generic, claude, claude-desktop, cursor, qoder")
	cmd.Flags().StringVar(&cluster, "cluster", "", "RocketMQ cluster id for MCP tool discovery and default tool calls")
	cmd.Flags().StringVar(&transport, "transport", mcpConfigStdioTransport, "MCP client transport: stdio or sse")
	return cmd
}

type mcpClientConfigOptions struct {
	Client    string
	Name      string
	Command   string
	Transport string
	Server    string
	Cluster   string
	TokenEnv  string
	StdioArgs []string
}

func mcpClientConfig(options mcpClientConfigOptions) (map[string]any, error) {
	template := strings.ToLower(strings.TrimSpace(options.Client))
	if template == "" {
		template = "generic"
	}
	switch template {
	case "generic", "claude", "claude-desktop", "cursor", "qoder":
		server := map[string]any{}
		switch options.Transport {
		case mcpConfigStdioTransport:
			server["command"] = options.Command
			server["args"] = options.StdioArgs
		case mcpConfigSSETransport:
			server["url"] = mcpSSEURL(options.Server, options.Cluster)
			if options.TokenEnv != "" {
				server["headers"] = map[string]any{
					"Authorization": "Bearer ${" + options.TokenEnv + "}",
				}
			}
		}
		return map[string]any{
			"mcpServers": map[string]any{
				options.Name: server,
			},
		}, nil
	default:
		return nil, fmt.Errorf("unsupported MCP client template: %s", options.Client)
	}
}

func mcpStdioArgs(opts *globalOptions, cluster string) []string {
	args := []string{"mcp", "stdio"}
	if opts.configPath != "" {
		args = append(args, "--config", opts.configPath)
	}
	if opts.profile != "" {
		args = append(args, "--profile", opts.profile)
	}
	if opts.server != "" {
		args = append(args, "--server", opts.server)
	}
	if cluster != "" {
		args = append(args, "--cluster", cluster)
	}
	if opts.tokenEnv != "" {
		args = append(args, "--token-env", opts.tokenEnv)
	}
	if opts.timeout != defaultTimeout {
		args = append(args, "--timeout", opts.timeout.String())
	}
	if opts.debug {
		args = append(args, "--debug")
	}
	return args
}

type mcpDoctorReport struct {
	Server    string           `json:"server"`
	Transport string           `json:"transport"`
	Cluster   string           `json:"cluster,omitempty"`
	Tool      string           `json:"tool,omitempty"`
	ToolCount int              `json:"toolCount"`
	ToolNames []string         `json:"toolNames,omitempty"`
	Passed    bool             `json:"passed"`
	Checks    []mcpDoctorCheck `json:"checks"`
}

type mcpDoctorCheck struct {
	Name   string `json:"name"`
	Status string `json:"status"`
	Detail string `json:"detail"`
}

type mcpRPCResponse struct {
	Result json.RawMessage `json:"result"`
	Error  *mcpRPCError    `json:"error"`
}

type mcpRPCError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

type mcpToolsListResult struct {
	Tools []mcpToolSchema `json:"tools"`
}

type mcpToolSchema struct {
	Name string `json:"name"`
}

type mcpToolCallResult struct {
	IsError bool                  `json:"isError"`
	Content []mcpToolCallContent  `json:"content"`
	Meta    mcpToolCallResultMeta `json:"_meta"`
}

type mcpToolCallContent struct {
	Type string `json:"type"`
	Text string `json:"text"`
}

type mcpToolCallResultMeta struct {
	RequestID      string `json:"requestId"`
	ToolName       string `json:"toolName"`
	Source         string `json:"source"`
	OperationLevel string `json:"operationLevel"`
	Policy         string `json:"policy"`
	PolicyReason   string `json:"policyReason"`
	ErrorCode      string `json:"errorCode"`
	Message        string `json:"message"`
	Executed       *bool  `json:"executed"`
	DryRun         *bool  `json:"dryRun"`
}

type mcpToolExecutionText struct {
	ToolName  string `json:"toolName"`
	Source    string `json:"source"`
	Executed  *bool  `json:"executed"`
	ErrorCode string `json:"errorCode"`
	Message   string `json:"message"`
}

const mcpDoctorPass = "PASS"
const mcpDoctorFail = "FAIL"
const mcpDoctorHTTPTransport = "http"
const mcpDoctorSSETransport = "sse"
const mcpDoctorSSESessionTransport = "sse-session"
const mcpConfigStdioTransport = "stdio"
const mcpConfigSSETransport = "sse"

type mcpDoctorCaller func(studio.Target, json.RawMessage) (json.RawMessage, bool, error)

func (a *App) runMcpDoctor(target studio.Target, transport string, tool string, callTool bool) mcpDoctorReport {
	report := mcpDoctorReport{
		Server:    target.Server,
		Transport: transport,
		Cluster:   target.Cluster,
		Tool:      tool,
		Passed:    true,
	}
	client := a.studioClient()
	caller, closeCaller, err := mcpDoctorTransportCaller(client, transport, target)
	if err != nil {
		report.addCheck("transport", mcpDoctorFail, err.Error())
		return report
	}
	defer closeCaller()
	if !runMcpInitializeCheck(caller, target, &report) {
		return report
	}
	if !runMcpNotificationCheck(caller, target, &report) {
		return report
	}
	if !runMcpToolsListCheck(caller, target, &report) {
		return report
	}
	if callTool {
		runMcpToolCallCheck(caller, target, tool, &report)
	}
	return report
}

func runMcpInitializeCheck(caller mcpDoctorCaller, target studio.Target, report *mcpDoctorReport) bool {
	response, ok, err := caller(target, mustMcpPayload(map[string]any{
		"jsonrpc": "2.0",
		"id":      1,
		"method":  "initialize",
		"params": map[string]any{
			"protocolVersion": "2024-11-05",
			"clientInfo": map[string]any{
				"name":    "rmqctl-mcp-doctor",
				"version": CLIVersion,
			},
		},
	}))
	if err != nil {
		report.addCheck("initialize", mcpDoctorFail, err.Error())
		return false
	}
	rpc, err := successfulMcpResponse(response, ok)
	if err != nil {
		report.addCheck("initialize", mcpDoctorFail, err.Error())
		return false
	}
	var result map[string]any
	if err := json.Unmarshal(rpc.Result, &result); err != nil {
		report.addCheck("initialize", mcpDoctorFail, "invalid result: "+err.Error())
		return false
	}
	report.addCheck("initialize", mcpDoctorPass, fmt.Sprintf("protocol=%v", result["protocolVersion"]))
	return true
}

func runMcpToolsListCheck(caller mcpDoctorCaller, target studio.Target, report *mcpDoctorReport) bool {
	response, ok, err := caller(target, mustMcpPayload(map[string]any{
		"jsonrpc": "2.0",
		"id":      2,
		"method":  "tools/list",
		"params":  map[string]any{},
	}))
	if err != nil {
		report.addCheck("tools/list", mcpDoctorFail, err.Error())
		return false
	}
	rpc, err := successfulMcpResponse(response, ok)
	if err != nil {
		report.addCheck("tools/list", mcpDoctorFail, err.Error())
		return false
	}
	var result mcpToolsListResult
	if err := json.Unmarshal(rpc.Result, &result); err != nil {
		report.addCheck("tools/list", mcpDoctorFail, "invalid result: "+err.Error())
		return false
	}
	report.ToolCount = len(result.Tools)
	report.ToolNames = make([]string, 0, len(result.Tools))
	for _, tool := range result.Tools {
		report.ToolNames = append(report.ToolNames, tool.Name)
	}
	if !containsString(report.ToolNames, "rmq.cluster.list") {
		report.addCheck("tools/list", mcpDoctorFail, "missing rmq.cluster.list")
		return false
	}
	if target.Cluster != "" && !containsString(report.ToolNames, "rmq.capabilities") {
		report.addCheck("tools/list", mcpDoctorFail, "cluster-scoped tools are not visible")
		return false
	}
	report.addCheck("tools/list", mcpDoctorPass, fmt.Sprintf("%d tools", report.ToolCount))
	return true
}

func runMcpNotificationCheck(caller mcpDoctorCaller, target studio.Target, report *mcpDoctorReport) bool {
	response, ok, err := caller(target, mustMcpPayload(map[string]any{
		"jsonrpc": "2.0",
		"method":  "notifications/initialized",
		"params":  map[string]any{},
	}))
	if err != nil {
		report.addCheck("notification", mcpDoctorFail, err.Error())
		return false
	}
	if ok || len(response) > 0 {
		report.addCheck("notification", mcpDoctorFail, "expected no JSON-RPC response")
		return false
	}
	report.addCheck("notification", mcpDoctorPass, "no stdout response")
	return true
}

func runMcpToolCallCheck(caller mcpDoctorCaller, target studio.Target, tool string, report *mcpDoctorReport) {
	response, ok, err := caller(target, mustMcpPayload(map[string]any{
		"jsonrpc": "2.0",
		"id":      3,
		"method":  "tools/call",
		"params": map[string]any{
			"name":      tool,
			"arguments": mcpDoctorToolArguments(target, tool),
		},
	}))
	if err != nil {
		report.addCheck("tools/call", mcpDoctorFail, err.Error())
		return
	}
	rpc, err := successfulMcpResponse(response, ok)
	if err != nil {
		report.addCheck("tools/call", mcpDoctorFail, err.Error())
		return
	}
	var result mcpToolCallResult
	if err := json.Unmarshal(rpc.Result, &result); err != nil {
		report.addCheck("tools/call", mcpDoctorFail, "invalid result: "+err.Error())
		return
	}
	execution := mcpToolExecutionText{}
	if len(result.Content) > 0 && result.Content[0].Text != "" {
		_ = json.Unmarshal([]byte(result.Content[0].Text), &execution)
	}
	errorCode := firstNonEmpty(result.Meta.ErrorCode, execution.ErrorCode)
	if result.IsError {
		if errorCode != "" {
			report.addCheck("tools/call", mcpDoctorFail,
				mcpToolErrorDetail(errorCode, firstNonEmpty(result.Meta.Message, execution.Message)))
			return
		}
		report.addCheck("tools/call", mcpDoctorFail, "tool returned isError=true")
		return
	}
	if errorCode != "" {
		report.addCheck("tools/call", mcpDoctorFail,
			mcpToolErrorDetail(errorCode, firstNonEmpty(result.Meta.Message, execution.Message)))
		return
	}
	report.addCheck("tools/call", mcpDoctorPass, mcpToolSuccessDetail(tool, result.Meta, execution))
}

func mcpToolErrorDetail(errorCode string, message string) string {
	if message == "" {
		return errorCode
	}
	return errorCode + ": " + message
}

func mcpToolSuccessDetail(defaultTool string, meta mcpToolCallResultMeta, execution mcpToolExecutionText) string {
	toolName := firstNonEmpty(meta.ToolName, execution.ToolName, defaultTool)
	parts := []string{}
	if meta.RequestID != "" {
		parts = append(parts, "requestId="+meta.RequestID)
	}
	if source := firstNonEmpty(meta.Source, execution.Source); source != "" {
		parts = append(parts, "source="+source)
	}
	if executed := firstBool(meta.Executed, execution.Executed); executed != nil {
		parts = append(parts, fmt.Sprintf("executed=%t", *executed))
	}
	if meta.Policy != "" {
		parts = append(parts, "policy="+meta.Policy)
	}
	if len(parts) == 0 {
		return toolName
	}
	return toolName + " " + strings.Join(parts, " ")
}

func (report *mcpDoctorReport) addCheck(name string, status string, detail string) {
	if status != mcpDoctorPass {
		report.Passed = false
	}
	report.Checks = append(report.Checks, mcpDoctorCheck{
		Name:   name,
		Status: status,
		Detail: detail,
	})
}

func mcpDoctorTable(w io.Writer, report mcpDoctorReport) error {
	fmt.Fprintf(w, "SERVER: %s\n", report.Server)
	fmt.Fprintf(w, "TRANSPORT: %s\n", report.Transport)
	if report.Cluster != "" {
		fmt.Fprintf(w, "CLUSTER: %s\n", report.Cluster)
	}
	if report.Tool != "" {
		fmt.Fprintf(w, "TOOL: %s\n", report.Tool)
	}
	fmt.Fprintf(w, "TOOLS: %d\n\n", report.ToolCount)
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(tw, "CHECK\tSTATUS\tDETAIL")
	for _, check := range report.Checks {
		fmt.Fprintf(tw, "%s\t%s\t%s\n", check.Name, check.Status, check.Detail)
	}
	return tw.Flush()
}

func successfulMcpResponse(response json.RawMessage, ok bool) (mcpRPCResponse, error) {
	if !ok || len(response) == 0 {
		return mcpRPCResponse{}, fmt.Errorf("empty JSON-RPC response")
	}
	var rpc mcpRPCResponse
	if err := json.Unmarshal(response, &rpc); err != nil {
		return mcpRPCResponse{}, fmt.Errorf("invalid JSON-RPC response: %w", err)
	}
	if rpc.Error != nil {
		return mcpRPCResponse{}, fmt.Errorf("JSON-RPC error %d: %s", rpc.Error.Code, rpc.Error.Message)
	}
	if len(rpc.Result) == 0 || string(rpc.Result) == "null" {
		return mcpRPCResponse{}, fmt.Errorf("JSON-RPC result is empty")
	}
	return rpc, nil
}

func mustMcpPayload(value any) json.RawMessage {
	payload, err := json.Marshal(value)
	if err != nil {
		panic(err)
	}
	return payload
}

func defaultMcpDoctorTool(cluster string) string {
	if cluster != "" {
		return "rmq.capabilities"
	}
	return "rmq.cluster.list"
}

func mcpDoctorToolArguments(target studio.Target, tool string) map[string]any {
	if target.Cluster == "" {
		return map[string]any{}
	}
	switch tool {
	case "rmq.capabilities",
		"rmq.dashboard.summary",
		"rmq.topic.list",
		"rmq.group.list",
		"rmq.alert.rule.list":
		return map[string]any{
			"cluster": target.Cluster,
		}
	default:
		return map[string]any{}
	}
}

func requireMcpDoctorTransport(transport string) error {
	switch transport {
	case mcpDoctorHTTPTransport, mcpDoctorSSETransport, mcpDoctorSSESessionTransport:
		return nil
	default:
		return fmt.Errorf("unsupported MCP doctor transport: %s", transport)
	}
}

func requireMcpConfigTransport(transport string) error {
	switch transport {
	case mcpConfigStdioTransport, mcpConfigSSETransport:
		return nil
	default:
		return fmt.Errorf("unsupported MCP config transport: %s", transport)
	}
}

func mcpDoctorTransportCaller(
	client studio.Client,
	transport string,
	target studio.Target,
) (mcpDoctorCaller, func(), error) {
	switch transport {
	case mcpDoctorSSETransport, mcpDoctorSSESessionTransport:
		session, err := client.OpenMCPSSESession(target)
		if err != nil {
			return nil, nil, err
		}
		return func(_ studio.Target, payload json.RawMessage) (json.RawMessage, bool, error) {
			return session.Call(payload)
		}, session.Close, nil
	default:
		return client.CallMCP, func() {}, nil
	}
}

func mcpSSEURL(server string, cluster string) string {
	base := strings.TrimRight(firstNonEmpty(server, studio.DefaultServerURL), "/") + "/api/ai/mcp/sse"
	if cluster == "" {
		return base
	}
	values := url.Values{}
	values.Set("cluster", cluster)
	return base + "?" + values.Encode()
}

func containsString(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}

func firstBool(values ...*bool) *bool {
	for _, value := range values {
		if value != nil {
			return value
		}
	}
	return nil
}
