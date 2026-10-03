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
	"strings"
	"testing"
)

func TestToolCallSummary(t *testing.T) {
	buf := &bytes.Buffer{}
	result := map[string]any{
		"status":        "PLANNED",
		"instanceId":    "dev",
		"confirm_token": "confirmation-token",
		"plan":          map[string]any{"summary": "Create topic orders"},
	}
	if err := ToolCallSummary(buf, result); err != nil {
		t.Fatal(err)
	}
	for _, expected := range []string{
		"dev", "PLANNED", "confirmation-token", "Create topic orders",
	} {
		if !strings.Contains(buf.String(), expected) {
			t.Fatalf("output missing %q:\n%s", expected, buf)
		}
	}
}

func TestRowsEscapesControlCharactersInCellValues(t *testing.T) {
	buf := &bytes.Buffer{}
	rows := []map[string]any{
		{"name": "broker-a", "desc": "x\ty\nz\rw"},
	}
	columns := []Column{{Header: "NAME", Key: "name"}, {Header: "DESC", Key: "desc"}}
	if err := Rows(buf, rows, columns); err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSuffix(buf.String(), "\n"), "\n")
	// One header line and one data line: an embedded tab would be read as a cell
	// delimiter and an embedded newline or CR would split or overwrite the row.
	if len(lines) != 2 {
		t.Fatalf("expected 2 lines (header + data), got %d:\n%q", len(lines), buf.String())
	}
	data := lines[1]
	if strings.Contains(data, "x\ty") || strings.Contains(data, "y\nz") || strings.Contains(data, "\r") {
		t.Fatalf("data line contains raw control characters: %q", data)
	}
	if !strings.Contains(data, `x\ty\nz\rw`) {
		t.Fatalf("data line does not contain escaped value: %q", data)
	}
}
