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

// Package brokerbusy implements the broker-busy diagnostic skill. See runbooks/broker-busy.md.
package brokerbusy

import (
	"context"
	"fmt"

	"github.com/apache/rocketmq-dashboard/mqctl/skill"
)

const SkillName = "broker-busy"

type Skill struct{}

func (Skill) Name() string        { return SkillName }
func (Skill) Description() string { return "Diagnose broker busy / write rejection." }

func (s Skill) Run(ctx context.Context, sctx skill.Context) (*skill.Diagnosis, error) {
	diag := &skill.Diagnosis{Skill: SkillName, Severity: skill.SeverityUnknown}

	out, err := sctx.Client.ExecuteTool("rmq.cluster.list", map[string]any{})
	if err != nil {
		diag.Summary = "Cannot fetch cluster list from Studio"
		diag.Notes = append(diag.Notes, err.Error())
		return diag, nil
	}
	clusters, err := asClusterList(out)
	if err != nil {
		return nil, fmt.Errorf("parse rmq.cluster.list output: %w", err)
	}

	diag.Summary = fmt.Sprintf("%d clusters found; missing broker-level data sources, cannot judge busy", len(clusters))
	for _, c := range clusters {
		diag.Findings = append(diag.Findings, skill.Finding{
			Item: c.name(),
			Detail: map[string]any{
				"id":      c.id(),
				"status":  c.status(),
				"version": c.version(),
			},
		})
	}

	diag.Notes = append(diag.Notes,
		"Judging IO/GC/lock needs rmq.broker.status and rmq.broker.config (both unexposed); "+
			"host-level CPU/disk/GC needs agent/JMX. Until then, mqadmin brokerStatus / getBrokerConfig can be used directly.")
	return diag, nil
}

func asClusterList(out any) ([]clusterView, error) {
	arr, ok := out.([]any)
	if !ok {
		return nil, fmt.Errorf("expected array, got %T", out)
	}
	clusters := make([]clusterView, 0, len(arr))
	for i, el := range arr {
		m, ok := el.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("element %d: expected object, got %T", i, el)
		}
		clusters = append(clusters, clusterView{raw: m})
	}
	return clusters, nil
}

type clusterView struct{ raw map[string]any }

func (c clusterView) name() string    { return asString(c.raw["name"]) }
func (c clusterView) id() string      { return asString(c.raw["id"]) }
func (c clusterView) status() string  { return asString(c.raw["status"]) }
func (c clusterView) version() string { return asString(c.raw["version"]) }

func asString(v any) string {
	if s, ok := v.(string); ok {
		return s
	}
	return ""
}

var _ skill.Skill = Skill{}
