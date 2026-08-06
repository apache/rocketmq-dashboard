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

// Package skill defines the diagnostic skill interface.
package skill

import (
	"context"
	"fmt"
	"sort"
)

type ToolClient interface {
	ExecuteTool(name string, input map[string]any) (any, error)
}

type Context struct {
	Client  ToolClient
	Cluster string
}

type Severity string

const (
	SeverityOK       Severity = "OK"
	SeverityWarning  Severity = "WARNING"
	SeverityCritical Severity = "CRITICAL"
	SeverityUnknown  Severity = "UNKNOWN"
)

type Finding struct {
	Item   string         `json:"item"`
	Detail map[string]any `json:"detail"`
	Note   string         `json:"note,omitempty"`
}

type Diagnosis struct {
	Skill    string    `json:"skill"`
	Severity Severity  `json:"severity"`
	Summary  string    `json:"summary"`
	Findings []Finding `json:"findings"`
	Notes    []string  `json:"notes,omitempty"`
}

type Skill interface {
	Name() string
	Description() string
	Run(ctx context.Context, sctx Context) (*Diagnosis, error)
}

type Registry struct {
	skills map[string]Skill
}

func NewRegistry() *Registry { return &Registry{skills: map[string]Skill{}} }

func (r *Registry) Register(s Skill) {
	if _, dup := r.skills[s.Name()]; dup {
		panic(fmt.Sprintf("duplicate skill: %s", s.Name()))
	}
	r.skills[s.Name()] = s
}

func (r *Registry) Get(name string) (Skill, bool) {
	s, ok := r.skills[name]
	return s, ok
}

func (r *Registry) Names() []string {
	out := make([]string, 0, len(r.skills))
	for n := range r.skills {
		out = append(out, n)
	}
	sort.Strings(out)
	return out
}
