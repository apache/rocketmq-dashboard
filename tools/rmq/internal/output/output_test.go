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

package output

import (
	"bytes"
	"errors"
	"reflect"
	"strings"
	"testing"
)

func TestWriteFormats(t *testing.T) {
	value := struct {
		Name string `json:"name" yaml:"name"`
	}{Name: "demo"}
	table := &Table{
		Headers: []string{"NAME", "VALUE"},
		Rows:    [][]string{{"name", "demo"}},
	}

	tests := []struct {
		name   string
		format string
		want   string
	}{
		{name: "json", format: "json", want: "{\n  \"name\": \"demo\"\n}\n"},
		{name: "yaml", format: "yaml", want: "name: demo\n"},
		{name: "table", format: "table", want: "NAME  VALUE\nname  demo\n"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var got bytes.Buffer
			if err := Write(&got, tt.format, value, table); err != nil {
				t.Fatalf("Write() error = %v", err)
			}
			if got.String() != tt.want {
				t.Fatalf("Write() = %q, want %q", got.String(), tt.want)
			}
		})
	}
}

func TestWriteRejectsUnknownFormat(t *testing.T) {
	err := Write(&bytes.Buffer{}, "xml", struct{}{}, nil)
	if err == nil || !strings.Contains(err.Error(), `unsupported output format "xml"; choose table, json, or yaml`) {
		t.Fatalf("Write() error = %v", err)
	}
}

type failingWriter struct{}

func (failingWriter) Write([]byte) (int, error) {
	return 0, errors.New("write failed")
}

func TestWritePropagatesWriterError(t *testing.T) {
	err := Write(failingWriter{}, "json", struct{}{}, nil)
	if err == nil || !strings.Contains(err.Error(), "write failed") {
		t.Fatalf("Write() error = %v", err)
	}
}

func TestNewKeyValueTableSortsMapRows(t *testing.T) {
	got := NewKeyValueTable([]string{"KEY", "VALUE"}, map[string]string{
		"zeta":  "last",
		"alpha": "first",
	})
	want := &Table{
		Headers: []string{"KEY", "VALUE"},
		Rows: [][]string{
			{"alpha", "first"},
			{"zeta", "last"},
		},
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("NewKeyValueTable() = %#v, want %#v", got, want)
	}
}

func TestWriteTableRequiresDescriptor(t *testing.T) {
	err := Write(&bytes.Buffer{}, "table", struct{}{}, nil)
	if err == nil || !strings.Contains(err.Error(), "table descriptor") {
		t.Fatalf("Write() error = %v", err)
	}
}
