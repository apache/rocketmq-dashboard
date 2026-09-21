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
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// The passthrough commands decode the studio payload into an `any`, where encoding/json
// would represent every number as float64 and silently corrupt int64 values beyond 2^53
// (RocketMQ offsets and timestamps). The client must keep the literal via json.Number.
func TestRequestPreservesLargeIntegersInPassthroughPayload(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"code":200,"message":"ok","data":{"items":[{"msgOffset":9007199254740993}]}}`))
	}))
	defer server.Close()

	client := NewClient(http.DefaultClient)
	target := Target{
		Server:     server.URL,
		InstanceID: "instance-1",
		Credential: Credential{AccessKey: "ak", SecretKey: "sk"},
		Timeout:    DefaultTimeout,
	}

	var payload any
	if err := client.request(context.Background(), target, http.MethodGet, "/api/brokers", nil, &payload); err != nil {
		t.Fatalf("request returned error: %v", err)
	}

	encoded, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}
	if !strings.Contains(string(encoded), "9007199254740993") {
		t.Fatalf("payload lost integer precision: %s", encoded)
	}
}
