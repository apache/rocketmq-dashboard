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
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
	"github.com/spf13/cobra"
)

const (
	defaultTimeout = 30 * time.Second
)

var CLIVersion = "0.1.0"

type App struct {
	In     io.Reader
	Out    io.Writer
	Err    io.Writer
	HTTP   *http.Client
	Store  config.Store
	Getenv func(string) string
}

type globalOptions struct {
	server     string
	token      string
	tokenEnv   string
	profile    string
	configPath string
	output     string
	timeout    time.Duration
	debug      bool
}

func NewApp(out io.Writer, err io.Writer) *App {
	store := config.NewStore()
	return &App{
		In:     os.Stdin,
		Out:    out,
		Err:    err,
		HTTP:   http.DefaultClient,
		Store:  store,
		Getenv: os.Getenv,
	}
}

func (a *App) Execute(args []string) int {
	root := a.newRootCommand()
	root.SetArgs(args)
	root.SetOut(a.Out)
	root.SetErr(a.Err)
	if len(args) == 0 {
		_ = root.Help()
		return 2
	}
	if err := root.Execute(); err != nil {
		fmt.Fprintln(a.Err, "error:", err)
		return 1
	}
	return 0
}

func (a *App) newRootCommand() *cobra.Command {
	opts := &globalOptions{output: "table"}
	root := &cobra.Command{
		Use:           "rmqctl",
		Short:         "RocketMQ Studio thin CLI",
		SilenceUsage:  true,
		SilenceErrors: true,
	}
	root.PersistentFlags().StringVar(&opts.server, "server", "", "Studio Server URL")
	root.PersistentFlags().StringVar(&opts.token, "token", "", "bearer token value")
	root.PersistentFlags().StringVar(&opts.tokenEnv, "token-env", "", "read bearer token from an environment variable")
	root.PersistentFlags().StringVar(&opts.profile, "profile", "", "config profile name")
	root.PersistentFlags().StringVar(&opts.configPath, "config", "", "config file path")
	root.PersistentFlags().DurationVar(&opts.timeout, "timeout", defaultTimeout, "request timeout")
	root.PersistentFlags().BoolVar(&opts.debug, "debug", false, "print request debug information")
	root.PersistentFlags().StringVarP(&opts.output, "output", "o", "table", "output format: table or json")

	root.AddCommand(a.newVersionCommand(opts))
	root.AddCommand(a.newConfigCommand(opts))
	root.AddCommand(a.newToolCommand(opts))
	root.AddCommand(a.newClusterCommand(opts))
	root.AddCommand(a.newTopicCommand(opts))
	root.AddCommand(a.newGroupCommand(opts))
	root.AddCommand(a.newDashboardCommand(opts))
	root.AddCommand(a.newAlertRuleCommand(opts))
	root.AddCommand(a.newMcpCommand(opts))
	return root
}

func (a *App) resolveTarget(opts *globalOptions, clusterOverride string) (studio.Target, error) {
	cfg, err := a.loadConfig(opts)
	if err != nil {
		return studio.Target{}, err
	}
	name := config.ProfileName(opts.profile, cfg)
	profileConfig := cfg.Profiles[name]
	server := firstNonEmpty(opts.server, a.Getenv("RMQCTL_SERVER"), profileConfig.Server, studio.DefaultServerURL)
	token, err := a.resolveToken(opts, profileConfig)
	if err != nil {
		return studio.Target{}, err
	}
	return studio.Target{
		Server:  strings.TrimRight(server, "/"),
		Token:   token,
		Cluster: firstNonEmpty(clusterOverride, a.Getenv("RMQCTL_CLUSTER"), profileConfig.Cluster),
		Timeout: opts.timeout,
		Debug:   opts.debug,
	}, nil
}

func (a *App) loadConfig(opts *globalOptions) (config.Config, error) {
	store := a.configStore()
	path, err := store.Path(opts.configPath)
	if err != nil {
		return config.Config{}, err
	}
	return store.Load(path)
}

func (a *App) configStore() config.Store {
	store := a.Store
	if store.Getenv == nil {
		store.Getenv = os.Getenv
	}
	if a.Getenv != nil {
		store.Getenv = a.Getenv
	}
	return store
}

func (a *App) resolveToken(opts *globalOptions, profileConfig config.Profile) (string, error) {
	if opts.token != "" {
		return opts.token, nil
	}
	if opts.tokenEnv != "" {
		return a.Getenv(opts.tokenEnv), nil
	}
	if envToken := a.Getenv("RMQCTL_TOKEN"); envToken != "" {
		return envToken, nil
	}
	if profileConfig.TokenRef == "" {
		return "", nil
	}
	if strings.HasPrefix(profileConfig.TokenRef, "env:") {
		return a.Getenv(strings.TrimPrefix(profileConfig.TokenRef, "env:")), nil
	}
	return profileConfig.TokenRef, nil
}

func (a *App) studioClient() studio.Client {
	client := studio.NewClient(a.Err)
	client.HTTP = a.HTTP
	return client
}

func requireOutputFormat(format string) error {
	if format != "table" && format != "json" {
		return fmt.Errorf("unsupported output format: %s", format)
	}
	return nil
}

func putIfNotEmpty(target map[string]any, key string, value string) {
	if value != "" {
		target[key] = value
	}
}

func putIfPositive(target map[string]any, key string, value int) {
	if value > 0 {
		target[key] = value
	}
}

func resolvedCluster(command string, flagCluster string, target studio.Target) (string, error) {
	cluster := firstNonEmpty(flagCluster, target.Cluster)
	if cluster == "" {
		return "", fmt.Errorf("%s requires --cluster or profile cluster", command)
	}
	return cluster, nil
}

func toolResultError(result types.ToolExecutionResult) error {
	if result.ErrorCode == "" {
		return nil
	}
	return fmt.Errorf("%s: %s", result.ErrorCode, result.Message)
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}
