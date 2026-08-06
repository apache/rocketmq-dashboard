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

package brokerbusy

import (
	"context"
	"errors"
	"fmt"
	"strings"
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

func TestRun_ReportsGapWhenNoBrokerData(t *testing.T) {
	c := fakeClient{resp: map[string]any{
		"rmq.cluster.list": []any{
			map[string]any{"id": "c1", "name": "cluster1", "status": "ONLINE", "version": "5.x"},
		},
	}}
	diag, err := Skill{}.Run(context.Background(), skill.Context{Client: c})
	if err != nil {
		t.Fatal(err)
	}
	if diag.Severity != skill.SeverityUnknown {
		t.Errorf("want UNKNOWN, got %s", diag.Severity)
	}
	if len(diag.Findings) != 1 {
		t.Fatalf("want 1 cluster finding, got %d", len(diag.Findings))
	}
	if diag.Findings[0].Item != "cluster1" {
		t.Errorf("want item cluster1, got %s", diag.Findings[0].Item)
	}
	joined := strings.Join(diag.Notes, " ")
	if !strings.Contains(joined, "broker.status") || !strings.Contains(joined, "broker.config") {
		t.Errorf("want gap note about broker.status/broker.config, got %v", diag.Notes)
	}
}

func TestRun_UnknownWhenFetchFails(t *testing.T) {
	c := fakeClient{errs: map[string]error{"rmq.cluster.list": errors.New("boom")}}
	diag, err := Skill{}.Run(context.Background(), skill.Context{Client: c})
	if err != nil {
		t.Fatal(err)
	}
	if diag.Severity != skill.SeverityUnknown {
		t.Errorf("want UNKNOWN, got %s", diag.Severity)
	}
}
