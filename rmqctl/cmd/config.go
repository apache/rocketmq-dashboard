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

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

func (a *App) newConfigCommand(opts *globalOptions) *cobra.Command {
	root := &cobra.Command{
		Use:   "config",
		Short: "Manage local rmqctl profiles",
	}
	root.AddCommand(a.newConfigSetCommand(opts))
	root.AddCommand(a.newConfigUseCommand(opts))
	root.AddCommand(a.newConfigShowCommand(opts))
	return root
}

func (a *App) newConfigSetCommand(opts *globalOptions) *cobra.Command {
	return &cobra.Command{
		Use:   "set <server|token|cluster> <value>",
		Short: "Set a profile value",
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			path, cfg, err := a.configForWrite(opts)
			if err != nil {
				return err
			}
			name := config.ProfileName(opts.profile, cfg)
			p := cfg.Profiles[name]
			switch args[0] {
			case "server":
				p.Server = args[1]
			case "token":
				p.TokenRef = args[1]
			case "cluster":
				p.Cluster = args[1]
			default:
				return fmt.Errorf("unsupported config key: %s", args[0])
			}
			cfg.Profiles[name] = p
			if cfg.CurrentProfile == "" {
				cfg.CurrentProfile = name
			}
			if err := a.Store.Save(path, cfg); err != nil {
				return err
			}
			fmt.Fprintf(a.Out, "updated profile %s\n", name)
			return nil
		},
	}
}

func (a *App) newConfigUseCommand(opts *globalOptions) *cobra.Command {
	return &cobra.Command{
		Use:   "use <profile>",
		Short: "Switch the current profile",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			path, cfg, err := a.configForWrite(opts)
			if err != nil {
				return err
			}
			if _, ok := cfg.Profiles[args[0]]; !ok {
				cfg.Profiles[args[0]] = config.Profile{}
			}
			cfg.CurrentProfile = args[0]
			if err := a.Store.Save(path, cfg); err != nil {
				return err
			}
			fmt.Fprintf(a.Out, "current profile: %s\n", args[0])
			return nil
		},
	}
}

func (a *App) newConfigShowCommand(opts *globalOptions) *cobra.Command {
	return &cobra.Command{
		Use:   "show",
		Short: "Show local rmqctl profiles",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := requireOutputFormat(opts.output); err != nil {
				return err
			}
			cfg, err := a.loadConfig(opts)
			if err != nil {
				return err
			}
			if opts.output == "json" {
				return output.JSON(a.Out, cfg)
			}
			return output.ConfigTable(a.Out, cfg)
		},
	}
}

func (a *App) configForWrite(opts *globalOptions) (string, config.Config, error) {
	store := a.configStore()
	path, err := store.Path(opts.configPath)
	if err != nil {
		return "", config.Config{}, err
	}
	cfg, err := store.Load(path)
	if err != nil {
		return "", config.Config{}, err
	}
	config.Normalize(&cfg)
	return path, cfg, nil
}
