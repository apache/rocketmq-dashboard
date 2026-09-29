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
	"io"
	"net/http"
	"reflect"
	"slices"
	"strconv"
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

type completionTransport struct{ t *testing.T }

func (transport completionTransport) RoundTrip(*http.Request) (*http.Response, error) {
	transport.t.Fatal("completion must not contact Studio")
	return nil, nil
}

type completionInput struct{ t *testing.T }

func (input completionInput) Read([]byte) (int, error) {
	input.t.Fatal("completion must not read stdin")
	return 0, io.EOF
}

func completeTestApp(t *testing.T, setup func(*App), args ...string) ([]string, cobra.ShellCompDirective) {
	t.Helper()
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.In = completionInput{t}
	app.HTTP = &http.Client{Transport: completionTransport{t}}
	app.Store.Getenv = func(name string) string { t.Fatalf("unexpected environment lookup: %s", name); return "" }
	app.Store.HomeDir = func() (string, error) { t.Fatal("unexpected home directory lookup"); return "", nil }
	app.confirm = func(io.Reader, io.Writer, string, string, string) error {
		t.Fatal("completion must not prompt")
		return nil
	}
	if setup != nil {
		setup(app)
	}
	if code := app.Execute(append([]string{"__complete"}, args...)); code != 0 {
		t.Fatalf("completion exit %d: %s", code, stderr.String())
	}
	return parseCompletion(t, stdout.String())
}

func parseCompletion(t *testing.T, text string) ([]string, cobra.ShellCompDirective) {
	t.Helper()
	lines := strings.Split(strings.TrimSuffix(text, "\n"), "\n")
	value, err := strconv.Atoi(strings.TrimPrefix(lines[len(lines)-1], ":"))
	if err != nil || !strings.HasPrefix(lines[len(lines)-1], ":") {
		t.Fatalf("invalid completion response: %q", text)
	}
	return lines[:len(lines)-1], cobra.ShellCompDirective(value)
}

func TestCatalogEnumCompletionTest(t *testing.T) {
	for _, tool := range toolcatalog.Default().Tools {
		var visit func(toolcatalog.InputSchema)
		visit = func(schema toolcatalog.InputSchema) {
			for _, field := range schema.Fields {
				if field.Object != nil {
					visit(*field.Object)
					continue
				}
				if field.Kind != toolcatalog.StringField || len(field.Enum) == 0 {
					continue
				}
				t.Run(tool.CommandPath()+"/"+field.Flag, func(t *testing.T) {
					original := slices.Clone(field.Enum)
					got, directive := completeTestApp(t, nil, tool.CLI.Resource, tool.CLI.Verb, "--"+field.Flag, "")
					want := slices.Clone(original)
					slices.Sort(want)
					if !slices.Equal(got, want) || directive != cobra.ShellCompDirectiveNoFileComp {
						t.Fatalf("got %v / %v; want %v / NoFileComp", got, directive, want)
					}
					if !slices.Equal(field.Enum, original) {
						t.Fatal("completion mutated the shared catalog")
					}
				})
			}
		}
		visit(tool.InputSchema)
	}
	for _, example := range []struct {
		prefix string
		want   []string
	}{{"F", []string{"FIFO"}}, {"not-a-type", nil}} {
		got, directive := completeTestApp(t, nil, "topic", "list", "--type", example.prefix)
		if !slices.Equal(got, example.want) || directive != cobra.ShellCompDirectiveNoFileComp {
			t.Fatalf("prefix %q: %v / %v", example.prefix, got, directive)
		}
	}
}

func TestNestedCatalogEnumCompletionTest(t *testing.T) {
	schema := toolcatalog.InputSchema{Fields: []toolcatalog.Field{{Name: "settings", Kind: toolcatalog.ObjectField, Object: &toolcatalog.InputSchema{Fields: []toolcatalog.Field{{Name: "mode", Flag: "mode", Kind: toolcatalog.StringField, Enum: []string{"SECOND", "FIRST"}}}}}}}
	cmd := &cobra.Command{Use: "nested"}
	if _, err := bindSchemaArguments(cmd, "fixture", schema); err != nil {
		t.Fatal(err)
	}
	stdout := &bytes.Buffer{}
	cmd.SetOut(stdout)
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{"__complete", "--mode", "F"})
	if err := cmd.Execute(); err != nil {
		t.Fatal(err)
	}
	got, directive := parseCompletion(t, stdout.String())
	if !reflect.DeepEqual(got, []string{"FIRST"}) || directive != cobra.ShellCompDirectiveNoFileComp {
		t.Fatalf("nested enum: %v / %v", got, directive)
	}
}

func TestOutputFormatCompletionTest(t *testing.T) {
	for _, flag := range []string{"--output", "-o"} {
		got, directive := completeTestApp(t, nil, "topic", "list", flag, "")
		want := output.SupportedFormats()
		slices.Sort(want)
		if !slices.Equal(got, want) || directive != cobra.ShellCompDirectiveNoFileComp {
			t.Fatalf("%s: %v / %v", flag, got, directive)
		}
		for _, format := range got {
			if err := output.RequireFormat(format); err != nil {
				t.Fatal(err)
			}
		}
	}
	got, directive := completeTestApp(t, nil, "--output", "j")
	if !slices.Equal(got, []string{"json"}) || directive != cobra.ShellCompDirectiveNoFileComp {
		t.Fatalf("output prefix: %v / %v", got, directive)
	}
}
