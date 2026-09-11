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
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

type commandRuntime struct {
	client  studio.Client
	store   config.Store
	options *option
	confirm confirmFunc
}

func (r commandRuntime) resolveTarget() (studio.Target, error) {
	cfg, err := r.loadConfig()
	if err != nil {
		return studio.Target{}, err
	}
	name, err := config.ContextName(r.options.context, cfg)
	if err != nil {
		return studio.Target{}, err
	}
	contextConfig := cfg.Contexts[name]
	credential, err := config.ResolveCredential(contextConfig.Credential, r.store.Getenv)
	if err != nil {
		return studio.Target{}, types.NewCLIError(
			types.CodeInvalidArgument,
			err.Error(),
			"Set the access-key and secret-key environment variables referenced by the context.")
	}
	return studio.Target{
		Server:     contextConfig.Server,
		Cluster:    contextConfig.Cluster,
		Credential: studio.Credential{AccessKey: credential.AccessKey, SecretKey: credential.SecretKey},
		Timeout:    r.options.timeout,
	}, nil
}

// targetServer returns the configured Studio Server URL for the active context
// without resolving credentials. It is used by confirmation prompts so the user
// can see where the dangerous request will be sent before approving it.
func (r commandRuntime) targetServer() string {
	cfg, err := r.loadConfig()
	if err != nil {
		return "Studio Server"
	}
	name, err := config.ContextName(r.options.context, cfg)
	if err != nil {
		return "Studio Server"
	}
	contextConfig, ok := cfg.Contexts[name]
	if !ok || contextConfig.Server == "" {
		return "Studio Server"
	}
	return contextConfig.Server
}

func (r commandRuntime) loadConfig() (config.Config, error) {
	path, err := r.store.Path(r.options.configPath)
	if err != nil {
		return config.Config{}, err
	}
	return r.store.Load(path)
}
