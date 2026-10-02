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

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

// int64BeyondFloat64Precision is 2^53 + 1, the smallest positive integer that
// float64 cannot represent: decoding the JSON literal into any the default way
// rounds it to 9007199254740992.
const (
	int64BeyondFloat64Precision = int64(9007199254740993)
	roundedInt64Precision       = "9007199254740992"
)

// TestCallToolPreservesInt64BeyondFloat64Precision pins the passthrough
// contract: a numeric literal from the Studio response reaches the decoded
// payload - and therefore every output format - unchanged, including integers
// above 2^53 that float64 cannot hold.
func TestCallToolPreservesInt64BeyondFloat64Precision(t *testing.T) {
	result := callToolForEnvelope(t,
		`{"items":[{"topicName":"orders","msgOffset":9007199254740993,"queueId":3}],"total":9007199254740993}`)

	payload, ok := result.(map[string]any)
	if !ok {
		t.Fatalf("decoded result type = %T, want map[string]any", result)
	}
	items, ok := payload["items"].([]any)
	if !ok || len(items) != 1 {
		t.Fatalf("decoded items = %#v, want one element", payload["items"])
	}
	row, ok := items[0].(map[string]any)
	if !ok {
		t.Fatalf("decoded row type = %T, want map[string]any", items[0])
	}
	for name, value := range map[string]any{
		"items[0].msgOffset": row["msgOffset"],
		"total":              payload["total"],
	} {
		if value != any(int64BeyondFloat64Precision) {
			t.Fatalf("decoded %s = %#v (%T), want int64 %d",
				name, value, value, int64BeyondFloat64Precision)
		}
	}
	if row["queueId"] != any(int64(3)) {
		t.Fatalf("decoded items[0].queueId = %#v (%T), want int64 3", row["queueId"], row["queueId"])
	}
	if row["topicName"] != any("orders") {
		t.Fatalf("decoded items[0].topicName = %#v, want \"orders\"", row["topicName"])
	}

	rendered := map[string]string{}
	for _, format := range []string{"json", "yaml"} {
		var buffer strings.Builder
		if err := output.Structured(&buffer, format, result); err != nil {
			t.Fatalf("render %s: %v", format, err)
		}
		rendered[format] = buffer.String()
	}
	rows, err := output.MapsFromAny(payload["items"])
	if err != nil {
		t.Fatalf("MapsFromAny: %v", err)
	}
	var table strings.Builder
	columns := []output.Column{{Header: "TOPIC", Key: "topicName"}, {Header: "OFFSET", Key: "msgOffset"}}
	if err := output.Rows(&table, rows, columns); err != nil {
		t.Fatalf("render table: %v", err)
	}
	rendered[output.FormatTable] = table.String()
	if !strings.Contains(rendered["yaml"], "msgOffset: 9007199254740993") {
		t.Fatalf("yaml output must keep the integer a number:\n%s", rendered["yaml"])
	}
	for format, text := range rendered {
		if !strings.Contains(text, "9007199254740993") {
			t.Fatalf("%s output lost int64 precision:\n%s", format, text)
		}
		if strings.Contains(text, roundedInt64Precision) {
			t.Fatalf("%s output rounded the int64:\n%s", format, text)
		}
	}
}

// TestMutationSummaryPreservesInt64BeyondFloat64Precision covers the second
// decode site: MutationOutput is re-encoded and decoded again to be inspected,
// so an int64 in a mutation payload must survive that round trip too.
func TestMutationSummaryPreservesInt64BeyondFloat64Precision(t *testing.T) {
	result := callToolForEnvelope(t,
		`{"status":"EXECUTED","instanceId":"instance-dev","result":{"msgOffset":9007199254740993}}`)

	mutation, err := types.DecodeMutationOutput(result)
	if err != nil {
		t.Fatalf("DecodeMutationOutput: %v", err)
	}
	nested, ok := mutation.Result.(map[string]any)
	if !ok {
		t.Fatalf("decoded mutation result type = %T, want map[string]any", mutation.Result)
	}
	if nested["msgOffset"] != any(int64BeyondFloat64Precision) {
		t.Fatalf("decoded mutation msgOffset = %#v (%T), want int64 %d",
			nested["msgOffset"], nested["msgOffset"], int64BeyondFloat64Precision)
	}
	var summary strings.Builder
	if err := output.ToolCallSummary(&summary, result); err != nil {
		t.Fatalf("ToolCallSummary: %v", err)
	}
	if !strings.Contains(summary.String(), "9007199254740993") {
		t.Fatalf("mutation summary lost int64 precision:\n%s", summary.String())
	}
	if strings.Contains(summary.String(), roundedInt64Precision) {
		t.Fatalf("mutation summary rounded the int64:\n%s", summary.String())
	}
}

// callToolForEnvelope runs one tool call against a stub Studio endpoint that
// answers with code 200 and data.
func callToolForEnvelope(t *testing.T, data string) any {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"code":200,"message":"success","data":` + data + `}`))
	}))
	t.Cleanup(server.Close)
	result, err := NewClient(server.Client()).CallTool(context.Background(), Target{
		Server:     server.URL,
		InstanceID: "instance-dev",
		Credential: Credential{AccessKey: "ak", SecretKey: "sk"},
	}, "rmq.topic.list", map[string]any{"instanceId": "instance-dev"})
	if err != nil {
		t.Fatalf("CallTool: %v", err)
	}
	return result
}
