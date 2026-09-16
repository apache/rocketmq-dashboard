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
	"runtime"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

type versionInfo struct {
	Name      string `json:"name"      yaml:"name"`
	Version   string `json:"version"   yaml:"version"`
	GitCommit string `json:"gitCommit" yaml:"gitCommit"`
	BuildDate string `json:"buildDate" yaml:"buildDate"`
	GoVersion string `json:"goVersion" yaml:"goVersion"`
	Platform  string `json:"platform"  yaml:"platform"`
}

func (a *App) newVersionCommand(opts *option) *cobra.Command {
	return &cobra.Command{
		Use:   "version",
		Short: "Print rmqctl version",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			info := versionInfo{
				Name:      "rmqctl",
				Version:   CLIVersion,
				GitCommit: GitCommit,
				BuildDate: BuildDate,
				GoVersion: runtime.Version(),
				Platform:  runtime.GOOS + "/" + runtime.GOARCH,
			}
			if output.IsStructured(opts.output) {
				return output.Structured(a.Out, opts.output, info)
			}
			lines := []struct{ label, value string }{
				{"rmqctl", info.Version},
				{"commit", info.GitCommit},
				{"built", info.BuildDate},
				{"go", info.GoVersion},
				{"os/arch", info.Platform},
			}
			for _, l := range lines {
				if l.value != "" {
					fmt.Fprintf(a.Out, "%s: %s\n", l.label, l.value)
				}
			}
			return nil
		},
	}
}
