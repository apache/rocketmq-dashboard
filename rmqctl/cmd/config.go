/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package cmd

import (
	"fmt"
	"strings"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

func newConfigCommand(runtime commandRuntime) *cobra.Command {
	root := &cobra.Command{Use: "config", Short: "Manage local rmqctl contexts"}
	root.AddCommand(newConfigSetContextCommand(runtime))
	root.AddCommand(newConfigUseContextCommand(runtime))
	root.AddCommand(newConfigDeleteContextCommand(runtime))
	root.AddCommand(newConfigGetContextsCommand(runtime))
	root.AddCommand(newConfigCurrentContextCommand(runtime))
	return root
}

func newConfigSetContextCommand(runtime commandRuntime) *cobra.Command {
	var server string
	var cluster string
	var accessKeyEnv string
	var secretKeyEnv string
	cmd := &cobra.Command{
		Use:   "set-context <name>",
		Short: "Create or update a complete Studio access context",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			path, cfg, err := runtime.configForWrite()
			if err != nil {
				return err
			}
			name := args[0]
			// Map zero-value lookup yields an empty Context for new entries,
			// so the per-flag Changed branches below fill it incrementally.
			contextValue, exists := cfg.Contexts[name]
			if !exists {
				if missing := missingFlags(cmd.Flags(), newContextRequiredFlags); len(missing) > 0 {
					return invalidArgument(
						"new context requires --" + strings.Join(missing, ", --"))
				}
			}
			if cmd.Flags().Changed("server") {
				contextValue.Server = server
			}
		if cmd.Flags().Changed("cluster") {
			contextValue.Cluster = cluster
		}
			if cmd.Flags().Changed("access-key-env") {
				contextValue.Credential.AccessKeyRef = "env:" + accessKeyEnv
			}
			if cmd.Flags().Changed("secret-key-env") {
				contextValue.Credential.SecretKeyRef = "env:" + secretKeyEnv
			}
			cfg.Contexts[name] = contextValue
			if err := runtime.store.Save(path, cfg); err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if output.IsStructured(runtime.options.output) {
				return output.Structured(out, runtime.options.output, map[string]any{"context": name, "created": !exists})
			}
			fmt.Fprintf(out, "updated context %s\n", name)
			return nil
		},
	}
	cmd.Flags().StringVar(&server, "server", "", "Studio Server URL")
	cmd.Flags().StringVar(&cluster, "cluster", "", "Studio Instance identifier")
	cmd.Flags().StringVar(&accessKeyEnv, "access-key-env", "", "environment variable containing the RocketMQ access key")
	cmd.Flags().StringVar(&secretKeyEnv, "secret-key-env", "", "environment variable containing the RocketMQ secret key")
	return cmd
}

func newConfigUseContextCommand(runtime commandRuntime) *cobra.Command {
	return &cobra.Command{
		Use:     "use-context <name>",
		Aliases: []string{"use"},
		Short:   "Switch the current context",
		Args:    cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			path, cfg, err := runtime.configForWrite()
			if err != nil {
				return err
			}
			if _, ok := cfg.Contexts[args[0]]; !ok {
				return invalidArgument(fmt.Sprintf("context %q does not exist", args[0]))
			}
			cfg.CurrentContext = args[0]
			if err := runtime.store.Save(path, cfg); err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if output.IsStructured(runtime.options.output) {
				return output.Structured(out, runtime.options.output, map[string]string{"currentContext": args[0]})
			}
			fmt.Fprintf(out, "current context: %s\n", args[0])
			return nil
		},
	}
}

func newConfigDeleteContextCommand(runtime commandRuntime) *cobra.Command {
	return &cobra.Command{
		Use:     "delete-context <name>",
		Aliases: []string{"delete"},
		Short:   "Delete a named rmqctl context",
		Args:    cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			path, cfg, err := runtime.configForWrite()
			if err != nil {
				return err
			}
			name := args[0]
			if _, ok := cfg.Contexts[name]; !ok {
				return invalidArgument(fmt.Sprintf("context %q does not exist", name))
			}
			delete(cfg.Contexts, name)
			if cfg.CurrentContext == name {
				cfg.CurrentContext = ""
			}
			if err := runtime.store.Save(path, cfg); err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if output.IsStructured(runtime.options.output) {
				return output.Structured(out, runtime.options.output, map[string]any{"deleted": name})
			}
			fmt.Fprintf(out, "deleted context %s\n", name)
			return nil
		},
	}
}

func newConfigGetContextsCommand(runtime commandRuntime) *cobra.Command {
	return &cobra.Command{
		Use:   "get-contexts",
		Short: "List local rmqctl contexts",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			cfg, err := runtime.loadConfig()
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if output.IsStructured(runtime.options.output) {
				return output.Structured(out, runtime.options.output, cfg)
			}
			return output.ConfigTable(out, cfg)
		},
	}
}

func newConfigCurrentContextCommand(runtime commandRuntime) *cobra.Command {
	return &cobra.Command{
		Use:   "current-context",
		Short: "Print the current rmqctl context name",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			cfg, err := runtime.loadConfig()
			if err != nil {
				return err
			}
			if cfg.CurrentContext == "" {
				return invalidArgument("no current context is selected; use 'config use-context' to select one")
			}
			out := cmd.OutOrStdout()
			if output.IsStructured(runtime.options.output) {
				return output.Structured(out, runtime.options.output, map[string]string{"currentContext": cfg.CurrentContext})
			}
			fmt.Fprintln(out, cfg.CurrentContext)
			return nil
		},
	}
}

// newContextRequiredFlags lists flags that must all be set when creating a
// brand-new context. Updates to an existing context may set any subset.
var newContextRequiredFlags = []string{
	"server", "cluster", "access-key-env", "secret-key-env",
}

func missingFlags(flags *pflag.FlagSet, required []string) []string {
	var missing []string
	for _, name := range required {
		if !flags.Changed(name) {
			missing = append(missing, name)
		}
	}
	return missing
}

func (r commandRuntime) configForWrite() (string, config.Config, error) {
	path, err := r.store.Path(r.options.configPath)
	if err != nil {
		return "", config.Config{}, err
	}
	cfg, err := r.store.Load(path)
	if err != nil {
		return "", config.Config{}, err
	}
	return path, cfg, nil
}
