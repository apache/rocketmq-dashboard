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
	"encoding/json"
	"fmt"
	"io"
	"maps"
	"slices"
	"strconv"
	"strings"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
	"golang.org/x/text/width"
	"gopkg.in/yaml.v3"
)

const (
	FormatTable = "table"
	formatJSON  = "json"
	formatYAML  = "yaml"
)

type Column struct {
	Header string
	Key    string
}

func JSON(w io.Writer, value any) error {
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	return encoder.Encode(value)
}

func YAML(w io.Writer, value any) error {
	encoder := yaml.NewEncoder(w)
	encoder.SetIndent(2)
	if err := encoder.Encode(value); err != nil {
		_ = encoder.Close()
		return err
	}
	return encoder.Close()
}

func Structured(w io.Writer, format string, value any) error {
	switch format {
	case formatJSON:
		return JSON(w, value)
	case formatYAML:
		return YAML(w, value)
	default:
		return fmt.Errorf("unsupported structured output format: %s", format)
	}
}

func IsStructured(format string) bool {
	return format == formatJSON || format == formatYAML
}

// RequireFormat validates that format is a supported output format.
func RequireFormat(format string) error {
	if format == FormatTable || IsStructured(format) {
		return nil
	}
	return types.NewCLIError(
		types.CodeInvalidArgument,
		fmt.Sprintf("unsupported output format: %s", format),
		"Use --output table, --output json, or --output yaml.")
}

func ConfigTable(w io.Writer, cfg config.Config) error {
	lines := [][]string{{"CURRENT", "CONTEXT", "SERVER", "ACCESS KEY REF", "SECRET KEY REF"}}
	for _, name := range slices.Sorted(maps.Keys(cfg.Contexts)) {
		current := ""
		if name == cfg.CurrentContext {
			current = "*"
		}
		context := cfg.Contexts[name]
		lines = append(lines, []string{
			current, name, context.Server,
			context.Credential.AccessKeyRef, context.Credential.SecretKeyRef,
		})
	}
	return writeTable(w, lines)
}

func ToolCallSummary(w io.Writer, result any) error {
	mutation, err := types.DecodeMutationOutput(result)
	if err != nil {
		return err
	}
	if err := writeTable(w, [][]string{
		{"INSTANCE", "STATUS", "CONFIRM TOKEN"},
		{mutation.InstanceID, string(mutation.Status), mutation.ConfirmToken},
	}); err != nil {
		return err
	}
	payload := mutation.Result
	if mutation.Status == types.MutationPlanned {
		payload = mutation.Plan
	}
	if payload != nil {
		fmt.Fprintln(w)
		return JSON(w, payload)
	}
	return nil
}

func Rows(w io.Writer, rows []map[string]any, columns []Column) error {
	headers := make([]string, 0, len(columns))
	for _, col := range columns {
		headers = append(headers, col.Header)
	}
	lines := [][]string{headers}
	for _, row := range rows {
		values := make([]string, 0, len(columns))
		for _, col := range columns {
			values = append(values, stringify(row[col.Key]))
		}
		lines = append(lines, values)
	}
	return writeTable(w, lines)
}

// writeTable renders single-line cells in fixed columns padded to terminal
// display width. text/tabwriter pads by rune count, so CJK ideographs
// (common in topic and consumer-group names) — which render two cells wide in
// every terminal — came out misaligned; this pads by the width the terminal
// actually displays. The last column is not padded, matching tabwriter's
// trailing behavior. It returns the first write error, like the
// tabwriter.Flush it replaced.
func writeTable(w io.Writer, lines [][]string) error {
	widths := make([]int, 0, 8)
	for _, line := range lines {
		for i, cell := range line {
			for i >= len(widths) {
				widths = append(widths, 0)
			}
			if cw := displayWidth(cell); cw > widths[i] {
				widths[i] = cw
			}
		}
	}
	for _, line := range lines {
		var builder strings.Builder
		for i, cell := range line {
			if i > 0 {
				builder.WriteString("  ")
			}
			builder.WriteString(cell)
			if i < len(line)-1 && i < len(widths) {
				builder.WriteString(strings.Repeat(" ", widths[i]-displayWidth(cell)))
			}
		}
		builder.WriteByte('\n')
		if _, err := fmt.Fprint(w, builder.String()); err != nil {
			return err
		}
	}
	return nil
}

// displayWidth returns the number of terminal cells a single-line string
// occupies, counting East Asian Wide and Fullwidth runes (e.g. CJK
// ideographs, kana, fullwidth forms) as two cells. East Asian Ambiguous
// runes (e.g. Greek, Cyrillic, °) count as one cell here, which is wrong on
// the many CJK terminals configured to render ambiguous-width runes two
// cells wide; the width table cannot know the terminal's locale setting.
func displayWidth(s string) int {
	cells := 0
	for _, r := range s {
		switch width.LookupRune(r).Kind() {
		case width.EastAsianWide, width.EastAsianFullwidth:
			cells += 2
		default:
			cells++
		}
	}
	return cells
}

// MapsFromAny converts a JSON-decoded value into a slice of row maps for table
// rendering. It returns an error if the input is an array containing non-object
// elements (e.g. null, string, number) or if the top-level value is not an
// object or array, so callers can surface data-quality issues to the user
// instead of silently dropping rows.
func MapsFromAny(value any) ([]map[string]any, error) {
	switch typed := value.(type) {
	case []any:
		rows := make([]map[string]any, 0, len(typed))
		for i, item := range typed {
			row, ok := item.(map[string]any)
			if !ok {
				return nil, fmt.Errorf("expected array element %d to be an object, got %T", i, item)
			}
			rows = append(rows, row)
		}
		return rows, nil
	case []map[string]any:
		return typed, nil
	case map[string]any:
		return []map[string]any{typed}, nil
	case nil:
		return nil, nil
	default:
		return nil, fmt.Errorf("expected an object or array of objects, got %T", value)
	}
}

func stringify(value any) string {
	if value == nil {
		return ""
	}
	if text, ok := value.(string); ok {
		// Table rows are tab-joined and newline-terminated, so a cell containing a
		// raw tab would be read as a column delimiter and an embedded newline or CR
		// would split or overwrite the row. Render the escapes instead.
		return strings.NewReplacer("\t", "\\t", "\n", "\\n", "\r", "\\r").Replace(text)
	}
	if f, ok := value.(float64); ok {
		return strconv.FormatFloat(f, 'f', -1, 64)
	}
	return fmt.Sprint(value)
}
