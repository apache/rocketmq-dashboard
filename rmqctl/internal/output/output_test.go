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
		"cluster":       "dev",
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
