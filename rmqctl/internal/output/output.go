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
	"sort"
	"strings"
	"text/tabwriter"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
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

func ConfigTable(w io.Writer, cfg config.Config) error {
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(tw, "CURRENT\tPROFILE\tSERVER\tTOKEN\tCLUSTER")
	names := make([]string, 0, len(cfg.Profiles))
	for name := range cfg.Profiles {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		current := ""
		if name == cfg.CurrentProfile {
			current = "*"
		}
		p := cfg.Profiles[name]
		fmt.Fprintf(tw, "%s\t%s\t%s\t%s\t%s\n", current, name, p.Server, p.TokenRef, p.Cluster)
	}
	return tw.Flush()
}

func ToolTable(w io.Writer, tools []types.ToolView) error {
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(tw, "NAME\tLEVEL\tIMPLEMENTED\tDESCRIPTION")
	for _, tool := range tools {
		fmt.Fprintf(tw, "%s\t%s\t%t\t%s\n",
			tool.Name,
			tool.OperationLevel,
			tool.Implemented,
			tool.Description)
	}
	return tw.Flush()
}

func ToolCallSummary(w io.Writer, result types.ToolExecutionResult) error {
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(tw, "REQUEST ID\tTOOL\tEXECUTED\tDRY RUN\tERROR\tMESSAGE")
	fmt.Fprintf(tw, "%s\t%s\t%t\t%t\t%s\t%s\n",
		result.RequestID,
		result.ToolName,
		result.Executed,
		result.DryRun,
		result.ErrorCode,
		result.Message)
	if err := tw.Flush(); err != nil {
		return err
	}
	if result.Result != nil {
		fmt.Fprintln(w)
		return JSON(w, result.Result)
	}
	return nil
}

func Rows(w io.Writer, rows []map[string]any, columns []Column) error {
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	headers := make([]string, 0, len(columns))
	for _, col := range columns {
		headers = append(headers, col.Header)
	}
	fmt.Fprintln(tw, strings.Join(headers, "\t"))
	for _, row := range rows {
		values := make([]string, 0, len(columns))
		for _, col := range columns {
			values = append(values, stringify(row[col.Key]))
		}
		fmt.Fprintln(tw, strings.Join(values, "\t"))
	}
	return tw.Flush()
}

func MapsFromAny(value any) []map[string]any {
	switch typed := value.(type) {
	case []any:
		rows := make([]map[string]any, 0, len(typed))
		for _, item := range typed {
			if row, ok := item.(map[string]any); ok {
				rows = append(rows, row)
			}
		}
		return rows
	case []map[string]any:
		return typed
	case map[string]any:
		return []map[string]any{typed}
	default:
		return nil
	}
}

func FilterRows(rows []map[string]any, key string, expected string) []map[string]any {
	filtered := make([]map[string]any, 0, len(rows))
	for _, row := range rows {
		if strings.EqualFold(stringify(row[key]), expected) {
			filtered = append(filtered, row)
		}
	}
	return filtered
}

func stringify(value any) string {
	if value == nil {
		return ""
	}
	if text, ok := value.(string); ok {
		return text
	}
	if stringer, ok := value.(fmt.Stringer); ok {
		return stringer.String()
	}
	return fmt.Sprint(value)
}
