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

// Package consumerlag implements the consumer-lag diagnostic skill. See runbooks/consumer-lag.md.
package consumerlag

import (
	"context"
	"fmt"
	"strconv"

	"github.com/apache/rocketmq-dashboard/mqctl/skill"
)

const SkillName = "consumer-lag"

const LagThreshold int64 = 1000

type Skill struct{}

func (Skill) Name() string        { return SkillName }
func (Skill) Description() string { return "Diagnose consumer-group message lag." }

func (s Skill) Run(ctx context.Context, sctx skill.Context) (*skill.Diagnosis, error) {
	if sctx.Cluster == "" {
		return nil, fmt.Errorf("consumer-lag requires --cluster")
	}
	diag := &skill.Diagnosis{Skill: SkillName}

	out, err := sctx.Client.ExecuteTool("rmq.group.list", map[string]any{
		"cluster": sctx.Cluster,
	})
	if err != nil {
		diag.Severity = skill.SeverityUnknown
		diag.Summary = "Cannot fetch consumer groups from Studio"
		diag.Notes = append(diag.Notes, err.Error())
		return diag, nil
	}
	groups, err := asGroupList(out)
	if err != nil {
		return nil, fmt.Errorf("parse rmq.group.list output: %w", err)
	}

	var lagging []groupLag
	for _, g := range groups {
		lag := g.totalLag()
		if lag >= LagThreshold {
			lagging = append(lagging, groupLag{name: g.name(), lag: lag, online: g.onlineInstances()})
		}
	}

	switch {
	case len(lagging) == 0:
		diag.Severity = skill.SeverityOK
		diag.Summary = fmt.Sprintf("No significant lag (threshold %d), %d groups", LagThreshold, len(groups))
	case len(lagging) == 1:
		diag.Severity = skill.SeverityWarning
		diag.Summary = fmt.Sprintf("1 lagging group (threshold %d)", LagThreshold)
	default:
		diag.Severity = skill.SeverityCritical
		diag.Summary = fmt.Sprintf("%d lagging groups (threshold %d)", len(lagging), LagThreshold)
	}

	for _, l := range lagging {
		hint := "likely client not started / rebalance issue"
		if l.online > 0 {
			hint = "online but falling behind; need client stack to tell client vs broker"
		}
		diag.Findings = append(diag.Findings, skill.Finding{
			Item: l.name,
			Detail: map[string]any{
				"totalLag":        l.lag,
				"onlineInstances": l.online,
				"interpretation":  hint,
			},
		})
	}

	diag.Notes = append(diag.Notes,
		"Root-cause split (client vs broker) needs rmq.group.progress and rmq.client.stack, neither exposed yet; integrate once available.")
	return diag, nil
}

type groupLag struct {
	name   string
	lag    int64
	online int64
}

func asGroupList(out any) ([]groupView, error) {
	arr, ok := out.([]any)
	if !ok {
		return nil, fmt.Errorf("expected array, got %T", out)
	}
	groups := make([]groupView, 0, len(arr))
	for i, el := range arr {
		m, ok := el.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("element %d: expected object, got %T", i, el)
		}
		groups = append(groups, groupView{raw: m})
	}
	return groups, nil
}

type groupView struct{ raw map[string]any }

func (g groupView) name() string {
	if v, ok := g.raw["name"].(string); ok {
		return v
	}
	return "<unknown>"
}

func (g groupView) totalLag() int64        { return toInt64(g.raw["totalLag"]) }
func (g groupView) onlineInstances() int64 { return toInt64(g.raw["onlineInstances"]) }

func toInt64(v any) int64 {
	switch n := v.(type) {
	case nil:
		return 0
	case float64:
		return int64(n)
	case int:
		return int64(n)
	case int64:
		return n
	case string:
		x, _ := strconv.ParseInt(n, 10, 64)
		return x
	default:
		return 0
	}
}

var _ skill.Skill = Skill{}
