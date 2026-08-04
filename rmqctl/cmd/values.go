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
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

func parseKeyValue(value string) (string, any, error) {
	key, raw, ok := strings.Cut(value, "=")
	if !ok || strings.TrimSpace(key) == "" {
		return "", nil, fmt.Errorf("argument must use key=value: %s", value)
	}
	return strings.TrimSpace(key), parseScalar(raw), nil
}

func parseScalar(value string) any {
	text := strings.TrimSpace(value)
	if text == "true" {
		return true
	}
	if text == "false" {
		return false
	}
	if i, err := strconv.ParseInt(text, 10, 64); err == nil {
		return i
	}
	if f, err := strconv.ParseFloat(text, 64); err == nil {
		return f
	}
	return value
}

func toolArguments(rawJSON string, values []string) (map[string]any, error) {
	result := map[string]any{}
	if rawJSON != "" {
		if err := json.Unmarshal([]byte(rawJSON), &result); err != nil {
			return nil, fmt.Errorf("invalid --raw-json: %w", err)
		}
	}
	for _, value := range values {
		key, parsed, err := parseKeyValue(value)
		if err != nil {
			return nil, err
		}
		result[key] = parsed
	}
	return result, nil
}
