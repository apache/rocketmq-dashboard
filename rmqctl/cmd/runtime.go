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
	"strings"

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
	// The instance identifier must be supplied explicitly on every invocation
	// (decision 7: no default injection from the context or any other config).
	instanceID := strings.TrimSpace(r.options.instanceID)
	if instanceID == "" {
		return studio.Target{}, types.NewCLIError(
			types.CodeInvalidArgument,
			"--instance-id is required",
			"Pass the Studio Instance identifier explicitly, e.g. --instance-id my-instance; "+
				"rmqctl never defaults it from the context.")
	}
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
		InstanceID: instanceID,
		Credential: studio.Credential{AccessKey: credential.AccessKey, SecretKey: credential.SecretKey},
		Timeout:    r.options.timeout,
	}, nil
}

func (r commandRuntime) loadConfig() (config.Config, error) {
	path, err := r.store.Path(r.options.configPath)
	if err != nil {
		return config.Config{}, err
	}
	return r.store.Load(path)
}
