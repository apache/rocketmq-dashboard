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
	"bytes"
	"errors"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
)

// failingWriter rejects every write so tests can exercise the paths where a
// command must report that it could not deliver its output.
type failingWriter struct{}

func (failingWriter) Write([]byte) (int, error) {
	return 0, errors.New("output unavailable")
}

func versionTextOutputSucceedsTest(t *testing.T) {
	var out bytes.Buffer
	app := &App{Out: &out, Err: &out}
	command := app.newVersionCommand(&option{output: output.FormatTable})
	if err := command.Execute(); err != nil {
		t.Fatalf("version command failed: %v", err)
	}
	if !strings.Contains(out.String(), "rmqctl: ") {
		t.Fatalf("version output missing the rmqctl line: %q", out.String())
	}
}

func versionTextOutputFailureIsReportedTest(t *testing.T) {
	app := &App{Out: failingWriter{}, Err: &bytes.Buffer{}}
	command := app.newVersionCommand(&option{output: output.FormatTable})
	err := command.Execute()
	if err == nil {
		t.Fatal("version command hid a failing text output writer")
	}
	if !strings.Contains(err.Error(), "output unavailable") {
		t.Fatalf("error does not carry the writer failure: %v", err)
	}
}

func TestVersionCommand(t *testing.T) {
	t.Run("text output succeeds", versionTextOutputSucceedsTest)
	t.Run("text output failure is reported", versionTextOutputFailureIsReportedTest)
}
