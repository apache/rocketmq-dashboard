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

package studio

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestListTools(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/ai/tools" || r.URL.Query().Get("cluster") != "c1" {
			t.Errorf("unexpected request: %s?%s", r.URL.Path, r.URL.RawQuery)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"code":200,"message":"success","data":[{"name":"rmq.cluster.list","description":"d","riskLevel":"L1"}]}`))
	}))
	defer srv.Close()

	c := New(srv.URL)
	tools, err := c.ListTools("c1")
	if err != nil {
		t.Fatal(err)
	}
	if len(tools) != 1 || tools[0].Name != "rmq.cluster.list" {
		t.Fatalf("unexpected tools: %+v", tools)
	}
}

func TestListTools_ServerError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte("boom"))
	}))
	defer srv.Close()

	if _, err := New(srv.URL).ListTools(""); err == nil {
		t.Fatal("want error on http 500")
	}
}

func TestListTools_ErrorCode(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{"code":404,"message":"not found","data":null}`))
	}))
	defer srv.Close()

	if _, err := New(srv.URL).ListTools(""); err == nil {
		t.Fatal("want error on non-success code")
	}
}

func TestExecuteTool(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/api/ai/tools/rmq.group.list/execute") {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		_, _ = w.Write([]byte(`{"code":200,"message":"success","data":[{"name":"g1","totalLag":3}]}`))
	}))
	defer srv.Close()

	out, err := New(srv.URL).ExecuteTool("rmq.group.list", map[string]any{"cluster": "c1"})
	if err != nil {
		t.Fatal(err)
	}
	arr, ok := out.([]any)
	if !ok || len(arr) != 1 {
		t.Fatalf("want 1-element array, got %T %+v", out, out)
	}
}
