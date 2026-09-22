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
	"context"
	"errors"
	"net"
	"net/url"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

func TestNormalizeCLIError(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want string
	}{
		{
			// Ctrl-C cancels the command context mid-request; the transport wraps it in a
			// *url.Error, which is a net.Error. It must surface as CANCELED, not UNAVAILABLE.
			name: "interrupted request reports canceled",
			err: &url.Error{
				Op:  "Post",
				URL: "http://studio.example/api",
				Err: context.Canceled,
			},
			want: types.CodeCanceled,
		},
		{
			name: "bare context canceled reports canceled",
			err:  context.Canceled,
			want: types.CodeCanceled,
		},
		{
			name: "deadline exceeded reports timeout",
			err: &url.Error{
				Op:  "Get",
				URL: "http://studio.example/api",
				Err: context.DeadlineExceeded,
			},
			want: types.CodeTimeout,
		},
		{
			name: "connection refused reports unavailable",
			err: &url.Error{
				Op:  "Get",
				URL: "http://studio.example/api",
				Err: &net.OpError{Op: "dial", Err: errors.New("connection refused")},
			},
			want: types.CodeUnavailable,
		},
		{
			name: "plain error reports command failed",
			err:  errors.New("something else failed"),
			want: types.CodeCommandFailed,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := normalizeCLIError(tt.err)
			if got.Code != tt.want {
				t.Fatalf("normalizeCLIError(%v).Code = %q, want %q", tt.err, got.Code, tt.want)
			}
			if got.Code == types.CodeCanceled {
				if got.Hint == "" || got.Hint == "Check --server, network connectivity, and Studio Server status." {
					t.Fatalf("canceled hint must point at the interrupt, got %q", got.Hint)
				}
			}
		})
	}
}
