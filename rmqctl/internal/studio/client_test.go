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
	"fmt"
	"net"
	"testing"
)

// stubHostResolution replaces real DNS with a deterministic table so isPrivateHost tests do not
// depend on the network. Hosts absent from the table fail resolution (stays on HTTPS).
func stubHostResolution(t *testing.T, table map[string][]string) {
	t.Helper()
	original := lookupHostIPAddrs
	lookupHostIPAddrs = func(host string) ([]net.IP, error) {
		if addrs, ok := table[host]; ok {
			ips := make([]net.IP, 0, len(addrs))
			for _, a := range addrs {
				ips = append(ips, net.ParseIP(a))
			}
			return ips, nil
		}
		return nil, fmt.Errorf("no such host %q", host)
	}
	t.Cleanup(func() { lookupHostIPAddrs = original })
}

// TestValidateServerSchemeAdaptiveHTTP verifies the adaptive scheme policy:
// HTTPS is always accepted, plain HTTP is accepted for loopback / RFC1918
// private / link-local hosts — literal or resolved exclusively to such
// addresses — and rejected for public or unresolvable hosts.
func TestValidateServerSchemeAdaptiveHTTP(t *testing.T) {
	stubHostResolution(t, map[string][]string{
		"studio.internal":       {"10.0.3.104"},
		"mixed.example.com":     {"10.0.3.104", "47.98.43.243"},
		"studio.example.com":    {"47.98.43.243"},
		"unresolvable.internal": nil,
	})
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
		{"http private dns name", "http://studio.internal:6789", false},
		{"http public ipv4 rejected", "http://47.98.43.243:6789", true},
		{"http public domain rejected", "http://studio.example.com", true},
		{"http mixed resolution rejected", "http://mixed.example.com:6789", true},
		{"http unresolvable host rejected", "http://unresolvable.internal:6789", true},
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
	stubHostResolution(t, map[string][]string{
		"studio.internal":       {"10.0.3.104"},
		"corp.local":            {"192.168.1.1", "192.168.1.2"},
		"mixed.example.com":     {"10.0.3.104", "47.98.43.243"},
		"studio.example.com":    {"47.98.43.243"},
		"unresolvable.internal": nil,
	})
	private := []string{
		"localhost", "127.0.0.1", "::1",
		"10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.1", "169.254.1.1",
		"studio.internal", "corp.local",
	}
	for _, host := range private {
		if !isPrivateHost(host) {
			t.Errorf("isPrivateHost(%q) = false, want true", host)
		}
	}
	public := []string{
		"studio.example.com", "47.98.43.243", "8.8.8.8",
		"172.15.0.1", "172.32.0.1", "11.0.0.1",
		"mixed.example.com", "unresolvable.internal", "absent.example.org",
	}
	for _, host := range public {
		if isPrivateHost(host) {
			t.Errorf("isPrivateHost(%q) = true, want false", host)
		}
	}
}
