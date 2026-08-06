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

package consumerlag

import (
	"context"
	"errors"
	"fmt"
	"testing"

	"github.com/apache/rocketmq-dashboard/mqctl/skill"
)

type fakeClient struct {
	resp map[string]any
	errs map[string]error
}

func (f fakeClient) ExecuteTool(name string, _ map[string]any) (any, error) {
	if e, ok := f.errs[name]; ok {
		return nil, e
	}
	if r, ok := f.resp[name]; ok {
		return r, nil
	}
	return nil, fmt.Errorf("unexpected tool call: %s", name)
}

func TestRun_RequiresCluster(t *testing.T) {
	s := Skill{}
	ctx := skill.Context{}
	if _, err := s.Run(context.Background(), ctx); err == nil {
		t.Fatal("want error when cluster is empty")
	}
}

func TestRun_NoLag(t *testing.T) {
	c := fakeClient{resp: map[string]any{
		"rmq.group.list": []any{
			map[string]any{"name": "g1", "totalLag": float64(10), "onlineInstances": float64(1)},
		},
	}}
	diag, err := Skill{}.Run(context.Background(), skill.Context{Client: c, Cluster: "c1"})
	if err != nil {
		t.Fatal(err)
	}
	if diag.Severity != skill.SeverityOK {
		t.Errorf("want OK, got %s", diag.Severity)
	}
	if len(diag.Findings) != 0 {
		t.Errorf("want 0 findings, got %d", len(diag.Findings))
	}
}

func TestRun_LaggingGroups(t *testing.T) {
	c := fakeClient{resp: map[string]any{
		"rmq.group.list": []any{
			map[string]any{"name": "ok", "totalLag": float64(500), "onlineInstances": float64(2)},
			map[string]any{"name": "lag-online", "totalLag": float64(5000), "onlineInstances": float64(1)},
			map[string]any{"name": "lag-offline", "totalLag": float64(2000), "onlineInstances": float64(0)},
		},
	}}
	diag, err := Skill{}.Run(context.Background(), skill.Context{Client: c, Cluster: "c1"})
	if err != nil {
		t.Fatal(err)
	}
	if diag.Severity != skill.SeverityCritical {
		t.Errorf("want CRITICAL, got %s", diag.Severity)
	}
	if len(diag.Findings) != 2 {
		t.Fatalf("want 2 findings, got %d", len(diag.Findings))
	}
	if got := diag.Findings[0].Detail["interpretation"]; got == "" {
		t.Errorf("want non-empty interpretation")
	}
}

func TestRun_UnknownWhenFetchFails(t *testing.T) {
	c := fakeClient{errs: map[string]error{"rmq.group.list": errors.New("boom")}}
	diag, err := Skill{}.Run(context.Background(), skill.Context{Client: c, Cluster: "c1"})
	if err != nil {
		t.Fatal(err)
	}
	if diag.Severity != skill.SeverityUnknown {
		t.Errorf("want UNKNOWN, got %s", diag.Severity)
	}
	if len(diag.Notes) == 0 {
		t.Errorf("want failure note recorded")
	}
}
