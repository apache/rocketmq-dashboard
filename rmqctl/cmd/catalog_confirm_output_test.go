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
	"strings"
	"sync/atomic"
	"testing"
)

var errConfirmationOutput = errors.New("synthetic confirmation output failure")

type confirmationFailWriter struct{ failAt, writes int }

func (w *confirmationFailWriter) Write(p []byte) (int, error) {
	w.writes++
	if w.writes == w.failAt {
		return 0, errConfirmationOutput
	}
	return len(p), nil
}

type confirmationCountingReader struct {
	io.Reader
	reads atomic.Int32
}

func (r *confirmationCountingReader) Read(p []byte) (int, error) {
	r.reads.Add(1)
	return r.Reader.Read(p)
}

func TestDefaultConfirmOutputFailureDoesNotReadAnswer(t *testing.T) {
	for _, failAt := range []int{1, 2} {
		reader := &confirmationCountingReader{Reader: strings.NewReader("yes\n")}
		err := defaultConfirm(context.Background(), charDeviceReader{r: reader}, &confirmationFailWriter{failAt: failAt}, "group reset-offset", "L3", "http://studio")
		if !errors.Is(err, errConfirmationOutput) {
			t.Errorf("write %d: lost error: %v", failAt, err)
		}
		if reader.reads.Load() != 0 {
			t.Errorf("write %d: consumed answer after output failure", failAt)
		}
	}
}

func TestConfirmationOutputFailureStopsToolExecution(t *testing.T) {
	cases := []struct {
		name, answer        string
		failAt              int
		flags               []string
		wantCode, wantCalls int
		wantRead            bool
	}{
		{name: "first prompt fails", answer: "yes\n", failAt: 1, wantCode: 1},
		{name: "second prompt fails", answer: "yes\n", failAt: 2, wantCode: 1},
		{name: "visible prompt accepted", answer: "yes\n", wantCalls: 2, wantRead: true},
		{name: "visible prompt rejected", answer: "no\n", wantCode: 1, wantRead: true},
		{name: "yes bypasses prompt", failAt: 1, flags: []string{"--yes"}, wantCalls: 2},
		{name: "dry run bypasses prompt", failAt: 1, flags: []string{"--dry-run"}, wantCalls: 1},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var calls atomic.Int32
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				calls.Add(1)
				var request struct {
					Name      string         `json:"name"`
					Arguments map[string]any `json:"arguments"`
				}
				if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
					t.Error(err)
					return
				}
				if request.Name != "rmq.group.reset_offset" {
					t.Errorf("unexpected tool %s", request.Name)
				}
				status := "EXECUTED"
				if request.Arguments["dry_run"] == true {
					status = "PLANNED"
				} else if request.Arguments["confirm_token"] != "synthetic-token" {
					t.Error("apply without preview token")
				}
				writeStudioSuccess(t, w, map[string]any{"status": status, "instanceId": "test-instance", "confirm_token": "synthetic-token", "plan": map[string]any{}, "result": map[string]any{}})
			}))
			defer server.Close()
			reader := &confirmationCountingReader{Reader: strings.NewReader(tc.answer)}
			writer := &confirmationFailWriter{failAt: tc.failAt}
			app := NewApp(&bytes.Buffer{}, writer)
			app.In = charDeviceReader{r: reader}
			app.HTTP = server.Client()
			app.Store.Getenv = testEnv
			path := newTestConfigPath(t)
			if err := app.Store.Save(path, newTestConfig(server.URL)); err != nil {
				t.Fatal(err)
			}
			args := []string{"--config", path, "--instance-id", "test-instance", "group", "reset-offset", "--group-name", "orders", "--topic-name", "orders", "--timestamp", "1700000000000"}
			code := app.Execute(append(args, tc.flags...))
			t.Logf("exit=%d HTTP requests=%d answer reads=%d", code, calls.Load(), reader.reads.Load())
			if code != tc.wantCode || int(calls.Load()) != tc.wantCalls {
				t.Errorf("exit/calls=%d/%d, want %d/%d", code, calls.Load(), tc.wantCode, tc.wantCalls)
			}
			if (reader.reads.Load() > 0) != tc.wantRead {
				t.Errorf("answer read=%v, want %v", reader.reads.Load() > 0, tc.wantRead)
			}
		})
	}
}
