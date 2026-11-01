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
	"errors"
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

// The paged tools hand pageSize to a service that rejects anything above 100 with a 400
// ("pageSize must be between 1 and 100"), so the bounds belong to the tool contract and not only to
// the server: a caller that follows the schema must not be able to build a call that fails at
// execution, and `--help` has to name the ceiling the same way it names the minimum.
func TestPagedToolPageSizeCeilingTest(t *testing.T) {
	for _, toolName := range []string{"rmq.acl.list", "rmq.audit.list", "rmq.message.query_dlq"} {
		t.Run(toolName, func(t *testing.T) {
			tool, ok := toolcatalog.LookupTool(toolName)
			if !ok {
				t.Fatalf("tool %q is missing", toolName)
			}
			pageSize, ok := nestedField(tool, "page", "pageSize")
			if !ok {
				t.Fatalf("%s has no page.pageSize field", toolName)
			}
			if !pageSize.HasMaximum || pageSize.Maximum != 100 {
				t.Fatalf("%s page.pageSize = %+v; want maximum 100", toolName, pageSize)
			}

			if err := validateValue(tool, pageSize, int64(100)); err != nil {
				t.Fatalf("%s rejects the boundary value 100: %v", toolName, err)
			}
			for _, value := range []int64{101, 1000} {
				err := validateValue(tool, pageSize, value)
				if err == nil {
					t.Fatalf("%s accepts pageSize %d, which the service rejects at execution", toolName, value)
				}
				assertInvalidArgument(t, err, "must be at most 100")
			}
			assertInvalidArgument(t, validateValue(tool, pageSize, int64(0)), "must be at least 1")

			usage := schemaFlagUsage(pageSize)
			if !strings.Contains(usage, "(maximum: 100)") {
				t.Fatalf("%s --help usage = %q; want the ceiling advertised next to the minimum", toolName, usage)
			}
			if !strings.Contains(usage, "(minimum: 1)") {
				t.Fatalf("%s --help usage = %q; want the minimum still advertised", toolName, usage)
			}
		})
	}
}

func nestedField(tool toolcatalog.Tool, path ...string) (toolcatalog.Field, bool) {
	fields := tool.InputSchema.Fields
	for index, name := range path {
		var found *toolcatalog.Field
		for position := range fields {
			if fields[position].Name == name {
				found = &fields[position]
				break
			}
		}
		if found == nil {
			return toolcatalog.Field{}, false
		}
		if index == len(path)-1 {
			return *found, true
		}
		if found.Object == nil {
			return toolcatalog.Field{}, false
		}
		fields = found.Object.Fields
	}
	return toolcatalog.Field{}, false
}

func assertInvalidArgument(t *testing.T, err error, want string) {
	t.Helper()
	if err == nil {
		t.Fatalf("err = nil, want INVALID_ARGUMENT containing %q", want)
	}
	var cliError *types.CLIError
	if !errors.As(err, &cliError) || cliError.Code != types.CodeInvalidArgument {
		t.Fatalf("err = %v, want INVALID_ARGUMENT", err)
	}
	if !strings.Contains(cliError.Message, want) {
		t.Fatalf("message = %q, want it to contain %q", cliError.Message, want)
	}
}
