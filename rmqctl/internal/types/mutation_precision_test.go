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
package types

import (
	"bytes"
	"encoding/json"
	"reflect"
	"testing"
)

func TestDecodeMutationOutputPreservesNestedNumbers(t *testing.T) {
	const nested = `{"queues":[{"offset":9007199254740993,"lag":9223372036854775807}],"values":[-9223372036854775808,1.25,1e+20,true,null,"9007199254740993"]}`
	for _, status := range []MutationStatus{MutationPlanned, MutationExecuted} {
		t.Run(string(status), func(t *testing.T) {
			input := MutationOutput{Status: status, InstanceID: "test-instance", Plan: json.RawMessage(nested), Result: json.RawMessage(nested), ConfirmToken: "synthetic-token"}
			decoded, err := DecodeMutationOutput(input)
			if err != nil {
				t.Fatal(err)
			}
			if decoded.Status != status || decoded.InstanceID != input.InstanceID || decoded.ConfirmToken != input.ConfirmToken {
				t.Fatalf("metadata changed: %#v", decoded)
			}
			for name, value := range map[string]any{"plan": decoded.Plan, "result": decoded.Result} {
				payload, err := json.Marshal(value)
				if err != nil {
					t.Fatal(err)
				}

				var got, want any
				gotDecoder := json.NewDecoder(bytes.NewReader(payload))
				gotDecoder.UseNumber()
				if err := gotDecoder.Decode(&got); err != nil {
					t.Fatal(err)
				}
				wantDecoder := json.NewDecoder(bytes.NewBufferString(nested))
				wantDecoder.UseNumber()
				if err := wantDecoder.Decode(&want); err != nil {
					t.Fatal(err)
				}
				if !reflect.DeepEqual(got, want) {
					t.Errorf("%s = %s, want %s", name, payload, nested)
				}
			}
		})
	}
}

func TestDecodeMutationOutputRejectsInvalidValues(t *testing.T) {
	for _, value := range []any{make(chan int), map[string]any{"status": true}, map[string]any{"status": "UNKNOWN"}} {
		if _, err := DecodeMutationOutput(value); err == nil {
			t.Fatalf("accepted invalid value %#v", value)
		}
	}
}
