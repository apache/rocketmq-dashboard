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
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

// streamedResponseBody reports how many bytes the client actually pulled from
// the response body, so a test can prove the read stopped at the cap.
type streamedResponseBody struct {
	reader io.Reader
	read   int
}

func (body *streamedResponseBody) Read(buffer []byte) (int, error) {
	count, err := body.reader.Read(buffer)
	body.read += count
	return count, err
}

func (body *streamedResponseBody) Close() error { return nil }

func oversizedRequestClient(body *streamedResponseBody) Client {
	return NewClient(&http.Client{Transport: roundTripFunc(
		func(request *http.Request) (*http.Response, error) {
			return &http.Response{
				StatusCode: http.StatusOK,
				Body:       body,
				Header:     http.Header{"Content-Type": []string{"application/json"}},
			}, nil
		})})
}

func requestTarget() Target {
	return Target{
		Server:     "https://studio.example.com",
		InstanceID: "instance-a",
		Credential: Credential{AccessKey: "access", SecretKey: "secret"},
		Timeout:    5 * time.Second,
	}
}

// TestRequestRejectsResponseBodyAboveLimit verifies that a Studio response body
// larger than the cap is refused instead of being buffered in full.
func TestRequestRejectsResponseBodyAboveLimit(t *testing.T) {
	restore := clientResponseBodyLimit
	clientResponseBodyLimit = 64
	t.Cleanup(func() { clientResponseBodyLimit = restore })

	// Far more data than the cap; the client must stop reading at the cap.
	body := &streamedResponseBody{reader: strings.NewReader(strings.Repeat("x", 4096))}
	err := oversizedRequestClient(body).request(
		context.Background(), requestTarget(), http.MethodGet, "/api/clusters", nil, nil)

	if err == nil {
		t.Fatal("request() error = nil, want an oversized-body error")
	}
	if !strings.Contains(err.Error(), "response body exceeds") {
		t.Fatalf("request() error = %v, want it to name the response body cap", err)
	}
	if body.read > clientResponseBodyLimit+1 {
		t.Fatalf("read %d bytes from the response body, want at most %d",
			body.read, clientResponseBodyLimit+1)
	}
}

// TestRequestAcceptsResponseBodyAtLimit verifies the cap does not reject a body
// that fits, so ordinary responses still decode.
func TestRequestAcceptsResponseBodyAtLimit(t *testing.T) {
	payload := `{"code":200,"data":{"name":"cluster-a"}}`
	body := &streamedResponseBody{reader: strings.NewReader(payload)}
	var out struct {
		Name string `json:"name"`
	}

	err := oversizedRequestClient(body).request(
		context.Background(), requestTarget(), http.MethodGet, "/api/clusters", nil, &out)

	if err != nil {
		t.Fatalf("request() error = %v, want nil", err)
	}
	if out.Name != "cluster-a" {
		t.Fatalf("decoded name = %q, want %q", out.Name, "cluster-a")
	}
}
