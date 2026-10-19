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
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// TestValidateServerSchemeAdaptiveHTTP verifies the adaptive scheme policy:
// HTTPS is always accepted, plain HTTP is accepted for loopback / RFC1918
// private / link-local hosts, and rejected for public hosts.
func TestValidateServerSchemeAdaptiveHTTP(t *testing.T) {
	tests := []struct {
		name    string
		server  string
		wantErr bool
	}{
		{"https public host", "https://studio.example.com", false},
		{"http ipv4 loopback", "http://127.0.0.1:6789", false},
		{"http localhost", "http://localhost:6789", false},
		{"http ipv6 loopback", "http://[::1]:6789", false},
		{"http private 10/8", "http://10.0.3.104:6789", false},
		{"http private 172.16/12", "http://172.16.5.10:6789", false},
		{"http private 192.168/16", "http://192.168.1.20:6789", false},
		{"http link-local 169.254/16", "http://169.254.1.5:6789", false},
		{"http public ipv4 rejected", "http://47.98.43.243:6789", true},
		{"http public domain rejected", "http://studio.example.com", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			target := Target{
				Server:     tt.server,
				InstanceID: "test-instance",
				Credential: Credential{AccessKey: "ak", SecretKey: "sk"},
			}
			err := target.validate()
			if tt.wantErr && err == nil {
				t.Fatalf("validate(%q) = nil, want HTTPS-required error", tt.server)
			}
			if !tt.wantErr && err != nil {
				t.Fatalf("validate(%q) = %v, want nil", tt.server, err)
			}
		})
	}
}

func TestIsPrivateHost(t *testing.T) {
	private := []string{
		"localhost", "127.0.0.1", "::1",
		"10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.1", "169.254.1.1",
	}
	for _, host := range private {
		if !isPrivateHost(host) {
			t.Errorf("isPrivateHost(%q) = false, want true", host)
		}
	}
	public := []string{
		"studio.example.com", "47.98.43.243", "8.8.8.8",
		"172.15.0.1", "172.32.0.1", "11.0.0.1",
	}
	for _, host := range public {
		if isPrivateHost(host) {
			t.Errorf("isPrivateHost(%q) = true, want false", host)
		}
	}
}

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

// A scoped IPv6 literal such as [fe80::1%25eth0] is a valid Studio endpoint:
// url.Parse decodes the "%25" escape and reports the hostname "fe80::1%eth0",
// zone included. net.ParseIP rejects that zone suffix, so scoped loopback,
// link-local and private addresses were misclassified as public hosts and plain
// HTTP was refused. Classification must parse the scoped address as-is, while
// the zone is preserved in the outgoing URL so the kernel can pick an interface.
func TestValidateServerSchemeScopedIPv6(t *testing.T) {
	tests := []struct {
		name    string
		server  string
		wantErr string
	}{
		{"http link-local named zone", "http://[fe80::1%25eth0]:6789", ""},
		{"http link-local numeric zone", "http://[fe80::1%252]:6789", ""},
		{"http loopback named zone", "http://[::1%25lo]:6789", ""},
		{"http private ula named zone", "http://[fd00::1%25eth0]:6789", ""},
		{"https link-local named zone", "https://[fe80::1%25eth0]:6789", ""},
		{"http public ipv6 with zone rejected", "http://[2001:db8::1%25eth0]:6789", "must use HTTPS"},
		{"http public ipv6 rejected", "http://[2001:db8::1]:6789", "must use HTTPS"},
		{"empty zone rejected", "http://[fe80::1%25]:6789", "invalid Studio Server URL"},
		{"malformed ipv6 rejected", "http://[zz::1%25eth0]:6789", "invalid Studio Server URL"},
		{"missing bracket rejected", "http://[fe80::1:6789", "invalid Studio Server URL"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			target := Target{
				Server:     tt.server,
				InstanceID: "test-instance",
				Credential: Credential{AccessKey: "ak", SecretKey: "sk"},
			}
			err := target.validate()
			if tt.wantErr == "" {
				if err != nil {
					t.Fatalf("validate(%q) = %v, want nil", tt.server, err)
				}
				return
			}
			if err == nil {
				t.Fatalf("validate(%q) = nil, want error containing %q", tt.server, tt.wantErr)
			}
			if !strings.Contains(err.Error(), tt.wantErr) {
				t.Fatalf("validate(%q) = %v, want error containing %q", tt.server, err, tt.wantErr)
			}
		})
	}
}

// The interface zone is part of the outgoing URL: it must survive URL joining
// and request construction as the "%25eth0" escape, otherwise a scoped literal
// cannot identify its link-local interface.
func TestRequestPreservesScopedIPv6Zone(t *testing.T) {
	var recorded *http.Request
	client := NewClient(&http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		recorded = request
		return &http.Response{
			StatusCode: http.StatusOK,
			Header:     make(http.Header),
			Body:       io.NopCloser(strings.NewReader(`{"code":200,"message":"ok","data":null}`)),
			Request:    request,
		}, nil
	})})
	target := Target{
		Server:     "http://[fe80::1%25eth0]:6789",
		InstanceID: "instance-1",
		Credential: Credential{AccessKey: "ak", SecretKey: "sk"},
		Timeout:    DefaultTimeout,
	}

	var payload any
	if err := client.request(context.Background(), target, http.MethodGet, "/api/brokers", nil, &payload); err != nil {
		t.Fatalf("request to scoped link-local endpoint returned error: %v", err)
	}
	if recorded == nil {
		t.Fatal("request never reached the transport")
	}
	if got, want := recorded.URL.Hostname(), "fe80::1%eth0"; got != want {
		t.Fatalf("hostname = %q, want %q", got, want)
	}
	if got, want := recorded.URL.String(), "http://[fe80::1%25eth0]:6789/api/brokers"; got != want {
		t.Fatalf("request URL = %q, want %q", got, want)
	}
}
