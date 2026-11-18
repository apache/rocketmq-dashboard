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
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

func TestMutationConfirmationUsesResolvedTargetTest(t *testing.T) {
	for _, change := range []string{"unchanged", "replaced", "removed"} {
		for _, explicitToken := range []bool{false, true} {
			name := change + "/automatic-preview"
			if explicitToken {
				name = change + "/explicit-token"
			}
			t.Run(name, func(t *testing.T) {
				var requests int
				server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					requests++
					var request struct {
						Arguments map[string]any `json:"arguments"`
					}
					if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
						t.Error(err)
						return
					}
					status := "EXECUTED"
					if request.Arguments["dry_run"] == true {
						status = "PLANNED"
					} else if request.Arguments["confirm_token"] != "synthetic-token" {
						t.Error("mutation lost preview token")
					}
					writeStudioSuccess(t, w, map[string]any{"status": status, "instanceId": "test-instance", "confirm_token": "synthetic-token"})
				}))
				defer server.Close()
				app := NewApp(&bytes.Buffer{}, &bytes.Buffer{})
				app.HTTP = server.Client()
				path := newTestConfigPath(t)
				if err := app.Store.Save(path, newTestConfig(server.URL)); err != nil {
					t.Fatal(err)
				}
				changed := false
				app.Store.Getenv = func(name string) string {
					// ResolveCredential runs after resolveTarget has loaded its config snapshot.
					if !changed && name == "RMQ_TEST_AK" {
						changed = true
						var err error
						switch change {
						case "replaced":
							err = app.Store.Save(path, newTestConfig("https://different-studio.example"))
						case "removed":
							err = os.Remove(path)
						}
						if err != nil {
							t.Fatal(err)
						}
					}
					return testEnv(name)
				}
				var promptedServer string
				app.confirm = func(_ context.Context, _ io.Reader, _ io.Writer, _, _, server string) error {
					promptedServer = server
					return nil
				}
				args := []string{"--config", path, "--instance-id", "test-instance", "group", "reset-offset", "--group-name", "orders", "--topic-name", "orders", "--timestamp", "1700000000000"}
				expectedRequests := 2
				if explicitToken {
					args = append(args, "--confirm-token", "synthetic-token")
					expectedRequests = 1
				}
				code := app.Execute(args)
				if code != 0 || requests != expectedRequests {
					t.Fatalf("exit/requests = %d/%d, want 0/%d", code, requests, expectedRequests)
				}
				if promptedServer != server.URL {
					t.Fatalf("prompt names %q, actual request went to %q", promptedServer, server.URL)
				}
			})
		}
	}
}

func TestResolvedTargetConfirmationRejectionPreventsRequestsTest(t *testing.T) {
	var requests int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { requests++ }))
	defer server.Close()
	app := NewApp(&bytes.Buffer{}, &bytes.Buffer{})
	app.HTTP = server.Client()
	path := newTestConfigPath(t)
	if err := app.Store.Save(path, newTestConfig(server.URL)); err != nil {
		t.Fatal(err)
	}
	app.Store.Getenv = testEnv
	app.confirm = func(_ context.Context, _ io.Reader, _ io.Writer, _, _, target string) error {
		if target != server.URL {
			t.Errorf("prompt target = %q, want %q", target, server.URL)
		}
		return errors.New("operator declined")
	}
	code := app.Execute([]string{"--config", path, "--instance-id", "test-instance", "group", "reset-offset", "--group-name", "orders", "--topic-name", "orders", "--timestamp", "1700000000000"})
	if code != 1 || requests != 0 {
		t.Fatalf("exit/requests = %d/%d, want 1/0", code, requests)
	}
}
