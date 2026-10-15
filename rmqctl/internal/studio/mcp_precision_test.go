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
package studio

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestMCPRequestPreservesIntegerPrecision(t *testing.T) {
	for _, number := range []string{"42", "9007199254740993", "9223372036854775807"} {
		t.Run(number, func(t *testing.T) {
			wire := make(chan []byte, 1)
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != http.MethodPost {
					w.WriteHeader(http.StatusMethodNotAllowed)
					return
				}
				body, err := io.ReadAll(r.Body)
				if err != nil {
					t.Error(err)
					return
				}
				wire <- body
				w.Header().Set("Content-Type", "application/json")
				fmt.Fprint(w, `{"jsonrpc":"2.0","id":1,"result":{"items":[]}}`)
			}))
			defer server.Close()
			client := NewClient(server.Client())
			session, err := client.NewMCPClientSession(Target{Server: server.URL, InstanceID: "test-instance", Credential: Credential{AccessKey: "synthetic-ak", SecretKey: "synthetic-sk"}, Timeout: time.Second})
			if err != nil {
				t.Fatal(err)
			}
			defer session.Close()
			payload := fmt.Sprintf(`{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"rmq.message.query_by_offset","arguments":{"topicName":"orders","brokerName":"broker-a","queueId":0,"offset":%s}}}`, number)
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
			defer cancel()
			_, ok, err := session.SendMessage(ctx, json.RawMessage(payload))
			if err != nil || !ok {
				t.Fatalf("SendMessage ok=%v err=%v", ok, err)
			}
			raw := <-wire
			var sent struct {
				Params struct {
					Arguments map[string]json.RawMessage `json:"arguments"`
				} `json:"params"`
			}
			if err := json.Unmarshal(raw, &sent); err != nil {
				t.Fatal(err)
			}
			got := string(sent.Params.Arguments["offset"])
			if string(sent.Params.Arguments["instanceId"]) != `"test-instance"` {
				t.Fatalf("instanceId = %s", sent.Params.Arguments["instanceId"])
			}
			if got != number {
				t.Errorf("offset changed in actual HTTP request: want %s got %s", number, got)
			}
		})
	}
}

func TestMCPRequestParamsRoundTrip(t *testing.T) {
	params := `{"nested":[9007199254740993,-9223372036854775808,1.25,1e+20,true,false,null,"9007199254740993"],"object":{"offset":9223372036854775807}}`
	for _, id := range []string{`1`, `"request-1"`} {
		t.Run(id, func(t *testing.T) {
			payload := json.RawMessage(`{"jsonrpc":"2.0","id":` + id + `,"method":"custom/request","params":` + params + `}`)
			decoded, err := decodeMCPMessage(payload)
			if err != nil {
				t.Fatal(err)
			}
			encoded, err := json.Marshal(decoded.request)
			if err != nil {
				t.Fatal(err)
			}
			var fields map[string]json.RawMessage
			if err := json.Unmarshal(encoded, &fields); err != nil {
				t.Fatal(err)
			}
			var compact bytes.Buffer
			if err := json.Compact(&compact, json.RawMessage(params)); err != nil {
				t.Fatal(err)
			}
			// Object key order is irrelevant; compare individual raw values so numbers never become float64 in the assertion.
			var got, want map[string]json.RawMessage
			if err := json.Unmarshal(fields["params"], &got); err != nil {
				t.Fatal(err)
			}
			if err := json.Unmarshal(compact.Bytes(), &want); err != nil {
				t.Fatal(err)
			}
			for key, value := range want {
				if string(got[key]) != string(value) {
					t.Errorf("%s = %s, want %s", key, got[key], value)
				}
			}
			if string(fields["id"]) != id {
				t.Errorf("id = %s, want %s", fields["id"], id)
			}
		})
	}
}

func TestMCPRequestRejectsTrailingJSON(t *testing.T) {
	for _, payload := range []string{`{"jsonrpc":"2.0","id":1,"method":"tools/list"} {}`, `{"jsonrpc":"2.0","id":1,"method":"tools/list","params": [}`} {
		if _, err := decodeMCPMessage(json.RawMessage(payload)); err == nil {
			t.Fatalf("accepted invalid payload %s", payload)
		}
	}
}
