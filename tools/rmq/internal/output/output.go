// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package output renders rmqctl values without relying on process-global
// output streams.
package output

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"sort"
	"strings"
	"text/tabwriter"

	"go.yaml.in/yaml/v3"
)

// Table describes stable tabular output.
type Table struct {
	Headers []string
	Rows    [][]string
}

// NewKeyValueTable converts a map to a two-column table in stable key order.
func NewKeyValueTable(headers []string, values map[string]string) *Table {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	rows := make([][]string, 0, len(keys))
	for _, key := range keys {
		rows = append(rows, []string{key, values[key]})
	}
	return &Table{
		Headers: append([]string(nil), headers...),
		Rows:    rows,
	}
}

// Write renders value as table, JSON, or YAML.
func Write(writer io.Writer, format string, value any, table *Table) error {
	if writer == nil {
		return errors.New("output writer must not be nil")
	}

	switch format {
	case "table":
		if table == nil {
			return errors.New("table output requires a table descriptor")
		}
		return writeTable(writer, table)
	case "json":
		contents, err := json.MarshalIndent(value, "", "  ")
		if err != nil {
			return fmt.Errorf("encode JSON output: %w", err)
		}
		return writeBytes(writer, appendExactlyOneNewline(contents))
	case "yaml":
		contents, err := yaml.Marshal(value)
		if err != nil {
			return fmt.Errorf("encode YAML output: %w", err)
		}
		return writeBytes(writer, appendExactlyOneNewline(contents))
	default:
		return fmt.Errorf("unsupported output format %q; choose table, json, or yaml", format)
	}
}

func writeTable(writer io.Writer, table *Table) error {
	if len(table.Headers) == 0 {
		return errors.New("table descriptor must contain headers")
	}
	for _, row := range table.Rows {
		if len(row) != len(table.Headers) {
			return fmt.Errorf("table row has %d columns; want %d", len(row), len(table.Headers))
		}
	}

	tabs := tabwriter.NewWriter(writer, 0, 4, 2, ' ', 0)
	if _, err := fmt.Fprintln(tabs, strings.Join(table.Headers, "\t")); err != nil {
		return fmt.Errorf("write table header: %w", err)
	}
	for _, row := range table.Rows {
		if _, err := fmt.Fprintln(tabs, strings.Join(row, "\t")); err != nil {
			return fmt.Errorf("write table row: %w", err)
		}
	}
	if err := tabs.Flush(); err != nil {
		return fmt.Errorf("flush table output: %w", err)
	}
	return nil
}

func appendExactlyOneNewline(contents []byte) []byte {
	trimmed := bytes.TrimRight(contents, "\r\n")
	result := make([]byte, len(trimmed)+1)
	copy(result, trimmed)
	result[len(result)-1] = '\n'
	return result
}

func writeBytes(writer io.Writer, contents []byte) error {
	written, err := writer.Write(contents)
	if err != nil {
		return err
	}
	if written != len(contents) {
		return io.ErrShortWrite
	}
	return nil
}
