// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package main implements mqctl, a CLI for RocketMQ Studio.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/apache/rocketmq-dashboard/mqctl/skill"
	"github.com/apache/rocketmq-dashboard/mqctl/skill/brokerbusy"
	"github.com/apache/rocketmq-dashboard/mqctl/skill/consumerlag"
	"github.com/apache/rocketmq-dashboard/mqctl/studio"
)

func main() {
	for _, a := range os.Args[1:] {
		if a == "-h" || a == "--help" {
			usage()
			return
		}
	}

	fs := flag.NewFlagSet("mqctl", flag.ExitOnError)
	studioURL := fs.String("studio", envOr("STUDIO_URL", "http://127.0.0.1:8888"), "studio base url")
	cluster := fs.String("cluster", "", "cluster id (required by remote tools)")
	pretty := fs.Bool("pretty", true, "pretty-print diagnosis output")
	inputJSON := fs.String("input", "", "tool input JSON for the call command")
	fs.Parse(reorderArgs(os.Args[1:]))

	args := fs.Args()
	if len(args) == 0 {
		usage()
		os.Exit(2)
	}

	registry := skill.NewRegistry()
	registry.Register(consumerlag.Skill{})
	registry.Register(brokerbusy.Skill{})

	client := studio.New(*studioURL)
	sctx := skill.Context{Client: client, Cluster: *cluster}

	switch args[0] {
	case "tools":
		listTools(client, *cluster)
	case "call":
		if len(args) < 2 {
			fmt.Fprintln(os.Stderr, "usage: mqctl [--input 'JSON'] call <tool>")
			os.Exit(2)
		}
		callTool(client, *cluster, args[1], *inputJSON)
	case "skills":
		listSkills(registry)
	case "diagnose":
		if len(args) < 2 {
			fmt.Fprintln(os.Stderr, "usage: mqctl diagnose <skill>")
			os.Exit(2)
		}
		runDiagnose(registry, args[1], sctx, *pretty)
	case "-h", "--help", "help":
		usage()
	default:
		fmt.Fprintf(os.Stderr, "unknown command: %s\n", args[0])
		usage()
		os.Exit(2)
	}
}

func listTools(c *studio.Client, cluster string) {
	tools, err := c.ListTools(cluster)
	if err != nil {
		fatal(err)
	}
	fmt.Printf("%-28s  %-8s  %s\n", "NAME", "RISK", "DESCRIPTION")
	for _, t := range tools {
		fmt.Printf("%-28s  %-8s  %s\n", t.Name, t.RiskLevel, t.Description)
	}
}

func callTool(c *studio.Client, cluster, name, inputJSON string) {
	input := map[string]any{}
	if cluster != "" {
		input["cluster"] = cluster
	}
	if inputJSON != "" {
		var extra map[string]any
		if err := json.Unmarshal([]byte(inputJSON), &extra); err != nil {
			fatal(fmt.Errorf("invalid --input: %w", err))
		}
		for k, v := range extra {
			input[k] = v
		}
	}
	out, err := c.ExecuteTool(name, input)
	if err != nil {
		fatal(err)
	}
	b, err := json.MarshalIndent(out, "", "  ")
	if err != nil {
		fatal(err)
	}
	fmt.Println(string(b))
}

func listSkills(r *skill.Registry) {
	for _, name := range r.Names() {
		s, _ := r.Get(name)
		fmt.Printf("%-20s  %s\n", name, s.Description())
	}
}

func runDiagnose(r *skill.Registry, name string, sctx skill.Context, pretty bool) {
	s, ok := r.Get(name)
	if !ok {
		fmt.Fprintf(os.Stderr, "unknown skill: %s (try 'skills')\n", name)
		os.Exit(2)
	}
	diag, err := s.Run(context.Background(), sctx)
	if err != nil {
		fatal(err)
	}
	var b []byte
	if pretty {
		b, err = json.MarshalIndent(diag, "", "  ")
	} else {
		b, err = json.Marshal(diag)
	}
	if err != nil {
		fatal(err)
	}
	fmt.Println(string(b))
}

func usage() {
	fmt.Println(`mqctl - RocketMQ Studio CLI

usage:
  mqctl [--studio URL] [--cluster ID] <command>

commands:
  tools                 list AI tools exposed by Studio
  call <tool>            call a Studio tool and print the result
  skills                 list built-in diagnostic skills
  diagnose <skill>       run a diagnostic skill (e.g. consumer-lag, broker-busy)

flags:
  --studio URL   studio base url (env STUDIO_URL, default http://127.0.0.1:8888)
  --cluster ID   cluster id (required by remote tools)
  --input JSON   tool input JSON for the call command
  --pretty       pretty-print diagnosis output (default true)`)
}

func fatal(err error) {
	fmt.Fprintf(os.Stderr, "mqctl: %v\n", err)
	os.Exit(1)
}

func envOr(key, dflt string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return dflt
}

var stringFlags = map[string]bool{"studio": true, "cluster": true, "input": true}
var boolFlags = map[string]bool{"pretty": true}

func reorderArgs(argv []string) []string {
	var flags, pos []string
	for i := 0; i < len(argv); i++ {
		a := argv[i]
		if strings.HasPrefix(a, "--") {
			name := strings.TrimPrefix(a, "--")
			if strings.IndexByte(name, '=') >= 0 {
				flags = append(flags, a)
				continue
			}
			if boolFlags[name] {
				flags = append(flags, a)
				continue
			}
			if stringFlags[name] {
				flags = append(flags, a)
				if i+1 < len(argv) {
					flags = append(flags, argv[i+1])
					i++
				}
				continue
			}
		}
		pos = append(pos, a)
	}
	return append(flags, pos...)
}
