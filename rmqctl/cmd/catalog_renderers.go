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
package cmd

import (
	"fmt"
	"io"
	"maps"
	"slices"
	"strings"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
)

func renderTable(w io.Writer, tool toolcatalog.Tool, result any) error {
	if tool.ViewHint != "table" {
		return output.JSON(w, result)
	}
	rows, err := tableRows(result, tool.TableDataKey)
	if err != nil {
		return fmt.Errorf("tool %s returned invalid table output: %w", tool.Name, err)
	}
	if len(rows) == 0 {
		return output.JSON(w, result)
	}
	return output.Rows(w, rows, tableColumns(rows))
}

func tableRows(result any, dataKey string) ([]map[string]any, error) {
	if dataKey == "" {
		return output.MapsFromAny(result)
	}
	payload, ok := result.(map[string]any)
	if !ok {
		return nil, fmt.Errorf("expected an object containing %q, got %T", dataKey, result)
	}
	value, exists := payload[dataKey]
	if !exists {
		return nil, fmt.Errorf("missing table data field %q", dataKey)
	}
	return output.MapsFromAny(value)
}

func tableColumns(rows []map[string]any) []output.Column {
	keys := make(map[string]struct{})
	for _, row := range rows {
		for key := range row {
			keys[key] = struct{}{}
		}
	}
	names := slices.Sorted(maps.Keys(keys))
	columns := make([]output.Column, 0, len(names))
	for _, name := range names {
		columns = append(columns, output.Column{Header: strings.ToUpper(name), Key: name})
	}
	return columns
}
