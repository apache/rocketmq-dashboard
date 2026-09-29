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

func TestReadBounded(t *testing.T) {
	t.Parallel()

	t.Run("accepts body at the limit", func(t *testing.T) {
		t.Parallel()
		body := strings.NewReader("12345")
		data, err := readBounded(body, 5)
		if err != nil {
			t.Fatalf("readBounded() error = %v", err)
		}
		if string(data) != "12345" {
			t.Fatalf("readBounded() = %q, want 12345", data)
		}
	})

	t.Run("rejects body over the limit", func(t *testing.T) {
		t.Parallel()
		body := strings.NewReader("123456")
		if _, err := readBounded(body, 5); err == nil {
			t.Fatal("readBounded() error = nil, want size-cap error")
		} else if !strings.Contains(err.Error(), "exceeds") {
			t.Fatalf("readBounded() error = %v, want mentions exceeds", err)
		}
	})
}

func TestRequestRejectsOversizedStudioResponse(t *testing.T) {
	t.Parallel()

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"code":200,"data":`))
		_, _ = w.Write([]byte(strings.Repeat("x", 64)))
		_, _ = w.Write([]byte(`}`))
	}))
	t.Cleanup(server.Close)

	client := NewClient(server.Client())
	client.maxResponseBytes = 16
	target := Target{
		Server:     server.URL,
		InstanceID: "inst",
		Credential: Credential{AccessKey: "ak", SecretKey: "sk"},
	}

	err := client.request(context.Background(), target, http.MethodGet, "/api/mcp", nil, &map[string]any{})
	if err == nil {
		t.Fatal("request() error = nil, want size-cap error")
	}
	if !strings.Contains(err.Error(), "exceeds") {
		t.Fatalf("request() error = %v, want size-cap error", err)
	}
}

func TestRequestAcceptsNormalStudioEnvelope(t *testing.T) {
	t.Parallel()

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"code":200,"data":{"ok":true}}`))
	}))
	t.Cleanup(server.Close)

	client := NewClient(server.Client())
	target := Target{
		Server:     server.URL,
		InstanceID: "inst",
		Credential: Credential{AccessKey: "ak", SecretKey: "sk"},
	}

	var out map[string]any
	if err := client.request(context.Background(), target, http.MethodGet, "/api/mcp", nil, &out); err != nil {
		t.Fatalf("request() error = %v", err)
	}
	if out["ok"] != true {
		t.Fatalf("request() out = %#v, want ok=true", out)
	}
}
