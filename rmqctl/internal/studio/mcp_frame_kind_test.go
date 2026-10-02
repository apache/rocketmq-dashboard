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
	"strings"
	"testing"
)

// TestDecodeMCPMessageNamesTheFrameKind pins that a rejected frame says which
// kind of frame it was. decodeMCPMessage classifies the frames arriving on the
// stdio channel, and a JSON-RPC response frame (an id with no method) is not
// one this transport originates, so the caller must be able to tell that case
// apart from a frame that carries neither an id nor a method.
func TestDecodeMCPMessageNamesTheFrameKind(t *testing.T) {
	tests := []struct {
		name string
		// frame is the raw frame handed to decodeMCPMessage.
		frame string
		// wantErrIn is required in the error text, wantErrNotIn is forbidden.
		wantErrIn    []string
		wantErrNotIn []string
	}{
		{
			name:         "response frame carrying a result",
			frame:        `{"jsonrpc":"2.0","id":7,"result":{"ok":true}}`,
			wantErrIn:    []string{"response frame"},
			wantErrNotIn: []string{"missing method"},
		},
		{
			name:         "response frame carrying an error",
			frame:        `{"jsonrpc":"2.0","id":8,"error":{"code":-32000,"message":"boom"}}`,
			wantErrIn:    []string{"response frame"},
			wantErrNotIn: []string{"missing method"},
		},
		{
			name:         "response frame with a null result",
			frame:        `{"jsonrpc":"2.0","id":"a3","result":null}`,
			wantErrIn:    []string{"response frame"},
			wantErrNotIn: []string{"missing method"},
		},
		{
			// Nothing identifies this frame: it is not a response frame and the
			// error must not pretend that it is.
			name:         "frame without id and without method",
			frame:        `{"jsonrpc":"2.0","params":{}}`,
			wantErrIn:    []string{"missing method"},
			wantErrNotIn: []string{"response frame"},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := decodeMCPMessage([]byte(tt.frame))
			if err == nil {
				t.Fatalf("decodeMCPMessage(%s) = nil error, want a decode failure", tt.frame)
			}
			for _, want := range tt.wantErrIn {
				if !strings.Contains(err.Error(), want) {
					t.Fatalf("decodeMCPMessage(%s) error = %q, want it to mention %q",
						tt.frame, err.Error(), want)
				}
			}
			for _, forbidden := range tt.wantErrNotIn {
				if strings.Contains(err.Error(), forbidden) {
					t.Fatalf("decodeMCPMessage(%s) error = %q, want it not to mention %q",
						tt.frame, err.Error(), forbidden)
				}
			}
		})
	}
}

// TestDecodeMCPMessageKeepsDecodingClientFrames guards the frames this
// transport does originate: naming the response frame must not disturb the
// request and notification paths.
func TestDecodeMCPMessageKeepsDecodingClientFrames(t *testing.T) {
	request, err := decodeMCPMessage([]byte(
		`{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{}}`))
	if err != nil {
		t.Fatalf("request frame no longer decodes: %v", err)
	}
	if request.request == nil || request.notification != nil {
		t.Fatalf("request frame decoded as %#v, want a request", request)
	}

	notification, err := decodeMCPMessage([]byte(
		`{"jsonrpc":"2.0","method":"notifications/initialized"}`))
	if err != nil {
		t.Fatalf("notification frame no longer decodes: %v", err)
	}
	if notification.notification == nil || notification.request != nil {
		t.Fatalf("notification frame decoded as %#v, want a notification", notification)
	}
}
