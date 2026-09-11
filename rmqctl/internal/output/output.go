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
	"text/tabwriter"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
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
	table := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(table, "CURRENT\tCONTEXT\tSERVER\tCLUSTER\tACCESS KEY REF\tSECRET KEY REF")
	for _, name := range slices.Sorted(maps.Keys(cfg.Contexts)) {
		current := ""
		if name == cfg.CurrentContext {
			current = "*"
		}
		context := cfg.Contexts[name]
		fmt.Fprintf(table, "%s\t%s\t%s\t%s\t%s\t%s\n",
			current, name, context.Server, context.Cluster,
			context.Credential.AccessKeyRef, context.Credential.SecretKeyRef)
	}
	return table.Flush()
}

func ToolCallSummary(w io.Writer, result any) error {
	mutation, err := types.DecodeMutationOutput(result)
	if err != nil {
		return err
	}
	table := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(table, "CLUSTER\tSTATUS\tCONFIRM TOKEN")
	fmt.Fprintf(table, "%s\t%s\t%s\n",
		mutation.Cluster, mutation.Status, mutation.ConfirmToken)
	if err := table.Flush(); err != nil {
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
	table := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	headers := make([]string, 0, len(columns))
	for _, col := range columns {
		headers = append(headers, col.Header)
	}
	fmt.Fprintln(table, strings.Join(headers, "\t"))
	for _, row := range rows {
		values := make([]string, 0, len(columns))
		for _, col := range columns {
			values = append(values, stringify(row[col.Key]))
		}
		fmt.Fprintln(table, strings.Join(values, "\t"))
	}
	return table.Flush()
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
		return text
	}
	if f, ok := value.(float64); ok {
		return strconv.FormatFloat(f, 'f', -1, 64)
	}
	return fmt.Sprint(value)
}
