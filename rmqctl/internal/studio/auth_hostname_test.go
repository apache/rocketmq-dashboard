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
	"encoding/binary"
	"net"
	"strings"
	"testing"
)

// TestPlainHTTPAllowedForIntranetDNSName pins the plain-HTTP policy for
// targets addressed by an intranet DNS name. A name that resolves exclusively
// to loopback / RFC1918 / link-local addresses is a private-network Studio
// endpoint, so it must be accepted exactly like the literal address it stands
// for; a name that also resolves to a public address, a public-only name and a
// name the intranet DNS cannot resolve must all stay on HTTPS.
func TestPlainHTTPAllowedForIntranetDNSName(t *testing.T) {
	stubHostResolver(t, map[string][]string{
		"studio.internal":       {"10.0.3.104"},
		"corp.internal":         {"192.168.1.20", "::1"},
		"mixed.internal":        {"10.0.3.104", "47.98.43.243"},
		"public.internal":       {"47.98.43.243"},
		"unresolvable.internal": nil,
	})
	tests := []struct {
		name    string
		server  string
		wantErr bool
	}{
		{"http intranet dns name accepted", "http://studio.internal:6789", false},
		{"http second intranet dns name accepted", "http://corp.internal:6789", false},
		{"https intranet dns name accepted", "https://studio.internal:6789", false},
		{"http mixed resolution rejected", "http://mixed.internal:6789", true},
		{"http public dns name rejected", "http://public.internal:6789", true},
		{"http unresolvable dns name rejected", "http://unresolvable.internal:6789", true},
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
				t.Fatalf("validate(%q) = nil, want an HTTPS-required error", tt.server)
			}
			if !tt.wantErr && err != nil {
				t.Fatalf("validate(%q) = %v, want nil", tt.server, err)
			}
		})
	}
}

// TestIsPrivateHostResolvesIntranetNames checks the predicate the policy is
// built on: literal private addresses keep being private, and a hostname
// counts as private only when every address it resolves to is private.
func TestIsPrivateHostResolvesIntranetNames(t *testing.T) {
	stubHostResolver(t, map[string][]string{
		"studio.internal":       {"10.0.3.104"},
		"corp.internal":         {"192.168.1.20", "169.254.10.7"},
		"mixed.internal":        {"10.0.3.104", "47.98.43.243"},
		"public.internal":       {"47.98.43.243", "8.8.8.8"},
		"unresolvable.internal": nil,
	})
	tests := []struct {
		host string
		want bool
	}{
		{"127.0.0.1", true},
		{"10.0.3.104", true},
		{"169.254.1.1", true},
		{"47.98.43.243", false},
		{"studio.internal", true},
		{"corp.internal", true},
		{"mixed.internal", false},
		{"public.internal", false},
		{"unresolvable.internal", false},
	}
	for _, tt := range tests {
		t.Run(tt.host, func(t *testing.T) {
			if got := isPrivateHost(tt.host); got != tt.want {
				t.Fatalf("isPrivateHost(%q) = %t, want %t", tt.host, got, tt.want)
			}
		})
	}
}

// stubHostResolver answers name lookups from table and fails every other name,
// so the scheme policy can be tested without depending on the machine's real
// intranet DNS. It points the process resolver at an in-process DNS server
// bound to a loopback UDP port, so no packet leaves the machine. A table entry
// mapped to an empty or nil slice behaves like a name with no address records.
func stubHostResolver(t *testing.T, table map[string][]string) {
	t.Helper()
	conn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("start stub DNS server: %v", err)
	}
	go serveStubDNS(conn, table)
	original := net.DefaultResolver
	net.DefaultResolver = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, _, _ string) (net.Conn, error) {
			var dialer net.Dialer
			return dialer.DialContext(ctx, "udp", conn.LocalAddr().String())
		},
	}
	t.Cleanup(func() {
		net.DefaultResolver = original
		_ = conn.Close()
	})
}

func serveStubDNS(conn net.PacketConn, table map[string][]string) {
	buffer := make([]byte, 4096)
	for {
		read, from, err := conn.ReadFrom(buffer)
		if err != nil {
			return
		}
		query := append([]byte(nil), buffer[:read]...)
		if response, ok := stubDNSResponse(query, table); ok {
			_, _ = conn.WriteTo(response, from)
		}
	}
}

const (
	stubDNSTypeA    = 1
	stubDNSTypeAAAA = 28
)

// stubDNSResponse echoes the question and answers it with the address records
// of the requested family that table holds for that name.
func stubDNSResponse(query []byte, table map[string][]string) ([]byte, bool) {
	name, offset, ok := readStubDNSName(query, 12)
	if !ok || offset+4 > len(query) {
		return nil, false
	}
	queryType := binary.BigEndian.Uint16(query[offset : offset+2])
	question := query[12 : offset+4]

	addresses, known := table[strings.ToLower(name)]
	var answers []byte
	records := 0
	for _, address := range addresses {
		parsed := net.ParseIP(address)
		if parsed == nil {
			continue
		}
		ipv4 := parsed.To4()
		var data []byte
		recordType := uint16(stubDNSTypeA)
		switch queryType {
		case stubDNSTypeA:
			if ipv4 == nil {
				continue
			}
			data = ipv4
		case stubDNSTypeAAAA:
			if ipv4 != nil {
				continue
			}
			data = parsed.To16()
			recordType = stubDNSTypeAAAA
		default:
			continue
		}
		if data == nil {
			continue
		}
		answers = append(answers, 0xc0, 0x0c) // name: pointer to the question
		answers = binary.BigEndian.AppendUint16(answers, recordType)
		answers = binary.BigEndian.AppendUint16(answers, 1) // IN
		answers = binary.BigEndian.AppendUint32(answers, 60)
		answers = binary.BigEndian.AppendUint16(answers, uint16(len(data)))
		answers = append(answers, data...)
		records++
	}

	response := make([]byte, 12, 12+len(question)+len(answers))
	copy(response[0:2], query[0:2]) // transaction id
	response[2] = 0x81              // response, recursion desired
	response[3] = 0x80              // recursion available, rcode 0
	if !known {
		response[3] = 0x83 // NXDOMAIN
	}
	binary.BigEndian.PutUint16(response[4:6], 1) // one question
	binary.BigEndian.PutUint16(response[6:8], uint16(records))
	response = append(response, question...)
	response = append(response, answers...)
	return response, true
}

func readStubDNSName(message []byte, offset int) (string, int, bool) {
	labels := make([]string, 0, 4)
	for {
		if offset >= len(message) {
			return "", 0, false
		}
		length := int(message[offset])
		offset++
		if length == 0 {
			return strings.Join(labels, "."), offset, true
		}
		if length > 63 || offset+length > len(message) {
			return "", 0, false
		}
		labels = append(labels, string(message[offset:offset+length]))
		offset += length
	}
}
