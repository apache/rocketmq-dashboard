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
	"errors"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"golang.org/x/text/width"
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

// CJK ideographs render two terminal cells wide; a table that pads by rune
// count misaligns every column after a wide-rune cell.
func TestRowsAlignsColumnsForWideRunes(t *testing.T) {
	buf := &bytes.Buffer{}
	rows := []map[string]any{
		{"name": "订单主题", "queues": 8},
		{"name": "orders", "queues": 8},
	}
	columns := []Column{{Header: "NAME", Key: "name"}, {Header: "QUEUES", Key: "queues"}}
	if err := Rows(buf, rows, columns); err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSuffix(buf.String(), "\n"), "\n")
	if len(lines) != 3 {
		t.Fatalf("expected 3 lines, got %d", len(lines))
	}
	// The QUEUES column must start at the same terminal display column on
	// every line: the display width of the prefix up to the column start.
	displayColumn := func(line string) int {
		idx := strings.LastIndex(line, "8")
		if idx < 0 {
			t.Fatalf("no value column in line %q", line)
		}
		cells := 0
		for _, r := range line[:idx] {
			switch width.LookupRune(r).Kind() {
			case width.EastAsianWide, width.EastAsianFullwidth:
				cells += 2
			default:
				cells++
			}
		}
		return cells
	}
	cjk := displayColumn(lines[1])
	ascii := displayColumn(lines[2])
	if cjk != ascii {
		t.Fatalf("QUEUES column starts at display column %d for the CJK row but %d for the ASCII row:\n%s", cjk, ascii, buf.String())
	}
}

// errWriter fails every write, the way the output stream does once the pager
// or terminal on the other end goes away.
type errWriter struct{}

var errWriteFailed = errors.New("write failed")

func (errWriter) Write([]byte) (int, error) { return 0, errWriteFailed }

// The table renderers must propagate write errors like the tabwriter Flush
// calls they replaced did; the error checks in cmd/ depend on it.
func TestTableRenderersPropagateWriteErrors(t *testing.T) {
	mutation := map[string]any{
		"status":        "PLANNED",
		"instanceId":    "dev",
		"confirm_token": "confirmation-token",
		"plan":          map[string]any{"summary": "Create topic orders"},
	}
	if err := Rows(errWriter{}, []map[string]any{{"name": "orders"}},
		[]Column{{Header: "NAME", Key: "name"}}); !errors.Is(err, errWriteFailed) {
		t.Fatalf("Rows did not propagate the write error: %v", err)
	}
	if err := ConfigTable(errWriter{}, config.Config{}); !errors.Is(err, errWriteFailed) {
		t.Fatalf("ConfigTable did not propagate the write error: %v", err)
	}
	if err := ToolCallSummary(errWriter{}, mutation); !errors.Is(err, errWriteFailed) {
		t.Fatalf("ToolCallSummary did not propagate the write error: %v", err)
	}
}
