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
package output

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"

	"gopkg.in/yaml.v3"
)

// Studio results carry json.Number leaves (the client decodes with UseNumber so int64 offsets
// survive). YAML output must render them as numbers, not quoted strings, and an offset beyond
// 2^53 must keep its exact digits instead of being bent through a float64.
func TestYAMLKeepsJSONNumbersNumeric(t *testing.T) {
	payload := map[string]any{
		"skippedCount": json.Number("5"),
		"offset":       json.Number("9007199254740993"), // 2^53 + 1
		"ratio":        json.Number("1.5"),
		"items": []any{
			map[string]any{"size": json.Number("3")},
		},
	}
	var buffer bytes.Buffer
	if err := YAML(&buffer, payload); err != nil {
		t.Fatalf("YAML encode: %v", err)
	}
	rendered := buffer.String()
	for _, forbidden := range []string{`"5"`, `"3"`, `"1.5"`, `"9007199254740993"`} {
		if strings.Contains(rendered, forbidden) {
			t.Fatalf("number rendered as a quoted string: %q in %q", forbidden, rendered)
		}
	}
	if !strings.Contains(rendered, "9007199254740993") {
		t.Fatalf("large offset lost its exact digits: %q", rendered)
	}

	var decoded map[string]any
	if err := yaml.Unmarshal(buffer.Bytes(), &decoded); err != nil {
		t.Fatalf("YAML decode: %v", err)
	}
	if got, ok := decoded["skippedCount"].(int); !ok || got != 5 {
		t.Fatalf("skippedCount = %#v, want int 5", decoded["skippedCount"])
	}
	switch got := decoded["offset"].(type) {
	case int:
		if got != 9007199254740993 {
			t.Fatalf("offset = %d, want 9007199254740993", got)
		}
	case int64:
		if got != 9007199254740993 {
			t.Fatalf("offset = %d, want 9007199254740993", got)
		}
	case uint64:
		if got != 9007199254740993 {
			t.Fatalf("offset = %d, want 9007199254740993", got)
		}
	default:
		t.Fatalf("offset = %#v, want an integer type with value 9007199254740993", decoded["offset"])
	}
	if got, ok := decoded["ratio"].(float64); !ok || got != 1.5 {
		t.Fatalf("ratio = %#v, want float64 1.5", decoded["ratio"])
	}
}
