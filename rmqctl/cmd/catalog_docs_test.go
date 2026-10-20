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
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
)

// TestQueryDlqDeclaresHowItsFiltersApply pins the contract of the dead-letter query tool, which only
// its schema can state: --help and explain render these descriptions, and an MCP client receives
// nothing else. The tool has two modes, and input that cannot apply to the selected one is dropped
// without a word - so the schema has to carry that, together with the window an omitted time filter
// silently means.
func TestQueryDlqDeclaresHowItsFiltersApply(t *testing.T) {
	document := toolcatalog.Default()
	var tool *toolcatalog.Tool
	for index := range document.Tools {
		if document.Tools[index].Name == "rmq.message.query_dlq" {
			tool = &document.Tools[index]
			break
		}
	}
	if tool == nil {
		t.Fatal("the catalog does not declare rmq.message.query_dlq")
	}

	usage := map[string]string{}
	var collect func(schema toolcatalog.InputSchema, prefix string)
	collect = func(schema toolcatalog.InputSchema, prefix string) {
		for _, field := range schema.Fields {
			if field.Object != nil {
				collect(*field.Object, prefix+field.Name+".")
				continue
			}
			usage[prefix+field.Name] = schemaFlagUsage(field)
		}
	}
	collect(tool.InputSchema, "")

	for property, want := range map[string]string{
		"groupName":      "omit it to list the dead-letter groups instead",
		"search":         "it has no effect once groupName is given",
		"time.startTime": "defaults to one hour before endTime",
		"time.endTime":   "Message listing only",
	} {
		if !strings.Contains(usage[property], want) {
			t.Errorf("%s usage = %q, want it to mention %q", property, usage[property], want)
		}
	}
	if !strings.Contains(usage["time.startTime"], "Epoch milliseconds") {
		t.Errorf("time.startTime usage = %q, want the unit it is expressed in", usage["time.startTime"])
	}
}
